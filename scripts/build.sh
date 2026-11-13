#!/bin/sh
set -eu
campus_python=python3
if ! command -v python3 >/dev/null 2>&1 || ! python3 -c 'import sys; sys.exit(0 if sys.version_info >= (3,12) else 1)' >/dev/null 2>&1; then
  if command -v brew >/dev/null 2>&1; then
    brew install python@3.13
    campus_python="$(brew --prefix python@3.13)/bin/python3.13"
  else
    printf '%s\n' 'Python 3.12 or newer required: https://www.python.org/downloads/ ; retry after installation.' >&2
    exit 2
  fi
fi
case "${1:-}" in --buffer) exec "$campus_python" "$(dirname "$0")/team.py" build --install --start --buffer ;; *) exec "$campus_python" "$(dirname "$0")/team.py" build --install --start ;; esac
