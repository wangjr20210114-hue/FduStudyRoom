"""Frozen ownership policy shared by the team CLI; no application business logic."""
import hashlib
import json
import os
import re
import posixpath
import subprocess
import tempfile
import uuid
from contextlib import contextmanager
from pathlib import Path, PurePosixPath

class PolicyError(ValueError): pass

def load(root):
    root=Path(root)
    return json.loads((root/'spec/modules.json').read_text(encoding='utf-8'))

def module_for(policy, module):
    matches=[m for m in policy['modules'] if module in (m['id'],m['key'])]
    if len(matches)!=1: raise PolicyError('UNKNOWN_MODULE')
    return matches[0]

def member_for(members, identity):
    matches=[m for m in members if identity==m['id'] or (m.get('gitee') and identity==m['gitee'])]
    if len(matches)!=1: raise PolicyError('UNKNOWN_OR_AMBIGUOUS_MEMBER')
    return matches[0]

def authorize(policy, member, module, paths):
    m=module_for(policy,module)
    if m['id'] not in member['modules'] or member['layer'] not in m['write_paths']: raise PolicyError('MODULE_NOT_ASSIGNED')
    allowed=m['write_paths'][member['layer']]
    for path in paths:
        if not path or '\\' in path or path.startswith('/') or ':' in path or any(p in ('..','.','') for p in path.split('/')): raise PolicyError('UNSAFE_PATH')
        p=PurePosixPath(path)
        if any(s in p.parts for s in ('.git','.campus','node_modules','target','dist')) or p.name.startswith('.env') or p.name.endswith(('.local','.log')): raise PolicyError('PRIVATE_OR_GENERATED_PATH')
        if any(path==prefix or (prefix.endswith('/') and path.startswith(prefix)) for prefix in policy.get('protected_paths',[])):raise PolicyError('FROZEN_BASELINE_PATH')
        if not any(path.startswith(prefix) for prefix in allowed): raise PolicyError('PATH_OUTSIDE_MODULE: '+path)
        if p.name!='.gitkeep':
            suffix=p.suffix.lower()
            extensions={'.vue','.ts','.css','.json','.md','.svg','.png','.jpg','.jpeg','.webp','.woff2'} if member['layer']=='frontend' else {'.java','.sql','.json','.yaml','.yml','.properties','.xml','.csv','.txt','.md'}
            if suffix not in extensions or (member['layer']=='backend' and '/java/' in path and suffix!='.java') or (path.startswith('spec/progress/') and suffix!='.json'):raise PolicyError('BASELINE_LANGUAGE_OR_FILE_TYPE_REQUIRED')

    return m

def git_command(root,*args):
    """Host Git only: exact-path trust and bounded settings for this process."""
    return ['git','-c','safe.directory='+str(Path(root).resolve()),
            '-c','core.longpaths=true','-c','http.postBuffer=1048576',
            '-c','pack.threads=1','-c','pack.windowMemory=32m',
            '-C',str(root),*[str(value) for value in args]]

def _git_diagnostic(root,stderr,env):
    text=stderr.decode('utf-8',errors='replace') if isinstance(stderr,bytes) else str(stderr or '')
    # Only stderr is retained: never record arguments, auth helpers or environment.
    text=re.sub(r'(?i)\b[a-z][a-z0-9+.-]*://[^\s<>\x22\x27]+','[REDACTED_URL]',text)
    text=re.sub(r'(?i)(authorization|proxy-authorization)\s*[:=][^\r\n]*',r'\1: [REDACTED]',text)
    for key,value in env.items():
        if value and any(tag in key.lower() for tag in ('token','password','secret','credential','signing_key','service_key')):
            text=text.replace(value,'[REDACTED]')
    folder=Path(root)/'.campus/git-diagnostics'
    try:
        folder.mkdir(mode=0o700,parents=True,exist_ok=True)
        path=folder/(uuid.uuid4().hex+'.local')
        descriptor=os.open(path,os.O_WRONLY|os.O_CREAT|os.O_EXCL,0o600)
        with os.fdopen(descriptor,'w',encoding='utf-8') as handle:handle.write(text)
        return '.campus/git-diagnostics/'+path.name
    except OSError:
        return None

@contextmanager
def _git_environment(root,args,env):
    effective=os.environ.copy() if env is None else dict(env)
    if os.name!='nt' or not any(args[index:index+2]==('worktree','add') for index in range(len(args)-1)):
        yield effective
        return
    # Native Git worktree clears -c/GIT_CONFIG_COUNT before its child checkout.
    # An invocation-only global overlay survives that boundary. Include original
    # files (and their relative/conditional includes) to preserve host auth/trust;
    # never modify them, copy credential values, or persist a repository setting.
    origins=subprocess.run(git_command(root,'var','GIT_CONFIG_GLOBAL'),env=effective,capture_output=True,timeout=30)
    if origins.returncode:
        raise PolicyError('GIT_GLOBAL_CONFIG_DISCOVERY_FAILED: update Git for Windows before creating deep worktrees')
    with tempfile.TemporaryDirectory(prefix='campus-git-') as temporary:
        config=Path(temporary)/'process.config'
        includes=''.join('[include]\npath='+json.dumps(value.replace('\\','/'),ensure_ascii=False)+'\n'
                         for value in origins.stdout.decode('utf-8').splitlines() if value)
        config.write_text(includes+'[core]\nlongpaths=true\n',encoding='utf-8')
        effective['GIT_CONFIG_GLOBAL']=str(config)
        yield effective

def git_run(root,*args,check=True,input=None,env=None,timeout=None):
    """Run host Git; return CompletedProcess without exporting host auth to runners."""
    effective=os.environ.copy() if env is None else env
    try:
        with _git_environment(root,args,env) as process_env:
            result=subprocess.run(git_command(root,*args),input=input,env=process_env,
                                  capture_output=True,timeout=timeout)
    except subprocess.TimeoutExpired as error:
        diagnostic=_git_diagnostic(root,error.stderr or b'Git operation timed out',effective)
        raise PolicyError('GIT_TIMEOUT: check network access and retry; '+('private diagnostic='+diagnostic if diagnostic else 'private diagnostic unavailable')) from None
    except OSError:
        raise PolicyError('GIT_UNAVAILABLE: check that Git is installed and the repository is accessible') from None
    if result.returncode:
        diagnostic=_git_diagnostic(root,result.stderr,effective)
        if check:
            raise PolicyError('GIT_FAILED: exit='+str(result.returncode)+'; check repository access, host credentials and conflicts before retrying; '+('private diagnostic='+diagnostic if diagnostic else 'private diagnostic unavailable'))
    return result

def git(root,*args,check=True):
    return git_run(root,*args,check=check).stdout

def changed_paths(root,base):
    paths=git(root,'diff','--name-only','--no-renames','-z',base).decode('utf-8').split('\0')
    paths+=git(root,'ls-files','--others','--exclude-standard','-z').decode('utf-8').split('\0')
    return sorted(set(p for p in paths if p))

def check_imports(path,text,module,policy):
    if path.endswith('.java'):
        for name in re.findall(r'\bcom\.campus\.(identity|resource|reservation|approval|repair|notification|audit|statistics|agent)\b',text):
            if name not in module['java_modules']:raise PolicyError('PRIVATE_JAVA_MODULE_DEPENDENCY: '+name)
    if path.endswith(('.ts','.vue')):
        for value in re.findall(r"(?:from\s*|import\s*\(|require\s*\()\s*['\"]([^'\"]+)['\"]",text):
            if value.startswith('@/'):resolved='frontend/src/'+value[2:]
            elif value.startswith('.'):
                resolved=posixpath.normpath(posixpath.join(posixpath.dirname(path),value))
            else:continue
            match=re.search(r'frontend/src/modules/([^/]+)/',resolved)
            if match and match.group(1) not in module['frontend_modules']:
                options=[resolved,resolved+'.ts',resolved+'.vue']
                if not any(x in policy.get('public_imports',[]) for x in options):raise PolicyError('PRIVATE_FRONTEND_MODULE_DEPENDENCY: '+match.group(1))

def guard(root,policy,member,module,base):
    if not re.fullmatch('[0-9a-f]{40}',base): raise PolicyError('INVALID_BASE_COMMIT')
    paths=changed_paths(root,base)
    owned=authorize(policy,member,module,paths)
    for path in paths:
        p=Path(root)/path
        if p.is_symlink() or not p.resolve().is_relative_to(Path(root).resolve()): raise PolicyError('SYMLINK_OR_ESCAPE')
        if p.is_file() and p.suffix in ('.java','.vue','.ts'):check_imports(path,p.read_text(encoding='utf-8'),owned,policy)
    # Renames include both source and destination through diff --name-only.
    modes=git(root,'diff','--raw',base).decode('utf-8')
    if '120000' in modes or '160000' in modes: raise PolicyError('SYMLINK_OR_SUBMODULE')
    return paths

def evidence_valid(evidence,requirements=None,strict=False):
    if not isinstance(evidence,dict):raise PolicyError('TDD_EVIDENCE_REQUIRED')
    if evidence.get('scope')=='scenario_pilot' or 'scenarios' in evidence:raise PolicyError('SCENARIO_PILOT_IS_NOT_FORMAL_REQUIREMENT_EVIDENCE')
    if 'requirements' in evidence:
        receipts=evidence['requirements']
        if not isinstance(receipts,dict) or not receipts or (requirements is not None and set(receipts)!=set(requirements)):raise PolicyError('TDD_REQUIREMENT_RECEIPTS_MISMATCH')
        for receipt in receipts.values():evidence_valid(receipt,strict=strict)
        return True
    if requirements is not None and len(requirements)!=1:raise PolicyError('PER_REQUIREMENT_TDD_RECEIPTS_REQUIRED')
    mode=evidence.get('completion_mode','tdd')
    if mode not in ('tdd','baseline_satisfied'):raise PolicyError('UNKNOWN_COMPLETION_MODE')
    first='baseline' if mode=='baseline_satisfied' else 'red'
    for name,code in [(first,0 if first=='baseline' else 1),('green',0),('mutation',1)]:
        item=evidence.get(name,{})
        if item.get('exit_code')!=code or not item.get('command') or not item.get('log') or not item.get('sha256'):raise PolicyError('INVALID_TDD_'+name.upper())
        if hashlib.sha256(item['log'].encode()).hexdigest()!=item['sha256']:raise PolicyError('TAMPERED_TDD_LOG')
        if strict or mode=='baseline_satisfied' or evidence.get('version')==2:
            if evidence.get('version')!=2 or not item.get('run_id') or not item.get('source',{}).get('sha256'):raise PolicyError('VERSIONED_RUN_EVIDENCE_REQUIRED')
        if 'test_result' in item or strict or mode=='baseline_satisfied' or evidence.get('version')==2:
            from test_reports import validate_result
            validate_result(item.get('test_result'),name)
        if name in ('red','mutation'):
            if not item.get('expected_assertion') or item['expected_assertion'] not in item['log']:raise PolicyError('FAILURE_NOT_OBSERVED')
            if any(x in item['log'].lower() for x in ['command not found','compilation failure','cannot find module','connection refused']):raise PolicyError('INFRA_FAILURE_IS_NOT_RED')
    if evidence[first]['command']!=evidence['green']['command']:raise PolicyError('RED_GREEN_COMMAND_CHANGED')
    if 'test_result' in evidence[first] and 'test_result' in evidence['green']:
        if set(evidence[first]['test_result']['required_tests'])!=set(evidence['green']['test_result']['required_tests']):raise PolicyError('RED_GREEN_ASSERTIONS_CHANGED')
    if strict or mode=='baseline_satisfied' or evidence.get('version')==2:
        mutation=evidence['mutation'];green=evidence['green']
        if mutation['command']!=green['command'] or set(mutation['test_result']['required_tests'])!=set(green['test_result']['required_tests']):raise PolicyError('MUTATION_ASSERTIONS_CHANGED')
        source=mutation['source'];parent=green['source']
        if source.get('parent_sha256')!=parent['sha256'] or not source.get('product_sha256') or source['product_sha256']==parent.get('product_sha256'):raise PolicyError('MUTATION_INPUT_MISMATCH')
    if mode=='baseline_satisfied':
        if evidence['baseline']['source'].get('product_sha256')!=evidence['green']['source'].get('product_sha256') or not evidence['baseline']['source'].get('product_sha256'):raise PolicyError('BASELINE_SATISFIED_PRODUCT_CHANGED')
    return True
