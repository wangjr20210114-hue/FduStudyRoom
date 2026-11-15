"""Export an acknowledged module patch to a development branch, never main."""
import json,os,re,subprocess
from pathlib import Path
from policy import PolicyError,git,load,guard
from commits import message

def github_export(root,worktree,context,ack,kind,title,details,requirements,patch=None):
    identifier=ack['id']
    if not re.fullmatch('[0-9a-f-]{36}',identifier):raise PolicyError('INVALID_BUFFER_ACK')
    root=Path(root);worktree=Path(worktree);policy=load(root)
    remote=policy['buffer']['github_remote']
    if remote!='https://github.com/wangjr20210114-hue/FduStudyRoom.git':raise PolicyError('GITHUB_REMOTE_REVIEW_REQUIRED')
    branch='development/'+context['module'].lower()+'/'+context['member']['id']+'/'+context['member']['layer']+'/'+identifier
    home=root/'.campus/github'/identifier;home.mkdir(parents=True,exist_ok=True)
    receipt=home/'result.local';checkout=home/'source'
    patch=patch if patch is not None else git(worktree,'diff','--cached','--binary','--full-index','--no-renames',context['base'])
    import hashlib
    fingerprint=hashlib.sha256(patch).hexdigest()
    if receipt.exists():
        result=json.loads(receipt.read_text(encoding='utf-8'))
        if result['patch_sha256']!=fingerprint:raise PolicyError('GITHUB_EXPORT_INPUT_CHANGED')
    else:
        if checkout.exists():raise PolicyError('INCOMPLETE_EXPORT: inspect the existing checkout; do not overwrite it')
        if git(root,'rev-parse','HEAD').decode().strip()!=context['base']:raise PolicyError('GITHUB_BASELINE_NOT_CHECKED_OUT')
        bundle=home/'baseline.bundle'
        if bundle.exists():raise PolicyError('INCOMPLETE_EXPORT: inspect the existing bundle; do not overwrite it')
        # A bundle keeps native Git from invoking a local upload-pack against a
        # WSL-owned source repository. Trust remains per-command and per-path.
        git(root,'bundle','create',str(bundle),'HEAD')
        checkout.mkdir();git(checkout,'init');git(checkout,'fetch',str(bundle),'HEAD');git(checkout,'checkout','--detach','FETCH_HEAD')
        apply=subprocess.run(['git','-c','safe.directory='+str(checkout.resolve()),'-C',str(checkout),'apply','--index','--'],input=patch,capture_output=True)
        if apply.returncode:raise PolicyError('GITHUB_EXPORT_CONFLICT_REQUIRES_HUMAN')
        guard(checkout,policy,context['member'],context['module'],context['base'])
        text=message(kind,context['module'],title,details,requirements)
        git(checkout,'-c','user.name='+context['member']['id'],'-c','user.email='+context['member']['id']+'@team.invalid','commit','-m',text)
        result={'branch':branch,'commit':git(checkout,'rev-parse','HEAD').decode().strip(),'patch_sha256':fingerprint,'buffer_id':identifier,'state':'prepared'}
        receipt.write_text(json.dumps(result,indent=2)+'\n',encoding='utf-8')
    env={**os.environ,'GIT_TERMINAL_PROMPT':'0','GCM_INTERACTIVE':'Never'}
    pushed=subprocess.run(['git','-c','safe.directory='+str(checkout.resolve()),'-C',str(checkout),'push',remote,result['commit']+':refs/heads/'+branch],env=env,capture_output=True,timeout=120)
    if pushed.returncode:raise PolicyError('GITHUB_PUSH_FAILED: buffer draft is preserved; fix this member credentials and retry github-retry --submission <buffer-id>')
    result['state']='pushed';receipt.write_text(json.dumps(result,indent=2)+'\n',encoding='utf-8')
    return result
