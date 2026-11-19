import json
import sys
import tempfile
import unittest
import zipfile
from pathlib import Path
from unittest.mock import patch

sys.path.insert(0,str(Path(__file__).resolve().parent))
import join
import pack

class OnboardingTest(unittest.TestCase):
    def test_each_packet_contains_only_its_own_secret_and_survives_repacking(self):
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp)
            bundle=root/"test.bundle"
            bundle.write_bytes(b"fixture")
            pack.pack(bundle,root/"private",root/"out")
            credentials=join.read(root/"private/slots.local")
            pack.pack(bundle,root/"private",root/"out")
            self.assertEqual(credentials,join.read(root/"private/slots.local"))
            for slot,module in join.SLOTS.items():
                with zipfile.ZipFile(root/"out"/f"FduStudyRoom-{slot}-{module}.zip") as packet:
                    self.assertEqual({"slot":slot,"access_code":credentials[slot]}, json.loads(packet.read("member.json")))
                    contents=b"".join(packet.read(name) for name in packet.namelist())
                    self.assertFalse(any(t.encode() in contents for other,t in credentials.items() if other!=slot))
                    self.assertIn(".agents/skills/campus-join/SKILL.md",packet.namelist())
                    self.assertNotIn(b"\r",packet.read("start.command"))

    def test_server_cannot_redirect_a_member_to_other_modules_or_versions(self):
        good=dict(id="a",modules=["M01"],admin=False,layer="backend",baseline=join.BASE)
        for wrong in ({"id":"b"},{"modules":["M02"]},{"admin":True},{"baseline":"other"},{"layer":"frontend"}):
            with patch.object(join,"buffer",return_value={**good,**wrong}),self.assertRaises(RuntimeError):
                join.profile("a","unused","backend")
        with patch.object(join,"buffer",return_value=good):
            self.assertEqual(good,join.profile("a","unused","backend"))

    def test_account_mismatch_is_rejected(self):
        with patch.object(join,"command"),patch.object(join,"value",return_value='{"login":"alice","id":57}'):
            for saved in ({"github":"bob"},{"github":"alice","github_id":58}):
                with self.assertRaises(RuntimeError):
                    join.account(saved)

    def test_inviter_checks_immutable_id_and_skips_pending_invites(self):
        import invitations
        rows = [{"member_id":"a","github_id":77,"github_login":"alice"}]
        with tempfile.TemporaryDirectory() as tmp:
            owner = Path(tmp)/"owner.local"
            join.save(owner,{"code":"fixture"})
            for pending,actual,invited in (([],77,True),([{"invitee":{"id":77}}],77,False),([],80,False)):
                calls = []
                def api(*args):
                    calls.append(args)
                    path=args[-1]
                    if path=="user":return {"login":"wangjr20210114-hue"}
                    if path.endswith("/invitations"):return [pending]
                    if path.endswith("/collaborators"):return [[]]
                    if path=="users/alice":return {"id":actual}
                    if args[:2]==("--method","PUT"):return {"invitee":{"id":77}}
                    raise AssertionError(args)
                with patch.object(invitations,"buffer",side_effect=[{"admin":True},rows]),patch.object(invitations,"gh",side_effect=api):
                    if actual==77:invitations.sync(owner)
                    else:
                        with self.assertRaises(RuntimeError):invitations.sync(owner)
                self.assertEqual(any(args[:2]==("--method","PUT") for args in calls),invited)

if __name__=="__main__":
    unittest.main()
