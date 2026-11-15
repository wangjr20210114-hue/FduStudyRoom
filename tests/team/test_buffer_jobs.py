import base64
import contextlib
import hashlib
import io
import importlib.util
import json
import shutil
import subprocess
import sys
import tempfile
import threading
import unittest
import uuid
from pathlib import Path
from unittest.mock import patch
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / 'tools/team'))
import buffer_job
from policy import PolicyError, git

spec = importlib.util.spec_from_file_location('verify_deployment', ROOT / 'scripts/verify-deployment.py')
deployment = importlib.util.module_from_spec(spec)
spec.loader.exec_module(deployment)


class BufferJobTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        (self.root / 'spec').mkdir()
        shutil.copyfile(ROOT / 'spec/modules.json', self.root / 'spec/modules.json')
        self.repo = self.root / 'repo'
        self.repo.mkdir()
        git(self.repo, 'init')
        git(self.repo, 'config', 'user.name', 'Fixture')
        git(self.repo, 'config', 'user.email', 'fixture@example.invalid')
        (self.repo / 'README.md').write_text('fixture\n', encoding='utf-8')
        git(self.repo, 'add', '.')
        git(self.repo, 'commit', '-m', 'fixture base')
        base = git(self.repo, 'rev-parse', 'HEAD').decode().strip()
        target = self.repo / 'backend/workflow-service/src/main/java/com/campus/notification/Fixture.java'
        target.parent.mkdir(parents=True)
        target.write_text('package com.campus.notification;\nclass Fixture {}\n', encoding='utf-8')
        git(self.repo, 'add', '.')
        source_patch = git(self.repo, 'diff', '--cached', '--binary')
        git(self.repo, 'reset', '--hard', base)
        def receipt(code):
            log = 'assertion failed: behavior' if code else 'all behavioral tests passed'
            return {'exit_code': code, 'command': ['test'], 'log': log,
                    'sha256': hashlib.sha256(log.encode()).hexdigest(), 'expected_assertion': 'behavior'}
        self.item = {'id': str(uuid.uuid4()), 'reviewer': 'human-reviewer', 'member_id': 'f',
                     'module_id': 'M06', 'layer': 'backend', 'patch': base64.b64encode(source_patch).decode(),
                     'metadata': {'base': base, 'requirements': ['SUP-01'], 'summary': 'Notification fixture',
                                  'evidence': {'red': receipt(1), 'green': receipt(0), 'mutation': receipt(1)}}}
        self.config = {'root': str(self.root), 'repository': str(self.repo), 'baseline': base,
                       'ciImage': 'fixture-ci', 'members': [{'id': 'f', 'layer': 'backend', 'modules': ['M06']}]}
        self.commands = []
        self.runner_code = 0
        self.fail_mailpit = False

    def fake_run(self, args, **kwargs):
        args = [str(arg) for arg in args]
        if args[0] != 'fixture-docker':
            return self.real_run(args, **kwargs)
        self.commands.append(args)
        code, output = 0, b''
        if args[1:3] == ['image', 'inspect']: output = b'sha256:fixture-ci\n'
        if args[1] == 'info': output = b'29.1.3\n'
        if args[1] == 'run' and '--name' in args and args[args.index('--name') + 1].endswith('-runner'):
            code, output = self.runner_code, b'behavioral runner output\n'
        if self.fail_mailpit and args[1] == 'run' and any('axllent/mailpit@sha256:' in arg for arg in args): code = 1
        return subprocess.CompletedProcess(args, code, output, b'')

    def run_verify(self):
        self.real_run = subprocess.run
        with patch.object(buffer_job, 'docker', return_value=['fixture-docker']), \
             patch.object(buffer_job, 'bind_identity', return_value='1000:1000'), \
             patch.object(buffer_job.subprocess, 'run', side_effect=self.fake_run):
            return buffer_job.verify(self.config, self.item)

    def test_backend_runner_gets_own_mailpit_and_separate_databases(self):
        result = self.run_verify()
        self.assertEqual('VERIFIED', result['state'])
        smtp = [c for c in self.commands if c[1] == 'run' and any('axllent/mailpit@sha256:' in a for a in c)]
        self.assertEqual(1, len(smtp), 'backend verification must start its own real Mailpit service')
        self.assertIn('--network-alias', smtp[0])
        self.assertEqual('smtp', smtp[0][smtp[0].index('--network-alias') + 1])
        self.assertEqual('128m', smtp[0][smtp[0].index('--memory') + 1])
        self.assertNotIn('-p', smtp[0])
        self.assertNotIn('--publish', smtp[0])
        self.assertTrue(any(c[1] == 'exec' and c[-2:] == ['/mailpit', 'readyz'] for c in self.commands))
        runner = next(c for c in self.commands if c[1] == 'run' and '--name' in c and c[c.index('--name') + 1].endswith('-runner'))
        for value in ['CAMPUS_TEST_SMTP_HOST=smtp', 'CAMPUS_TEST_SMTP_PORT=1025', 'CAMPUS_TEST_MAILPIT_URL=http://smtp:8025',
                      'CAMPUS_DB_URL=jdbc:postgresql://postgres:5432/module_test',
                      'CAMPUS_TEST_DB_URL=jdbc:postgresql://postgres:5432/campus_migration_probe']:
            self.assertIn(value, runner)
        self.assertFalse(any('docker.sock' in a for a in runner))
        removed = {c[-1] for c in self.commands if c[1:3] == ['rm', '-f']}
        self.assertEqual({c[c.index('--name') + 1] for c in self.commands if c[1] == 'run' and '--name' in c}, removed)
        self.assertTrue(all(self.item['id'] in name for name in removed))

    def test_failed_ci_preserves_input_without_creating_delivery_commit(self):
        self.runner_code = 1
        result = self.run_verify()
        self.assertEqual('VERIFY_FAILED', result['state'])
        home = self.root / '.campus/buffer/jobs' / self.item['id']
        self.assertEqual(self.config['baseline'], git(home / 'workspace', 'rev-parse', 'HEAD').decode().strip())
        self.assertFalse((home / 'verified.local').exists())
        self.assertEqual([], git(self.repo, 'branch', '--list', 'buffer/*').decode().splitlines())
        removed = [c[-1] for c in self.commands if c[1:3] == ['rm', '-f']]
        self.assertIn('fdu-ci-' + self.item['id'] + '-smtp', removed)

    def test_mailpit_start_failure_cleans_job_resources(self):
        self.fail_mailpit = True
        with self.assertRaises(PolicyError): self.run_verify()
        removed = [c[-1] for c in self.commands if c[1:3] == ['rm', '-f']]
        self.assertIn('fdu-ci-' + self.item['id'] + '-smtp', removed)
        self.assertIn('fdu-ci-' + self.item['id'] + '-db', removed)
        self.assertTrue(any(c[1:3] == ['network', 'rm'] for c in self.commands))

    def test_unreviewed_patch_never_launches_runner(self):
        self.item['reviewer'] = self.item['member_id']
        with self.assertRaisesRegex(PolicyError, 'HUMAN_REVIEW_REQUIRED'): self.run_verify()
        self.assertEqual([], self.commands)

    def test_frontend_runner_stays_offline_without_smtp_credentials(self):
        self.item['layer'] = 'frontend'
        self.config['members'][0]['layer'] = 'frontend'
        target = self.repo / 'frontend/src/modules/notification/fixture.ts'
        target.parent.mkdir(parents=True)
        target.write_text('export const fixture = true;\n', encoding='utf-8')
        git(self.repo, 'add', '.')
        self.item['patch'] = base64.b64encode(git(self.repo, 'diff', '--cached', '--binary')).decode()
        git(self.repo, 'reset', '--hard', self.config['baseline'])
        self.assertEqual('VERIFIED', self.run_verify()['state'])
        runner = next(c for c in self.commands if c[1] == 'run' and '--name' in c and c[c.index('--name') + 1].endswith('-runner'))
        self.assertEqual('none', runner[runner.index('--network') + 1])
        self.assertFalse(any('SMTP_' in arg or 'DB_PASSWORD=' in arg or 'MAILPIT_URL=' in arg for arg in runner))
        self.assertFalse(any('axllent/mailpit@sha256:' in arg for command in self.commands for arg in command))

    def test_gitee_authentication_uses_header_for_lookup_and_creation(self):
        remote = self.root / 'remote.git'
        git(remote.parent, 'init', '--bare', str(remote))
        branch = 'buffer/m06/' + self.item['id']
        git(self.repo, 'branch', branch)
        commit = git(self.repo, 'rev-parse', 'HEAD').decode().strip()
        config = {**self.config, 'enabled': True, 'remote': str(remote), 'giteeOwner': 'fixture',
                  'giteeRepo': 'fixture', 'branch': 'main', 'giteeApiToken': 'fixture-secret-only'}
        item = {**self.item, 'result': {'branch': branch, 'commit': commit}}
        requests = []
        def respond(request, **kwargs):
            requests.append(request)
            return io.BytesIO(json.dumps([] if len(requests) == 1 else {'html_url': 'https://gitee.com/fixture/fixture/pulls/1'}).encode())
        with patch.object(buffer_job.urllib.request, 'urlopen', side_effect=respond), \
             patch.object(buffer_job.urllib.request.OpenerDirector, 'open', side_effect=respond):
            result = buffer_job.publish(config, item)
        self.assertEqual('PUBLISHED', result['state'])
        self.assertEqual(2, len(requests))
        for request in requests:
            url = request if isinstance(request, str) else request.full_url
            self.assertNotIn('fixture-secret-only', url, 'API credentials must never enter request URLs')
            self.assertNotIn('access_token', url)
            self.assertEqual('Bearer fixture-secret-only', request.get_header('Authorization'))
            self.assertNotIn(b'access_token', request.data or b'')
        self.assertEqual(commit, git(remote, 'rev-parse', 'refs/heads/' + branch).decode().strip())

    def test_missing_api_token_keeps_publication_blocked_after_local_branch_push(self):
        remote = self.root / 'remote.git'
        git(remote.parent, 'init', '--bare', str(remote))
        branch = 'buffer/m06/' + self.item['id']
        git(self.repo, 'branch', branch)
        config = {**self.config, 'enabled': True, 'remote': str(remote), 'giteeApiToken': ''}
        item = {**self.item, 'result': {'branch': branch, 'commit': self.config['baseline']}}
        with patch.dict('os.environ', {'CAMPUS_GITEE_API_TOKEN': ''}), \
             patch.object(buffer_job.urllib.request, 'urlopen', side_effect=AssertionError('network forbidden')):
            result = buffer_job.publish(config, item)
        self.assertEqual('PUSH_FAILED', result['state'])
        self.assertEqual('BRANCH_PUSHED_MR_CREDENTIAL_MISSING', result['code'])

    def test_authorization_header_is_not_forwarded_to_redirect_target(self):
        reached = []
        class Handler(BaseHTTPRequestHandler):
            def do_GET(self):
                reached.append(self.path)
                self.send_response(302)
                self.send_header('Location', '/unexpected-target')
                self.end_headers()
            def log_message(self, *args): pass
        server = ThreadingHTTPServer(('127.0.0.1', 0), Handler)
        thread = threading.Thread(target=server.serve_forever, daemon=True)
        thread.start()
        try:
            opener = buffer_job.urllib.request.build_opener(buffer_job.NoCredentialRedirect())
            request = buffer_job.urllib.request.Request('http://127.0.0.1:' + str(server.server_port) + '/initial',
                                                       headers={'Authorization': 'Bearer synthetic-fixture-value'})
            with self.assertRaises(buffer_job.urllib.error.HTTPError): opener.open(request, timeout=2)
            self.assertEqual(['/initial'], reached)
        finally:
            server.shutdown()
            server.server_close()
            thread.join(timeout=2)

    def real_docker_services(self):
        """Explicit integration entry: python -m unittest test_buffer_jobs.BufferJobTests.real_docker_services.

        Requires the trusted fdu-campus-ci:baseline image and pinned PG/Mailpit images.
        Normal tooling discovery remains independent of a local Docker installation.
        """
        self.config['ciImage'] = 'fdu-campus-ci:baseline'
        probe = "python3 - <<'PY'\n" + r'''
import glob, json, os, smtplib, subprocess, urllib.request
from pathlib import Path
assert not Path('/var/run/docker.sock').exists()
assert os.environ['CAMPUS_TEST_SMTP_HOST'] == 'smtp'
with smtplib.SMTP(os.environ['CAMPUS_TEST_SMTP_HOST'], int(os.environ['CAMPUS_TEST_SMTP_PORT']), timeout=10) as smtp:
    smtp.sendmail('sender@example.test', ['recipient@example.test'], 'From: sender@example.test\r\nTo: recipient@example.test\r\nSubject: isolated worker probe\r\n\r\nreal SMTP data\r\n')
with urllib.request.urlopen(os.environ['CAMPUS_TEST_MAILPIT_URL'] + '/api/v1/messages', timeout=10) as response:
    messages = json.load(response)['messages']
assert len(messages) == 1 and messages[0]['Subject'] == 'isolated worker probe'
source = Path('/tmp/DatabaseProbe.java')
source.write_text(''' + '"""' + r'''
import java.sql.*;
class DatabaseProbe {
 static Connection connect(String prefix)throws Exception {
  return DriverManager.getConnection(System.getenv(prefix+"URL"),System.getenv(prefix+"USER"),System.getenv(prefix+"PASSWORD"));
 }
 public static void main(String[] args)throws Exception {
  Class.forName("org.postgresql.Driver");
  try(var app=connect("CAMPUS_DB_");var probe=connect("CAMPUS_TEST_DB_")) {
   try(var sql=app.createStatement()) {sql.execute("CREATE TABLE public.worker_probe(value text)");sql.execute("INSERT INTO public.worker_probe VALUES ('retained')");}
   try(var sql=probe.createStatement()) {sql.execute("DROP SCHEMA public CASCADE");sql.execute("CREATE SCHEMA public");}
   try(var sql=app.createStatement();var rows=sql.executeQuery("SELECT value FROM public.worker_probe")) {
    if(!rows.next() || !"retained".equals(rows.getString(1))) throw new AssertionError("migration probe corrupted application database");
   }
  }
 }
}
''' + '"""' + r''')
subprocess.run(['java', '-cp', glob.glob('/opt/warm/m2/repository/org/postgresql/postgresql/*/*.jar')[0], str(source)], check=True)
print('REAL_SMTP_DB_ISOLATION_PASS')
''' + '\nPY\n'
        identifier = self.item['id']
        for fail in (True, False):
            with patch.object(buffer_job, 'review_command', return_value=probe + ('\nexit 7\n' if fail else '')):
                result = buffer_job.verify(self.config, self.item)
            self.assertEqual('VERIFY_FAILED' if fail else 'VERIFIED', result['state'])
            home = self.root / '.campus/buffer/jobs' / identifier
            self.assertIn('REAL_SMTP_DB_ISOLATION_PASS', (home / 'ci.log').read_text())
            if fail:
                self.assertFalse((home / 'verified.local').exists())
                self.assertEqual(self.config['baseline'], git(home / 'workspace', 'rev-parse', 'HEAD').decode().strip())
            dc = buffer_job.docker(self.root)
            for suffix in ('-db', '-smtp', '-runner'):
                self.assertNotEqual(0, subprocess.run(dc + ['container', 'inspect', 'fdu-ci-' + identifier + suffix], capture_output=True).returncode)
            self.assertNotEqual(0, subprocess.run(dc + ['network', 'inspect', 'fdu-ci-' + identifier], capture_output=True).returncode)


class PublicDeploymentTests(unittest.TestCase):
    def setUp(self):
        self.version = {'commit': 'a' * 40, 'sequence': 2}
        self.responses = {'/': b'<html>StudyRoom</html>', '/health/resource': {'status': 'UP'},
                          '/health/workflow': {'status': 'UP'}, '/api/auth/config': {'available': True}}
        self.paths = []
        fixture = self
        class Handler(BaseHTTPRequestHandler):
            def do_GET(self):
                fixture.paths.append(self.path)
                value = fixture.version if self.path == '/version.json' else fixture.responses[self.path]
                self.send_response(200)
                self.end_headers()
                self.wfile.write(value if isinstance(value, bytes) else json.dumps(value).encode())
            def log_message(self, *args): pass
        self.server = ThreadingHTTPServer(('127.0.0.1', 0), Handler)
        self.thread = threading.Thread(target=self.server.serve_forever, daemon=True)
        self.thread.start()
        self.addCleanup(self.close)

    def close(self):
        self.server.shutdown()
        self.server.server_close()
        self.thread.join(timeout=2)

    def verify(self, receipt, commit='a' * 40, sequence=2):
        return deployment.verify(receipt, commit, sequence,
                                 base_url='http://127.0.0.1:' + str(self.server.server_port), attempts=1, delay=0)

    def test_matching_release_checks_all_five_real_http_endpoints(self):
        result = self.verify({'state': 'deployed', **self.version})
        self.assertEqual('deployed', result['state'])
        self.assertEqual({'/', '/health/resource', '/health/workflow', '/api/auth/config', '/version.json'}, set(self.paths))

    def test_receiver_skip_is_explicitly_superseded_and_names_running_commit(self):
        result = self.verify({'state': 'skipped_old_push', 'commit': 'a' * 40}, commit='b' * 40, sequence=1)
        self.assertEqual('superseded', result['state'])
        self.assertEqual('b' * 40, result['requested_commit'])
        self.assertEqual('a' * 40, result['running_commit'])

    def test_cli_records_superseded_in_action_summary_and_stdout(self):
        with tempfile.TemporaryDirectory() as directory:
            receipt = Path(directory) / 'receiver.json'
            summary = Path(directory) / 'summary.md'
            receipt.write_text(json.dumps({'state': 'skipped_old_push', 'commit': 'a' * 40}), encoding='utf-8')
            stdout = io.StringIO()
            actual_verify = deployment.verify
            def local_verify(receipt, commit, sequence):
                return actual_verify(receipt, commit, sequence,
                                     base_url='http://127.0.0.1:' + str(self.server.server_port), attempts=1, delay=0)
            with patch.object(deployment, 'verify', side_effect=local_verify), \
                 patch.object(sys, 'argv', ['verify-deployment.py', '--receiver-result', str(receipt), '--commit', 'b' * 40, '--sequence', '1']), \
                 patch.dict('os.environ', {'GITHUB_STEP_SUMMARY': str(summary)}), contextlib.redirect_stdout(stdout):
                deployment.main()
            self.assertEqual('superseded', json.loads(stdout.getvalue())['state'])
            self.assertIn('**superseded**', summary.read_text())
            self.assertIn('does not claim', summary.read_text())

    def test_newer_public_release_does_not_claim_requested_sha_is_running(self):
        result = self.verify({'state': 'deployed', 'commit': 'b' * 40, 'sequence': 1}, commit='b' * 40, sequence=1)
        self.assertEqual('superseded', result['state'])
        self.assertEqual('a' * 40, result['running_commit'])

    def test_wrong_version_or_unavailable_auth_cannot_pass(self):
        for changes in [{'commit': 'b' * 40, 'sequence': 2}, {'commit': 'a' * 40, 'sequence': 1}]:
            self.version = changes
            with self.assertRaisesRegex(ValueError, 'PUBLIC_DEPLOYMENT_VERIFICATION_FAILED'):
                self.verify({'state': 'deployed', 'commit': 'a' * 40, 'sequence': 2})
        self.version = {'commit': 'a' * 40, 'sequence': 2}
        self.responses['/api/auth/config'] = {'available': False}
        with self.assertRaisesRegex(ValueError, 'PUBLIC_DEPLOYMENT_VERIFICATION_FAILED'):
            self.verify({'state': 'deployed', **self.version})

    def test_skip_requires_receiver_commit_to_match_public_commit(self):
        with self.assertRaisesRegex(ValueError, 'PUBLIC_DEPLOYMENT_VERIFICATION_FAILED'):
            self.verify({'state': 'skipped_old_push', 'commit': 'c' * 40}, commit='b' * 40, sequence=1)

    def test_failed_receiver_is_rejected_before_http_requests(self):
        with self.assertRaisesRegex(ValueError, 'RECEIVER_DID_NOT_ACCEPT_RELEASE'):
            self.verify({'state': 'failed', **self.version})
        self.assertEqual([], self.paths)


if __name__ == '__main__': unittest.main()
