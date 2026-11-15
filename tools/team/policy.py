"""Frozen ownership policy shared by the team CLI; no application business logic."""
import hashlib
import json
import re
import posixpath
import subprocess
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

def git(root,*args,check=True):
    result=subprocess.run(['git','-c','safe.directory='+str(Path(root).resolve()),'-C',str(root),*args],capture_output=True,check=check)
    return result.stdout

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
