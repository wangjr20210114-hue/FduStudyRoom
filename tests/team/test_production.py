import hashlib,importlib.util,io,json,tarfile,tempfile,unittest
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]
spec=importlib.util.spec_from_file_location('production_deploy',ROOT/'infra/production/deploy.py');deploy=importlib.util.module_from_spec(spec);spec.loader.exec_module(deploy)
class Runtime:
 def __init__(self):self.calls=[];self.bad=set()
 def prepare(self,r):self.calls.append(('prepare',r.name))
 def backup(self,r,p):self.calls.append(('backup',r.name));p.write_bytes(b'backup')
 def start(self,r):self.calls.append(('start',r.name))
 def healthy(self,r):return r.name not in self.bad
class ProductionTests(unittest.TestCase):
 def setUp(self):
  self.temp=tempfile.TemporaryDirectory();self.root=Path(self.temp.name);(self.root/'private').mkdir();(self.root/'releases').mkdir();self.runtime=Runtime()
 def tearDown(self):self.temp.cleanup()
 def bundle(self,seq=1,commit='a'*40,extra=None,corrupt=False):
  files={'resource.jar':b'jar','workflow.jar':b'jar','web/index.html':b'<html>app</html>','web/version.json':json.dumps({'commit':commit,'sequence':seq,'branch':'development/b'}).encode()}
  manifest={'commit':commit,'sequence':seq,'branch':'development/b','files':{n:hashlib.sha256(b).hexdigest() for n,b in files.items()}}
  if corrupt:files['resource.jar']=b'changed'
  files['manifest.json']=json.dumps(manifest).encode()
  if extra:files.update(extra)
  target=self.root/('bundle-'+str(seq)+'.tar.gz')
  with tarfile.open(target,'w:gz') as archive:
   for name,data in files.items():
    info=tarfile.TarInfo(name);info.size=len(data);archive.addfile(info,io.BytesIO(data))
  return target
 def test_first_release_backed_up_then_published(self):
  result=deploy.ReleaseManager(self.root,self.runtime).apply(self.bundle())
  self.assertEqual('deployed',result['state']);self.assertEqual(['prepare','backup','start'],[x[0] for x in self.runtime.calls])
  self.assertEqual('a'*40,json.loads((self.root/'private/deployed.local').read_text())['commit'])
 def test_old_push_cannot_overwrite_newer_success(self):
  manager=deploy.ReleaseManager(self.root,self.runtime);manager.apply(self.bundle(2));count=len(self.runtime.calls)
  self.assertEqual('skipped_old_push',manager.apply(self.bundle(1,'b'*40))['state']);self.assertEqual(count,len(self.runtime.calls))
 def test_failed_health_restores_application_and_preserves_database_backup(self):
  manager=deploy.ReleaseManager(self.root,self.runtime);manager.apply(self.bundle(1));self.runtime.bad.add('b'*40+'-2')
  with self.assertRaises(RuntimeError):manager.apply(self.bundle(2,'b'*40))
  state=json.loads((self.root/'private/deployed.local').read_text());self.assertEqual('a'*40,state['commit'])
  failure=json.loads((self.root/'private/last-failure.local').read_text());self.assertTrue(failure['application_rollback_ok']);self.assertEqual('manual_only',failure['database_restore'])
 def test_tampered_bundle_never_reaches_docker(self):
  with self.assertRaises(ValueError):deploy.ReleaseManager(self.root,self.runtime).apply(self.bundle(corrupt=True))
  self.assertEqual([],self.runtime.calls)
 def test_archive_cannot_write_outside_release_or_replace_server_config(self):
  for i,name in enumerate(['../../private/runtime.env','config/compose.yaml','web/../../escape']):
   with self.assertRaises(ValueError):deploy.ReleaseManager(self.root,self.runtime).apply(self.bundle(i,extra={name:b'bad'}))
  self.assertEqual([],self.runtime.calls)
 def test_upload_cannot_mount_host_or_request_a_shell(self):
  with self.assertRaises(ValueError):deploy.ReleaseManager(self.root,self.runtime).apply(self.bundle(extra={'compose.yaml':b'privileged: true'}))
  self.assertEqual([],self.runtime.calls)

 def test_failed_release_can_be_retried_with_the_same_verified_inputs(self):
  manager=deploy.ReleaseManager(self.root,self.runtime);self.runtime.bad.add('a'*40+'-1');bundle=self.bundle(1)
  with self.assertRaises(RuntimeError):manager.apply(bundle)
  self.runtime.bad.clear()
  result=None
  try:result=manager.apply(bundle)
  except OSError:pass
  self.assertIsNotNone(result,'Retry must reuse the already checked release rather than fail on an existing directory')
  self.assertEqual('deployed',result['state'])
