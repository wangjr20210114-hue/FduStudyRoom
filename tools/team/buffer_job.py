"""Trusted buffer worker. Git and Docker operations use argument arrays, never member shell text."""
import argparse,base64,hashlib,json,os,re,secrets,subprocess,sys,time,urllib.parse,urllib.request,uuid
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parent))
from policy import PolicyError,load,authorize,git,git_run,evidence_valid,guard
from environment import docker,docker_path,bind_identity,PG
from members import scoped
from commits import message

MAILPIT='axllent/mailpit@sha256:23fe39e81bde596a918000b3135c0f85dc8bb4c9ea6be274b051143f3e300387'

class NoCredentialRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self,req,fp,code,msg,headers,newurl):
        # A redirect must never forward the API Authorization header elsewhere.
        return None

def execute(args,cwd=None,env=None,timeout=900,input=None):
    p=subprocess.run([str(x) for x in args],cwd=cwd,env=env,input=input,capture_output=True,timeout=timeout)
    if p.returncode:raise PolicyError('PROCESS_FAILED_'+str(p.returncode))
    return p.stdout

def execute_git(root,*args,env=None,timeout=900,input=None):
    p=git_run(root,*args,env=env,timeout=timeout,input=input,check=False)
    if p.returncode:raise PolicyError('PROCESS_FAILED_'+str(p.returncode))
    return p.stdout

def verify_patch(root,member,module,patch,policy):
    if any(value in patch for value in [b'mode 120000',b'mode 160000',b'\nrename from ',b'\ncopy from ']):raise PolicyError('LINKS_FORBIDDEN')
    output=execute_git(root,'apply','--numstat','-z','--',input=patch)
    paths=[]
    for entry in output.decode('utf-8').split('\0'):
        if not entry:continue
        fields=entry.split('\t',2)
        if len(fields)!=3:raise PolicyError('INVALID_PATCH_PATH')
        paths.append(fields[2])
    if not paths:raise PolicyError('EMPTY_PATCH')
    authorize(policy,member,module,paths)
    return paths

def review_command(layer):
    if layer=='frontend':return 'cd /workspace/frontend && (test -e node_modules || ln -s /opt/warm/frontend/node_modules node_modules) && npm test -- --maxWorkers=1 && npm run build'
    if layer=='backend':return 'cp -R /opt/warm/m2 /tmp/m2 && cd /workspace/backend && MAVEN_USER_HOME=/tmp/m2 mvn -o -B -Dmaven.repo.local=/tmp/m2/repository -pl workflow-service -am -DskipTests package && MAVEN_USER_HOME=/tmp/m2 mvn -o -B -Dmaven.repo.local=/tmp/m2/repository verify -DreuseForks=false'
    raise PolicyError('INVALID_LAYER')

def require_approved_baseline(config,repo,base):
    history=config.get('approvedBaselines',[])
    current=config.get('baseline')
    if not isinstance(history,list) or any(not isinstance(value,str) or not re.fullmatch('[0-9a-f]{40}',value) for value in [current,*history]):
        raise PolicyError('INVALID_APPROVED_BASELINES')
    approved={current,*history}
    if not isinstance(base,str) or base not in approved:raise PolicyError('BASELINE_CHANGED')
    for value in approved:
        result=git_run(repo,'cat-file','-t',value,check=False)
        if result.returncode or result.stdout.strip()!=b'commit':raise PolicyError('APPROVED_BASELINE_NOT_COMMIT')


def verify(config,item):
    root=Path(config['root']);repo=Path(config['repository']);identifier=str(uuid.UUID(item['id']))
    if not item.get('reviewer') or item['reviewer']==item['member_id']:raise PolicyError('HUMAN_REVIEW_REQUIRED')
    member=next((m for m in config['members'] if m['id']==item['member_id']),None)
    if not member:raise PolicyError('MEMBER_MISMATCH')
    member=scoped(member,item['layer'])
    meta=item['metadata'];base=meta['base']
    require_approved_baseline(config,repo,base)
    evidence_valid(meta['evidence'],meta['requirements']);patch=base64.b64decode(item['patch'],validate=True)
    paths=verify_patch(repo,member,item['module_id'],patch,load(root))
    home=root/'.campus/buffer/jobs'/identifier;home.mkdir(parents=True,exist_ok=True)
    fingerprint=hashlib.sha256(patch).hexdigest();cached=home/'verified.local'
    if cached.exists():
        data=json.loads(cached.read_text())
        if data['patch_sha256']==fingerprint and git(repo,'rev-parse',data['branch']).decode().strip()==data['commit']:return data
    worktree=home/'workspace'
    previous=worktree.exists()
    if not previous:git(repo,'worktree','add','--detach',str(worktree),base)
    branch='buffer/'+item['module_id'].lower()+'/'+identifier
    password=secrets.token_urlsafe(24);network='fdu-ci-'+identifier;database=network+'-db';runner=network+'-runner';smtp=network+'-smtp'
    dc=docker(root);made_network=False;made_db=False;made_smtp=False
    try:
        applied=home/'applied.local'
        if previous:
            if not applied.exists():raise PolicyError('PREVIOUS_JOB_REQUIRES_REVIEW')
            old=json.loads(applied.read_text())
            if old['patch_sha256']!=fingerprint or old['tree']!=git(worktree,'write-tree').decode().strip() or git_run(worktree,'diff','--exit-code',check=False).returncode:raise PolicyError('PREVIOUS_JOB_SOURCE_CHANGED')
        else:
            try:execute_git(worktree,'apply','--check','--index','--',input=patch)
            except PolicyError:return {'state':'CONFLICT','code':'PATCH_NO_LONGER_APPLIES','workspace':str(worktree)}
            execute_git(worktree,'apply','--index','--',input=patch)
            applied.write_text(json.dumps({'patch_sha256':fingerprint,'tree':git(worktree,'write-tree').decode().strip()}),encoding='utf-8')
        guard(worktree,load(root),member,item['module_id'],base)
        try:image=execute(dc+['image','inspect','--format','{{.Id}}',config['ciImage']]).decode().strip()
        except PolicyError:raise PolicyError('CI_IMAGE_MISSING: build the official isolated CI image before verification')
        version=execute(dc+['info','--format','{{.ServerVersion}}']).decode().strip()
        if int(version.split('.')[0])<28:raise PolicyError('DOCKER_28_REQUIRED_FOR_STRICT_NETWORK_ISOLATION')
        execute(dc+['network','create','--internal','--opt','com.docker.network.bridge.gateway_mode_ipv4=isolated',network]);made_network=True
        execute(dc+['run','-d','--rm','--memory','256m','--pids-limit','128','--tmpfs','/var/lib/postgresql:rw,size=256m','--entrypoint','/usr/bin/timeout','--name',database,'--network',network,'--network-alias','postgres','-e','POSTGRES_DB=module_test','-e','POSTGRES_USER=module_user','-e','POSTGRES_PASSWORD='+password,PG,'--signal=TERM','--kill-after=5s','1100','docker-entrypoint.sh','postgres']);made_db=True
        deadline=time.monotonic()+45
        while True:
            check=subprocess.run(dc+['exec',database,'pg_isready','-U','module_user','-d','module_test'],capture_output=True)
            if check.returncode==0:break
            if time.monotonic()>deadline:raise PolicyError('CI_DATABASE_NOT_READY')
            time.sleep(1)
        execute(dc+['exec',database,'createdb','-U','module_user','campus_migration_probe'])
        if member['layer']=='backend':
            # The inbox belongs to this job's internal network and has no host ports.
            # Track the attempted name before launch so partial startup also cleans up.
            made_smtp=True
            execute(dc+['run','-d','--rm','--name',smtp,'--network',network,'--network-alias','smtp','--read-only','--cap-drop','ALL','--security-opt','no-new-privileges','--memory','128m','--pids-limit','64','--tmpfs','/tmp:rw,nosuid,nodev,size=32m','-e','MP_DATABASE=/tmp/mailpit.db',MAILPIT])
            deadline=time.monotonic()+45
            while True:
                check=subprocess.run(dc+['exec',smtp,'/mailpit','readyz'],capture_output=True,timeout=5)
                if check.returncode==0:break
                if time.monotonic()>deadline:raise PolicyError('CI_MAILPIT_NOT_READY')
                time.sleep(1)
        command=dc+['run','--rm','--name',runner,'--user',bind_identity(root,image,worktree),'--read-only','--network',network,'--cap-drop','ALL','--security-opt','no-new-privileges','--pids-limit','256','--memory','2g','--cpus','2','--tmpfs','/tmp:rw,nosuid,nodev,size=1g','--mount','type=bind,src='+docker_path(worktree)+',dst=/workspace','-e','HOME=/tmp/campus-home','-e','CAMPUS_DB_URL=jdbc:postgresql://postgres:5432/module_test','-e','CAMPUS_DB_USER=module_user','-e','CAMPUS_DB_PASSWORD='+password,'-e','CAMPUS_TEST_DB_URL=jdbc:postgresql://postgres:5432/campus_migration_probe','-e','CAMPUS_TEST_DB_USER=module_user','-e','CAMPUS_TEST_DB_PASSWORD='+password,'-e','CAMPUS_TEST_SMTP_HOST=smtp','-e','CAMPUS_TEST_SMTP_PORT=1025','-e','CAMPUS_TEST_MAILPIT_URL=http://smtp:8025',image,'timeout','--signal=TERM','--kill-after=5s','900','sh','-ec',review_command(member['layer'])]
        if member['layer']=='frontend':
            clean=[];i=0
            while i<len(command):
                if command[i]=='--network':clean+=['--network','none'];i+=2
                elif command[i]=='-e':i+=2
                else:clean.append(command[i]);i+=1
            marker=clean.index(image);clean[marker:marker]=['-e','HOME=/tmp/campus-home'];command=clean
        result=subprocess.run(command,capture_output=True,timeout=900)
        log=(result.stdout+result.stderr).decode('utf-8',errors='replace').replace(password,'[REDACTED]')
        (home/'ci.log').write_text(log,encoding='utf-8')
        if result.returncode:return {'state':'VERIFY_FAILED','code':'INDEPENDENT_TESTS_FAILED','category':'tests','log':str(home/'ci.log'),'ci_image':image}
        # The test process must not rewrite the production inputs it just verified.
        if git_run(worktree,'diff','--exit-code',check=False).returncode:raise PolicyError('CI_CHANGED_SUBMITTED_SOURCE')
        execute_git(worktree,'-c','user.name='+member['id'],'-c','user.email='+member['id']+'@team.invalid','commit','-m',message(meta.get('commit_type','feat'),item['module_id'],meta['summary'],meta.get('details',[meta['summary']]),meta['requirements'],item['reviewer']))
        commit=git(worktree,'rev-parse','HEAD').decode().strip();git(repo,'branch',branch,commit)
        data={'state':'VERIFIED','category':'success','branch':branch,'commit':commit,'patch_sha256':fingerprint,'ci_image':image,'ci_log_sha256':hashlib.sha256(log.encode()).hexdigest(),'verification_scope':'module; real cross-module/production acceptance remains separate'}
        cached.write_text(json.dumps(data),encoding='utf-8');return data
    finally:
        # Names originate from this server-generated UUID only; never enumerate/delete unrelated containers.
        for name in [runner]+([smtp] if made_smtp else [])+([database] if made_db else []):subprocess.run(dc+['rm','-f',name],capture_output=True)
        if made_network:subprocess.run(dc+['network','rm',network],capture_output=True)
        # Preserve the isolated worktree and logs for review; no automatic conflict resolution or recursive cleanup.

def publish(config,item):
    if not config.get('enabled'):raise PolicyError('PUBLICATION_DISABLED')
    result=item['result'];branch=result.get('branch','');commit=result.get('commit','')
    if not re.fullmatch(r'buffer/m0[1-6]/[0-9a-f-]{36}',branch) or not re.fullmatch('[0-9a-f]{40}',commit):raise PolicyError('UNVERIFIED_COMMIT')
    repo=Path(config['repository'])
    if git(repo,'rev-parse',branch).decode().strip()!=commit:raise PolicyError('BRANCH_CHANGED_AFTER_VERIFICATION')
    # This worker only publishes feature refs. It cannot force-update or push main.
    env={**os.environ,'GIT_TERMINAL_PROMPT':'0','GCM_INTERACTIVE':'Never'}
    execute_git(repo,'push',config['remote'],branch+':refs/heads/'+branch,env=env,timeout=120)
    if config.get('publicationMode')=='branch-only':
        return {**result,'state':'PUBLISHED','code':'GITEE_FEATURE_BRANCH_PUBLISHED','delivery':'feature_branch','merge_status':'awaiting_human_merge'}
    token=config.get('giteeApiToken','') or os.environ.get('CAMPUS_GITEE_API_TOKEN','')
    if not token:return {**result,'state':'PUSH_FAILED','code':'BRANCH_PUSHED_MR_CREDENTIAL_MISSING'}
    owner=config['giteeOwner'];repository=config['giteeRepo']
    if not all(re.fullmatch('[A-Za-z0-9_.-]+',x) for x in [owner,repository]):raise PolicyError('INVALID_GITEE_REPOSITORY')
    endpoint=f'https://gitee.com/api/v5/repos/{owner}/{repository}/pulls'
    parameters={'state':'open','head':owner+':'+branch,'base':config['branch'],'per_page':'100'}
    headers={'Authorization':'Bearer '+token,'Content-Type':'application/x-www-form-urlencoded'}
    opener=urllib.request.build_opener(NoCredentialRedirect())
    request=urllib.request.Request(endpoint+'?'+urllib.parse.urlencode(parameters),headers=headers)
    with opener.open(request,timeout=30) as response:existing=json.load(response)
    pull=next((p for p in existing if p.get('head',{}).get('ref')==branch),None)
    if pull is None:
        body=urllib.parse.urlencode({'title':item['metadata']['summary'],'head':branch,'base':config['branch'],'body':'Requirements: '+','.join(item['metadata']['requirements'])+'\nReviewed and independently tested by the team buffer. Merge and production release require human approval.'}).encode()
        with opener.open(urllib.request.Request(endpoint,data=body,headers=headers),timeout=30) as response:pull=json.load(response)
    return {**result,'state':'PUBLISHED','pull_request':pull['html_url'],'merge_status':'awaiting_human_merge'}

def main():
    parser=argparse.ArgumentParser();parser.add_argument('command',choices=['verify','publish']);parser.add_argument('--config',required=True);parser.add_argument('--input',required=True);args=parser.parse_args()
    config=json.loads(Path(args.config).read_text(encoding='utf-8'));item=json.loads(Path(args.input).read_text(encoding='utf-8'))
    try:result=verify(config,item) if args.command=='verify' else publish(config,item)
    except Exception as e:
        # Avoid including credentials, URLs, patch data or stack traces in web responses.
        result={'state':'VERIFY_FAILED' if args.command=='verify' else 'PUSH_FAILED','code':str(e) if isinstance(e,PolicyError) else type(e).__name__,'category':'policy' if isinstance(e,PolicyError) and any(tag in str(e) for tag in ['DEPENDENCY','OUTSIDE','LANGUAGE','EVIDENCE','TDD_']) else 'external'}
    print(json.dumps(result,ensure_ascii=False))
if __name__=='__main__':main()
