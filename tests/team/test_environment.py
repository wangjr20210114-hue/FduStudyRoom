"""Behavioral regressions for the isolated member runner configuration."""
import json
import os
import subprocess
from pathlib import Path
import sys
import tempfile
import unittest
from unittest.mock import patch

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "tools/team"))
import environment
from policy import load


class SandboxEnvironmentTests(unittest.TestCase):
    def configuration(self, layer="backend", start=False):
        temporary = tempfile.TemporaryDirectory()
        self.addCleanup(temporary.cleanup)
        root = Path(temporary.name)
        worktree = root / "workspace"
        worktree.mkdir()
        context = {"module": "M02", "base": "a" * 40, "policy_root": str(root)}
        member = {"id": "b", "layer": layer, "modules": ["M02"]}
        # No real engine or host credentials are needed to inspect the emitted configuration.
        with patch.object(environment, "docker", return_value=["docker"]), patch.object(environment, "run", return_value="29.8.1") as invoke, patch.object(environment, "bind_identity", return_value="1000:1000"):
            environment.sandbox(root, worktree, load(ROOT), member, context, start=start)
        config = json.loads((worktree / ".campus/sandbox.local.json").read_text(encoding="utf-8"))
        return worktree, config, invoke.call_args_list

    def test_wsl_checkout_uses_its_docker_even_when_windows_docker_is_installed(self):
        with patch.object(environment.os, "name", "nt"), patch.object(environment.shutil, "which", return_value=r"C:\Docker\docker.exe"):
            command = environment.docker(r"\\wsl.localhost\CampusStudyRoom\opt\new-project")
        self.assertEqual(["wsl.exe", "-d", "CampusStudyRoom", "--exec", "docker"], command,
                         "A WSL checkout must use its own engine rather than Windows Docker Desktop")

    def test_local_checkout_keeps_available_native_docker(self):
        with patch.object(environment.shutil, "which", return_value="/usr/bin/docker"):
            self.assertEqual(["/usr/bin/docker"], environment.docker("/opt/new-project"))

    def test_maven_uses_the_populated_private_repository(self):
        _, config, commands = self.configuration(start=True)
        runner = config["services"]["runner"]
        self.assertIn("-Dmaven.repo.local=/cache/m2/repository", runner["environment"].get("MAVEN_ARGS", ""),
                      "Maven must consume the cache warmed for this member instead of /root/.m2")
        self.assertTrue(any(volume.get("target") == "/cache/m2" and volume["type"] == "volume" for volume in runner["volumes"]))

    def test_private_workflow_files_are_hidden_from_member_container(self):
        _, config, _ = self.configuration()
        self.assertTrue(any(value.startswith("/workspace/.campus:") and "ro" in value.split(":", 1)[1].split(",")
                            for value in config["services"]["runner"]["tmpfs"]),
                        "The read-only source bind must not expose host .campus credentials and workflow receipts")

    def test_mounts_never_add_docker_socket_or_host_credentials(self):
        for layer in ("backend", "frontend"):
            with self.subTest(layer=layer):
                worktree, config, _ = self.configuration(layer)
                runner = config["services"]["runner"]
                self.assertTrue(runner["read_only"])
                self.assertEqual(["ALL"], runner["cap_drop"])
                self.assertEqual(["no-new-privileges:true"], runner["security_opt"])
                self.assertFalse(runner.get("privileged", False))
                self.assertNotIn("env_file", runner)
                for volume in runner["volumes"]:
                    self.assertNotIn("docker.sock", volume["target"])
                    if volume["type"] == "bind":
                        self.assertTrue(Path(volume["source"]).resolve().is_relative_to(worktree.resolve()))
                        self.assertNotIn(".ssh", Path(volume["source"]).parts)
                if layer == "frontend":
                    self.assertEqual("none", runner["network_mode"])
                    self.assertNotIn("postgres", config["services"])
                else:
                    self.assertTrue(config["networks"]["private"]["internal"])
                    self.assertNotIn("ports", config["services"]["postgres"])

    @unittest.skipUnless(os.name == "posix", "The streamed helper executes inside the Linux runner")
    def test_sandbox_streamed_helper_repairs_both_private_cache_trees(self):
        _, _, commands = self.configuration(start=True)
        warming = next(call for call in commands if "--user" in call.args[0])
        self.assertEqual(["python3", "-", "/opt/warm/m2", "/cache/m2",
                          "/opt/warm/frontend/node_modules", "/workspace/frontend/node_modules"],
                         warming.args[0][-6:])
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            maven, private_maven = root / "warm-m2", root / "private-m2"
            node, private_node = root / "warm-node", root / "private-node"
            for tree in (maven, private_maven, node, private_node):
                tree.mkdir()
            (maven / "parent.pom").write_bytes(b"trusted pom")
            (private_maven / "parent.pom").write_bytes(b"")
            (node / ".bin").mkdir()
            (node / "package").mkdir()
            executable = node / "package/cli"
            executable.write_text("#!/bin/sh\nprintf 'streamed-bin-ready'\n", encoding="utf-8")
            executable.chmod(0o755)
            (node / ".bin/cli").symlink_to("../package/cli")
            result = subprocess.run([sys.executable, "-", str(maven), str(private_maven),
                                     str(node), str(private_node)], input=warming.kwargs["input"],
                                    capture_output=True, text=True)
            self.assertEqual(0, result.returncode, result.stderr)
            self.assertEqual(b"trusted pom", (private_maven / "parent.pom").read_bytes())
            command = subprocess.run([str(private_node / ".bin/cli")], capture_output=True,
                                     text=True, check=True)
            self.assertEqual("streamed-bin-ready", command.stdout)


if __name__ == "__main__":
    unittest.main()
