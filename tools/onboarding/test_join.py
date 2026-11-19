import json
import os
import sys
import subprocess
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
                    self.assertIn(".agents/skills/campus-join/scripts/join.py",packet.namelist())
                    self.assertIn(".agents/skills/campus-join/references/host.md",packet.namelist())
                    self.assertFalse(any(n.endswith((".cmd",".command")) for n in packet.namelist()))
                    folder = root/("kit-" + slot)
                    packet.extractall(folder)
                    helper = folder/join.ENTRY/"scripts/join.py"
                    info = subprocess.check_output([sys.executable, "-X","utf8",helper,"status"],text=True,encoding="utf-8")
                    self.assertEqual(json.loads(info)["module"],module)
                    self.assertNotIn(credentials[slot],info)
                    repo = root/("checkout-" + slot)
                    (repo/".git/info").mkdir(parents=True)
                    with patch.object(join,"KIT",folder):
                        join.install_entry(repo)
                        join.install_entry(repo)
                    join.save(repo/".campus/member.local",{"identity":slot,"buffer_url":join.SITE,"token":credentials[slot]})
                    info = subprocess.check_output([sys.executable,"-X","utf8",repo/join.ENTRY/"scripts/join.py","status"],text=True,encoding="utf-8")
                    self.assertEqual(json.loads(info)["project"],str(repo))
                    self.assertFalse(json.loads(info)["prepared"])

    def test_server_cannot_redirect_a_member_to_other_modules_or_versions(self):
        good=dict(id="a",modules=["M01"],admin=False,layer="backend",baseline=join.BASE)
        for wrong in ({"id":"b"},{"modules":["M02"]},{"admin":True},{"baseline":"other"},{"layer":"frontend"}):
            with patch.object(join,"buffer",return_value={**good,**wrong}),self.assertRaises(RuntimeError):
                join.profile("a","unused","backend")
        with patch.object(join,"buffer",return_value=good):
            self.assertEqual(good,join.profile("a","unused","backend"))

    def test_account_mismatch_is_rejected(self):
        authenticated = subprocess.CompletedProcess([],0,stdout='{"login":"alice","id":57}')
        with patch.object(join,"command",return_value=authenticated):
            for saved in ({"github":"bob"},{"github":"alice","github_id":58}):
                with self.assertRaises(RuntimeError):
                    join.account(saved)
            with self.assertRaisesRegex(join.MemberAction,"CONFIRM_GITHUB_ACCOUNT alice"):
                join.account({})
            self.assertEqual({"login":"alice","id":57},join.account({},"ALICE"))

    def test_failed_invitation_resumes_without_interactive_input(self):
        from contextlib import ExitStack, redirect_stdout
        from io import StringIO
        with tempfile.TemporaryDirectory() as tmp, ExitStack() as stack:
            base = Path(tmp)
            kit, root = base/"kit", base/"a"
            token = "x"*32
            join.save(kit/"member.json",{"slot":"a","access_code":token})
            stack.enter_context(patch.object(join,"KIT",kit))
            stack.enter_context(patch.object(join,"ROOT_HOME",base))
            stack.enter_context(patch("builtins.input",side_effect=AssertionError("terminal prompt")))
            for name in ("checkout","github_transport","profile","buffer","install_entry","command"):
                stack.enter_context(patch.object(join,name))
            stack.enter_context(patch.object(join,"account",return_value={"login":"alice","id":57}))
            stack.enter_context(patch.object(join,"development_tools",return_value={}))
            stack.enter_context(patch.object(join,"repository_access",side_effect=[False,True]))
            with self.assertRaisesRegex(join.MemberAction,"INVITATION_PENDING"):
                join.main(["prepare","--github","alice"])
            self.assertTrue((root/".campus/member.local").is_file())
            self.assertFalse((root/".campus/join-ready.local").exists())
            join.main(["prepare"])
            result = StringIO()
            with redirect_stdout(result):
                join.main(["status"])
            self.assertTrue(json.loads(result.getvalue())["prepared"])
            self.assertNotIn(token,result.getvalue())

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

    def test_network_setting(self):
        import subprocess
        with tempfile.TemporaryDirectory() as tmp:
            directory=Path(tmp)
            root=directory/"member"
            other=directory/"unrelated"
            for repo in (root,other):
                subprocess.run(["git","init","--quiet",repo],check=True)
            env={**os.environ,"GIT_CONFIG_GLOBAL":str(directory/"global.gitconfig")}
            original=subprocess.run
            def simulate(args, **kw):
                if "ls-remote" not in args:
                    return original(args,**kw)
                if "-c" in args:
                    return subprocess.CompletedProcess(args,0)
                found=original(["git","config","--get","http.https://github.com/.proxy"],cwd=root,capture_output=True)
                return subprocess.CompletedProcess(args,found.returncode)
            with patch.dict("os.environ",env),patch.object(join.urllib.request,"getproxies",return_value={"http":"http://127.0.0.1:8011"}),patch.object(subprocess,"run",side_effect=simulate):
                join.github_transport(root)
                join.github_transport(root)
                self.assertEqual(join.value(["git","config","--get","http.https://github.com/.proxy"],cwd=root),"http://127.0.0.1:8011")
                self.assertEqual(join.value(["git","config","--get","http.https://github.com/.proxy"],cwd=other,check=False),"")

if __name__=="__main__":
    unittest.main()
