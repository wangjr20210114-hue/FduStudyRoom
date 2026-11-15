"""Trusted provider preparation and recorder preflight, separate from business TDD."""
import hashlib,io,json,os,subprocess,sys,tempfile,unittest,zipfile
from contextlib import redirect_stdout
from pathlib import Path
from types import SimpleNamespace
from unittest.mock import patch
ROOT=Path(__file__).resolve().parents[2]
sys.path.insert(0,str(ROOT/'tools/team'))
import environment,recording
from policy import PolicyError

class FixtureAutoprepTests(unittest.TestCase):
 def test_black_box_provider_is_chosen_from_module_and_layer_only(self):
  choose=getattr(environment,'fixture_provider',lambda ctx:None)
  self.assertEqual('workflow',choose({'module':'M01','member':{'layer':'backend'}}))
  for module in ['M04','M05']:
   self.assertEqual('resource',choose({'module':module,'member':{'layer':'backend'}}))
  for module in ['M01','M02','M03','M04','M05','M06']:
   self.assertIsNone(choose({'module':module,'member':{'layer':'frontend'}}))
  for module in ['M02','M03','M06']:
   self.assertIsNone(choose({'module':module,'member':{'layer':'backend'}}))

 def test_missing_fixture_blocks_before_receipt_reservation_or_source_snapshot(self):
  with tempfile.TemporaryDirectory() as folder:
   work=Path(folder);private=work/'.campus';private.mkdir()
   ctx={'module':'M04','base':'a'*40,'member':{'id':'d','layer':'backend','modules':['M04']},'policy_root':str(ROOT)}
   (private/'task.local').write_text(json.dumps(ctx))
   args=SimpleNamespace(worktree=str(work),stage='check',test_command=['mvn','package'],requirement='M04',scenario=None,output=str(private/'tdd.local'),report=[],require_test=[],assertion=None,kind='build',completion_mode=None)
   execute=lambda argv,**kw:subprocess.CompletedProcess(argv,0,b'fixture command must not be reached',b'')
   with patch.object(recording,'source_snapshot',return_value={'sha256':'b'*64}) as snapshot,patch.object(recording,'docker',return_value=['unused-docker']),redirect_stdout(io.StringIO()):
    with self.assertRaisesRegex(PolicyError,'ENV_FIXTURE'):
     recording.record(ROOT,args,execute)
   snapshot.assert_not_called()
   self.assertFalse((private/'tdd.local').exists())
   self.assertFalse((private/'runs').exists())

 def test_cache_accepts_only_exact_boot_provider_bytes_and_rechecks_sha(self):
  with tempfile.TemporaryDirectory() as folder:
   directory=Path(folder);ctx={'base':'a'*40,'module':'M04','member':{'layer':'backend'}};image='sha256:'+'b'*64
   jar=directory/'resource.jar'
   with zipfile.ZipFile(jar,'w') as archive:
    archive.writestr('META-INF/MANIFEST.MF','Manifest-Version: 1.0\r\nStart-Class: com.campus.ResourceApplication\r\n\r\n'.replace('\\r\\n','\r\n'))
    archive.writestr('BOOT-INF/classes/proof','frozen source')
   digest=hashlib.sha256(jar.read_bytes()).hexdigest();proof={'provider':'resource','coreCommit':ctx['base'],'trustedImage':image,'productionSourceSha256':'c'*64,'artifacts':{'resource':{'file':'resource.jar','sha256':digest}}}
   (directory/'manifest.json').write_text(json.dumps(proof))
   valid=environment._fixture_validate(directory,ctx,image,'c'*64)
   self.assertEqual(digest,valid['jarSha256'])
   jar.write_bytes(jar.read_bytes()+b'corrupted')
   with self.assertRaisesRegex(PolicyError,'ENV_FIXTURE_INVALID_CACHE'):environment._fixture_validate(directory,ctx,image,'c'*64)

 def test_existing_runner_mismatch_never_rebuilds_or_recreates(self):
  from policy import load
  with tempfile.TemporaryDirectory() as folder:
   work=Path(folder);ctx={'base':'a'*40,'module':'M04','member':{'id':'d','layer':'backend','modules':['M04']}}
   with patch.object(environment,'docker',return_value=['docker']),patch.object(environment,'run',return_value='29.8.1') as run,patch.object(environment,'_fixture_runner',return_value='existing'),patch.object(environment,'fixture_ready',side_effect=PolicyError('ENV_FIXTURE_CONFIG_CHANGED')),patch.object(environment,'prepare_fixture') as prepare:
    with self.assertRaisesRegex(PolicyError,'ENV_FIXTURE_CONFIG_CHANGED'):environment.sandbox(ROOT,work,load(ROOT),ctx['member'],ctx,start=True)
   prepare.assert_not_called()
   self.assertFalse(any('up' in call.args[0] or 'rm' in call.args[0] for call in run.call_args_list))

 def test_recorder_keeps_source_hash_separate_from_fixture_fingerprint(self):
  with tempfile.TemporaryDirectory() as folder:
   work=Path(folder);private=work/'.campus';private.mkdir();ctx={'module':'M04','base':'a'*40,'member':{'id':'d','layer':'backend','modules':['M04']},'policy_root':str(ROOT)}
   (private/'task.local').write_text(json.dumps(ctx));args=SimpleNamespace(worktree=str(work),stage='check',test_command=['mvn','package'],requirement='M04',scenario=None,output=str(private/'tdd.local'),report=[],require_test=[],assertion=None,kind='build',completion_mode=None)
   source={'sha256':'b'*64,'product_sha256':'c'*64};fixture={'provider':'resource','coreCommit':'a'*40,'trustedImage':'sha256:'+'d'*64,'jarSha256':'e'*64,'manifestSha256':'f'*64,'fingerprint':'1'*64}
   execute=lambda argv,**kw:subprocess.CompletedProcess(argv,0,b'build passed',b'')
   with patch.object(recording,'fixture_ready',return_value=fixture),patch.object(recording,'source_snapshot',return_value=source),patch.object(recording,'docker',return_value=['unused-docker']),redirect_stdout(io.StringIO()):recording.record(ROOT,args,execute)
   item=json.loads((private/'tdd.local').read_text())['module']['check'];self.assertEqual(source,item['source']);self.assertEqual(fixture,item['fixture_fingerprint'])

 def test_matching_stopped_services_resume_only_in_explicit_sandbox_start(self):
  from policy import load
  with tempfile.TemporaryDirectory() as folder:
   work=Path(folder);ctx={'base':'a'*40,'module':'M04','member':{'id':'d','layer':'backend','modules':['M04']}}
   proof={'_stopped':True,'_containers':{'postgres':'pg-original','runner':'runner-original'}}
   with patch.object(environment,'docker',return_value=['docker']),patch.object(environment,'run',return_value='29.8.1') as run,patch.object(environment,'_fixture_runner',return_value='runner-original'),patch.object(environment,'fixture_ready',return_value=proof) as ready,patch.object(environment,'_fixture_wait',create=True),patch.object(environment,'_fixture_database'),patch.object(environment,'prepare_fixture') as prepare,redirect_stdout(io.StringIO()):
    environment.sandbox(ROOT,work,load(ROOT),ctx['member'],ctx,start=True)
   self.assertTrue(any(call.args[0][-3:]==['start','postgres','runner'] for call in run.call_args_list),'matching stopped services must resume in place from sandbox --start')
   self.assertGreaterEqual(ready.call_count,2,'after starting, full ready checks run again')
   prepare.assert_not_called()
   self.assertFalse(any('up' in call.args[0] or 'rm' in call.args[0] for call in run.call_args_list))
   self.assertFalse((work/'.campus/sandbox.local.json').exists(),'recovery must not write a new canonical config')

 def test_published_cache_rejects_any_extra_file_or_directory(self):
  with tempfile.TemporaryDirectory() as folder:
   directory=Path(folder);ctx={'base':'a'*40,'module':'M04','member':{'layer':'backend'}};image='sha256:'+'b'*64;jar=directory/'resource.jar'
   with zipfile.ZipFile(jar,'w') as archive:
    archive.writestr('META-INF/MANIFEST.MF','Start-Class: com.campus.ResourceApplication\n'.replace('\\n','\n'));archive.writestr('BOOT-INF/classes/proof','frozen')
   proof={'provider':'resource','coreCommit':ctx['base'],'trustedImage':image,'productionSourceSha256':'c'*64,'artifacts':{'resource':{'file':'resource.jar','sha256':hashlib.sha256(jar.read_bytes()).hexdigest()}}};(directory/'manifest.json').write_text(json.dumps(proof))
   for name,is_dir in [('.credentials.local',False),('extra-folder',True)]:
    with self.subTest(name=name):
     extra=directory/name
     if is_dir:extra.mkdir()
     else:extra.write_text('must never be exposed by the RO cache directory bind')
     try:
      with self.assertRaisesRegex(PolicyError,'ENV_FIXTURE_INVALID_CACHE'):environment._fixture_validate(directory,ctx,image,'c'*64)
     finally:
      if is_dir:extra.rmdir()
      else:extra.unlink()

 @unittest.skipUnless(os.name=='posix','Real directory symlink checked on Linux')
 def test_prepare_and_ready_reject_symlink_cache_root_before_following_it(self):
  with tempfile.TemporaryDirectory() as folder:
   work=Path(folder);private=work/'.campus';private.mkdir();external=work/'external';external.mkdir();(private/'frozen-fixtures').symlink_to(external,target_is_directory=True)
   ctx={'base':'a'*40,'module':'M04','member':{'id':'d','layer':'backend'}};compose=private/'sandbox.local.json';compose.write_text('{}');saved={'directory':str(private/'frozen-fixtures/cache'),'productionSourceSha256':'c'*64,'fingerprint':'f'*64,'composeSha256':hashlib.sha256(compose.read_bytes()).hexdigest()};(private/'fixture.local').write_text(json.dumps(saved))
   with patch.object(environment,'git_run',return_value=subprocess.CompletedProcess([],0,b'archive',b'')),patch.object(environment,'_fixture_image',return_value='sha256:'+'b'*64),patch.object(environment,'_fixture_validate',return_value={'fingerprint':'f'*64}),patch.object(environment,'_fixture_runner',return_value=None):
    for action in [lambda:environment.prepare_fixture(ROOT,work,ctx,'sha256:'+'b'*64),lambda:environment.fixture_ready(ROOT,work,ctx)]:
     with self.assertRaisesRegex(PolicyError,'ENV_FIXTURE_CACHE_LINK'):action()

if __name__=='__main__':unittest.main()
