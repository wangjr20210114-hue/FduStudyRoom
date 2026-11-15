import json,sys,unittest
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2];sys.path.insert(0,str(ROOT/'tools/team'))
from members import layers,scoped
from commits import message
from policy import PolicyError,authorize,load
class DeliveryTests(unittest.TestCase):
 def test_six_domain_owners_each_have_both_layers(self):
  people=json.loads((ROOT/'spec/members.example.json').read_text(encoding='utf-8'))['members']
  self.assertEqual(list('abcdef'),[p['id'] for p in people])
  self.assertEqual(['M01','M02','M03','M04','M05','M06'],[p['modules'][0] for p in people])
  for p in people:self.assertEqual(['backend','frontend'],layers(p))
 def test_fullstack_identity_keeps_module_and_layer_boundaries(self):
  person={'id':'b','layer':'fullstack','modules':['M02']}
  self.assertEqual(['backend','frontend'],layers(person))
  policy=load(ROOT)
  for layer in layers(person):
   profile=scoped(person,layer);self.assertEqual('b',profile['id'])
   prefix=policy['modules'][1]['write_paths'][layer][0]
   authorize(policy,profile,'M02',[prefix+('Room.java' if layer=='backend' else 'Room.vue')])
   with self.assertRaises(PolicyError):authorize(policy,profile,'M05',['frontend/src/modules/repair/Room.vue'])
  with self.assertRaises(PolicyError):scoped({'id':'c','layer':'frontend'},'backend')
 def test_same_member_frontend_and_backend_have_separate_containers(self):
  from environment import sandbox_project
  member={'id':'b','layer':'backend','modules':['M02']}
  backend=sandbox_project(ROOT,member,'M02','a'*40)
  frontend=sandbox_project(ROOT,{**member,'layer':'frontend'},'M02','a'*40)
  self.assertNotEqual(backend,frontend,'Each layer needs a different Compose project and volumes')
 def test_commit_has_type_title_bullets_and_trailers(self):
  self.assertEqual('fix(M02): 修复教室筛选\n\n- 保留所选校区\n- 补充空结果测试\n\nRequirements: RES-01\nReviewed-by: coordinator\n',message('fix','M02','修复教室筛选',['保留所选校区','补充空结果测试'],['RES-01'],'coordinator'))
 def test_invalid_commit_metadata_is_rejected(self):
  for kind,title,details in [('fit','title',['detail']),('feat','title\nInjected',['detail']),('refactor','title',[])]:
   rejected=False
   try:message(kind,'M02',title,details)
   except PolicyError:rejected=True
   self.assertTrue(rejected,'Invalid commit description must be rejected')

class GitHubExportTests(unittest.TestCase):
 def test_same_patch_is_pushed_without_changing_member_worktree(self):
  import tempfile,shutil,subprocess
  from unittest.mock import patch
  from policy import git
  from delivery import github_export
  with tempfile.TemporaryDirectory() as temporary:
   parent=Path(temporary);root=parent/'baseline';root.mkdir();(root/'spec').mkdir()
   shutil.copyfile(ROOT/'spec/modules.json',root/'spec/modules.json');(root/'.gitignore').write_text('.campus/\n')
   git(root,'init');git(root,'config','user.name','Fixture');git(root,'config','user.email','fixture@example.invalid');git(root,'add','.');git(root,'commit','-m','fixture baseline')
   base=git(root,'rev-parse','HEAD').decode().strip();work=parent/'member';git(root,'worktree','add','--detach',str(work),base)
   page=work/'frontend/src/modules/resource/Room.vue';page.parent.mkdir(parents=True);page.write_text('<template>Room</template>\n');git(work,'add','.')
   before=git(work,'diff','--cached','--binary',base)
   bare=parent/'github.git';subprocess.run(['git','init','--bare',str(bare)],capture_output=True,check=True)
   context={'base':base,'module':'M02','member':{'id':'b','layer':'frontend','modules':['M02']}}
   ack={'id':'11111111-1111-4111-8111-111111111111'};original=subprocess.run
   def local_push(command,*args,**kwargs):
    if 'push' in command:
     command=list(command);index=command.index('push')
     # Verify checkout trust with the actual push command prefix. The local
     # bare remote is not the different-owner checkout under test.
     probe=original(command[:index]+['rev-parse','HEAD'],*args,**kwargs)
     self.assertEqual(0,probe.returncode,probe.stderr.decode(errors='replace'))
     for key,expected in [('core.longpaths','true'),('http.postBuffer','1048576'),('pack.threads','1'),('pack.windowMemory','32m')]:
      effective=original(command[:index]+['config','--get',key],*args,**kwargs)
      self.assertEqual(expected,effective.stdout.decode().strip(),'Export Git must use process-scoped host limits')
     self.assertEqual('host-only-helper-token',kwargs['env']['GH_TOKEN'])
     helper=original(command[:index]+['config','--get','credential.helper'],*args,**kwargs)
     self.assertEqual('host-only-helper',helper.stdout.decode().strip(),'Host helper authentication must survive the per-process limits')
     command[index+1]=str(bare)
     kwargs={**kwargs,'env':{**kwargs['env'],'GIT_TEST_ASSUME_DIFFERENT_OWNER':'0'}}
    return original(command,*args,**kwargs)
   with patch.dict('os.environ',{'GIT_TEST_ASSUME_DIFFERENT_OWNER':'1','GH_TOKEN':'host-only-helper-token','GIT_CONFIG_COUNT':'1','GIT_CONFIG_KEY_0':'credential.helper','GIT_CONFIG_VALUE_0':'host-only-helper'}),patch('policy.subprocess.run',side_effect=local_push):
    result=github_export(root,work,context,ack,'feat','Add room page',['Show room list'],['RES-01'])
    again=github_export(root,work,context,ack,'feat','Add room page',['Show room list'],['RES-01'])
   self.assertEqual('pushed',result['state']);self.assertEqual(result['commit'],again['commit'])
   self.assertEqual(before,git(work,'diff','--cached','--binary',base));self.assertEqual(base,git(work,'rev-parse','HEAD').decode().strip())
   exported=git(bare,'show',result['commit']+':frontend/src/modules/resource/Room.vue').decode();self.assertIn('Room',exported)
   self.assertIn('\n\n- Show room list',git(bare,'show','-s','--format=%B',result['commit']).decode())
