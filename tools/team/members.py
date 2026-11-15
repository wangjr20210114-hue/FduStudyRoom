"""Keep one authenticated person, with separate bounded layer sessions."""
from policy import PolicyError

def layers(member):
    if member['layer']=='fullstack':return ['backend','frontend']
    if member['layer'] in ('backend','frontend'):return [member['layer']]
    if member.get('admin'):return []
    raise PolicyError('INVALID_MEMBER_LAYER')

def scoped(member, layer):
    if layer not in layers(member):raise PolicyError('LAYER_NOT_ASSIGNED')
    return {**member,'layer':layer,'layers':layers(member)}
