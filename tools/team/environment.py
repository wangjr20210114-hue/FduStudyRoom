"""Recoverable local build and per-member Docker environments (Windows/macOS)."""
import hashlib,io,json,os,platform,re,secrets,shutil,socket,subprocess,sys,tarfile,tempfile,time,urllib.request,uuid,zipfile
from pathlib import Path
from contextlib import contextmanager
from policy import PolicyError,module_for,git_run
PG='postgres@sha256:bbf83594798e144bff365a9c191c5d779f181fb3135902064ab8ff11918020fc'

def run(args,**kwargs):
    kwargs.setdefault('timeout',900)
    result=subprocess.run([str(x) for x in args],capture_output=True,encoding='utf-8',errors='replace',**kwargs)
    if globals().get('LOG_DIRECTORY'):
        text=result.stdout+result.stderr
        for key,value in kwargs.get('env',{}).items():
            if value and len(value)>=12 and any(tag in key.lower() for tag in ['password','secret','token','signing_key','service_key']):text=text.replace(value,'[REDACTED]')
        folder=Path(LOG_DIRECTORY);folder.mkdir(parents=True,exist_ok=True);(folder/(str(time.time_ns())+'.log')).write_text(text,encoding='utf-8')
    if result.returncode:raise PolicyError('COMMAND_FAILED: '+str(args[0])+'; exit='+str(result.returncode))
    return result.stdout.strip()

def save(path,data):
    path=Path(path);path.parent.mkdir(parents=True,exist_ok=True);path.write_text(json.dumps(data,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    try:path.chmod(0o600)
    except OSError:pass

def docker(root):
    # A WSL source path belongs to that distribution's engine, even if Docker
    # Desktop has also installed a native Windows CLI on PATH.
    match=re.match(r'^\\\\wsl(?:\.localhost|\$)\\([^\\]+)',str(root),re.I)
    if os.name=='nt' and match:return ['wsl.exe','-d',match[1],'--exec','docker']
    found=shutil.which('docker')
    if found:return [found]
    raise PolicyError('DOCKER_MISSING: install Docker Desktop/Engine and start it; IDE absence is only a notice')

def docker_path(path):
    value=str(Path(path).resolve())
    match=re.match(r'^\\\\wsl(?:\.localhost|\$)\\[^\\]+(.*)$',value,re.I)
    if match:return match[1].replace('\\','/')
    return value

def bind_identity(root,image,path):
    value=run(docker(root)+['run','--rm','--read-only','--network','none','--cap-drop','ALL','--mount','type=bind,src='+docker_path(path)+',dst=/probe,readonly','--entrypoint','/usr/bin/stat',image,'-c','%u:%g','/probe'])
    if not re.fullmatch(r'[0-9]+:[0-9]+',value):raise PolicyError('CANNOT_DETERMINE_BIND_OWNER')
    return value

def safe_extract(archive,dest):
    dest=Path(dest).resolve();dest.mkdir(parents=True,exist_ok=True)
    if str(archive).endswith('.zip'):
        with zipfile.ZipFile(archive) as z:
            for entry in z.infolist():
                if not (dest/entry.filename).resolve().is_relative_to(dest) or (entry.external_attr>>16)&0o170000==0o120000:raise PolicyError('UNSAFE_ARCHIVE')
            z.extractall(dest)
    else:
        if not hasattr(tarfile,'data_filter'):raise PolicyError('PYTHON_312_REQUIRED_FOR_SAFE_TOOL_EXTRACTION: use scripts/build.ps1 or build.sh')
        with tarfile.open(archive) as tar:tar.extractall(dest,filter='data')

def download(url,path,sha):
    if not url.startswith(('https://nodejs.org/','https://github.com/adoptium/')):raise PolicyError('UNTRUSTED_TOOL_DOWNLOAD')
    with urllib.request.urlopen(url,timeout=60) as response:body=response.read()
    if hashlib.sha256(body).hexdigest()!=sha.lower():raise PolicyError('TOOL_CHECKSUM_MISMATCH')
    Path(path).parent.mkdir(parents=True,exist_ok=True);Path(path).write_bytes(body)

def install_tool(root,name):
    base=Path(root)/'.campus/tools';system={'Windows':'windows','Darwin':'mac','Linux':'linux'}.get(platform.system())
    arch='aarch64' if platform.machine().lower() in ('arm64','aarch64') else 'x64'
    if not system:raise PolicyError('UNSUPPORTED_OS')
    if name=='node':
        index=json.load(urllib.request.urlopen('https://nodejs.org/dist/index.json',timeout=30))
        version=next(x['version'] for x in index if x['version'].startswith('v24.'))
        osname={'windows':'win','mac':'darwin','linux':'linux'}[system];nodearch='arm64' if arch=='aarch64' else 'x64'
        filename=f'node-{version}-{osname}-{nodearch}'+('.zip' if system=='windows' else '.tar.gz')
        prefix=f'https://nodejs.org/dist/{version}/'
        sums=urllib.request.urlopen(prefix+'SHASUMS256.txt',timeout=30).read().decode()
        sha=next(line.split()[0] for line in sums.splitlines() if line.split()[-1]==filename)
        archive=base/filename;download(prefix+filename,archive,sha);safe_extract(archive,base/'node')
    else:
        url=f'https://api.adoptium.net/v3/assets/latest/21/hotspot?architecture={arch}&image_type=jdk&os={system}&vendor=eclipse'
        package=json.load(urllib.request.urlopen(url,timeout=30))[0]['binary']['package']
        archive=base/package['name'];download(package['link'],archive,package['checksum']);safe_extract(archive,base/'java')
    save(base/(name+'-download.local'),{'source':url if name!='node' else prefix,'sha256':sha if name=='node' else package['checksum']})

def tools(root,install=False,_attempted=frozenset()):
    root=Path(root);env=os.environ.copy();notices=[]
    existing_node=shutil.which('node');existing_java=shutil.which('java')
    candidates={'node':([Path(existing_node)] if existing_node else [])+list((root/'.campus/tools/node').rglob('node.exe' if os.name=='nt' else 'node')),
      'java':([Path(existing_java)] if existing_java else [])+list((root/'.campus/tools/java').rglob('java.exe' if os.name=='nt' else 'java'))}
    if os.name=='nt':candidates['java']+=list((Path.home()/'AppData/Local/FduStudyRoom/tools').glob('*/bin/java.exe'))
    picked={}
    for name,major in [('node',24),('java',21)]:
        for path in candidates[name]:
            try:
                value=subprocess.run([str(path),'--version' if name=='node' else '-version'],capture_output=True,encoding='utf-8',errors='replace')
                version=value.stdout+value.stderr
                if (re.search(r'v24\.',version) if name=='node' else re.search(r'version "21\.',version)):
                    picked[name]=str(path);break
            except OSError:pass
        if name not in picked and install and name not in _attempted:
            install_tool(root,name);return tools(root,True,_attempted|{name})
        if name not in picked:raise PolicyError(name.upper()+'_MISSING_OR_WRONG_MAJOR: run build --install')
    if not shutil.which('git'):
        if install and shutil.which('winget'):run(['winget','install','--id','Git.Git','--exact','--scope','user','--silent']);raise PolicyError('RESTART_TERMINAL_TO_LOAD_GIT')
        if install and shutil.which('brew'):run(['brew','install','git']);return tools(root,False)
        raise PolicyError('GIT_MISSING: install Git from https://git-scm.com/downloads')
    ide=any(shutil.which(x) for x in ['code','codearts','codearts-ide','idea']) or any(os.environ.get(x) for x in ['VSCODE_PID','VSCODE_IPC_HOOK_CLI','IDEA_INITIAL_DIRECTORY'])
    if not ide:notices.append('IDE_NOT_DETECTED: install CodeArts IDE/VS Code or open its terminal; build may continue without an IDE')
    env['JAVA_HOME']=str(Path(picked['java']).parent.parent)
    env['PATH']=str(Path(picked['node']).parent)+os.pathsep+str(Path(picked['java']).parent)+os.pathsep+env.get('PATH','')
    return picked,env,notices

def sandbox_project(root,member,module,base,instance=""):
    if not re.fullmatch('[0-9a-f]{40}',base):raise PolicyError('INVALID_BASE_COMMIT')
    if instance and not re.fullmatch('mutation-[0-9a-f]{12}',instance):raise PolicyError('INVALID_SANDBOX_INSTANCE')
    suffix=('-'+instance) if instance else ''
    return 'fdu-'+hashlib.sha256(str(Path(root).resolve()).encode()).hexdigest()[:8]+'-'+member['id']+'-'+member['layer']+'-'+module.lower()+'-'+base[:8]+suffix

def fixture_provider(ctx):
    if ctx['member']['layer']!='backend':return None
    return {'M01':'workflow','M04':'resource','M05':'resource'}.get(ctx['module'])

def _fixture_hash(path):return hashlib.sha256(Path(path).read_bytes()).hexdigest()
def _fixture_digest(data):return hashlib.sha256(json.dumps(data,sort_keys=True,separators=(',',':')).encode()).hexdigest()

def _fixture_home(worktree):
    home=Path(worktree)/'.campus/frozen-fixtures'
    if home.is_symlink():raise PolicyError('ENV_FIXTURE_CACHE_LINK')
    return home

def _fixture_image(root):
    image=run(docker(root)+['image','inspect','--format','{{.Id}}','fdu-campus-ci:baseline'])
    if not re.fullmatch(r'sha256:[0-9a-f]{64}',image):raise PolicyError('ENV_FIXTURE_TRUSTED_IMAGE_MISSING')
    return image

def _fixture_runner(root,ctx):
    project=sandbox_project(ctx.get('policy_root',root),ctx['member'],ctx['module'],ctx['base'],ctx.get('instance',''))
    ids=run(docker(root)+['ps','-aq','--filter','label=com.docker.compose.project='+project,'--filter','label=com.docker.compose.service=runner']).split()
    if len(ids)>1:raise PolicyError('ENV_FIXTURE_AMBIGUOUS_RUNNER')
    return ids[0] if ids else None

def _fixture_validate(directory,ctx,image,source_sha):
    provider=fixture_provider(ctx);directory=Path(directory);jar=directory/(provider+'.jar');manifest=directory/'manifest.json'
    try:
        if directory.is_symlink() or jar.is_symlink() or manifest.is_symlink():raise ValueError('links')
        if {entry.name for entry in directory.iterdir()}!={jar.name,manifest.name}:raise ValueError('unexpected cache contents')
        proof=json.loads(manifest.read_text(encoding='utf-8'));artifact=proof['artifacts'][provider]
        if proof['coreCommit']!=ctx['base'] or proof['trustedImage']!=image or proof['productionSourceSha256']!=source_sha or proof['provider']!=provider:raise ValueError('provenance')
        if artifact['file']!=jar.name or artifact['sha256']!=_fixture_hash(jar):raise ValueError('bytes')
        with zipfile.ZipFile(jar) as archive:
            text=archive.read('META-INF/MANIFEST.MF').decode().replace('\r\n ','')
            if not re.search(r'^Start-Class: com\.campus\.'+provider.title()+r'Application\r?$',text,re.M):raise ValueError('Start-Class')
            if not any(name.startswith('BOOT-INF/classes/') for name in archive.namelist()):raise ValueError('not executable provider')
        return {'provider':provider,'directory':str(directory),'coreCommit':ctx['base'],'trustedImage':image,'productionSourceSha256':source_sha,'jarSha256':artifact['sha256'],'manifestSha256':_fixture_hash(manifest),'fingerprint':_fixture_digest(proof)}
    except (OSError,ValueError,KeyError,zipfile.BadZipFile) as error:raise PolicyError('ENV_FIXTURE_INVALID_CACHE: preserve it for diagnosis; no writable target fallback') from error

def prepare_fixture(root,worktree,ctx,image):
    """Export only frozen Git bytes; build in an ephemeral offline container, never the member runner."""
    provider=fixture_provider(ctx)
    if not provider:return None
    if not re.fullmatch('[0-9a-f]{40}',ctx['base']):raise PolicyError('ENV_FIXTURE_INVALID_BASE')
    archive=git_run(Path(ctx.get('policy_root',root)),'archive','--format=tar',ctx['base'],'backend').stdout
    source_sha=hashlib.sha256(archive).hexdigest();home=_fixture_home(worktree);home.mkdir(parents=True,exist_ok=True)
    directory=home/(provider+'-'+ctx['base']+'-'+image.split(':')[1])
    if directory.exists():return _fixture_validate(directory,ctx,image,source_sha)
    # Independent staging directories and atomic publication need no shared cache/receipt lock.
    with tempfile.TemporaryDirectory(prefix='.prepare-',dir=home) as temporary:
        staging=Path(temporary);source=staging/'source';source.mkdir();output=staging/'out';output.mkdir()
        with tarfile.open(fileobj=io.BytesIO(archive)) as tar:
            for entry in tar.getmembers():
                parts=Path(entry.name).parts
                if not parts or parts[0]!='backend' or any(x in parts for x in ('.campus','.git','target')) or entry.issym() or entry.islnk():raise PolicyError('ENV_FIXTURE_UNSAFE_FROZEN_SOURCE')
            tar.extractall(source,filter='data')
        for module in ('platform-common','resource-service','workflow-service','buffer-service'):(source/'backend'/module/'target').mkdir(parents=True,exist_ok=True)
        name='campus-fixture-'+uuid.uuid4().hex
        command=['sh','-ec','cp -a /opt/warm/m2 /cache/m2; mvn -o -B -f /src/pom.xml -Dmaven.repo.local=/cache/m2/repository -pl '+provider+'-service -am -DskipTests package; cp /src/'+provider+'-service/target/'+provider+'-service-0.1.0-SNAPSHOT.jar /out/'+provider+'.jar']
        arguments=docker(root)+['run','--rm','--name',name,'--network','none','--read-only','--cap-drop','ALL','--security-opt','no-new-privileges:true','--pids-limit','256','--memory','2g','--cpus','2','--user',bind_identity(root,image,output),'--mount','type=bind,src='+docker_path(source/'backend')+',dst=/src,readonly','--mount','type=bind,src='+docker_path(output)+',dst=/out','--tmpfs','/cache:rw,nosuid,nodev,size=1g','--tmpfs','/tmp:rw,nosuid,nodev,size=256m']
        for module in ('platform-common','resource-service','workflow-service','buffer-service'):arguments+=['--tmpfs','/src/'+module+'/target:rw,nosuid,nodev,size=256m']
        try:run(arguments+[image,*command])
        finally:subprocess.run(docker(root)+['rm','-f',name],capture_output=True,timeout=30)
        jar=output/(provider+'.jar');proof={'version':1,'coreCommit':ctx['base'],'provider':provider,'trustedImage':image,'productionSourceSha256':source_sha,'artifacts':{provider:{'file':jar.name,'sha256':_fixture_hash(jar)}}};save(output/'manifest.json',proof)
        _fixture_validate(output,ctx,image,source_sha)
        try:output.rename(directory)
        except FileExistsError:pass
    return _fixture_validate(directory,ctx,image,source_sha)

def _fixture_database(root,compose,project,provider,create=True):
    database='frozen_'+provider+'_fixture';prefix=docker(root)+['compose','-p',project,'-f',docker_path(compose),'exec','-T','postgres','psql','-U','module_user','-d','postgres','-v','ON_ERROR_STOP=1','-tA','-c']
    query="SELECT pg_get_userbyid(datdba) FROM pg_database WHERE datname='"+database+"'"
    owner=run(prefix+[query])
    if not owner and create:run(prefix+['CREATE DATABASE '+database+' OWNER module_user']);owner=run(prefix+[query])
    if owner!='module_user':raise PolicyError('ENV_FIXTURE_DATABASE_OWNER_MISMATCH')

def fixture_ready(root,worktree,ctx,allow_stopped=False):
    """Read-only preflight; never prepare, rebuild, edit Compose, reserve receipts or restart a runner."""
    if not fixture_provider(ctx):return None
    worktree=Path(worktree);metadata=worktree/'.campus/fixture.local';compose=worktree/'.campus/sandbox.local.json'
    home=_fixture_home(worktree)
    if not metadata.is_file() or not compose.is_file():raise PolicyError('ENV_FIXTURE_NOT_READY: run the existing sandbox --start stage first')
    saved=json.loads(metadata.read_text(encoding='utf-8'));directory=Path(saved['directory'])
    if not directory.resolve().is_relative_to(home.resolve()):raise PolicyError('ENV_FIXTURE_CACHE_OUTSIDE_TASK')
    proof=_fixture_validate(directory,ctx,_fixture_image(root),saved['productionSourceSha256'])
    if proof['fingerprint']!=saved['fingerprint'] or _fixture_hash(compose)!=saved['composeSha256']:raise PolicyError('ENV_FIXTURE_CONFIG_CHANGED: preserve existing runner and evidence')
    runner=_fixture_runner(root,ctx)
    if not runner:raise PolicyError('ENV_FIXTURE_RUNNER_NOT_READY')
    inspect=json.loads(run(docker(root)+['inspect',runner]))[0]
    project=sandbox_project(ctx.get('policy_root',root),ctx['member'],ctx['module'],ctx['base'],ctx.get('instance',''))
    expected=run(docker(root)+['compose','-p',project,'-f',docker_path(compose),'config','--hash','runner']).split()[-1]
    labels=inspect['Config']['Labels'];mounts=inspect['Mounts'];fixture=[m for m in mounts if m['Destination']=='/opt/campus-fixtures']
    if inspect['Image']!=proof['trustedImage'] or labels.get('com.docker.compose.config-hash')!=expected or len(fixture)!=1 or fixture[0]['RW'] or fixture[0]['Source']!=docker_path(directory) or any('docker.sock' in m.get('Source','') for m in mounts):raise PolicyError('ENV_FIXTURE_RUNNER_CONFIG_MISMATCH: do not recreate an existing runner')
    prefix=docker(root)+['compose','-p',project,'-f',docker_path(compose)]
    postgres=run(prefix+['ps','-aq','postgres']).split()
    if len(postgres)!=1:raise PolicyError('ENV_FIXTURE_POSTGRES_NOT_READY')
    pg=json.loads(run(docker(root)+['inspect',postgres[0]]))[0]
    pg_hash=run(prefix+['config','--hash','postgres']).split()[-1]
    pg_image=run(docker(root)+['image','inspect','--format','{{.Id}}',json.loads(compose.read_text())['services']['postgres']['image']])
    pg_volumes=[m for m in pg['Mounts'] if m['Destination']=='/var/lib/postgresql']
    if pg['Image']!=pg_image or pg['Config']['Labels'].get('com.docker.compose.config-hash')!=pg_hash or len(pg_volumes)!=1 or pg_volumes[0].get('Name')!=project+'_postgres':raise PolicyError('ENV_FIXTURE_POSTGRES_CONFIG_MISMATCH')
    stopped=not inspect['State']['Running'] or not pg['State']['Running']
    if stopped and not allow_stopped:raise PolicyError('ENV_FIXTURE_RUNNER_NOT_READY: record never starts stopped services')
    if not stopped and pg['State'].get('Health',{}).get('Status')!='healthy':raise PolicyError('ENV_FIXTURE_POSTGRES_NOT_HEALTHY')
    if inspect['State']['Running']:
        digest=run(docker(root)+['exec',runner,'sha256sum','/opt/campus-fixtures/'+proof['provider']+'.jar']).split()[0]
        if digest!=proof['jarSha256']:raise PolicyError('ENV_FIXTURE_MOUNT_HASH_MISMATCH')
    return {**proof,'_stopped':stopped,'_containers':{'runner':inspect['Id'],'postgres':pg['Id']}}

def _fixture_wait(root,containers):
    deadline=time.monotonic()+60
    while time.monotonic()<deadline:
        states={row['Id']:row['State'] for row in json.loads(run(docker(root)+['inspect',*containers.values()]))}
        if all(states[cid]['Running'] for cid in containers.values()) and states[containers['postgres']].get('Health',{}).get('Status')=='healthy':return
        time.sleep(1)
    raise PolicyError('ENV_FIXTURE_START_NOT_READY: existing containers preserved')

def sandbox(root,worktree,policy,member,ctx,start=False):
    ctx={**ctx,'member':member}
    m=module_for(policy,ctx['module']);dc=docker(root);project=sandbox_project(ctx.get('policy_root',root),member,m['id'],ctx['base'],ctx.get('instance',''))
    version=run(dc+['info','--format','{{.ServerVersion}}'])
    if int(version.split('.')[0])<28:raise PolicyError('DOCKER_28_REQUIRED_FOR_STRICT_NETWORK_ISOLATION')
    home=worktree/'.campus';home.mkdir(exist_ok=True)
    provider=fixture_provider(ctx);fixture=None;image='fdu-campus-ci:baseline'
    if provider and _fixture_runner(root,ctx):
        checked=fixture_ready(root,worktree,ctx,allow_stopped=start)
        if start and checked['_stopped']:
            compose=home/'sandbox.local.json'
            # start cannot create/recreate containers; this Compose version has no start --wait.
            run(dc+['compose','-p',project,'-f',docker_path(compose),'start','postgres','runner'])
            _fixture_wait(root,checked['_containers'])
            ready=fixture_ready(root,worktree,ctx)
            if ready['_containers']!=checked['_containers']:raise PolicyError('ENV_FIXTURE_CONTAINER_CHANGED_DURING_START')
            _fixture_database(root,compose,project,provider,create=False)
        print('Existing runner and frozen fixture verified; no rebuild or recreation.')
        return
    if provider and start:
        if (home/'fixture.local').exists() or (home/'runs').exists():raise PolicyError('ENV_FIXTURE_EXISTING_EVIDENCE: preserve runner/configuration; coordinator review required')
        image=_fixture_image(root);fixture=prepare_fixture(root,worktree,ctx,image)

    private=home/'database.local'
    password=''
    if member['layer']=='backend':
        password=json.loads(private.read_text())['password'] if private.exists() else secrets.token_urlsafe(32)
        save(private,{'password':password})
    for target in ['backend/platform-common/target','backend/resource-service/target','backend/workflow-service/target','frontend/dist','frontend/node_modules']:(worktree/target).mkdir(parents=True,exist_ok=True)
    volumes=[{'type':'bind','source':docker_path(worktree),'target':'/workspace','read_only':True}]
    for prefix in m['write_paths'][member['layer']]:
        path=worktree/prefix;path.mkdir(parents=True,exist_ok=True)
        volumes.append({'type':'bind','source':docker_path(path),'target':'/workspace/'+prefix.rstrip('/')})
    for frozen in policy.get('protected_paths',[]):
        if not frozen.endswith('/') and any(frozen.startswith(prefix) for prefix in m['write_paths'][member['layer']]) and (worktree/frozen).is_file():volumes.append({'type':'bind','source':docker_path(worktree/frozen),'target':'/workspace/'+frozen,'read_only':True})
    cache_volume=({'type':'volume','source':'m2','target':'/cache/m2'} if member['layer']=='backend' else
                  {'type':'volume','source':'node_modules','target':'/workspace/frontend/node_modules'})
    volumes.append(cache_volume)
    data={'services':{'postgres':{'image':PG,'environment':{'POSTGRES_USER':'module_user','POSTGRES_PASSWORD':password,'POSTGRES_DB':'module_test'},'volumes':['postgres:/var/lib/postgresql'],'networks':['private'],'healthcheck':{'test':['CMD-SHELL','pg_isready -U module_user -d module_test'],'interval':'2s','retries':20}},'runner':{'image':'fdu-campus-ci:baseline','read_only':True,'working_dir':'/workspace','command':['sleep','infinity'],'cap_drop':['ALL'],'security_opt':['no-new-privileges:true'],'pids_limit':256,'mem_limit':'2g','cpus':2,'tmpfs':['/tmp:rw,nosuid,nodev,size=1g','/workspace/.campus:ro,nosuid,nodev,noexec,size=1m,mode=0555','/workspace/backend/platform-common/target:rw','/workspace/backend/resource-service/target:rw','/workspace/backend/workflow-service/target:rw','/workspace/frontend/dist:rw'],'volumes':volumes,'environment':{'CAMPUS_SANDBOX':'1','CAMPUS_DB_URL':'jdbc:postgresql://postgres:5432/module_test','CAMPUS_DB_USER':'module_user','CAMPUS_DB_PASSWORD':password},'networks':['private'],'depends_on':{'postgres':{'condition':'service_healthy'}}}},'networks':{'private':{'internal':True,'driver_opts':{'com.docker.network.bridge.gateway_mode_ipv4':'isolated'}}},'volumes':{'postgres':{},'m2':{},'node_modules':{}}}
    if member['layer']=='frontend':
        del data['services']['postgres'];data['services']['runner'].pop('depends_on',None);data['services']['runner'].pop('networks',None);data['services']['runner']['network_mode']='none';data['services']['runner']['environment']={'CAMPUS_SANDBOX':'1'};data.pop('networks',None);data['volumes'].pop('postgres',None)
    data['volumes'].pop('node_modules' if member['layer']=='backend' else 'm2')
    if start:data['services']['runner']['user']=bind_identity(root,image,worktree)
    data['services']['runner']['environment'].update({'HOME':'/tmp/campus-home','MAVEN_USER_HOME':'/cache/m2','MAVEN_CONFIG':'/cache/m2','MAVEN_ARGS':'-Dmaven.repo.local=/cache/m2/repository','npm_config_cache':'/tmp/npm-cache'})
    if fixture:
        data['services']['runner']['image']=image
        data['services']['runner']['volumes'].append({'type':'bind','source':docker_path(fixture['directory']),'target':'/opt/campus-fixtures','read_only':True})
        key='CAMPUS_FROZEN_'+provider.upper();target='/opt/campus-fixtures/'
        data['services']['runner']['environment'].update({key+'_JAR':target+provider+'.jar',key+'_SHA256':fixture['jarSha256'],key+'_MANIFEST':target+'manifest.json',key+'_COMMIT':ctx['base'],key+'_DB_URL':'jdbc:postgresql://postgres:5432/frozen_'+provider+'_fixture',key+'_DB_USER':'module_user',key+'_DB_PASSWORD':password})
        if _fixture_runner(root,ctx):raise PolicyError('ENV_FIXTURE_RUNNER_APPEARED: preserve it; retry after coordinator review')
    compose=home/'sandbox.local.json';save(compose,data)
    if fixture:save(home/'fixture.local',{**fixture,'composeSha256':_fixture_hash(compose)})
    if start:
        run(dc+['compose','-p',project,'-f',docker_path(compose),'up','-d','--wait'])
        # Repair only the assigned layer's cache; do not scan the other image tree.
        helper=Path(__file__).with_name('cache.py').read_text(encoding='utf-8')
        cache_paths=['/opt/warm/m2','/cache/m2'] if member['layer']=='backend' else ['/opt/warm/frontend/node_modules','/workspace/frontend/node_modules']
        run(dc+['compose','-p',project,'-f',docker_path(compose),'exec','-T','--user','0','runner','python3','-',*cache_paths],input=helper)
        if fixture:
            _fixture_database(root,compose,project,provider)
            fixture_ready(root,worktree,ctx)
    print('Private sandbox prepared; only assigned paths writable, no Docker socket, no other member credentials, private DB/network/caches. '+str(compose))

def background(args,cwd,env,log):
    log=Path(log);log.parent.mkdir(parents=True,exist_ok=True)
    with log.open('ab') as output:
        options={'creationflags':subprocess.CREATE_NO_WINDOW|subprocess.CREATE_NEW_PROCESS_GROUP} if os.name=='nt' else {'start_new_session':True}
        process=subprocess.Popen([str(x) for x in args],cwd=str(cwd),env=env,stdout=output,stderr=output,**options)
    return process.pid

def free_port(port):
    with socket.socket() as s:
        try:s.bind(('127.0.0.1',port))
        except OSError:raise PolicyError('PORT_IN_USE: '+str(port)+'; do not stop another project')

def runtime_jar(source,private):
    source=Path(source);digest=hashlib.sha256(source.read_bytes()).hexdigest();folder=Path(private)/'artifacts';folder.mkdir(parents=True,exist_ok=True)
    target=folder/(source.stem+'-'+digest+'.jar')
    if target.exists():
        if hashlib.sha256(target.read_bytes()).hexdigest()!=digest:raise PolicyError('RUNTIME_ARTIFACT_CHECKSUM_MISMATCH')
    else:shutil.copyfile(source,target)
    return target

def start_buffer(root,picked,env):
    root=Path(root);private=root/'.campus/buffer';runtime=json.loads((private/'runtime.local').read_text())
    compose={'name':'fdu-team-buffer','services':{'postgres':{'image':PG,'restart':'unless-stopped','environment':{'POSTGRES_DB':'campus_buffer','POSTGRES_USER':'campus_buffer','POSTGRES_PASSWORD':runtime['postgres_password']},'ports':['127.0.0.1:'+str(runtime['postgres_port'])+':5432'],'volumes':['data:/var/lib/postgresql'],'healthcheck':{'test':['CMD-SHELL','pg_isready -U campus_buffer -d campus_buffer'],'interval':'2s','retries':20}}},'volumes':{'data':{}}}
    path=private/'compose.local.json';save(path,compose);dc=docker(root)
    run(dc+['compose','-f',docker_path(path),'up','-d','--wait'])
    env={**env,'CAMPUS_BUFFER_CONFIG':str(private/'config.local'),'CAMPUS_BUFFER_DB_URL':f"jdbc:postgresql://127.0.0.1:{runtime['postgres_port']}/campus_buffer",'CAMPUS_BUFFER_DB_USER':'campus_buffer','CAMPUS_BUFFER_DB_PASSWORD':runtime['postgres_password'],'SERVER_PORT':str(runtime['server_port'])}
    jar=next(p for p in (root/'tools/buffer/backend/target').glob('*.jar') if not p.name.endswith('.original'))
    jar=runtime_jar(jar,private)
    pid=launch_owned([picked['java'],'-jar',jar],root,env,private,'server',runtime['server_port'])
    save(private/'process.local',{'pid':pid,'url':'http://127.0.0.1:'+str(runtime['server_port'])})
    url='http://127.0.0.1:'+str(runtime['server_port'])+'/health'
    deadline=time.monotonic()+60
    while time.monotonic()<deadline:
        try:
            with urllib.request.urlopen(url,timeout=2) as response:
                if response.status==200:print('Buffer ready: '+url.removesuffix('/health'));return
        except OSError:time.sleep(1)
    raise PolicyError('BUFFER_NOT_READY: inspect private server.log, do not print credentials')


def host_path(path):
    path=Path(path).resolve()
    if os.name=='nt' and str(path).startswith('\\\\'):
        # Reuse an existing mapping. Never create/remove shared pushd drive mappings.
        for letter in 'ZYXWVUTSRQPONMLKJIHGFED':
            drive=Path(letter+':/')
            try:
                target=drive.resolve()
                if drive.exists() and path.is_relative_to(target):return drive/path.relative_to(target)
            except OSError:pass
        raise PolicyError('UNC_NATIVE_TOOLS_REQUIRE_MAPPING: use an existing drive mapping or clone on a local Windows drive')
    return path

def process_stamp(pid):
    if os.name=='nt':
        import ctypes
        from ctypes import wintypes
        kernel=ctypes.WinDLL('kernel32',use_last_error=True)
        kernel.OpenProcess.restype=wintypes.HANDLE
        handle=kernel.OpenProcess(0x1000,False,int(pid))
        if not handle:return None
        values=[wintypes.FILETIME() for _ in range(4)]
        try:
            exit_code=wintypes.DWORD()
            if not kernel.GetExitCodeProcess(handle,ctypes.byref(exit_code)) or exit_code.value!=259:return None
            if not kernel.GetProcessTimes(handle,*[ctypes.byref(v) for v in values]):return None
            return str((values[0].dwHighDateTime<<32)|values[0].dwLowDateTime)
        finally:kernel.CloseHandle(handle)
    result=subprocess.run(['ps','-p',str(int(pid)),'-o','lstart='],capture_output=True,text=True)
    return result.stdout.strip() or None

def wait_http(url,seconds=75):
    deadline=time.monotonic()+seconds
    while time.monotonic()<deadline:
        try:
            with urllib.request.urlopen(url,timeout=2) as response:
                if response.status==200:return
        except OSError:time.sleep(1)
    raise PolicyError('SERVICE_NOT_READY: '+url)

def launch_owned(args,cwd,env,private,name,port):
    state=private/(name+'-process.local')
    inputs=[str(x) for x in args]+[hashlib.sha256(Path(x).read_bytes()).hexdigest() for x in args if str(x).endswith('.jar') and Path(x).is_file()]
    inputs.append(hashlib.sha256(json.dumps({k:v for k,v in env.items() if k.startswith(('CAMPUS_','SERVER_','SPRING_'))},sort_keys=True).encode()).hexdigest())
    if env.get('CAMPUS_BUFFER_CONFIG') and Path(env['CAMPUS_BUFFER_CONFIG']).is_file():inputs.append(hashlib.sha256(Path(env['CAMPUS_BUFFER_CONFIG']).read_bytes()).hexdigest())
    signature=hashlib.sha256(json.dumps(inputs).encode()).hexdigest()
    restarting=False
    if state.exists():
        old=json.loads(state.read_text())
        if process_stamp(old['pid'])==old.get('stamp') and old.get('stamp'):
            if old.get('signature')==signature:return old['pid']
            import signal
            os.kill(old['pid'],signal.SIGTERM)
            restarting=True
            deadline=time.monotonic()+10
            while process_stamp(old['pid'])==old['stamp'] and time.monotonic()<deadline:time.sleep(.1)
    deadline=time.monotonic()+(15 if restarting else 0)
    while True:
        try:free_port(port);break
        except PolicyError:
            if time.monotonic()>=deadline:raise
            time.sleep(.25)
    pid=background(args,cwd,env,private/(name+'.log'))
    save(state,{'pid':pid,'stamp':process_stamp(pid),'signature':signature,'port':port})
    return pid

def start_application(root,picked,env):
    import base64
    root=Path(root);native=host_path(root);private=root/'.campus/app';private.mkdir(parents=True,exist_ok=True)
    config=private/'runtime.local'
    if config.exists():runtime=json.loads(config.read_text())
    else:
        runtime={'resource_db_port':55432,'workflow_db_port':55433,'resource_port':8080,'workflow_port':8081,'frontend_port':5173,'resource_password':secrets.token_urlsafe(32),'workflow_password':secrets.token_urlsafe(32),'signing_key':base64.b64encode(secrets.token_bytes(32)).decode(),'service_key':secrets.token_urlsafe(32),'admin_login':'admin','admin_password':secrets.token_urlsafe(24)}
        save(config,runtime)
    project='fdu-app-'+hashlib.sha256(str(root).encode()).hexdigest()[:8]
    services={}
    for name in ['resource','workflow']:
        services[name+'-db']={'image':PG,'restart':'unless-stopped','environment':{'POSTGRES_DB':'fdu_'+name,'POSTGRES_USER':'campus_'+name,'POSTGRES_PASSWORD':runtime[name+'_password']},'ports':['127.0.0.1:'+str(runtime[name+'_db_port'])+':5432'],'volumes':[name+'_data:/var/lib/postgresql'],'healthcheck':{'test':['CMD-SHELL','pg_isready -U campus_'+name+' -d fdu_'+name],'interval':'2s','retries':25}}
    compose=private/'compose.local.json';save(compose,{'name':project,'services':services,'volumes':{'resource_data':{},'workflow_data':{}}})
    run(docker(root)+['compose','-f',docker_path(compose),'up','-d','--wait'])
    common={**env,'SPRING_PROFILES_ACTIVE':'local','SERVER_ADDRESS':'127.0.0.1','CAMPUS_AUTH_SIGNING_KEY':runtime['signing_key'],'CAMPUS_AUTH_SERVICE_KEY':runtime['service_key'],'CAMPUS_AUTH_MODE':'password'}
    for name in ['resource','workflow']:
        jar=next(p for p in (native/'backend'/ (name+'-service')/'target').glob('*.jar') if not p.name.endswith('.original'))
        jar=runtime_jar(jar,private)
        service_env={**common,'SERVER_PORT':str(runtime[name+'_port']),'CAMPUS_DB_URL':f"jdbc:postgresql://127.0.0.1:{runtime[name+'_db_port']}/fdu_{name}",'CAMPUS_DB_USER':'campus_'+name,'CAMPUS_DB_PASSWORD':runtime[name+'_password'],'CAMPUS_WORKFLOW_IDENTITY_URL':f"http://127.0.0.1:{runtime['resource_port']}/internal/auth/introspect"}
        if name=='resource':service_env.update(CAMPUS_BOOTSTRAP_ADMIN_LOGIN=runtime['admin_login'],CAMPUS_BOOTSTRAP_ADMIN_PASSWORD=runtime['admin_password'])
        launch_owned([picked['java'],'-jar',jar],native,service_env,private,name,runtime[name+'_port'])
        wait_http(f"http://127.0.0.1:{runtime[name+'_port']}/health")
    front_env={**env,'CAMPUS_API_TARGET':f"http://127.0.0.1:{runtime['resource_port']}",'CAMPUS_WORKFLOW_TARGET':f"http://127.0.0.1:{runtime['workflow_port']}",'CHOKIDAR_USEPOLLING':'1','CHOKIDAR_INTERVAL':'300'}
    launch_owned([picked['node'],native/'frontend/node_modules/vite/bin/vite.js','--host','127.0.0.1','--port',str(runtime['frontend_port'])],native/'frontend',front_env,private,'frontend',runtime['frontend_port'])
    url=f"http://127.0.0.1:{runtime['frontend_port']}";wait_http(url+'/api/auth/config')
    print('Application ready: '+url+'/login; private admin credentials in .campus/app/runtime.local')

@contextmanager
def verification_environment(root, env):
    """Prepare only this checkout's verification services, separate from application data."""
    root=Path(root);private=root/'.campus/verify';private.mkdir(parents=True,exist_ok=True)
    state=private/'runtime.local'
    runtime=json.loads(state.read_text(encoding='utf-8')) if state.exists() else {'postgres_password':secrets.token_urlsafe(32)}
    if not isinstance(runtime.get('postgres_password'),str) or len(runtime['postgres_password'])<24:raise PolicyError('INVALID_VERIFICATION_CONFIG')
    save(state,runtime)
    project='fdu-verify-'+hashlib.sha256(str(root.resolve()).encode()).hexdigest()[:12]
    mailpit='axllent/mailpit@sha256:23fe39e81bde596a918000b3135c0f85dc8bb4c9ea6be274b051143f3e300387'
    services={'postgres':{'image':PG,'environment':{'POSTGRES_DB':'module_test','POSTGRES_USER':'module_user','POSTGRES_PASSWORD':runtime['postgres_password']},'ports':['127.0.0.1:0:5432'],'volumes':['postgres:/var/lib/postgresql'],'healthcheck':{'test':['CMD-SHELL','pg_isready -U module_user -d module_test'],'interval':'2s','retries':30}},
      'smtp':{'image':mailpit,'read_only':True,'cap_drop':['ALL'],'security_opt':['no-new-privileges:true'],'mem_limit':'128m','tmpfs':['/tmp:rw,nosuid,nodev,size=32m'],'environment':{'MP_DATABASE':'/tmp/mailpit.db'},'ports':['127.0.0.1:0:1025','127.0.0.1:0:8025'],'healthcheck':{'test':['CMD','/mailpit','readyz'],'interval':'2s','retries':30}}}
    compose=private/'compose.local.json';save(compose,{'name':project,'services':services,'volumes':{'postgres':{}}})
    command=docker(root)+['compose','-p',project,'-f',docker_path(compose)]
    try:
        run(command+['up','-d','--wait','--wait-timeout','120'])
        for name in ['campus_migration_probe','campus_buffer_test']:
            found=run(command+['exec','-T','postgres','psql','-U','module_user','-d','postgres','-tA','-c',"SELECT 1 FROM pg_database WHERE datname='"+name+"'"])
            if found!='1':run(command+['exec','-T','postgres','psql','-U','module_user','-d','postgres','-v','ON_ERROR_STOP=1','-c','CREATE DATABASE '+name+' OWNER module_user'])
        def port(service,target):
            value=run(command+['port',service,str(target)])
            match=re.fullmatch(r'127\.0\.0\.1:([0-9]+)',value)
            if not match or not 0<int(match[1])<65536:raise PolicyError('VERIFICATION_PORT_UNAVAILABLE')
            return match[1]
        postgres=port('postgres',5432);smtp=port('smtp',1025);inbox=port('smtp',8025)
        output={**env,'CAMPUS_TEST_SMTP_HOST':'127.0.0.1','CAMPUS_TEST_SMTP_PORT':smtp,'CAMPUS_TEST_MAILPIT_URL':'http://127.0.0.1:'+inbox}
        for prefix,name in [('CAMPUS_DB','module_test'),('CAMPUS_TEST_DB','campus_migration_probe'),('CAMPUS_BUFFER_TEST_DB','campus_buffer_test')]:
            output.update({prefix+'_URL':'jdbc:postgresql://127.0.0.1:'+postgres+'/'+name,prefix+'_USER':'module_user',prefix+'_PASSWORD':runtime['postgres_password']})
        yield output
    finally:
        failed=sys.exc_info()[0] is not None
        try:
            run(command+['down'])
            save(private/'cleanup.local',{'state':'STOPPED','volumes':'preserved','project':project})
        except Exception as error:
            save(private/'cleanup.local',{'state':'ERROR','project':project,'reason':type(error).__name__})
            if not failed:raise


def build(root,args):
    global LOG_DIRECTORY
    root=Path(root);state=root/'.campus/build.local';results=[];LOG_DIRECTORY=root/'.campus/build-logs'
    def stage(name,status='passed',detail=None):
        results.append({'stage':name,'status':status,'detail':detail});save(state,{'stages':results});print(name+': '+status+((' — '+str(detail)) if detail else ''),flush=True)
    try:
        stage('0 repository',detail='Current checkout preserved; clean-tree updates use ff-only')
        picked,env,notices=tools(root,args.install);stage('1 tools and IDE',detail=notices)
        dc=docker(root);run(dc+['info','--format','{{.ServerVersion}}']);run(dc+['compose','version']);stage('2 container engine')
        native=host_path(root)
        stage('3 source and locked stack',detail='Java21 / Node24 / PostgreSQL / Maven Wrapper / frozen npm lock')
        node_modules=native/'frontend/node_modules'
        npm=Path(picked['node']).parent/'node_modules/npm/bin/npm-cli.js'
        if not npm.exists():
            found=shutil.which('npm');npm=Path(found).resolve() if found else npm
        if not node_modules.exists():run([picked['node'],npm,'ci'],cwd=native/'frontend',env=env)
        stage('4 frozen frontend dependencies',detail='Shared by app and buffer; no second lockfile')
        wrapper=native/'backend'/('mvnw.cmd' if os.name=='nt' else 'mvnw')
        maven=[str(wrapper)] if os.name=='nt' else ['sh',str(wrapper)]
        if args.buffer:
            if not (root/'.campus/buffer/config.local').exists():run([sys.executable,root/'scripts/team.py','init-buffer'])
            stage('5 private buffer config')
            with verification_environment(root,env) as verify_env:
                stage('5 verification services',detail='private PostgreSQL and SMTP ready')
                run(maven+['-B','-q','-f',str(native/'tools/buffer/backend/pom.xml'),'verify'],cwd=native/'backend',env=verify_env);stage('6 buffer backend verify')
            run([picked['node'],node_modules/'vue-tsc/bin/vue-tsc.js','--noEmit','-p',native/'tools/buffer/frontend/tsconfig.json'],cwd=native,env=env)
            run([picked['node'],node_modules/'vitest/vitest.mjs','run','--config',native/'tools/buffer/frontend/vite.config.ts','--maxWorkers','1'],cwd=native/'tools/buffer/frontend',env=env)
            run([picked['node'],node_modules/'vite/bin/vite.js','build'],cwd=native/'tools/buffer/frontend',env=env);stage('7 buffer frontend verify')
            if args.start:start_buffer(root,picked,env)
        else:
            with verification_environment(root,env) as verify_env:
                stage('5 verification services',detail='private PostgreSQL and SMTP ready')
                run(maven+['-B','-q','-pl','workflow-service','-am','-DskipTests','package'],cwd=native/'backend',env=verify_env)
                run(maven+['-B','-q','verify'],cwd=native/'backend',env=verify_env);stage('6 backend verify')
            run([picked['node'],node_modules/'vue-tsc/bin/vue-tsc.js','--noEmit','-p','tsconfig.json'],cwd=native/'frontend',env=env)
            run([picked['node'],node_modules/'typescript/bin/tsc','--noEmit','-p','tsconfig.node.json'],cwd=native/'frontend',env=env)
            run([picked['node'],node_modules/'vitest/vitest.mjs','run','--maxWorkers','1'],cwd=native/'frontend',env=env)
            run([picked['node'],node_modules/'vite/bin/vite.js','build'],cwd=native/'frontend',env=env);stage('6 frontend verify')
            if args.start:start_application(root,picked,env)
        stage('8 services and health',detail='started' if args.start else 'startup not requested')
        stage('9 report',detail='No production deployment or remote push; see private stage record')
    except Exception as failure:
        stage('blocked','blocked',str(failure));raise

