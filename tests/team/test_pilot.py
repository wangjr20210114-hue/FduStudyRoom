"""Scenario-only trial receipts must never complete their parent requirement."""
import copy
import hashlib
import importlib
import io
import json
import sys
import tempfile
import unittest
from contextlib import redirect_stdout
from pathlib import Path
from subprocess import CompletedProcess
from types import SimpleNamespace
from unittest.mock import patch

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "tools/team"))
from policy import PolicyError, evidence_valid
from recording import record


class ScenarioPilotTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.root = Path(self.temp.name)
        for relative in ("spec/modules.json", "spec/requirements.json", "spec/spec-network.json", "spec/specs/S040.md"):
            target = self.root / relative
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_bytes((ROOT / relative).read_bytes())
        self.work = self.root / ".campus/work"
        self.work.mkdir(parents=True)
        self.member = {"id": "d", "layer": "backend", "modules": ["M04"]}
        self.context = {"member": self.member, "module": "M04", "base": "a" * 40, "policy_root": str(self.root)}
        self.save(self.work / ".campus/task.local", self.context)
        self.output = self.work / ".campus/pilot.local"

    def tearDown(self):
        self.temp.cleanup()

    def save(self, path, data):
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(json.dumps(data), encoding="utf-8")

    def pilot(self):
        found = importlib.util.find_spec("pilot")
        self.assertIsNotNone(found, "scenario pilot resolver must exist before partial evidence can be recorded")
        module = importlib.import_module("pilot")
        self.assertTrue(callable(getattr(module, "resolve_pilot", None)), "scenario pilot scope needs an explicit resolver")
        return module

    def resolve(self, **changes):
        values = {"root": self.root, "member": self.member, "module": "M04", "scenario_id": "S040.SC02", "requirement": "M2-02"}
        values.update(changes)
        return self.pilot().resolve_pilot(**values)

    def args(self, stage="green", **changes):
        values = dict(worktree=str(self.work), stage=stage, test_command=["mvn", "test"], requirement="M2-02", scenario="S040.SC02", output=str(self.output), report=["/tmp/campus-reports/pilot.xml"], require_test=["Decision#rejectsSelfApproval"], assertion="self approval must be rejected", kind="unit", completion_mode="tdd")
        values.update(changes)
        return SimpleNamespace(**values)

    def run_record(self, args):
        negative = args.stage in ("red", "mutation")
        xml = b'<testsuite tests="1"><testcase classname="Decision" name="rejectsSelfApproval">' + (b'<failure>self approval must be rejected</failure>' if negative else b'') + b'</testcase></testsuite>'
        calls = []
        def execute(argv, **kwargs):
            calls.append(argv)
            if "cat" in argv:
                return CompletedProcess(argv, 0, xml, b"")
            return CompletedProcess(argv, 1 if negative and "timeout" in argv else 0, b"pilot test run", b"")
        source = {"sha256": "b" * 64, "product_sha256": "c" * 64}
        with patch("recording.docker", return_value=["fixture-docker"]), patch("recording.source_snapshot", return_value=source), redirect_stdout(io.StringIO()):
            record(self.root, args, execute)
        return json.loads(self.output.read_text(encoding="utf-8")), calls

    def test_source_matches_authoritative_scenario_and_records_parent_without_completion(self):
        scope = self.resolve()
        self.assertEqual("scenario_pilot", scope["scope"])
        self.assertEqual("partial", scope["status"])
        self.assertEqual("M2-02", scope["parent_requirement"])
        self.assertEqual("S040.SC02", scope["scenario_id"])
        self.assertIn("申请人不能审批自己的申请", scope["source"]["scenario"]["overview"])
        self.assertEqual(hashlib.sha256((self.root / "spec/spec-network.json").read_bytes()).hexdigest(), scope["source"]["network_sha256"])
        self.assertIn("IAM-05", scope["integration_dependencies"])
        self.assertNotIn("requirements", scope)

    def test_pilot_wrapper_and_extracted_receipt_are_rejected_by_formal_validator(self):
        data, _ = self.run_record(self.args())
        for value in (data, data["scenarios"]["S040.SC02"]):
            with self.subTest(value=list(value)), self.assertRaisesRegex(PolicyError, "SCENARIO_PILOT_IS_NOT_FORMAL"):
                evidence_valid(value, ["M2-02"], strict=True)

    def test_member_context_cannot_reuse_another_members_pilot_receipt(self):
        self.run_record(self.args())
        self.context["member"]["id"] = "another-member"
        self.save(self.work / ".campus/task.local", self.context)
        with self.assertRaisesRegex(PolicyError, "PILOT_RECEIPT_CONTEXT_MISMATCH"):
            self.run_record(self.args())

    def test_partial_source_discloses_all_criteria_and_preserves_original_scenario(self):
        scope = self.resolve()
        self.assertEqual(["M2-02.AC2"], scope["source"]["scenario"]["acceptance_refs"])
        self.assertEqual(3, len(scope["source"]["parent_requirement"]["acceptance"]))
        self.assertIn("S039", scope["depends_on"])
        self.assertEqual("partial", scope["status"])

    def test_source_mismatch_is_rejected_before_container_execution(self):
        self.run_record(self.args())
        (self.root / "spec/specs/S040.md").write_bytes(b"updated scene source")
        with patch("recording.source_snapshot") as snapshot, patch("recording.docker") as docker:
            with self.assertRaisesRegex(PolicyError, "PILOT_SOURCE_CHANGED"):
                record(self.root, self.args())
            snapshot.assert_not_called()
            docker.assert_not_called()

    def test_unknown_scene_wrong_parent_module_layer_and_unassigned_member_are_rejected(self):
        self.pilot()
        for changes in ({"scenario_id": "S040.SC99"}, {"requirement": "M3-06"}, {"requirement": "M04"}, {"module": "M05"}, {"member": {**self.member, "modules": ["M05"]}}, {"member": {**self.member, "layer": "coordinator"}}):
            with self.subTest(changes=changes), self.assertRaises(PolicyError):
                self.resolve(**changes)

    def test_scope_hash_changes_when_authoritative_source_changes(self):
        first = self.resolve()
        network = self.root / "spec/spec-network.json"
        value = json.loads(network.read_text(encoding="utf-8"))
        spec = next(s for s in value["specifications"] if s["id"] == "S040")
        spec["scenarios"][1]["overview"] += " updated source"
        self.save(network, value)
        self.assertNotEqual(first["source"]["sha256"], self.resolve()["source"]["sha256"])

    def test_scenario_run_uses_fresh_container_report_and_never_creates_requirement_receipt(self):
        data, calls = self.run_record(self.args())
        self.assertEqual("scenario_pilot", data.get("scope"), "a partial scenario must not become a full requirement receipt")
        self.assertNotIn("requirements", data)
        self.assertNotIn("module", data)
        receipt = data["scenarios"]["S040.SC02"]
        self.assertEqual("M2-02", receipt["parent_requirement"])
        self.assertEqual(2, receipt["version"])
        self.assertEqual("PASS", receipt["stages"]["green"]["result"])
        self.assertTrue(any("rm" in argv and "--" in argv for argv in calls))
        self.assertTrue(any("timeout" in argv and "mvn" in argv for argv in calls))
        with self.assertRaises(PolicyError):
            evidence_valid(data, ["M2-02"], strict=True)

    def test_pilot_refuses_to_mix_with_formal_requirement_receipts_before_running(self):
        self.save(self.output, {"requirements": {"M2-02": {}}})
        before = self.output.read_bytes()
        with self.assertRaisesRegex(PolicyError, "PILOT.*MIX|MIX.*PILOT"):
            self.run_record(self.args())
        self.assertEqual(before, self.output.read_bytes())

    def test_formal_recorder_refuses_pilot_output_before_running(self):
        self.save(self.output, {"scope": "scenario_pilot", "scenarios": {}})
        with self.assertRaisesRegex(PolicyError, "PILOT.*MIX|MIX.*PILOT"):
            self.run_record(self.args(scenario=None))

    def test_module_run_cannot_be_presented_as_a_scenario_trial(self):
        with self.assertRaises(PolicyError):
            self.run_record(self.args(requirement="M04", stage="check", report=[]))
        self.assertFalse(self.output.exists())

    def test_scope_drift_cannot_overwrite_previous_pilot_evidence(self):
        self.run_record(self.args())
        before = self.output.read_bytes()
        scene = self.root / "spec/specs/S040.md"
        scene.write_text(scene.read_text(encoding="utf-8") + "\nsource changed\n", encoding="utf-8")
        with self.assertRaisesRegex(PolicyError, "PILOT_SOURCE_CHANGED"):
            self.run_record(self.args())
        self.assertEqual(before, self.output.read_bytes())

    def test_red_records_observed_failure_without_marking_parent_done(self):
        data, _ = self.run_record(self.args(stage="red"))
        self.assertNotIn("requirements", data, "scenario RED must not create canonical requirement evidence")
        receipt = data["scenarios"]["S040.SC02"]
        self.assertEqual("FAIL", receipt["stages"]["red"]["result"])
        self.assertIn("self approval must be rejected", receipt["stages"]["red"]["log"])


if __name__ == "__main__":
    unittest.main()
