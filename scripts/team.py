#!/usr/bin/env python3
"""Identity-bound planning, scoped worktrees, TDD receipts, and buffer upload."""
import argparse
import base64
import hashlib
import json
import os
from pathlib import Path
import secrets
import shutil
import subprocess
import sys
import urllib.request
ROOT=Path(__file__).resolve().parents[1]
sys.path.insert(0,str(ROOT/'tools/team'))
from policy import PolicyError,load,module_for,member_for,guard,evidence_valid,git

def json_file(p): return json.loads(Path(p).read_text(encoding='utf-8'))
def save(p,data):
    p=Path(p);p.parent.mkdir(parents=True,exist_ok=True);p.write_text(json.dumps(data,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    try:p.chmod(0o600)
    except OSError:pass

def local_config():
    p=ROOT/'.campus/member.local'
    task=ROOT/'.campus/task.local'
    if not p.exists() and task.exists():p=Path(json_file(task)['policy_root'])/'.campus/member.local'
    if not p.exists():raise PolicyError('MEMBER_NOT_PROVISIONED: configure .campus/member.local once; a name is not authentication')
    return json_file(p)

def api(config,path,payload=None):
    url=config.get('buffer_url','http://127.0.0.1:7780').rstrip('/')
    if not url.startswith(('http://127.0.0.1:','http://localhost:','https://')):raise PolicyError('HTTPS_REQUIRED_FOR_REMOTE_BUFFER')
    request=urllib.request.Request(url+path,data=None if payload is None else json.dumps(payload).encode(),headers={'Authorization':'Bearer '+config['token'],'Content-Type':'application/json'})
    with urllib.request.urlopen(request,timeout=60) as response:return json.load(response)

def init_buffer(args):
    path=ROOT/'.campus/buffer/config.local'
    if path.exists():print('Existing buffer configuration preserved.');return
    members=json_file(ROOT/'spec/members.example.json')['members']
    credentials={}
    configured=[]
    for m in members+[{'id':'coordinator','layer':'coordinator','modules':[],'admin':True}]:
        token=secrets.token_urlsafe(32);credentials[m['id']]={'buffer_url':'http://127.0.0.1:7780','identity':m['id'],'token':token}
        configured.append({**m,'tokenHash':hashlib.sha256(token.encode()).hexdigest()})
    save(path,{'root':str(ROOT),'pythonExecutable':sys.executable,'repository':str(ROOT),'remote':'https://gitee.com/wangjr2021/fdu-study-room.git','branch':'main','baseline':git(ROOT,'rev-parse','HEAD').decode().strip(),'enabled':False,'intervalSeconds':259200,'ciImage':'fdu-campus-ci:baseline','members':configured,'giteeOwner':'wangjr2021','giteeRepo':'fdu-study-room','giteeApiToken':''})
    save(ROOT/'.campus/buffer/member-credentials.local',credentials)
    save(ROOT/'.campus/buffer/runtime.local',{'postgres_password':secrets.token_urlsafe(32),'postgres_user':'campus_buffer','postgres_database':'campus_buffer','postgres_port':55434,'server_port':7780})
    print('Created private buffer configuration and example member credentials in .campus/buffer/. No credentials printed. Replace examples before sharing.')

def plan(args):
    cfg=local_config();member=api(cfg,'/api/me')
    if args.identity not in (member['id'],member.get('gitee')):raise PolicyError('IDENTITY_DOES_NOT_MATCH_TOKEN')
    data=api(cfg,'/api/requirements')
    status={'完成':'accepted','已完成':'accepted','未完成':'pending','全部':'all'}.get(args.status,args.status)
    items=[q for q in data if (not args.module or q['module']==args.module) and (status=='all' or (q['state']=='accepted')==(status=='accepted'))]
    print(json.dumps({'member':member['id'],'layer':member['layer'],'requirements':items},ensure_ascii=False,indent=2))

def worktree(args):
    cfg=local_config();member=api(cfg,'/api/me');policy=load(ROOT);m=module_for(policy,args.module)
    if args.identity not in (member['id'],member.get('gitee')) or m['id'] not in member['modules']:raise PolicyError('MODULE_NOT_ASSIGNED')
    base=git(ROOT,'rev-parse','HEAD').decode().strip()
    if base!=member.get('baseline'):raise PolicyError('BASELINE_CHANGED: update checkout to the approved frozen baseline before development')
    # A team checkout must include the new frozen policy, not the old pre-isolation baseline.
    if not git(ROOT,'show',base+':spec/modules.json',check=False):raise PolicyError('PUBLISH_OPTIMIZED_BASELINE_FIRST')
    name='campus/'+member['id']+'/'+m['id'].lower()+'/'+member['layer']+'/'+base[:12]
    path=ROOT/'.campus/worktrees'/member['id']/m['key']/base[:12]
    if path.exists():
        old=json_file(path/'.campus/task.local')
        if old['base']!=base or old['member']['id']!=member['id']:raise PolicyError('WORKTREE_CONTEXT_MISMATCH')
        print(str(path));return
    path.parent.mkdir(parents=True,exist_ok=True)
    git(ROOT,'worktree','add','-b',name,str(path),base)
    save(path/'.campus/task.local',{'member':member,'module':m['id'],'base':base,'policy_root':str(ROOT)})
    print(str(path))

def task(args):
    p=Path(args.worktree).resolve();ctx=json_file(p/'.campus/task.local')
    if args.command=='submit' and ctx.get('mode')=='mutation':raise PolicyError('MUTATION_COPY_CANNOT_SUBMIT')
    member=ctx['member'];policy=load(ROOT)
    paths=guard(p,policy,member,ctx['module'],ctx['base'])
    if args.command=='guard':print(json.dumps({'allowed':True,'paths':paths},ensure_ascii=False));return
    if args.command=='sandbox':
        from environment import sandbox
        sandbox(ROOT,p,policy,member,ctx,args.start);return
    if not paths:raise PolicyError('EMPTY_CHANGE')
    evidence=json_file(args.evidence);evidence_valid(evidence)
    requirements=sorted(set(q.strip() for q in args.requirements.split(',') if q.strip()))
    if not args.workflow:evidence_valid(evidence,requirements)
    catalogue={q['id']:q for q in json_file(ROOT/'spec/requirements.json')['requirements']}
    if not requirements or any(q not in catalogue or catalogue[q]['module']!=ctx['module'] or member['layer'] not in catalogue[q]['layers'] for q in requirements):raise PolicyError('REQUIREMENT_SCOPE_MISMATCH')
    if args.workflow:
        from workflow import before_upload
        before_upload(ROOT,args.workflow,ctx['module'],p,args.evidence,requirements)
        evidence={**evidence,'requirements':{q:evidence['requirements'][q] for q in requirements}}
    git(p,'add','--',*paths)
    patch=git(p,'diff','--cached','--no-renames','--binary','--full-index',ctx['base'])
    result=api(local_config(),'/api/submissions',{'module':ctx['module'],'base':ctx['base'],'requirements':requirements,'summary':args.summary,'patch':base64.b64encode(patch).decode(),'evidence':evidence})
    if args.workflow:
        from workflow import submitted
        submitted(ROOT,args.workflow,ctx['module'],result)
    print(json.dumps({key:result[key] for key in ('id','module_id','state')},ensure_ascii=False))
    print('Uploaded draft diff; no formal commit or direct Gitee push. Human review and independent verification are pending.')

def record(args):
    from recording import record as execute_record
    execute_record(ROOT,args)

def provision(args):
    import re
    if not re.fullmatch('[a-z][a-z0-9-]{2,47}',args.identity):raise PolicyError('INVALID_MEMBER_ID')
    modules=[x.strip() for x in args.modules.split(',') if x.strip()]
    policy=load(ROOT)
    for module in modules:module_for(policy,module)
    path=ROOT/'.campus/buffer/config.local';config=json_file(path)
    if any(m['id']==args.identity or (args.gitee and m.get('gitee')==args.gitee) for m in config['members']):raise PolicyError('MEMBER_ALREADY_EXISTS')
    previous=next((m for m in config['members'] if m['id']==args.replace),None) if args.replace else None
    if args.replace and (previous is None or previous.get('admin')):raise PolicyError('REPLACEMENT_MEMBER_NOT_FOUND')
    others=[m for m in config['members'] if m!=previous]
    if any(m.get('layer')==args.layer and set(m['modules']) & set(modules) for m in others):raise PolicyError('MODULE_LAYER_ALREADY_ASSIGNED: replace the old owner explicitly')
    config['members']=others
    token=secrets.token_urlsafe(32)
    config['members'].append({'id':args.identity,'gitee':args.gitee,'layer':args.layer,'modules':modules,'tokenHash':hashlib.sha256(token.encode()).hexdigest(),'admin':False})
    save(path,config)
    credential=ROOT/'.campus/buffer/credentials'/(args.identity+'.local')
    save(credential,{'identity':args.identity,'buffer_url':'http://127.0.0.1:7780','token':token})
    print('Member provisioned. Give only this private file to the member and restart the buffer: '+str(credential))

def install_skills(args):
    from workflow import SKILLS,skill_manifest
    base=Path(os.environ.get('CODEX_HOME',str(Path.home()/'.codex'))) if args.client=='codex' else Path.home()/'.claude'
    target=Path(args.skills_dir).expanduser().resolve() if args.skills_dir else base/'skills'
    if args.check:
        expected=skill_manifest(ROOT)['files'];missing=[];different=[]
        for relative,sha in expected.items():
            file=target/Path(relative).relative_to('skills')
            if not file.is_file():missing.append(str(file))
            elif hashlib.sha256(file.read_bytes()).hexdigest()!=sha:different.append(str(file))
        if missing or different:raise PolicyError('SKILL_INSTALL_MISMATCH: '+json.dumps({'missing':missing,'different':different},ensure_ascii=False))
        print('All five skills match the repository source.');return
    # Check all destinations before creating any, so a collision cannot leave a partial upgrade.
    if any((target/name).exists() for name in SKILLS):raise PolicyError('SKILL_ALREADY_INSTALLED: use --check and review differences; existing skills preserved')
    target.mkdir(parents=True,exist_ok=True)
    for name in SKILLS:shutil.copytree(ROOT/'skills'/name,target/name)
    print('Installed five project skills. Refresh the client skill list.')

def workflow_command(args):
    from workflow import open_workflow,refresh_submissions,next_action,state_path
    cfg=local_config();member=api(cfg,'/api/me')
    if args.identity not in (member['id'],member.get('gitee')):raise PolicyError('IDENTITY_DOES_NOT_MATCH_TOKEN')
    data=api(cfg,'/api/requirements')
    if args.preview:
        print(json.dumps({'member':member['id'],'layer':member['layer'],'requirements':[{'id':q['id'],'module':q['module'],'title':q['title'],'state':q['state'],'skip_coding':q.get('skip_coding',False)} for q in data if not args.module or q['module']==args.module]},ensure_ascii=False,indent=2));return
    state=open_workflow(ROOT,member,data,args.module)
    state=refresh_submissions(ROOT,state,api(cfg,'/api/submissions'))
    print(json.dumps({'workflow_id':state['id'],'state_file':str(state_path(ROOT,state['id'])),'next':next_action(state,args.module),'verification_scope':'member modules; integration, human review and publication remain separate'},ensure_ascii=False,indent=2))

def workflow_checkpoint(args):
    from workflow import checkpoint,next_action
    state=checkpoint(ROOT,args.run,args.module,args.stage,args.artifact,args.reset)
    print(json.dumps({'workflow_id':state['id'],'next':next_action(state)},ensure_ascii=False,indent=2))

def workflow_block(args):
    from workflow import block,next_action
    state=block(ROOT,args.run,args.module,args.requirement,args.kind,args.reason,args.resume_when)
    print(json.dumps({'workflow_id':state['id'],'next':next_action(state)},ensure_ascii=False,indent=2))

def workflow_unblock(args):
    from workflow import state_path,state_lock,read,save,event,STAGES,next_action
    path=state_path(ROOT,args.run)
    if not args.resolution.strip():raise PolicyError('BLOCKER_RESOLUTION_REQUIRED')
    with state_lock(path):
        state=read(path);item=state['modules'][args.module]
        if args.requirement not in item['blocked']:raise PolicyError('BLOCKER_NOT_FOUND')
        previous=item['blocked'].pop(args.requirement)
        for stage in STAGES[2:]:item['checkpoints'].pop(stage,None)
        event(state,'UNBLOCK',module=args.module,requirement=args.requirement,previous=previous,resolution=args.resolution)
        save(path,state)
    print(json.dumps({'workflow_id':state['id'],'next':next_action(state)},ensure_ascii=False,indent=2))

def main():
    parser=argparse.ArgumentParser();sub=parser.add_subparsers(dest='command',required=True)
    sub.add_parser('init-buffer')
    p=sub.add_parser('install-skills');p.add_argument('--client',choices=['codex','claude'],default='codex');p.add_argument('--skills-dir');p.add_argument('--check',action='store_true')
    p=sub.add_parser('workflow');p.add_argument('identity');p.add_argument('--module');p.add_argument('--preview',action='store_true')
    p=sub.add_parser('workflow-checkpoint');p.add_argument('--run',required=True);p.add_argument('--module',required=True);p.add_argument('--stage',choices=['workspace','baseline','requirements','test-plan','tdd','regression'],required=True);p.add_argument('--artifact');p.add_argument('--reset',action='store_true')
    p=sub.add_parser('workflow-block');p.add_argument('--run',required=True);p.add_argument('--module',required=True);p.add_argument('--requirement',required=True);p.add_argument('--kind',choices=['external','clarification','scope'],required=True);p.add_argument('--reason',required=True);p.add_argument('--resume-when',required=True)
    p=sub.add_parser('workflow-unblock');p.add_argument('--run',required=True);p.add_argument('--module',required=True);p.add_argument('--requirement',required=True);p.add_argument('--resolution',required=True)
    p=sub.add_parser('mutation');p.add_argument('--worktree',required=True)
    p=sub.add_parser('plan');p.add_argument('identity');p.add_argument('--status',default='未完成');p.add_argument('--module')
    p=sub.add_parser('worktree');p.add_argument('identity');p.add_argument('module')
    for name in ['guard','sandbox','submit']:
        p=sub.add_parser(name);p.add_argument('--worktree',required=True)
        if name=='sandbox':p.add_argument('--start',action='store_true')
        if name=='submit':
            p.add_argument('--evidence',required=True);p.add_argument('--requirements',required=True);p.add_argument('--summary',required=True);p.add_argument('--workflow')
    p=sub.add_parser('record');p.add_argument('--worktree',required=True);p.add_argument('--requirement',required=True);p.add_argument('--stage',choices=['baseline','red','green','mutation','regression','check'],required=True);p.add_argument('--output',required=True);p.add_argument('--assertion',default='');p.add_argument('--report',action='append');p.add_argument('--require-test',action='append');p.add_argument('--kind',choices=['unit','component','api','db','e2e','contract','build']);p.add_argument('--completion-mode',choices=['tdd','baseline_satisfied']);p.add_argument('test_command',nargs=argparse.REMAINDER)
    p=sub.add_parser('provision-member');p.add_argument('identity');p.add_argument('--gitee');p.add_argument('--layer',choices=['frontend','backend'],required=True);p.add_argument('--modules',required=True);p.add_argument('--replace',help='Replace the retired example member without keeping overlapping ownership')
    p=sub.add_parser('build');p.add_argument('--install',action='store_true');p.add_argument('--start',action='store_true');p.add_argument('--buffer',action='store_true')
    args=parser.parse_args()
    if args.command=='init-buffer':init_buffer(args)
    elif args.command=='install-skills':install_skills(args)
    elif args.command=='plan':plan(args)
    elif args.command=='worktree':worktree(args)
    elif args.command=='workflow':workflow_command(args)
    elif args.command=='workflow-checkpoint':workflow_checkpoint(args)
    elif args.command=='workflow-block':workflow_block(args)
    elif args.command=='workflow-unblock':workflow_unblock(args)
    elif args.command=='mutation':
        from recording import mutation;mutation(ROOT,args)
    elif args.command=='record':record(args)
    elif args.command=='provision-member':provision(args)
    elif args.command=='build':
        sys.path.insert(0,str(ROOT/'tools/team'));from environment import build;build(ROOT,args)
    else:task(args)
if __name__=='__main__':
    try:main()
    except subprocess.TimeoutExpired:
        print('BLOCKED: COMMAND_TIMEOUT',file=sys.stderr);sys.exit(2)
    except (PolicyError,subprocess.CalledProcessError,urllib.error.URLError) as e:
        print('BLOCKED: '+str(e),file=sys.stderr);sys.exit(2)
