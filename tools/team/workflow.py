"""Persistent member workflow; stage completion requires artifacts, never a success flag."""
import hashlib
import json
import re
import uuid
from contextlib import contextmanager
from datetime import datetime, timezone
from pathlib import Path
from policy import PolicyError, load, module_for, guard, git, evidence_valid
from test_reports import validate_result

SKILLS = ("campus-workflow", "campus-build", "campus-req", "campus-test", "campus-tdd")
STAGES = ("workspace", "baseline", "requirements", "test-plan", "tdd", "regression")
RUNNABLE = {"pending", "needs_fix"}

def now():
    return datetime.now(timezone.utc).isoformat()

def read(path):
    return json.loads(Path(path).read_text(encoding="utf-8"))

def digest(value):
    return hashlib.sha256(value).hexdigest()

def save(path, value):
    path = Path(path); path.parent.mkdir(parents=True, exist_ok=True)
    temp = path.with_name(path.name + "." + uuid.uuid4().hex + ".tmp")
    temp.write_bytes((json.dumps(value, ensure_ascii=False, indent=2) + "\n").encode("utf-8"))
    temp.replace(path)

def skill_manifest(root):
    entries = {}
    for name in SKILLS:
        folder = Path(root) / "skills" / name
        if not (folder / "SKILL.md").is_file():
            raise PolicyError("WORKFLOW_SKILL_MISSING: " + name)
        for path in sorted(folder.rglob("*")):
            if path.is_file() and path.suffix in (".md", ".yaml"):
                entries[path.relative_to(root).as_posix()] = digest(path.read_bytes())
    engine={}
    for name in ("scripts/team.py","tools/team/workflow.py","tools/team/recording.py","tools/team/test_reports.py","tools/team/policy.py","tools/team/environment.py","tools/team/members.py","tools/team/commits.py","tools/team/delivery.py"):
        path=Path(root)/name
        if path.is_file():engine[name]=digest(path.read_bytes())
    return {"files":entries,"engine":engine,"sha256":digest(json.dumps({"files":entries,"engine":engine},sort_keys=True).encode())}

def source_snapshot(worktree, context, destination=None):
    worktree = Path(worktree)
    policy = load(context["policy_root"])
    paths = guard(worktree, policy, context["member"], context["module"], context["base"])
    records = []; product = []
    for name in paths:
        if name.startswith("spec/progress/"):
            continue
        path = worktree / name
        body = path.read_bytes() if path.is_file() else None
        item = {"path": name, "sha256": digest(body) if body is not None else None}
        records.append(item)
        testing = "/src/test/" in name or name.startswith("frontend/tests/") or "/__tests__/" in name or ".test." in name or ".spec." in name
        if not testing:
            product.append(item)
        if destination and body is not None:
            target = Path(destination) / "inputs" / name
            target.parent.mkdir(parents=True, exist_ok=True); target.write_bytes(body)
    def key(items):
        return digest(json.dumps({"base": context["base"], "files": items}, sort_keys=True).encode())
    result = {"head": git(worktree, "rev-parse", "HEAD").decode().strip(), "sha256": key(records), "product_sha256": key(product), "files": records}
    if destination:
        folder = Path(destination); folder.mkdir(parents=True, exist_ok=True)
        names = [item["path"] for item in records]
        patch = git(worktree, "diff", "--binary", "--no-renames", context["base"], "--", *names) if names else b""
        (folder / "source.patch").write_bytes(patch)
        save(folder / "source.local", result)
    return result

def analysis_path(root, state, module):
    item = state["modules"][module]; key = module_for(load(root), module)["key"]
    return Path(item["worktree"]) / "spec/progress" / key / state["member"]["layer"] / "analysis.json"

def validate_analysis(value, catalogue, member, module, targets, blocked=(), testing=False):
    if value.get("version")!=2:raise PolicyError("ANALYSIS_VERSION_REQUIRED")
    if value.get("module") != module or value.get("layer") != member["layer"]:
        raise PolicyError("ANALYSIS_SCOPE_MISMATCH")
    rows = value.get("requirements", [])
    if len(rows) != len(targets) or {r["id"] for r in rows} != set(targets):
        raise PolicyError("ANALYSIS_REQUIREMENTS_MISMATCH")
    scenario_ids = set(); dependencies = []; test_map = {}
    for row in rows:
        q = catalogue[row["id"]]
        if q["module"] != module or member["layer"] not in q["layers"] or row.get("acceptance") != q["acceptance"] or row.get("source") != q["source"]:
            raise PolicyError("CANONICAL_REQUIREMENT_CHANGED")
        if row["id"] in blocked:
            continue
        covered = set(); tests = set(); kinds = set()
        for scene in row.get("scenarios", []):
            identifier = scene.get("id", "")
            if not re.fullmatch(re.escape(row["id"]) + r"\.S[0-9]{2,}", identifier) or identifier in scenario_ids:
                raise PolicyError("UNSTABLE_OR_DUPLICATE_SCENARIO_ID")
            scenario_ids.add(identifier); dependencies.extend(scene.get("depends_on", []))
            for field in ("preconditions", "actions", "expected"):
                if not isinstance(scene.get(field), list) or not scene[field] or any(not isinstance(x, str) or not x.strip() for x in scene[field]):
                    raise PolicyError("SCENARIO_BEHAVIOR_INCOMPLETE")
            criteria = scene.get("covers", [])
            if not criteria or any(type(i) is not int or not 1 <= i <= len(q["acceptance"]) for i in criteria):
                raise PolicyError("INVALID_ACCEPTANCE_ANCHOR")
            covered.update(criteria)
            objects = scene.get("objects")
            if not isinstance(objects, list) or not objects:
                raise PolicyError("BOUNDARY_OBJECT_MAPPING_REQUIRED")
            for obj in objects:
                if obj.get("status") not in ("existing", "missing", "unverified") or not obj.get("key") or not obj.get("evidence"):
                    raise PolicyError("BOUNDARY_FACT_REQUIRED")
            if testing:
                cell_ids = set(); cell_coverage = set()
                for cell in scene.get("cells", []):
                    cid = cell.get("id", "")
                    if not cid.startswith(identifier + "#") or cid in cell_ids:
                        raise PolicyError("INVALID_CHECK_CELL_ID")
                    cell_ids.add(cid)
                    index = cell.get("criterion")
                    if index not in criteria or cell.get("basis") != q["acceptance"][index-1]:
                        raise PolicyError("TEST_EXPECTATION_HAS_NO_SOURCE")
                    if cell.get("kind") not in ("unit", "component", "api", "db", "e2e", "contract"):
                        raise PolicyError("UNKNOWN_TEST_KIND")
                    for field in ("expected", "sample", "observation", "comparison"):
                        if not isinstance(cell.get(field), str) or not cell[field].strip():
                            raise PolicyError("CHECK_PLAN_INCOMPLETE")
                    ids = cell.get("test_ids")
                    if not isinstance(ids, list) or not ids or any(not isinstance(x, str) or not x.strip() for x in ids):
                        raise PolicyError("CHECK_CELL_NOT_MAPPED_TO_TEST")
                    cell_coverage.add(index); tests.update(ids); kinds.add(cell["kind"])
                if cell_coverage != set(criteria):
                    raise PolicyError("ACCEPTANCE_WITHOUT_CHECK_CELL")
        if covered != set(range(1, len(q["acceptance"])+1)):
            raise PolicyError("ACCEPTANCE_NOT_COVERED")
        if testing:
            if member["layer"] == "frontend" and not kinds.intersection({"component", "e2e"}):
                raise PolicyError("FRONTEND_INTERACTION_TEST_REQUIRED")
            if member["layer"] == "backend" and not kinds.intersection({"api", "db", "contract"}):
                raise PolicyError("BACKEND_CONTRACT_OR_PERSISTENCE_TEST_REQUIRED")
            test_map[row["id"]] = tests
    if any(dep not in scenario_ids for dep in dependencies):
        raise PolicyError("UNKNOWN_LOCAL_SCENARIO_DEPENDENCY")
    graph = {s["id"]: s.get("depends_on", []) for row in rows if row["id"] not in blocked for s in row.get("scenarios", [])}
    visiting=set(); visited=set()
    def visit(node):
        if node in visiting:raise PolicyError("SCENARIO_DEPENDENCY_CYCLE")
        if node in visited:return
        visiting.add(node)
        for child in graph[node]:visit(child)
        visiting.remove(node);visited.add(node)
    for node in graph:visit(node)
    return test_map

def state_path(root, identifier):
    if not re.fullmatch(r"[a-z][a-z0-9-]{2,47}-[0-9a-f]{12}", identifier):
        raise PolicyError("INVALID_WORKFLOW_ID")
    return Path(root) / ".campus/workflows" / identifier / "state.local"

@contextmanager
def state_lock(path):
    lock = path.with_name("write.lock")
    try:lock.mkdir()
    except FileExistsError:raise PolicyError("WORKFLOW_BUSY: inspect the existing writer; do not remove a live lock")
    try:yield
    finally:lock.rmdir()

def event(state, action, **detail):
    state["updated_at"] = now()
    state["history"].append({"at": state["updated_at"], "action": action, **detail})

def open_workflow(root, member, requirements, selected=None):
    root=Path(root); base=git(root,"rev-parse","HEAD").decode().strip()
    if base != member.get("baseline"):raise PolicyError("BASELINE_CHANGED")
    if not git(root,"show",base+":spec/modules.json",check=False):raise PolicyError("PUBLISH_OPTIMIZED_BASELINE_FIRST")
    if member.get("admin") or member.get("layer") not in ("frontend","backend"):raise PolicyError("USE_DEVELOPMENT_MEMBER_IDENTITY")
    if selected and selected not in member["modules"]:raise PolicyError("MODULE_NOT_ASSIGNED")
    identifier=member["id"]+"-"+member["layer"]+"-"+base[:12]; path=state_path(root,identifier); path.parent.mkdir(parents=True,exist_ok=True)
    manifest=skill_manifest(root)
    with state_lock(path):
        if path.exists():
            state=read(path)
            if state["base"]!=base or state["member"]["id"]!=member["id"] or state["member"]["layer"]!=member["layer"] or state["member"]["modules"]!=member["modules"]:raise PolicyError("WORKFLOW_IDENTITY_OR_BASELINE_CHANGED")
            if state["skills"]["sha256"]!=manifest["sha256"]:raise PolicyError("WORKFLOW_SKILL_VERSION_CHANGED: preserve the old run; review an upgrade before resuming")
        else:
            public={k:member[k] for k in ("id","layer","modules")}
            state={"version":2,"id":identifier,"base":base,"member":public,"skills":manifest,"modules":{},"history":[]}
            for module in member["modules"]:
                targets=[q["id"] for q in requirements if q["module"]==module and q.get("state") in RUNNABLE and q.get("execution_owner","member")=="member"]
                definition=module_for(load(root),module)
                state["modules"][module]={"targets":targets,"blocked":{},"checkpoints":{},"worktree":str(root/".campus/worktrees"/member["id"]/member["layer"]/definition["key"]/base[:12]),"submission":None}
            event(state,"START")
        eligible={q["id"]:q.get("state") for q in requirements}
        for current_module,item in state["modules"].items():
            item["deferred"]=[{"id":q["id"],"state":q.get("state")} for q in requirements if q["module"] in member["modules"] and q["module"]==current_module and q.get("state") not in RUNNABLE|{"accepted","verified"} and q["id"] not in item["targets"]]
            item["server_completed"]=[q for q in item["targets"] if eligible.get(q) in ("accepted","verified")]
        save(path,state)
    return state

def active_ids(item):
    return [q for q in item["targets"] if q not in item["blocked"] and q not in item.get("server_completed",[])]

def next_action(state, selected=None):
    waits=[]
    for module,item in state["modules"].items():
        if selected and module!=selected:continue
        ids=active_ids(item)
        if item["submission"] and item["submission"]["state"] in ("VERIFIED","PUBLISHED"):
            continue
        if item["submission"]:
            waits.append({"module":module,"status":item["submission"]["state"],"id":item["submission"]["id"]});continue
        if not ids:continue
        for stage in STAGES:
            if stage not in item["checkpoints"]:
                return {"module":module,"stage":stage,"requirements":ids,"worktree":item["worktree"]}
        return {"module":module,"stage":"upload","requirements":ids,"worktree":item["worktree"]}
    deferred=[q for item in state["modules"].values() for q in item.get("deferred",[])]
    return {"stage":"waiting" if waits or deferred else "blocked" if any(i["blocked"] for i in state["modules"].values()) else "development_complete","waiting":waits,"deferred":deferred,"blocked":{m:i["blocked"] for m,i in state["modules"].items() if i["blocked"]}}

def validate_run(item, stage, source=None, reports=True):
    if not item.get("run_id") or not item.get("source",{}).get("sha256") or item.get("exit_code")!=0 or not item.get("log") or digest(item["log"].encode())!=item.get("sha256"):
        raise PolicyError("VALID_RUN_RECEIPT_REQUIRED")
    if reports:validate_result(item.get("test_result"),stage)
    if source and item["source"]["sha256"]!=source["sha256"]:raise PolicyError("SOURCE_CHANGED_AFTER_TEST")
    return item

def checkpoint(root, identifier, module, stage, artifact=None, reset=False):
    root=Path(root); path=state_path(root,identifier)
    with state_lock(path):
        state=read(path)
        if stage not in STAGES or module not in state["modules"]:raise PolicyError("INVALID_WORKFLOW_STAGE")
        if skill_manifest(root)["sha256"]!=state["skills"]["sha256"]:raise PolicyError("WORKFLOW_SKILL_VERSION_CHANGED")
        item=state["modules"][module]
        if item["submission"]:raise PolicyError("SUBMISSION_ALREADY_PENDING")
        if reset:
            for key in STAGES[STAGES.index(stage):]:item["checkpoints"].pop(key,None)
            event(state,"INVALIDATE",module=module,stage=stage);save(path,state);return state
        previous=STAGES[:STAGES.index(stage)]
        if any(key not in item["checkpoints"] for key in previous):raise PolicyError("WORKFLOW_STAGE_PREREQUISITE_MISSING")
        if stage in ("baseline","tdd","regression") and not artifact:raise PolicyError("RUN_RECEIPT_ARTIFACT_REQUIRED")
        worktree=Path(item["worktree"]); ctx=read(worktree/".campus/task.local")
        if ctx["base"]!=state["base"] or ctx["member"]["id"]!=state["member"]["id"] or ctx["member"]["layer"]!=state["member"]["layer"] or ctx["module"]!=module or ctx.get("mode")=="mutation":raise PolicyError("WORKTREE_CONTEXT_MISMATCH")
        source=source_snapshot(worktree,ctx)
        if source["head"]!=state["base"]:raise PolicyError("UNREVIEWED_LOCAL_COMMIT")
        details={"source":source,"at":now()}
        if stage=="workspace":
            details["product_at_start"]=source["product_sha256"]
        elif stage=="baseline":
            proof=read(artifact);validate_run(proof.get("module",{}).get("baseline",{}),"baseline",source)
        elif stage in ("requirements","test-plan"):
            analysis=read(analysis_path(root,state,module))
            catalogue={q["id"]:q for q in read(root/"spec/requirements.json")["requirements"]}
            validate_analysis(analysis,catalogue,state["member"],module,item["targets"],list(item["blocked"])+item.get("server_completed",[]),stage=="test-plan")
            details["analysis_sha256"]=digest(analysis_path(root,state,module).read_bytes())
        else:
            if digest(analysis_path(root,state,module).read_bytes())!=item["checkpoints"]["test-plan"]["analysis_sha256"]:raise PolicyError("ANALYSIS_CHANGED_REPLAN_REQUIRED")
            proof=read(artifact); ids=active_ids(item)
            filtered={"requirements":{q:proof.get("requirements",{}).get(q,{}) for q in ids}}
            evidence_valid(filtered,ids,strict=True)
            catalogue={q["id"]:q for q in read(root/"spec/requirements.json")["requirements"]}
            mapping=validate_analysis(read(analysis_path(root,state,module)),catalogue,state["member"],module,item["targets"],list(item["blocked"])+item.get("server_completed",[]),True)
            for q in ids:
                receipt=filtered["requirements"][q]
                if item.get("needs_fix_after") and receipt["green"].get("started_at","")<=item["needs_fix_after"]:raise PolicyError("FRESH_TEST_EVIDENCE_REQUIRED_AFTER_REVIEW")
                if not mapping[q].issubset(set(receipt["green"]["test_result"]["required_tests"])):raise PolicyError("CHECK_PLAN_TESTS_NOT_PROVEN")
                if receipt.get("completion_mode")=="baseline_satisfied" and receipt["baseline"]["source"]["product_sha256"]!=item["checkpoints"]["workspace"]["product_at_start"]:raise PolicyError("BASELINE_SATISFIED_NOT_ORIGINAL_PRODUCT")
            if stage=="regression":
                all_tests=set().union(*(mapping[q] for q in ids))
                candidate=proof.get("module",{}).get("regression")
                if candidate is None:
                    candidate=next((proof["requirements"][q]["green"] for q in ids if proof["requirements"][q]["green"]["source"]["sha256"]==source["sha256"] and all_tests.issubset(set(proof["requirements"][q]["green"]["test_result"]["required_tests"]))),{})
                    details["regression_reused_run_id"]=candidate.get("run_id")
                run=validate_run(candidate,"regression",source)
                if not all_tests.issubset(set(run["test_result"]["required_tests"])):raise PolicyError("REGRESSION_COVERAGE_MISSING")
                validate_run(proof.get("module",{}).get("check",{}),"check",source,reports=False)
            details["evidence_sha256"]=digest(Path(artifact).read_bytes())
            details["artifact"]=str(Path(artifact).resolve())
        if artifact:
            details["artifact_sha256"]=digest(Path(artifact).read_bytes())
        item["checkpoints"][stage]=details
        archive=path.parent/"checkpoints"/(uuid.uuid4().hex+".json");save(archive,{"module":module,"stage":stage,**details})
        event(state,"CHECKPOINT",module=module,stage=stage);save(path,state)
    return state

def block(root,identifier,module,requirement,kind,reason,resume_when):
    if kind not in ("external","clarification","scope") or not reason.strip() or not resume_when.strip():raise PolicyError("BLOCKER_REASON_AND_RELEASE_CONDITION_REQUIRED")
    path=state_path(root,identifier)
    with state_lock(path):
        state=read(path);item=state["modules"][module]
        if requirement not in item["targets"] or item["submission"]:raise PolicyError("INVALID_BLOCKED_REQUIREMENT")
        item["blocked"][requirement]={"kind":kind,"reason":reason,"resume_when":resume_when,"at":now()}
        for key in STAGES[2:]:item["checkpoints"].pop(key,None)
        event(state,"BLOCK",module=module,requirement=requirement);save(path,state)
    return state

def before_upload(root,identifier,module,worktree,evidence,ids):
    state=read(state_path(root,identifier));item=state["modules"][module]
    if item["submission"] or "regression" not in item["checkpoints"] or set(ids)!=set(active_ids(item)):raise PolicyError("WORKFLOW_NOT_READY_TO_UPLOAD")
    if Path(worktree).resolve()!=Path(item["worktree"]).resolve():raise PolicyError("WORKTREE_CONTEXT_MISMATCH")
    if digest(analysis_path(root,state,module).read_bytes())!=item["checkpoints"]["test-plan"]["analysis_sha256"]:raise PolicyError("ANALYSIS_CHANGED_AFTER_TEST")
    current=source_snapshot(Path(worktree),read(Path(worktree)/".campus/task.local"))
    if current["sha256"]!=item["checkpoints"]["regression"]["source"]["sha256"]:raise PolicyError("SOURCE_CHANGED_AFTER_REGRESSION")
    if digest(Path(evidence).read_bytes())!=item["checkpoints"]["regression"]["evidence_sha256"]:raise PolicyError("EVIDENCE_CHANGED_AFTER_REGRESSION")
    return state

def submitted(root,identifier,module,row):
    path=state_path(root,identifier)
    with state_lock(path):
        state=read(path); item=state["modules"][module]
        if row["member_id"]!=state["member"]["id"] or row["module_id"]!=module:raise PolicyError("BUFFER_SUBMISSION_SCOPE_MISMATCH")
        item["submission"]={"id":row["id"],"state":row["state"]}
        event(state,"UPLOAD",module=module,submission=row["id"]);save(path,state)

def refresh_submissions(root,state,rows):
    path=state_path(root,state["id"]); by_id={row["id"]:row for row in rows}
    with state_lock(path):
        state=read(path)
        for module,item in state["modules"].items():
            if not item["submission"]:continue
            row=by_id.get(item["submission"]["id"])
            if row is None:continue
            if row.get("member_id")!=state["member"]["id"] or row.get("module_id")!=module:raise PolicyError("BUFFER_SUBMISSION_SCOPE_MISMATCH")
            item["submission"]["state"]=row["state"]
            if row["state"] in ("REJECTED","VERIFY_FAILED") and row.get("result",{}).get("category")!="external":
                item.setdefault("past_submissions",[]).append(item["submission"]);item["submission"]=None;item["needs_fix_after"]=now()
                for stage in ("tdd","regression"):item["checkpoints"].pop(stage,None)
                event(state,"RETURN_FOR_FIX",module=module)
        save(path,state)
    return state
