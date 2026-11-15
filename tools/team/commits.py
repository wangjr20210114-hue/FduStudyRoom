"""Deterministic descriptions for GitHub exports and reviewed Gitee commits."""
import re
from policy import PolicyError

def single(value, limit=200):
    if not isinstance(value,str) or not value.strip() or len(value)>limit or any(ord(c)<32 for c in value):
        raise PolicyError('INVALID_COMMIT_DESCRIPTION')
    return value.strip()

def message(kind, scope, title, details, requirements=(), reviewer=None):
    if kind not in ('feat','fix','refactor','docs','test','build','ci','chore','perf','style'):raise PolicyError('INVALID_COMMIT_TYPE')
    if not re.fullmatch('[A-Za-z0-9_.-]{1,48}',scope):raise PolicyError('INVALID_COMMIT_SCOPE')
    if not isinstance(details,list) or not 1<=len(details)<=30:raise PolicyError('COMMIT_BULLETS_REQUIRED')
    body=f'{kind}({scope}): {single(title)}\n\n'+'\n'.join('- '+single(x,500) for x in details)
    trailers=[]
    if requirements:trailers.append('Requirements: '+','.join(single(x,64) for x in requirements))
    if reviewer:trailers.append('Reviewed-by: '+single(reviewer,64))
    return body+('\n\n'+'\n'.join(trailers) if trailers else '')+'\n'
