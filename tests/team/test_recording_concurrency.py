"""Exercise the real recorder in competing processes; Docker and source inputs are fixtures."""
import io
import json
import subprocess
import sys
import tempfile
import time
import unittest
from contextlib import redirect_stdout
from pathlib import Path
from types import SimpleNamespace
from unittest.mock import patch

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "tools/team"))


def write(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value), encoding="utf-8")


def worker(config_path):
    import recording
    from policy import PolicyError
    config = json.loads(Path(config_path).read_text(encoding="utf-8"))
    if "lock_timeout" in config:
        recording.RECEIPT_LOCK_TIMEOUT_SECONDS = config["lock_timeout"]
    args = SimpleNamespace(**config["args"])
    negative = args.stage in ("red", "mutation") and not config.get("existing_behavior")
    report = (b'<testsuite tests="1"><testcase classname="Decision" name="rejectsSelfApproval">'
              + (b'<failure>self approval must be rejected</failure>' if negative else b'')
              + b'</testcase></testsuite>')

    def execute(argv, **kwargs):
        if "timeout" in argv:
            Path(config["entered"]).touch()
            deadline = time.monotonic() + 15
            while not Path(config["release"]).exists():
                if time.monotonic() > deadline:
                    raise RuntimeError("fixture barrier was not released")
                time.sleep(0.01)
        return subprocess.CompletedProcess(argv, 0 if "cat" in argv or not negative or "timeout" not in argv else 1,
                                           report if "cat" in argv else b"fixture tests ran", b"")

    def snapshot(*args, **kwargs):
        return {"sha256": "b" * 64, "product_sha256": "c" * 64}

    try:
        with patch("recording.docker", return_value=["fixture-docker"]), patch("recording.source_snapshot", side_effect=snapshot), redirect_stdout(io.StringIO()):
            recording.record(ROOT, args, execute)
    except PolicyError as error:
        print(str(error))
        return 2
    return 0


class ReceiptConcurrencyTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.root = Path(self.temp.name)
        for name in ("spec/modules.json", "spec/requirements.json", "spec/spec-network.json", "spec/specs/S040.md"):
            target = self.root / name
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_bytes((ROOT / name).read_bytes())
        self.work = self.root / ".campus/work"
        self.mutant = self.root / ".campus/mutant"
        context = {"member": {"id": "d", "layer": "backend", "modules": ["M04"]},
                   "module": "M04", "base": "a" * 40, "policy_root": str(self.root)}
        write(self.work / ".campus/task.local", context)
        write(self.mutant / ".campus/task.local", {**context, "mode": "mutation", "parent_worktree": str(self.work), "parent_source_sha256": "b" * 64})
        self.output = self.work / ".campus/tdd.local"
        self.processes = []
        self.counter = 0

    def tearDown(self):
        for process, config in self.processes:
            Path(config["release"]).touch()
            if process.poll() is None:
                process.kill()
            process.communicate(timeout=5)
        self.temp.cleanup()

    def start(self, stage, scenario=None, **options):
        self.counter += 1
        marker = self.root / ("worker-" + str(self.counter))
        args = dict(worktree=str(self.mutant if stage == "mutation" else self.work), stage=stage,
                    test_command=["mvn", "test"], requirement="M04" if stage in ("check", "regression") else "M2-02",
                    scenario=scenario, output=str(self.output), report=[] if stage == "check" else ["/tmp/campus-reports/concurrent.xml"],
                    require_test=["Decision#rejectsSelfApproval"], assertion="self approval must be rejected", kind="unit", completion_mode="tdd")
        config = {"args": args, "entered": str(marker.with_suffix(".entered")), "release": str(marker.with_suffix(".release")), **options}
        path = marker.with_suffix(".json")
        write(path, config)
        process = subprocess.Popen([sys.executable, str(Path(__file__).resolve()), "--worker", str(path)], stdout=subprocess.PIPE, stderr=subprocess.PIPE)
        self.processes.append((process, config))
        return process, config

    def entered(self, run):
        process, config = run
        deadline = time.monotonic() + 7
        while not Path(config["entered"]).exists():
            if process.poll() is not None:
                stdout, stderr = process.communicate()
                self.fail("recorder exited before test barrier: " + (stdout + stderr).decode(errors="replace"))
            if time.monotonic() > deadline:
                self.fail("another recorder could not enter its test command while a test was running")
            time.sleep(0.01)

    def finish(self, run, code=0, reason=None):
        process, config = run
        Path(config["release"]).touch()
        stdout, stderr = process.communicate(timeout=8)
        self.assertEqual(code, process.returncode, (stdout + stderr).decode(errors="replace"))
        if reason:
            self.assertIn(reason, stdout.decode(errors="replace"))

    def receipt(self):
        return json.loads(self.output.read_text(encoding="utf-8"))

    def stage_map(self, data, scenario=None, module=False):
        return data["module"] if module else data["scenarios"][scenario]["stages"] if scenario else data["requirements"]["M2-02"]

    def test_different_stages_merge_after_reverse_completion_without_locking_tests(self):
        for scenario, first_stage, second_stage in ((None, "green", "mutation"), ("S040.SC02", "green", "mutation"), (None, "check", "regression")):
            with self.subTest(scenario=scenario, stages=(first_stage, second_stage)):
                if self.output.exists():
                    self.output.unlink()
                older = self.start(first_stage, scenario)
                self.entered(older)
                newer = self.start(second_stage, scenario)
                self.entered(newer)
                self.finish(newer)
                self.finish(older)
                stages = self.stage_map(self.receipt(), scenario, first_stage == "check")
                self.assertIn(second_stage, stages, "a slower recorder must preserve the other stage committed during its test run")
                self.assertIn(first_stage, stages)

    def test_older_same_stage_cannot_replace_newer_committed_run_and_keeps_evidence(self):
        older = self.start("green")
        self.entered(older)
        newer = self.start("green")
        self.entered(newer)
        self.finish(newer)
        accepted = self.output.read_bytes()
        self.finish(older, 2, "STALE_RECEIPT_RUN")
        self.assertEqual(accepted, self.output.read_bytes(), "late old run must leave the newer receipt intact")
        runs = [json.loads(path.read_text(encoding="utf-8")) for path in self.output.parent.glob("runs/*/run.local")]
        self.assertEqual(2, len(runs), "rejected evidence still needs its own immutable run")
        rejected = [run for run in runs if run.get("receipt_commit", {}).get("status") == "rejected"]
        self.assertEqual(1, len(rejected), "the rejected run must explicitly explain why it was not committed")
        self.assertIn("STALE_RECEIPT_RUN", rejected[0]["receipt_commit"]["reason"])
        self.assertEqual("PASS", rejected[0]["result"], "receipt conflict must preserve the actual test result")

    def test_existing_behavior_alias_cannot_overwrite_a_newer_green(self):
        older = self.start("red", existing_behavior=True)
        self.entered(older)
        newer = self.start("green")
        self.entered(newer)
        self.finish(newer)
        accepted = self.output.read_bytes()
        self.finish(older, 2, "STALE_RECEIPT_RUN")
        self.assertEqual(accepted, self.output.read_bytes())

    def test_scope_is_revalidated_after_another_scope_commits(self):
        for old_scope, new_scope in ((None, "S040.SC02"), ("S040.SC02", None)):
            with self.subTest(old_scope=old_scope):
                if self.output.exists():
                    self.output.unlink()
                older = self.start("green", old_scope)
                self.entered(older)
                newer = self.start("green", new_scope)
                self.entered(newer)
                self.finish(newer)
                accepted = self.output.read_bytes()
                self.finish(older, 2, "PILOT_FORMAL_RECEIPT_MIXING_FORBIDDEN")
                self.assertEqual(accepted, self.output.read_bytes(), "fresh scope checks must run inside the commit lock")

    def test_commit_waits_for_a_busy_lock_then_merges_after_its_owner_releases_it(self):
        first = self.start("green")
        self.entered(first)
        self.finish(first)
        accepted = self.output.read_bytes()
        run = self.start("mutation")
        self.entered(run)
        lock = self.output.with_name(self.output.name + ".lock")
        lock.mkdir()
        owner = lock / "owner.local"
        owner.write_text("active recorder", encoding="utf-8")
        Path(run[1]["release"]).touch()
        time.sleep(0.15)
        self.assertIsNone(run[0].poll(), "a busy short lock should wait for its owner")
        self.assertEqual(accepted, self.output.read_bytes(), "no receipt write may bypass an active lock")
        owner.unlink()
        lock.rmdir()
        self.finish(run)
        stages = self.stage_map(self.receipt())
        self.assertIn("green", stages)
        self.assertIn("mutation", stages)

    def test_busy_commit_lock_times_out_without_removing_the_active_lock(self):
        run = self.start("green", lock_timeout=0.2)
        self.entered(run)
        lock = self.output.with_name(self.output.name + ".lock")
        lock.mkdir()
        owner = lock / "owner.local"
        owner.write_text("active recorder", encoding="utf-8")
        started = time.monotonic()
        self.finish(run, 2, "RECEIPT_LOCK_BUSY")
        self.assertLess(time.monotonic() - started, 4, "lock wait must be bounded")
        self.assertEqual("active recorder", owner.read_text(encoding="utf-8"), "a timeout cannot reclaim somebody else's live lock")
        self.assertFalse(self.output.exists())
        evidence = list(self.output.parent.glob("runs/*/run.local"))
        self.assertEqual(1, len(evidence))
        self.assertEqual("rejected", json.loads(evidence[0].read_text(encoding="utf-8"))["receipt_commit"]["status"])


if __name__ == "__main__":
    if len(sys.argv) > 1 and sys.argv[1] == "--worker":
        sys.exit(worker(sys.argv[2]))
    unittest.main()
