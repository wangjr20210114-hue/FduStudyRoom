"""Real filesystem regressions for interrupted private dependency warming."""
from pathlib import Path
import os
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import patch

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "tools/team"))
import cache


@unittest.skipUnless(os.name == "posix", "Cache repair executes inside the Linux runner")
class CacheRepairTests(unittest.TestCase):
    def setUp(self):
        temporary = tempfile.TemporaryDirectory()
        self.addCleanup(temporary.cleanup)
        self.root = Path(temporary.name)
        self.source = self.root / "warm"
        self.destination = self.root / "private"
        self.source.mkdir()
        self.destination.mkdir()

    def dependency(self, original, damaged):
        (self.source / "parent.pom").write_bytes(original)
        target = self.destination / "parent.pom"
        target.write_bytes(damaged)
        return target

    def test_zero_byte_dependency_is_repaired_without_deleting_extra_cache_entries(self):
        target = self.dependency(b"trusted parent pom", b"")
        extra = self.destination / "member-downloaded.jar"
        extra.write_bytes(b"keep private dependency")
        cache.repair_cache(self.source, self.destination)
        self.assertEqual(b"trusted parent pom", target.read_bytes(),
                         "Interrupted zero-byte cache entries must be repaired from the trusted image")
        self.assertEqual(b"keep private dependency", extra.read_bytes())
        stamp = target.stat().st_mtime_ns
        cache.repair_cache(self.source, self.destination)
        self.assertEqual(stamp, target.stat().st_mtime_ns, "Correct cache contents must remain unchanged")

    def test_same_size_corrupt_dependency_is_repaired(self):
        target = self.dependency(b"correct", b"corrupt")
        cache.repair_cache(self.source, self.destination)
        self.assertEqual(b"correct", target.read_bytes(),
                         "A matching size must not hide corrupted cache content")

    def test_interrupted_copy_preserves_existing_file_and_retry_repairs_it(self):
        target = self.dependency(b"complete trusted artifact", b"old damaged artifact")
        def interrupted(source, destination):
            destination.write(source.read(4))
            raise OSError("simulated interrupted cache write")
        with patch.object(cache, "_copy_file", side_effect=interrupted):
            with self.assertRaisesRegex(OSError, "simulated interrupted cache write"):
                cache.repair_cache(self.source, self.destination)
        self.assertEqual(b"old damaged artifact", target.read_bytes(),
                         "An interrupted repair must not truncate the existing cache entry")
        self.assertEqual(["parent.pom"], sorted(p.name for p in self.destination.iterdir()))
        cache.repair_cache(self.source, self.destination)
        self.assertEqual(b"complete trusted artifact", target.read_bytes())

    def test_relative_node_bin_symlink_remains_executable(self):
        package = self.source / "example/bin"
        package.mkdir(parents=True)
        executable = package / "example"
        executable.write_text("#!/bin/sh\nprintf 'cache-bin-ready'\n", encoding="utf-8")
        executable.chmod(0o755)
        (self.source / ".bin").mkdir()
        (self.source / ".bin/example").symlink_to("../example/bin/example")
        cache.repair_cache(self.source, self.destination)
        link = self.destination / ".bin/example"
        self.assertTrue(link.is_symlink())
        self.assertEqual("../example/bin/example", os.readlink(link))
        self.assertEqual(0o111, (self.destination / "example/bin/example").stat().st_mode & 0o111)
        result = subprocess.run([str(link)], capture_output=True, text=True, check=True)
        self.assertEqual("cache-bin-ready", result.stdout)

    def test_replace_failure_preserves_existing_file_and_retry_repairs_it(self):
        target = self.dependency(b"trusted artifact", b"original damaged artifact")
        with patch.object(cache.os, "replace", side_effect=OSError("simulated atomic replace failure")):
            with self.assertRaisesRegex(OSError, "simulated atomic replace failure"):
                cache.repair_cache(self.source, self.destination)
        self.assertEqual(b"original damaged artifact", target.read_bytes())
        self.assertEqual(["parent.pom"], sorted(p.name for p in self.destination.iterdir()))
        cache.repair_cache(self.source, self.destination)
        self.assertEqual(b"trusted artifact", target.read_bytes())

    def test_source_links_cannot_escape_the_trusted_tree(self):
        outside = self.root / "outside"
        outside.write_bytes(b"outside sentinel")
        link = self.source / "escaped"
        for target in (str(outside), "../outside"):
            with self.subTest(target=target):
                link.symlink_to(target)
                try:
                    with self.assertRaises(cache.CacheSafetyError):
                        cache.repair_cache(self.source, self.destination)
                    self.assertEqual([], list(self.destination.iterdir()))
                    self.assertEqual(b"outside sentinel", outside.read_bytes())
                finally:
                    link.unlink()

    def test_existing_destination_directory_link_cannot_redirect_writes(self):
        outside = self.root / "outside"
        outside.mkdir()
        (outside / "sentinel").write_bytes(b"outside sentinel")
        (self.source / "package").mkdir()
        (self.source / "package/dependency").write_bytes(b"trusted")
        (self.destination / "package").symlink_to(outside, target_is_directory=True)
        with self.assertRaises(cache.CacheSafetyError):
            cache.repair_cache(self.source, self.destination)
        self.assertEqual(["sentinel"], sorted(p.name for p in outside.iterdir()))
        self.assertEqual(b"outside sentinel", (outside / "sentinel").read_bytes())

    def test_destination_root_link_cannot_redirect_writes(self):
        outside = self.root / "outside"
        outside.mkdir()
        (self.source / "dependency").write_bytes(b"trusted")
        self.destination.rmdir()
        self.destination.symlink_to(outside, target_is_directory=True)
        with self.assertRaises(cache.CacheSafetyError):
            cache.repair_cache(self.source, self.destination)
        self.assertEqual([], list(outside.iterdir()))

    def test_file_link_is_replaced_without_modifying_its_external_target(self):
        outside = self.root / "outside"
        outside.write_bytes(b"outside sentinel")
        (self.source / "dependency").write_bytes(b"trusted")
        target = self.destination / "dependency"
        target.symlink_to(outside)
        cache.repair_cache(self.source, self.destination)
        self.assertFalse(target.is_symlink())
        self.assertEqual(b"trusted", target.read_bytes())
        self.assertEqual(b"outside sentinel", outside.read_bytes())

    def test_wrong_node_bin_link_is_repaired_and_correct_link_is_unchanged(self):
        (self.source / "package").mkdir()
        (self.source / "package/cli").write_bytes(b"executable")
        (self.source / ".bin").mkdir()
        (self.source / ".bin/cli").symlink_to("../package/cli")
        (self.destination / ".bin").mkdir()
        link = self.destination / ".bin/cli"
        link.symlink_to("../missing")
        cache.repair_cache(self.source, self.destination)
        self.assertEqual("../package/cli", os.readlink(link))
        self.assertEqual(b"executable", link.read_bytes())
        stamp = link.lstat().st_mtime_ns
        cache.repair_cache(self.source, self.destination)
        self.assertEqual(stamp, link.lstat().st_mtime_ns)


if __name__ == "__main__":
    unittest.main()
