"""Recoverable local build and per-member Docker environments (Windows/macOS)."""
import hashlib,json,os,platform,re,secrets,shutil,socket,subprocess,sys,tarfile,time,urllib.request,zipfile
from pathlib import Path
from policy import PolicyError,module_for
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

def sandbox(root,worktree,policy,member,ctx,start=False):
    m=module_for(policy,ctx['module']);dc=docker(root);project=sandbox_project(ctx.get('policy_root',root),member,m['id'],ctx['base'],ctx.get('instance',''))
    version=run(dc+['info','--format','{{.ServerVersion}}'])
    if int(version.split('.')[0])<28:raise PolicyError('DOCKER_28_REQUIRED_FOR_STRICT_NETWORK_ISOLATION')
    home=worktree/'.campus';home.mkdir(exist_ok=True)
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
    volumes+=[{'type':'volume','source':'m2','target':'/cache/m2'},{'type':'volume','source':'node_modules','target':'/workspace/frontend/node_modules'}]
    data={'services':{'postgres':{'image':PG,'environment':{'POSTGRES_USER':'module_user','POSTGRES_PASSWORD':password,'POSTGRES_DB':'module_test'},'volumes':['postgres:/var/lib/postgresql'],'networks':['private'],'healthcheck':{'test':['CMD-SHELL','pg_isready -U module_user -d module_test'],'interval':'2s','retries':20}},'runner':{'image':'fdu-campus-ci:baseline','read_only':True,'working_dir':'/workspace','command':['sleep','infinity'],'cap_drop':['ALL'],'security_opt':['no-new-privileges:true'],'pids_limit':256,'mem_limit':'2g','cpus':2,'tmpfs':['/tmp:rw,nosuid,nodev,size=1g','/workspace/.campus:ro,nosuid,nodev,noexec,size=1m,mode=0555','/workspace/backend/platform-common/target:rw','/workspace/backend/resource-service/target:rw','/workspace/backend/workflow-service/target:rw','/workspace/frontend/dist:rw'],'volumes':volumes,'environment':{'CAMPUS_SANDBOX':'1','CAMPUS_DB_URL':'jdbc:postgresql://postgres:5432/module_test','CAMPUS_DB_USER':'module_user','CAMPUS_DB_PASSWORD':password},'networks':['private'],'depends_on':{'postgres':{'condition':'service_healthy'}}}},'networks':{'private':{'internal':True,'driver_opts':{'com.docker.network.bridge.gateway_mode_ipv4':'isolated'}}},'volumes':{'postgres':{},'m2':{},'node_modules':{}}}
    if member['layer']=='frontend':
        del data['services']['postgres'];data['services']['runner'].pop('depends_on',None);data['services']['runner'].pop('networks',None);data['services']['runner']['network_mode']='none';data['services']['runner']['environment']={'CAMPUS_SANDBOX':'1'};data.pop('networks',None);data['volumes'].pop('postgres',None)
    if start:data['services']['runner']['user']=bind_identity(root,'fdu-campus-ci:baseline',worktree)
    data['services']['runner']['environment'].update({'HOME':'/tmp/campus-home','MAVEN_USER_HOME':'/cache/m2','MAVEN_CONFIG':'/cache/m2','MAVEN_ARGS':'-Dmaven.repo.local=/cache/m2/repository','npm_config_cache':'/tmp/npm-cache'})
    compose=home/'sandbox.local.json';save(compose,data)
    if start:
        run(dc+['compose','-p',project,'-f',docker_path(compose),'up','-d','--wait'])
        # Populate isolated caches from the image before offline test execution.
        run(dc+['compose','-p',project,'-f',docker_path(compose),'exec','-T','--user','0','runner','sh','-ec','cp -Rn /opt/warm/m2/. /cache/m2/; cp -Rn /opt/warm/frontend/node_modules/. /workspace/frontend/node_modules/; chmod -R a+rwX /cache/m2 /workspace/frontend/node_modules'])
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
        if args.buffer:
            if not (root/'.campus/buffer/config.local').exists():run([sys.executable,root/'scripts/team.py','init-buffer'])
            stage('5 private buffer config')
            run([str(wrapper),'-B','-q','-f',str(native/'tools/buffer/backend/pom.xml'),'verify'],cwd=native/'backend',env=env);stage('6 buffer backend verify')
            run([picked['node'],node_modules/'vue-tsc/bin/vue-tsc.js','--noEmit','-p',native/'tools/buffer/frontend/tsconfig.json'],cwd=native,env=env)
            run([picked['node'],node_modules/'vitest/vitest.mjs','run','--config',native/'tools/buffer/frontend/vite.config.ts','--maxWorkers','1'],cwd=native/'tools/buffer/frontend',env=env)
            run([picked['node'],node_modules/'vite/bin/vite.js','build'],cwd=native/'tools/buffer/frontend',env=env);stage('7 buffer frontend verify')
            if args.start:start_buffer(root,picked,env)
        else:
            run([str(wrapper),'-B','-q','verify'],cwd=native/'backend',env=env);stage('5 backend verify')
            run([picked['node'],node_modules/'vue-tsc/bin/vue-tsc.js','--noEmit','-p','tsconfig.json'],cwd=native/'frontend',env=env)
            run([picked['node'],node_modules/'typescript/bin/tsc','--noEmit','-p','tsconfig.node.json'],cwd=native/'frontend',env=env)
            run([picked['node'],node_modules/'vitest/vitest.mjs','run','--maxWorkers','1'],cwd=native/'frontend',env=env)
            run([picked['node'],node_modules/'vite/bin/vite.js','build'],cwd=native/'frontend',env=env);stage('6 frontend verify')
            if args.start:start_application(root,picked,env)
        stage('8 services and health',detail='started' if args.start else 'startup not requested')
        stage('9 report',detail='No production deployment or remote push; see private stage record')
    except Exception as failure:
        stage('blocked','blocked',str(failure));raise

