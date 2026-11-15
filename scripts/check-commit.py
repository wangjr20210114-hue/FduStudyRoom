#!/usr/bin/env python3
"""Require a typed title and bullet body for team commits."""
import re,sys
from pathlib import Path

def validate(text):
    lines=text.strip().splitlines()
    if len(lines)<3 or not re.fullmatch(r'(feat|fix|refactor|docs|test|build|ci|chore|perf|style)(\([A-Za-z0-9_.-]{1,48}\))?: [^\r\n]+',lines[0]) or lines[1].strip():raise ValueError('Use type(scope): title, a blank line, then - detail')
    if not any(re.fullmatch(r'- \S.*',line) for line in lines[2:]):raise ValueError('At least one bullet is required')
    if any(line.strip() and not line.startswith('- ') and not re.fullmatch(r'[A-Za-z-]+: .+',line) for line in lines[2:]):raise ValueError('Body requires bullet points or trailers')
if __name__=='__main__':validate(Path(sys.argv[1]).read_text(encoding='utf-8'))
