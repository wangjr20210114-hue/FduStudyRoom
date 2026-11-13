import hashlib,json,subprocess,sys,tempfile,unittest
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2];sys.path.insert(0,str(ROOT/'tools/team'))
from policy import *
from buffer_job import verify_patch,publish
from environment import sandbox_project
class OwnershipTests(unittest.TestCase):
 def setUp(self):
  self.policy=load(ROOT);self.member={'id':'frontend-a','layer':'frontend','modules':['M02','M03']}
 def test_six_modules_and_four_roles_have_disjoint_write_roots(self):
  self.assertEqual(6,len(self.policy['modules']))
  entries=[]
  for module in self.policy['modules']:
   for layer,prefixes in module['write_paths'].items():
    for prefix in prefixes:
     for other in entries:self.assertFalse(prefix.startswith(other) or other.startswith(prefix),(prefix,other))
     entries.append(prefix)
 def test_owned_paths_and_blocked_shared_private_or_other_module_paths(self):
  authorize(self.policy,self.member,'M02',['frontend/src/modules/resource/pages/RoomList.vue'])
  for path in ['frontend/src/modules/repair/a.vue','frontend/src/shared/i18n/locales/en.ts','frontend/src/modules/resource/../auth/a.vue','frontend/src/modules/resource/.env','frontend/src/modules/resource/.env.ts','frontend/src/modules/resource/target/a.ts','frontend/src/modules/resource/a.local']:
   with self.assertRaises(PolicyError,msg=path):authorize(self.policy,self.member,'M02',[path])
 def test_language_stack_and_progress_types_are_enforced(self):
  with self.assertRaises(PolicyError):authorize(self.policy,self.member,'M02',['frontend/src/modules/resource/server.py'])
  with self.assertRaises(PolicyError):authorize(self.policy,self.member,'M02',['spec/progress/resource/frontend/report.md'])
 def test_private_imports_are_rejected_but_public_identity_hook_is_allowed(self):
  module=module_for(self.policy,'M02')
  check_imports('frontend/src/modules/resource/pages/Page.vue',"import {useAuthSession} from '@/modules/auth/model/auth-session'",module,self.policy)
  check_imports('frontend/src/modules/resource/pages/Page.vue',"import {useAuthSession} from '../../auth/model/auth-session'",module,self.policy)
  with self.assertRaises(PolicyError):check_imports('frontend/src/modules/resource/pages/Page.vue',"import {repair} from '@/modules/repair/api/client'",module,self.policy)
  with self.assertRaises(PolicyError):check_imports('frontend/src/modules/resource/pages/Page.vue',"import {repair} from '../../repair/api/client'",module,self.policy)
  with self.assertRaises(PolicyError):check_imports('backend/resource-service/src/main/java/com/campus/resource/A.java','import com.campus.identity.domain.Account;',module,self.policy)
 def test_sandbox_projects_are_separate_between_clones_modules_and_baselines(self):
  name=sandbox_project(ROOT,self.member,'M02','a'*40)
  self.assertEqual(name,sandbox_project(ROOT,self.member,'M02','a'*40))
  for other in [sandbox_project(ROOT/'clone',self.member,'M02','a'*40),sandbox_project(ROOT,self.member,'M03','a'*40),sandbox_project(ROOT,self.member,'M02','b'*40)]:
   self.assertNotEqual(name,other)
 def test_backend_cannot_change_frontend(self):
  with self.assertRaises(PolicyError):authorize(self.policy,{'layer':'backend','modules':['M02']},'M02',['frontend/src/modules/resource/a.vue'])
 def test_username_does_not_bypass_member_mapping(self):
  with self.assertRaises(PolicyError):member_for([self.member],'wangjr2021')
 def test_git_rename_detects_source_outside_scope(self):
  with tempfile.TemporaryDirectory() as directory:
   root=Path(directory);git(root,'init');git(root,'config','user.name','Test');git(root,'config','user.email','test@example.invalid')
   (root/'AGENTS.md').write_text('protected\n');git(root,'add','.');git(root,'commit','-m','base');base=git(root,'rev-parse','HEAD').decode().strip()
   target=root/'frontend/src/modules/resource/page.vue';target.parent.mkdir(parents=True);(root/'AGENTS.md').rename(target)
   with self.assertRaises(PolicyError):guard(root,self.policy,self.member,'M02',base)
 def test_server_git_parser_rejects_path_escape_and_rename(self):
  def patch(path):return ('diff --git a/'+path+' b/'+path+'\nnew file mode 100644\nindex 0000000..7898192\n--- /dev/null\n+++ b/'+path+'\n@@ -0,0 +1 @@\n+a\n').encode()
  self.assertEqual(['frontend/src/modules/resource/a.vue'],verify_patch(ROOT,self.member,'M02',patch('frontend/src/modules/resource/a.vue'),self.policy))
  with self.assertRaises(PolicyError):verify_patch(ROOT,self.member,'M02',patch('AGENTS.md'),self.policy)
 def test_publication_pushes_only_feature_ref_to_local_bare_remote(self):
  with tempfile.TemporaryDirectory() as directory:
   root=Path(directory);repo=root/'repo';remote=root/'remote.git';repo.mkdir();git(repo,'init');git(repo,'config','user.name','Test');git(repo,'config','user.email','test@example.invalid');(repo/'a').write_text('x');git(repo,'add','.');git(repo,'commit','-m','reviewed');subprocess.run(['git','init','--bare',str(remote)],check=True,capture_output=True)
   branch='buffer/m02/00000000-0000-0000-0000-000000000001';git(repo,'branch',branch);commit=git(repo,'rev-parse','HEAD').decode().strip()
   result=publish({'enabled':True,'repository':str(repo),'remote':str(remote),'giteeApiToken':''},{'result':{'branch':branch,'commit':commit}})
   self.assertEqual('BRANCH_PUSHED_MR_CREDENTIAL_MISSING',result['code']);self.assertEqual(commit,git(remote,'rev-parse','refs/heads/'+branch).decode().strip());self.assertNotEqual(0,subprocess.run(['git','--git-dir',str(remote),'show-ref','--verify','refs/heads/main'],capture_output=True).returncode)
class EvidenceTests(unittest.TestCase):
 def evidence(self):
  def item(code,log):return {'exit_code':code,'command':['npm','test'],'log':log,'sha256':hashlib.sha256(log.encode()).hexdigest(),'expected_assertion':'seat already reserved'}
  return {'red':item(1,'assertion failed: seat already reserved'),'green':item(0,'all behavioral tests passed'),'mutation':item(1,'assertion failed: seat already reserved')}
 def test_green_cannot_claim_success_with_zero_executed_tests(self):
  data=self.evidence();data['green']['test_result']={'counts':{'total':0,'executed':0,'passed':0,'failed':0,'errors':0,'skipped':0},'cases':[],'required_tests':['seat-case']}
  with self.assertRaises(PolicyError):evidence_valid(data)
 def test_green_cannot_skip_a_required_test(self):
  data=self.evidence();data['green']['test_result']={'counts':{'total':2,'executed':1,'passed':1,'failed':0,'errors':0,'skipped':1},'cases':[{'id':'seat-case','result':'PASS'},{'id':'permission-case','result':'SKIPPED'}],'required_tests':['seat-case','permission-case']}
  with self.assertRaises(PolicyError):evidence_valid(data)
 def test_real_failure_evidence_and_green(self):self.assertTrue(evidence_valid(self.evidence()))
 def test_maven_assertion_failure_is_accepted_as_red(self):
  data=self.evidence();text='Tests run: 1, Failures: 1; seat already reserved; BUILD FAILURE';data['red'].update(log=text,sha256=hashlib.sha256(text.encode()).hexdigest())
  self.assertTrue(evidence_valid(data))
 def test_tampered_or_infrastructure_failure_is_rejected(self):
  data=self.evidence();data['red']['log']='different'
  with self.assertRaises(PolicyError):evidence_valid(data)
  data=self.evidence();text='Compilation failure: seat already reserved';data['red'].update(log=text,sha256=hashlib.sha256(text.encode()).hexdigest())
  with self.assertRaises(PolicyError):evidence_valid(data)
 def test_each_requirement_keeps_its_own_tdd_history(self):
  self.assertTrue(evidence_valid({'requirements':{'RES-01':self.evidence(),'RES-02':self.evidence()}},['RES-01','RES-02']))
  with self.assertRaises(PolicyError):evidence_valid({'requirements':{'RES-01':self.evidence()}},['RES-01','RES-02'])
  with self.assertRaises(PolicyError):evidence_valid(self.evidence(),['RES-01','RES-02'])
 def test_different_red_green_command_is_rejected(self):
  data=self.evidence();data['green']['command']=['echo','pass']
  with self.assertRaises(PolicyError):evidence_valid(data)
if __name__=='__main__':unittest.main()
