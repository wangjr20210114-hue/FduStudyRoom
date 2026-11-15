"""Render business-only specifications; keep workflow obligations separately traceable."""
import argparse
import copy
import hashlib
import json
import re
from collections import Counter, defaultdict
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
INDEX = ROOT / 'spec/spec-network.json'
POLICY_STATUS_LABELS = {
    'proposal_requires_review':'待评审建议',
    'existing_baseline_design':'沿用现有设计',
    'test_fixture_not_production_default':'场景示例值，不是正式默认值',
    'external_delivery_limit':'外部邮件能力限制',
    'user_confirmed_2026_11_15':'用户已确认（2026-11-15）',
    'user_confirmed_2026_11_16':'用户已确认（2026-11-16）',
    'user_confirmed_defaults_and_external_fixture':'默认值已确认；外部数据时效仍为示例',
}

class PlanError(ValueError):
    pass


def require(condition, message):
    if not condition:
        raise PlanError(message)


def boundary(data):
    path = (ROOT / data['workflow_boundary_path']).resolve()
    require(path.is_relative_to(ROOT.resolve()), 'workflow索引路径越界')
    return json.loads(path.read_text(encoding='utf-8'))


def snapshot(data):
    row = boundary(data)
    path = (ROOT / row['source_snapshot']).resolve()
    require(path.is_relative_to(ROOT.resolve()), '历史来源路径越界')
    raw = path.read_bytes()
    candidates = {raw}
    lf = raw.replace(b'\r\n', b'\n')
    if b'\r' not in lf:
        candidates.update((lf, lf.replace(b'\n', b'\r\n')))
    require(any(hashlib.sha256(value).hexdigest() == row['source_snapshot_sha256'] for value in candidates), '历史来源已变更，需明确评审')
    return json.loads(raw)


def current_boundary(data, requirements):
    historical = boundary(data)
    source = snapshot(data)
    require(historical['catalog_sha256'] == source['catalog_sha256'], '历史分类与历史来源目录不同')
    extension = data.get('current_catalog_extension', {})
    refs = {q['id']+'.AC'+str(i) for q in requirements+historical['user_extensions'] for i in range(1,len(q['acceptance'])+1)}
    original = set(historical['acceptance_routes'])
    additions = set(extension.get('acceptance_refs', []))
    require(original <= refs and refs-original == additions, '本轮新增要求未明确登记或历史要求丢失')
    if extension:
        require(extension['historical_catalog_sha256'] == historical['catalog_sha256'], '新增要求的历史目录关联不匹配')
        require(len(extension['acceptance_refs']) == len(additions), '新增要求重复登记')
        require(additions <= set(data['business_acceptance_refs']), '新增要求未显式登记为业务')
        require({ref.split('.AC')[0] for ref in additions} == set(extension['requirement_ids']), '新增目录要求登记不匹配')
    else:
        require(data['catalog_sha256'] == historical['catalog_sha256'], '业务与workflow来源不同')
    routing = copy.deepcopy(historical)
    routing['acceptance_routes'].update({ref:{'placement':'business','reason':extension['basis']} for ref in additions})
    return routing


def load():
    data = json.loads(INDEX.read_text(encoding='utf-8'))
    raw = (ROOT / data['catalog_path']).read_bytes()
    require(hashlib.sha256(raw).hexdigest() == data['catalog_sha256'], '需求来源已变更，先评审并更新追踪')
    catalog = json.loads(raw)
    requirements = copy.deepcopy(catalog['requirements'])
    lookup = {q['id']: q for q in requirements}
    for row in data.get('requirement_overrides', []):
        q = lookup[row['id']]
        i = row['criterion'] - 1
        require(q['acceptance'][i] == row['previous'], '被替代的旧要求不匹配')
        q['acceptance'][i] = row['effective']
    return data, requirements


def validate(data, requirements):
    require(data.get('scope') == 'business_only', '当前索引只接受业务spec')
    routing = current_boundary(data, requirements)
    source = snapshot(data)
    all_requirements = requirements + routing['user_extensions']
    reqs = {q['id']:q for q in all_requirements}
    source_refs = {q['id']+'.AC'+str(i) for q in all_requirements for i in range(1,len(q['acceptance'])+1)}
    routes = routing['acceptance_routes']
    require(set(routes) == source_refs, '原始要求的业务/workflow去向有遗漏或多项')
    require(all(r['placement'] in {'business','workflow','shared'} for r in routes.values()), '无效的要求分类')
    require(all(r.get('business_part') and r.get('workflow_part') for r in routes.values() if r['placement']=='shared'), '混合要求未分别说明')
    expected = {ref for ref,row in routes.items() if row['placement'] in {'business','shared'}}
    require(set(data['business_acceptance_refs']) == expected, '业务要求范围与分类表不一致')
    require(set(data['readable_acceptance']) == expected, '业务易读说明有缺项或多项')
    items = data['specifications']
    by = {q['id']:q for q in items}
    require(len(items) == len(by), '任务编号重复')
    modules = {m['id'] for m in data['modules']}
    require(len(modules) == len(data['modules']), '业务分组重复')
    old_ids = {q['id'] for q in source['specifications']}
    extension = data.get('current_catalog_extension', {})
    additions = extension.get('additional_specifications', {})
    require(set(by)-old_ids == set(additions), '新增任务未明确登记')
    for identifier, registration in additions.items():
        item = by[identifier]
        require(registration['source_specification'] in old_ids and item['module'] == registration['module'] and item['requirements'] == registration['requirements'], '新增任务来源或范围不匹配')
        require({ref for scene in item['scenarios'] for ref in scene['acceptance_refs']} <= set(extension['acceptance_refs']), '新增任务引用未登记要求')
    historical_specs = {q['id']:q for q in source['specifications']}
    for identifier, binding in extension.get('source_rebindings', {}).items():
        before, current = historical_specs[identifier], by[identifier]
        require(before['requirements'] == binding['from_requirements'] and current['requirements'] == binding['to_requirements'], '来源重绑要求不匹配')
        old_scenes = {scene['id']:scene for scene in before['scenarios']}
        scenes = {scene['id']:scene for scene in current['scenarios']}
        require(set(binding['scenarios']) == set(old_scenes) == set(scenes), '来源重绑场景有遗漏')
        for sid, refs in binding['scenarios'].items():
            require(old_scenes[sid]['acceptance_refs'] == refs['from_acceptance_refs'] and scenes[sid]['source_acceptance_refs'] == refs['from_acceptance_refs'] and scenes[sid]['acceptance_refs'] == refs['to_acceptance_refs'], '来源重绑依据不匹配')
    policies = {p['id'] for p in data['policies']}
    covered = defaultdict(list)
    scene_ids = set()
    for q in items:
        require(re.fullmatch(r'S[0-9]{3}',q['id']) is not None and q['id'] in old_ids | set(additions), '任务编号无效或未保留原编号')
        require(q['module'] in modules and q['kind'] in {'product','extra'}, q['id']+'包含非业务任务')
        require(not {'owner','effort','development_dependencies','contract_dependencies','baseline_evidence'} & set(q), q['id']+'仍混有分工或workflow字段')
        require(q['path'] == 'spec/specs/'+q['id']+'.md', '任务路径不匹配')
        require(q['status'] == 'draft', '草案不能自动标为已完成')
        require(q['scenarios'],q['id']+'没有场景')
        require(len(q['depends_on'])==len(set(q['depends_on'])),q['id']+'依赖重复')
        for dep in q['depends_on']:
            require(dep in by and dep!=q['id'],q['id']+'指向移出的或无效的任务')
        require(set(q['policy_refs']) <= policies,q['id']+'引用无效规则')
        require(set(q['requirements']+q['user_extensions']) <= set(reqs),q['id']+'引用无效来源')
        for s in q['scenarios']:
            require(s['id'].startswith(q['id']+'.SC') and s['id'] not in scene_ids,'场景编号重复或不匹配')
            scene_ids.add(s['id'])
            for key in ('preconditions','actions','expected'):
                require(isinstance(s.get(key),list) and s[key] and all(isinstance(x,str) and x.strip() for x in s[key]),s['id']+'缺少明确条件、操作或结果')
            require(isinstance(s.get('overview'),str) and s['overview'].strip(),s['id']+'缺少场景概述')
            require(isinstance(s.get('steps'),list) and len(s['steps']) >= 2 and all(isinstance(x,str) and x.strip() for x in s['steps']),s['id']+'缺少连续操作步骤与预期结果')
            require(s['acceptance_refs'],s['id']+'缺少业务要求依据')
            for ref in s['acceptance_refs']:
                require(ref in expected,s['id']+'引用workflow要求或无效要求')
                require(ref.split('.AC')[0] in q['requirements']+q['user_extensions'],s['id']+'来源未登记')
                covered[ref].append(s['id'])
            require(all(isinstance(s.get('technical_reference',{}).get(key),list) and s['technical_reference'][key] for key in ('preconditions','actions','expected')),s['id']+'精确约束缺失')
    require(set(covered)==expected,'业务要求覆盖不完整: '+','.join(sorted(expected-set(covered))))
    require(not set(by) & set(routing['excluded_specifications']),'已移出任务仍在业务图中')
    require({p.stem for p in (ROOT/'spec/specs').glob('S*.md')} <= set(by), 'spec目录存在未登记或旧的任务文件')
    require(all(not (ROOT/'spec/specs'/f'{id}.md').exists() for id in routing['excluded_specifications']),'spec目录中仍有旧非业务任务')
    for p in data['policies']:
        require(p['status'] in POLICY_STATUS_LABELS, p['id']+'规则状态未登记')
        require(set(p.get('affected',[])) <= set(by),p['id']+'仍指向移出任务')
    require(all(x in data['external_labels'] for q in items for x in q['external_dependencies']),'外部业务条件缺少说明')
    remaining={q['id']:set(q['depends_on']) for q in items}
    levels=[]
    while remaining:
        ready=sorted(id for id,deps in remaining.items() if not deps)
        require(ready,'业务依赖有循环: '+','.join(sorted(remaining)))
        levels.append(ready)
        remaining={id:deps-set(ready) for id,deps in remaining.items() if id not in ready}
    business_ids={ref.split('.AC')[0] for ref in expected}
    original_ids={q['id'] for q in requirements}
    summary={'specs':len(items),'scenarios':len(scene_ids),'requirements':len(business_ids&original_ids),'user_extensions':len(business_ids-original_ids),'acceptance_points':len(expected),'covered_points':len(covered),'edges':sum(len(q['depends_on']) for q in items),'levels':len(levels),'modules':dict(Counter(q['module'] for q in items)),'original_acceptance_points':len(boundary(data)['acceptance_routes']),'registered_acceptance_points':len(source_refs),'new_acceptance_points':len(extension.get('acceptance_refs',[])),'workflow_only_points':sum(row['placement']=='workflow' for row in routes.values()),'shared_points':sum(row['placement']=='shared' for row in routes.values()),'archived_specs':len(routing['excluded_specifications'])}
    return summary,covered,levels


def reference(ref,data):
    return ref+'：'+data['readable_acceptance'][ref]


def scene_number(n):
    digits='一二三四五六七八九'
    if 1 <= n <= 9:return digits[n-1]
    if n == 10:return '十'
    if 11 <= n <= 19:return '十'+digits[n-11]
    if 20 <= n <= 99:return digits[n//10-1]+'十'+(digits[n%10-1] if n%10 else '')
    return str(n)


def render_spec(q,data,by):
    modules={m['id']:m['title'] for m in data['modules']}
    lines=['# '+q['title'],'','> 业务需求草案；页面和按钮名称用于说明计划中的功能，不表示已经实现。','']
    for i,s in enumerate(q['scenarios'],1):
        lines+=['<a id="'+s['id'].lower().replace('.','')+'"></a>','','## 场景'+scene_number(i),'','### 场景概述','',s['overview'],'','### 操作步骤与预期结果','']
        lines+=[str(i)+'. '+text for i,text in enumerate(s['steps'],1)]
        lines+=['','<details>','<summary>场景编号与精确规则补充（'+s['id']+'）</summary>','','- 场景名称：'+s['title'],'- 场景编号：'+s['id'],'','**对应的业务要求**','']+['- '+reference(ref,data) for ref in s['acceptance_refs']]
        raw=s['technical_reference']
        lines+=['','**精确条件**','']+['- '+x for x in raw['preconditions']]
        lines+=['','**精确动作**','']+['- '+x for x in raw['actions']]
        lines+=['','**结果与约束**','']+['- '+x for x in raw['expected']]+['','</details>','']
    deps='、'.join('['+id+' '+by[id]['title']+']('+id+'.md)' for id in q['depends_on']) or '没有其他业务功能前置'
    lines+=['<details>','<summary>功能分类、业务依赖和共用说明</summary>','','- 功能编号：'+q['id'],'- 业务分组：'+modules[q['module']],'- 范围：'+('附加AI助手' if q['scope']=='extra' else '核心业务'),'- 对应要求：'+'、'.join(q['requirements']+q['user_extensions']),'- 有关业务功能：'+deps+'。']
    if q['external_dependencies']:lines+=['- 需要的外部业务条件：'+'、'.join(data['external_labels'][x] for x in q['external_dependencies'])+'。']
    if q['policy_refs']:lines+=['- 相关业务规则：'+'、'.join('['+x+'](../policies.md#'+x.lower()+')' for x in q['policy_refs'])+'。待评审规则仍是建议。']
    lines+=['- [场景共用说明](../policies.md)列出示例账号、教室和时间。','','</details>','']
    return '\n'.join(lines)

def views(data,requirements,summary,coverage,levels):
    routing=current_boundary(data, requirements);previous=snapshot(data)
    all_reqs={q['id']:q for q in requirements+routing['user_extensions']}
    by={q['id']:q for q in data['specifications']}
    module_by={m['id']:m for m in data['modules']}
    outputs={q['path']:render_spec(q,data,by) for q in by.values()}
    lines=['# 业务要求和场景对照','','本表只统计业务功能。共'+str(summary['acceptance_points'])+'条业务相关要求都有场景，其中'+str(summary['shared_points'])+'条原要求同时含工程部分，工程部分另由[workflow清单](../docs/18-workflow-obligations.md)管理。覆盖不表示实现或测试已通过。','','| 要求编号 | 业务上应该做到什么 | 场景 |','| --- | --- | --- |']
    for ref in data['business_acceptance_refs']:
        links=[]
        for sid in coverage[ref]:
            id=sid.split('.')[0];links.append('['+sid+'](specs/'+id+'.md#'+sid.lower().replace('.','')+')')
        lines.append('| '+ref+' | '+data['readable_acceptance'][ref].replace('|','/')+' | '+'、'.join(links)+' |')
    lines+=['','原始目录的'+str(summary['original_acceptance_points'])+'条要求都保留去向记录，见[分类索引](../'+data['workflow_boundary_path']+')；本轮新增'+str(summary['new_acceptance_points'])+'条要求也在当前业务索引中明确登记，当前共'+str(summary['registered_acceptance_points'])+'条。没有把工程要求当作已经完成。','']
    outputs['spec/coverage.md']='\n'.join(lines)
    lines=['# 业务规则和场景共用说明','','各条规则分别标明已确认、待评审或外部条件，请以状态和正文为准。示例账号和教室仅用于说明场景；审批、维修等已确认默认值见对应规则。','','## 示例账号、教室和时间','']
    names={'clock':'时间','accounts':'账号','resources':'教室和座位','interval':'示例时段','cleanliness':'示例数据'}
    for key,value in data['fixtures'].items():lines.append('- **'+names[key]+'**：'+value)
    lines+=['','## 业务规则','']
    statuses=POLICY_STATUS_LABELS
    for p in data['policies']:
        lines+=['### '+p['id'],'','- 状态：'+statuses[p['status']],'- 规则：'+p['rule'],'- 涉及功能：'+('、'.join(p.get('affected',[])) or '相关业务功能'),'','<details>','<summary>精确规则补充</summary>','',p['technical_rule'],'','</details>','']
    outputs['spec/policies.md']='\n'.join(lines)
    mmd=['flowchart TB']
    for m in data['modules']:
        mmd+=['  subgraph '+m['id']+'["'+m['title']+'"]']
        for q in by.values():
            if q['module']==m['id']:mmd.append('    '+q['id']+'["'+q['id']+' '+q['title']+('（附加）' if q['scope']=='extra' else '')+'"]')
        mmd.append('  end')
    for q in by.values():
        for dep in q['depends_on']:mmd.append('  '+dep+' --> '+q['id'])
    outputs['spec/topology.mmd']='\n'.join(mmd)+'\n'
    lines=['# 业务需求清单','','共'+str(summary['specs'])+'项业务功能、'+str(summary['scenarios'])+'个场景。这里列的是系统要提供什么能力，按业务分组；分工、环境、测试流程、报告和交付步骤见[workflow文档](../docs/18-workflow-obligations.md)。','','- [业务关系图](../docs/spec-topology.html)','- [业务拆分说明](../docs/17-spec-topology-and-assignment.md)','- [业务要求和场景对照](coverage.md)','- [业务规则和场景说明](policies.md)','','## 按业务分组','']
    for m in data['modules']:
        lines+=['### '+m['title'],'']
        for q in by.values():
            if q['module']==m['id']:lines.append('- ['+q['id']+' '+q['title']+'](specs/'+q['id']+'.md)'+('（附加）' if q['scope']=='extra' else ''))
        lines+=['']
    lines+=['## 场景格式','','每个场景按“场景概述”和“操作步骤与预期结果”书写，操作和对应结果合并在编号步骤中。','','## 文档维护','','spec-network.json是业务规范源。修改后由workflow运行以下生成与检查命令：','','```sh','python tools/spec_plan/render.py','python tools/spec_plan/render.py --check','```','','编号保留，移出工程条目后出现空号属于正常情况，不代表漏掉业务。','']
    outputs['spec/README.md']='\n'.join(lines)
    outputs['docs/17-spec-topology-and-assignment.md']=render_main(data,summary)
    outputs['docs/18-workflow-obligations.md']=render_workflow(data,summary,routing,previous,all_reqs)
    visible_reqs=[]
    for id,q in all_reqs.items():
        criteria=[{'ref':id+'.AC'+str(i),'text':data['readable_acceptance'][id+'.AC'+str(i)],'shared':routing['acceptance_routes'][id+'.AC'+str(i)]['placement']=='shared'} for i in range(1,len(q['acceptance'])+1) if id+'.AC'+str(i) in data['readable_acceptance']]
        if criteria:visible_reqs.append({'id':id,'title':data['readable_titles'][id],'criteria':criteria})
    payload={'specifications':list(by.values()),'modules':data['modules'],'summary':summary,'levels':levels,'requirements':visible_reqs,'fixtures':data['fixtures'],'policies':data['policies'],'external_labels':data['external_labels'],'reading_style':data['reading_style']}
    template=(ROOT/'tools/spec_plan/topology.template.html').read_text(encoding='utf-8')
    outputs['docs/spec-topology.html']=template.replace('__SPEC_DATA__',json.dumps(payload,ensure_ascii=False).replace('<','\\u003c')).replace('__POLICY_STATUS_LABELS__',json.dumps(POLICY_STATUS_LABELS,ensure_ascii=False))
    return outputs


def render_main(data,summary):
    descriptions={'M01':'注册登录、账号角色和权限、使用资格、参数设置、导航和中英文页面','M02':'教室座位、插座、开放时间、院系限制、占用冲突、维修限制和签到码','M03':'找座位、预约取消、签到、三类定时处理、个人历史和结果恢复','M04':'整室申请、逐级审批、资源确认、撤销、进度和异常恢复','M05':'报修派单、开始维修、超时上报、独立验收、评分取消和安全恢复','M06':'站内通知、邮件、操作记录、统计和附加AI助手'}
    lines=['# 业务需求拆分和关系图','','这里仅分解自习室预约系统的业务功能，共**'+str(summary['specs'])+'项功能、'+str(summary['scenarios'])+'个场景**。所有条目仍是草案。','','开发分工、环境准备、接口准备、测试流程、实验报告和发布步骤已移到[workflow文档](18-workflow-obligations.md)，不再作为业务图中的任务。','','- [打开业务关系图](spec-topology.html)：按业务模块筛选，点击节点查看具体场景。','- [全部业务功能](../spec/README.md)：按模块阅读每个功能。','- [业务要求和场景对照](../spec/coverage.md)：查看每条业务要求落在哪个场景。','- [业务规则与示例说明](../spec/policies.md)：查看时间边界、权限、状态变化和待确认规则。','','## 业务分为哪些部分','','| 业务模块 | 包含什么 | 功能数 | 场景数 |','| --- | --- | --- | --- |']
    for m in data['modules']:
        owned=[q for q in data['specifications'] if q['module']==m['id']]
        lines.append('| '+m['title']+' | '+descriptions[m['id']]+' | '+str(len(owned))+' | '+str(sum(len(q['scenarios']) for q in owned))+' |')
    lines+=['','## 一份业务spec写什么','','每个场景固定使用“场景概述”和“操作步骤与预期结果”两个部分。概述用一段话说明用户处境和目标，编号步骤把操作与对应结果写在一起；开始条件融入概述或第一步。场景编号、精确约束和有关业务能力放在可展开的补充信息中。不写开发分工、报告、环境和提交代码的步骤。','','例如[S029签到](../spec/specs/S029.md)：','','1. user1已预约R101的A01，09:00开始，现在是09:00。','2. 打开“我的预约”，找到这笔预约，输入教室当天的签到码，点击“签到”。','3. 页面显示已签到，同一笔只记一次。错码、提前、截止后和替别人签到的情况另列场景。','','自动功能也属于业务。例如开始前15分钟提醒、开始后10分钟未签到再提醒、15分钟仍未签到自动取消。它们会实际改变用户看到的结果，仍需要写清。','','## 哪些恢复和权限规则仍保留','','以下直接影响系统业务，继续保留：重复提交不多下单、两人不能订到同座同一时段、故障后不能假报成功、取消只释放自己的占用、维修不能错误恢复、不能查看或修改别人的记录。','','报修里的派单、维修负责人和独立验收是产品功能；它们与开发成员的任务责任不同。中英文、清楚的状态提示和简洁页面也保留。','','## 图里的箭头怎样理解','','箭头从被其他功能使用的能力，指向使用它的功能。图按业务模块分列，不按人员分列。当前有'+str(summary['edges'])+'条业务依赖、'+str(summary['levels'])+'个先后层次，没有互相循环等待。层数不表示周数或实际开发排期。','','接口准备、构建、测试、容器和部署不会再成为业务节点。原先经过这些工程节点的关系已重新整理，只保留真正的业务关联。','','## 哪些规则已确认，哪些条件仍待确认','','签到、取消、审批维修默认值和统计口径等，分别按[规则状态](../spec/policies.md)区分已确认与待确认。已确认规则沿用确认内容，未确认项继续保留；文档生成不改变批准状态。','','真实学校教室数据、正式邮件服务、教室现场展示条件和真实模型服务仍要具备才能完成对应业务。缺一项只影响相关部分，不能用测试数据冒充真实接入。','','## 原始要求是否丢了','','原目录仍完整保留。'+str(summary['acceptance_points'])+'条业务相关要求留在业务规范，'+str(summary['workflow_only_points'])+'条工程或过程要求转到workflow；另有'+str(summary['shared_points'])+'条同时包含业务和工程内容，分别记录。当前合计登记'+str(summary['registered_acceptance_points'])+'条，其中'+str(summary['original_acceptance_points'])+'条历史要求全部保留，另有'+str(summary['new_acceptance_points'])+'条本轮新增要求；没有把移出业务图当作已完成。','','业务编号继续沿用，因此会有空号。S009只保留页面一致性、中英文和简洁提示；S068只保留助手的追问、权限和安全操作，模型或插件安装部署归workflow。','','本次只调整需求文档及其生成工具，没有改变正式账号权限或业务代码。','']
    return '\n'.join(lines)


def render_workflow(data,summary,routing,previous,reqs):
    old={q['id']:q for q in previous['specifications']}
    lines=['# workflow管理的工程和过程要求','','这些内容从业务spec中移出，在workflow中统一管理。它们不是用户预约、审批或报修时使用的业务功能。','','本次只是分类和文档整理，不表示自动化已经实现。已有技能、权限、TDD检查、人工验收、合并和发布规则继续执行；需要真实成员完成或批准的事项不能由AI代签。','','## 包含哪些工作','','- 成员登记、文件分工、模块隔离和公共变更评审。','- 接口说明的准备、公共组件复用和技术栈保持。','- 环境检查、工具安装、本机运行、数据库升级与初始化。','- 先测后写、回归、故障联调和证据保存。','- 缓冲网站收件、审核、独立检查、72小时同步与合并请求。','- 测试部署、生产批准、发布回退、课程评审和个人报告。','','## 原要求的去向','','历史'+str(summary['original_acceptance_points'])+'条要求全部保留分类；本轮新增'+str(summary['new_acceptance_points'])+'条业务要求，当前共'+str(summary['registered_acceptance_points'])+'条。下面列'+str(summary['workflow_only_points']+summary['shared_points'])+'条workflow相关要求，其中'+str(summary['shared_points'])+'条还含有业务部分；业务部分见[业务对照表](../spec/coverage.md)。','','| 要求编号 | workflow管理的部分 | 分类 |','| --- | --- | --- |']
    for ref,row in routing['acceptance_routes'].items():
        if row['placement']=='business':continue
        text=row.get('workflow_part') or previous['readable_acceptance'][ref]
        lines.append('| '+ref+' | '+text.replace('|','/')+' | '+('同时有业务部分' if row['placement']=='shared' else '工程或过程要求')+' |')
    lines+=['','## 从业务图移出的旧条目','','以下'+str(summary['archived_specs'])+'个旧条目仅留历史资料，不是新的业务任务；不会因为移动就变成完成状态。','','| 原编号 | 原内容 |','| --- | --- |']
    for id in routing['excluded_specifications']:lines.append('| ['+id+'](workflow/archive/'+id+'.md) | '+old[id]['title']+' |')
    lines+=['','混合条目另外处理：','','- S009：公共实现、字典检查和技术栈检查留在workflow；业务图只保留用户能看到的页面和语言效果。','- S068.SC01：模型/插件安装、流水线和凭据配置留在workflow；S068其余场景仍在业务图。','','## 分工与运行配置','','旧分工保存在[历史分工资料](workflow/assignments/a.md)，其余成员清单在同一目录。它们是规划资料，不自动改变真实成员、文件授权或容器权限。新的业务源文件不再保存开发负责人、难度点数或工程依赖。','','当前仍按[团队工作流规则](14-team-workflow.md)执行。把业务图改为纯业务，不会绕过测试、审核、冲突处理或生产批准。','','## 可核对的来源','','- [原始需求目录](../spec/requirements.json)','- [历史170条要求的分类索引](workflow/requirements-boundary.json)','- [本轮新增要求与来源重绑登记](../spec/spec-network.json)','- [调整前的完整81项计划](workflow/previous-spec-plan.json)','- [调整前总说明](workflow/archive/17-previous-plan.md)','','原始资料用于追溯，不作为当前业务清单。','']
    return '\n'.join(lines)


def main():
    parser=argparse.ArgumentParser()
    parser.add_argument('--check',action='store_true')
    args=parser.parse_args()
    data,requirements=load()
    summary,covered,levels=validate(data,requirements)
    outputs=views(data,requirements,summary,covered,levels)
    for name,text in outputs.items():
        path=(ROOT/name)
        require(path.resolve().is_relative_to(ROOT.resolve()),'输出路径越界')
        body=text.encode('utf-8')
        if args.check:
            require(path.is_file() and path.read_bytes()==body,'生成视图过期: '+name)
        else:
            path.parent.mkdir(parents=True,exist_ok=True)
            path.write_bytes(body)
    print(json.dumps({**summary,'mode':'check' if args.check else 'render','views':len(outputs)},ensure_ascii=False))

if __name__=='__main__':
    main()
