"""Behavioral regressions for bounded, process-scoped host Git execution."""
import os
import contextlib
import io
import json
import shutil
from types import SimpleNamespace
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import patch

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "tools/team"))
from policy import PolicyError, git
import recording


class GitExecutionTests(unittest.TestCase):
    def setUp(self):
        temporary = tempfile.TemporaryDirectory()
        self.addCleanup(temporary.cleanup)
        self.home = Path(temporary.name)
        self.root = self.home / "repository"
        self.root.mkdir()
        self.global_config = self.home / "global.config"
        self.global_text = ("[core]\n\tlongpaths = false\n[http]\n\tpostBuffer = 524288000\n"
                            "[pack]\n\tthreads = 8\n\twindowMemory = 512m\n"
                            "[credential]\n\thelper = fixture-host-helper\n")
        self.global_config.write_text(self.global_text, encoding="utf-8")
        environment = patch.dict(os.environ, {"GIT_CONFIG_GLOBAL": str(self.global_config),
                                              "GIT_CONFIG_NOSYSTEM": "1"})
        environment.start()
        self.addCleanup(environment.stop)
        git(self.root, "init")

    def test_effective_limits_override_large_host_config_without_persisting(self):
        for key, expected in (("core.longpaths", "true"), ("http.postBuffer", "1048576"),
                              ("pack.threads", "1"), ("pack.windowMemory", "32m")):
            with self.subTest(key=key):
                self.assertEqual(expected, git(self.root, "config", "--get", key).decode().strip(),
                                 "Git must apply bounded settings to this process")
                local = git(self.root, "config", "--local", "--get", key, check=False)
                self.assertEqual(b"", local, "The helper must not mutate repository settings")
        self.assertEqual(self.global_text, self.global_config.read_text(encoding="utf-8"))

    def test_trust_is_limited_to_the_exact_repository_path(self):
        self.assertEqual([str(self.root.resolve())],
                         git(self.root, "config", "--get-all", "safe.directory").decode().splitlines(),
                         "Trust must not expand to wildcards or unrelated repositories")

    @unittest.skipUnless(os.name == "nt", "Native Windows Git long path regression")
    def test_worktree_checkout_supports_deep_windows_paths(self):
        git(self.root, "config", "user.name", "Fixture")
        git(self.root, "config", "user.email", "fixture@example.invalid")
        tracked = Path("nested") / ("n" * 80 + ".txt")
        (self.root / tracked).parent.mkdir()
        (self.root / tracked).write_text("expected deep worktree content", encoding="utf-8")
        git(self.root, "add", ".")
        git(self.root, "commit", "-m", "fixture baseline")
        checkout = self.home / ("deep-" + "x" * max(8, 190 - len(str(self.home)))) / "checkout"
        self.assertGreater(len(str(checkout / tracked)), 260)
        hook=self.root / ".git/hooks/post-checkout"
        hook.write_text("#!/bin/sh\ngit config --get core.longpaths > checkout-longpaths.txt\ngit config --get credential.helper > checkout-helper.txt\nprintf '%s' \"$GIT_CONFIG_GLOBAL\" > checkout-overlay.txt\n", encoding="utf-8")
        hook.chmod(0o755)
        failed = False
        try:
            git(self.root, "worktree", "add", "--detach", str(checkout), "HEAD")
        except (subprocess.CalledProcessError, PolicyError):
            failed = True
        diagnostics = "\n".join(p.read_text(encoding="utf-8") for p in (self.root / ".campus/git-diagnostics").glob("*.local"))
        self.assertFalse(failed, "Worktree mutation must support deep Windows paths without local Git config changes: " + diagnostics)
        self.assertEqual("expected deep worktree content", (checkout / tracked).read_text(encoding="utf-8"))
        self.assertEqual("true", (checkout / "checkout-longpaths.txt").read_text().strip())
        self.assertEqual("fixture-host-helper", (checkout / "checkout-helper.txt").read_text().strip(),
                         "The worktree child must retain the host credential helper")
        overlay = Path((checkout / "checkout-overlay.txt").read_text())
        self.assertFalse(overlay.exists(), "Process-only config overlays must disappear after Git returns")
        self.assertEqual(self.global_text, self.global_config.read_text(encoding="utf-8"))

    @unittest.skipUnless(os.name == "nt", "Native Windows Git mutation patch long path regression")
    def test_mutation_applies_staged_new_migration_at_deep_windows_path(self):
        # Keep fixture creation short; reproduce the deep path only at the mutation destination.
        # Match the member host: Windows Git defaults to short paths unless the invocation opts in.
        self.global_text = self.global_text.replace(chr(9) + 'longpaths = false' + chr(10), '')
        self.global_config.write_text(self.global_text, encoding='utf-8')
        location = self.home
        location.mkdir(parents=True, exist_ok=True)
        with tempfile.TemporaryDirectory(prefix='mutation-copy-', dir=location) as directory:
            policy_root = Path(directory) / ('policy-' + 'n' * 50)
            policy_root.mkdir()
            (policy_root / 'spec').mkdir()
            shutil.copyfile(ROOT / 'spec/modules.json', policy_root / 'spec/modules.json')
            repository = Path(directory) / 'repository'
            repository.mkdir()
            git(repository, 'init')
            git(repository, 'config', 'user.name', 'Fixture')
            git(repository, 'config', 'user.email', 'fixture@example.invalid')
            (repository / '.gitignore').write_text('.campus/\n', encoding='utf-8')
            git(repository, 'add', '.')
            git(repository, 'commit', '-m', 'fixture mutation baseline')
            base = git(repository, 'rev-parse', 'HEAD').decode().strip()
            tracked = Path('backend/resource-service/src/main/resources/db/modules/identity/migration') / 'V2026111601__new_mutation_migration.sql'
            (repository / tracked).parent.mkdir(parents=True)
            (repository / tracked).write_bytes(b'CREATE TABLE iam.fixture_long_path(id integer);\n')
            git(repository, 'add', '--', tracked.as_posix())
            original = git(repository, 'diff', '--binary', base)
            context = {'policy_root': str(policy_root), 'member': {'id': 'a', 'layer': 'backend', 'modules': ['M01']}, 'module': 'M01', 'base': base}
            recording.save(repository / '.campus/task.local', context)
            output = io.StringIO()
            try:
                with contextlib.redirect_stdout(output):
                    recording.mutation(policy_root, SimpleNamespace(worktree=repository))
            except PolicyError as error:
                diagnostics = chr(10).join(p.read_text(encoding='utf-8') for p in policy_root.glob('.campus/mutations/*/workspace/.campus/git-diagnostics/*.local'))
                self.fail('Mutation copy must apply a staged new migration on a deep Windows path: ' + str(error) + chr(10) + diagnostics)
            target = Path(json.loads(output.getvalue())['worktree'])
            self.assertGreater(len(str(target / tracked)), 260)
            self.assertEqual((repository / tracked).read_bytes(), (target / tracked).read_bytes())
            self.assertEqual(base, git(target, 'rev-parse', 'HEAD').decode().strip())
            self.assertEqual(original, git(repository, 'diff', '--binary', base), 'Original TDD inputs must remain unchanged')
            self.assertEqual(self.global_text, self.global_config.read_text(encoding='utf-8'))

    def test_failure_hides_authentication_url_and_retains_private_diagnostic(self):
        secret = "fixture-never-echo-token"
        address = "https://fixture:" + secret + "@127.0.0.1:1/private.git"
        with patch.dict(os.environ, {"GH_TOKEN": secret}):
            try:
                git(self.root, "ls-remote", address)
            except Exception as failure:
                error = failure
            else:
                self.fail("A closed loopback port must fail the Git operation")
        self.assertIsInstance(error, PolicyError, "Git errors need a safe, actionable public error")
        self.assertNotIn(secret, str(error))
        self.assertNotIn(address, str(error))
        logs = list((self.root / ".campus/git-diagnostics").glob("*.local"))
        self.assertEqual(1, len(logs), "A private diagnostic must retain sanitized Git stderr")
        diagnostic = logs[0].read_text(encoding="utf-8")
        self.assertIn("fatal", diagnostic)
        self.assertNotIn(secret, diagnostic)
        self.assertNotIn(address, diagnostic)
        if os.name == "posix":
            self.assertEqual(0o600, logs[0].stat().st_mode & 0o777)


if __name__ == "__main__":
    unittest.main()
