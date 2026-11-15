#!/usr/bin/env python3
"""Trusted server receiver. Only checked application bundles enter the fixed stack."""
import gzip,hashlib,json,os,re,shutil,subprocess,sys,tarfile,tempfile,time,urllib.request
from pathlib import Path,PurePosixPath

MAX_UPLOAD=200*1024*1024

def unpack(package,destination):
    destination=Path(destination);destination.mkdir(parents=True,exist_ok=False)
    with tarfile.open(package,'r:gz') as archive:
        entries=archive.getmembers()
        if len(entries)>4000 or sum(e.size for e in entries)>512*1024*1024:raise ValueError('RELEASE_TOO_LARGE')
        names=set()
        for entry in entries:
            path=PurePosixPath(entry.name)
            if not entry.isfile() or entry.name in names or path.is_absolute() or '..' in path.parts or str(path)!=entry.name or '\\' in entry.name:raise ValueError('UNSAFE_RELEASE_ENTRY')
            if entry.name not in ('manifest.json','resource.jar','workflow.jar') and not entry.name.startswith('web/'):raise ValueError('UNEXPECTED_RELEASE_FILE')
            names.add(entry.name)
            target=destination/entry.name;target.parent.mkdir(parents=True,exist_ok=True)
            with archive.extractfile(entry) as source,target.open('xb') as output:shutil.copyfileobj(source,output)
            target.chmod(0o644)
    manifest=json.loads((destination/'manifest.json').read_text(encoding='utf-8'))
    if not re.fullmatch('[0-9a-f]{40}',manifest.get('commit','')) or type(manifest.get('sequence')) is not int or manifest['sequence']<0:raise ValueError('INVALID_RELEASE_ID')
    if not isinstance(manifest.get('branch'),str) or len(manifest['branch'])>256:raise ValueError('INVALID_RELEASE_BRANCH')
    if set(manifest['files'])!=names-{'manifest.json'} or not {'resource.jar','workflow.jar','web/index.html','web/version.json'}<=names:raise ValueError('INCOMPLETE_RELEASE')
    for name,digest in manifest['files'].items():
        if hashlib.sha256((destination/name).read_bytes()).hexdigest()!=digest:raise ValueError('RELEASE_CHECKSUM_MISMATCH')
    version=json.loads((destination/'web/version.json').read_text(encoding='utf-8'))
    if any(version.get(k)!=manifest[k] for k in ('commit','sequence','branch')):raise ValueError('RELEASE_VERSION_MISMATCH')
    return manifest

class DockerRuntime:
    def __init__(self,root):self.root=Path(root)
    def command(self,release,*args):
        env={**os.environ,'RELEASE_DIR':str(release)}
        command=['docker','compose','-p','fdu-study-room-production','--env-file',str(self.root/'private/runtime.env'),'-f',str(self.root/'config/compose.yaml'),*args]
        result=subprocess.run(command,env=env,capture_output=True,timeout=600)
        if result.returncode:
            log=(result.stdout+result.stderr).decode('utf-8',errors='replace')
            for row in (self.root/'private/runtime.env').read_text().splitlines():
                if '=' in row:
                    name,value=row.split('=',1)
                    if ('PASSWORD' in name or 'KEY' in name) and value:log=log.replace(value,'[REDACTED]')
            target=self.root/'private/compose-last.log';target.write_text(log,encoding='utf-8');target.chmod(0o600)
            raise RuntimeError('COMPOSE_FAILED: '+args[0]+'; inspect private compose-last.log')
        return result.stdout
    def prepare(self,release):
        # Runtime image IDs are provisioned once and pinned in the private server configuration.
        # Pulling mutable tags on every push would change the runtime independently of the tested source.

        self.command(release,'up','-d','--wait','--wait-timeout','100','postgres')
    def backup(self,release,output):
        data=self.command(release,'exec','-T','postgres','pg_dumpall','-U','postgres')
        with gzip.open(output,'wb') as stream:stream.write(data)
        output.chmod(0o600)
    def start(self,release):
        self.command(release,'up','-d','--wait','--wait-timeout','180','--pull','never')
    def healthy(self,release):
        expected=json.loads((release/'manifest.json').read_text(encoding='utf-8'))
        for attempt in range(20):
            try:
                for path in ('/health/resource','/health/workflow','/api/auth/config','/version.json','/'):
                    with urllib.request.urlopen('http://127.0.0.1:18080'+path,timeout=4) as response:
                        body=response.read()
                        if response.status!=200:raise ValueError('HTTP_HEALTH_FAILED')
                        if path.startswith('/health/') and json.loads(body).get('status')!='UP':raise ValueError('SERVICE_NOT_UP')
                        if path=='/api/auth/config' and not json.loads(body).get('available'):raise ValueError('AUTH_NOT_AVAILABLE')
                        if path=='/version.json' and any(json.loads(body).get(k)!=expected[k] for k in ('commit','sequence')):raise ValueError('WRONG_RELEASE_RUNNING')
                return True
            except (OSError,ValueError,KeyError):time.sleep(2)
        return False

class ReleaseManager:
    def __init__(self,root,runtime):self.root=Path(root);self.runtime=runtime
    def apply(self,package):
        statefile=self.root/'private/deployed.local'
        previous=json.loads(statefile.read_text(encoding='utf-8')) if statefile.exists() else None
        stage=self.root/'releases'/('incoming-'+str(time.time_ns()))
        try:manifest=unpack(package,stage)
        except Exception:
            if stage.exists():shutil.rmtree(stage)
            raise
        if previous and manifest['sequence']<=previous['sequence']:
            shutil.rmtree(stage)
            return {'state':'skipped_old_push','commit':previous['commit']}
        release=stage.with_name(manifest['commit']+'-'+str(manifest['sequence']))
        if release.exists():
            old=json.loads((release/'manifest.json').read_text(encoding='utf-8'))
            if old!=manifest:raise ValueError('RELEASE_INPUT_CHANGED')
            for name,digest in manifest['files'].items():
                if hashlib.sha256((release/name).read_bytes()).hexdigest()!=digest:raise ValueError('PREVIOUS_RELEASE_CHANGED')
            shutil.rmtree(stage)
        else:stage.rename(release)
        self.runtime.prepare(release)
        backup=self.root/'private/backups'/(release.name+'-'+str(time.time_ns())+'.sql.gz');backup.parent.mkdir(parents=True,exist_ok=True)
        self.runtime.backup(release,backup)
        try:
            self.runtime.start(release)
            if not self.runtime.healthy(release):raise RuntimeError('HEALTH_CHECK_FAILED')
        except Exception as failure:
            recovered=False
            if previous:
                try:
                    self.runtime.start(Path(previous['release']))
                    recovered=self.runtime.healthy(Path(previous['release']))
                except Exception:pass
            result={'state':'failed','commit':manifest['commit'],'application_rollback_ok':recovered,'database_backup':str(backup),'database_restore':'manual_only'}
            (self.root/'private/last-failure.local').write_text(json.dumps(result)+'\n',encoding='utf-8')
            raise RuntimeError('DEPLOY_FAILED: application rollback '+('passed' if recovered else 'requires attention')+'; database is never restored automatically') from failure
        result={**{k:manifest[k] for k in ('commit','sequence','branch')},'release':str(release),'state':'deployed','at':time.time()}
        temporary=statefile.with_suffix('.tmp');temporary.write_text(json.dumps(result)+'\n',encoding='utf-8');temporary.replace(statefile)
        return result

def main():
    import fcntl,signal
    root=Path('/opt/fdu-study-room');signal.alarm(1500)
    with (root/'private/deploy.lock').open('a') as lock:
        fcntl.flock(lock,fcntl.LOCK_EX)
        with tempfile.NamedTemporaryFile(dir=root/'private',suffix='.tar.gz') as upload:
            size=0
            while True:
                chunk=sys.stdin.buffer.read(1024*1024)
                if not chunk:break
                size+=len(chunk)
                if size>MAX_UPLOAD:raise ValueError('UPLOAD_TOO_LARGE')
                upload.write(chunk)
            upload.flush()
            result=ReleaseManager(root,DockerRuntime(root)).apply(upload.name)
            print(json.dumps(result))
if __name__=='__main__':main()
