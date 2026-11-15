#!/usr/bin/env python3
"""Verify receiver disposition and the publicly served application version."""
import argparse
import json
import os
import re
import time
import urllib.request
from pathlib import Path


def verify(receipt, commit, sequence, base_url='https://studyroom.seatraces.com', attempts=36, delay=5):
    if not re.fullmatch('[0-9a-f]{40}', commit) or type(sequence) is not int or sequence < 0:
        raise ValueError('INVALID_EXPECTED_RELEASE')
    if receipt.get('state') not in ('deployed', 'skipped_old_push'):
        raise ValueError('RECEIVER_DID_NOT_ACCEPT_RELEASE')
    if receipt['state'] == 'deployed' and (receipt.get('commit') != commit or receipt.get('sequence') != sequence):
        raise ValueError('RECEIVER_RELEASE_MISMATCH')
    if not re.fullmatch('[0-9a-f]{40}', receipt.get('commit', '')):
        raise ValueError('INVALID_RECEIVER_COMMIT')
    error = None
    for attempt in range(attempts):
        try:
            values = {}
            for path in ('/', '/health/resource', '/health/workflow', '/api/auth/config', '/version.json'):
                with urllib.request.urlopen(base_url.rstrip('/') + path, timeout=10) as response:
                    body = response.read(1024 * 1024)
                    if response.status != 200 or not body:
                        raise ValueError('PUBLIC_ENDPOINT_UNAVAILABLE')
                    if path != '/': values[path] = json.loads(body)
            if any(values[path].get('status') != 'UP' for path in ('/health/resource', '/health/workflow')):
                raise ValueError('PUBLIC_SERVICE_NOT_UP')
            if values['/api/auth/config'].get('available') is not True:
                raise ValueError('PUBLIC_AUTH_NOT_AVAILABLE')
            version = values['/version.json']
            running_commit, running_sequence = version.get('commit'), version.get('sequence')
            if not isinstance(running_commit, str) or not re.fullmatch('[0-9a-f]{40}', running_commit) or type(running_sequence) is not int:
                raise ValueError('INVALID_PUBLIC_VERSION')
            if running_sequence < sequence or (running_sequence == sequence and running_commit != commit):
                raise ValueError('PUBLIC_VERSION_MISMATCH')
            if receipt['state'] == 'skipped_old_push' and running_commit != receipt['commit']:
                raise ValueError('PUBLIC_RECEIVER_VERSION_MISMATCH')
            state = 'superseded' if receipt['state'] == 'skipped_old_push' or running_sequence > sequence else 'deployed'
            return {'state': state, 'requested_commit': commit, 'requested_sequence': sequence,
                    'running_commit': running_commit, 'running_sequence': running_sequence}
        except (OSError, ValueError, KeyError, TypeError) as failure:
            error = failure
            if attempt + 1 < attempts: time.sleep(delay)
    raise ValueError('PUBLIC_DEPLOYMENT_VERIFICATION_FAILED') from error


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--receiver-result', required=True)
    parser.add_argument('--commit', required=True)
    parser.add_argument('--sequence', type=int, required=True)
    args = parser.parse_args()
    result = verify(json.loads(Path(args.receiver_result).read_text(encoding='utf-8')), args.commit, args.sequence)
    print(json.dumps(result))
    summary = os.environ.get('GITHUB_STEP_SUMMARY')
    if summary:
        text = (f"Deployment result: **{result['state']}**. Requested `{result['requested_commit']}` "
                f"(sequence {result['requested_sequence']}); running `{result['running_commit']}` "
                f"(sequence {result['running_sequence']}).\n")
        if result['state'] == 'superseded': text += 'This run does not claim that its requested commit was deployed.\n'
        with Path(summary).open('a', encoding='utf-8') as output: output.write(text)


if __name__ == '__main__': main()
