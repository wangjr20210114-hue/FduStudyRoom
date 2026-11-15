"""Run tests only in the assigned container, with fresh reports and immutable input snapshots."""
import hashlib
import json
import re
import shutil
import subprocess
import uuid
from pathlib import Path, PurePosixPath
from policy import PolicyError, guard, load, git
from environment import docker, docker_path, sandbox_project
from workflow import source_snapshot, save, read, now
from test_reports import parse_report, merge_reports, validate_result
from pilot import resolve_pilot, validate_receipt_scope

def report_path(value, context):
    path=PurePosixPath(value)
    if not path.is_absolute() or ".." in path.parts or any(ch in value for ch in ("\0","\r","\n")):
        raise PolicyError("INVALID_REPORT_PATH")
    service=next(m["service"] for m in load(context["policy_root"])["modules"] if m["id"]==context["module"])
    if not (value.startswith("/tmp/campus-reports/") or value.startswith("/workspace/backend/"+service+"/target/surefire-reports/")):
        raise PolicyError("REPORT_PATH_OUTSIDE_TEST_OUTPUT")
    if path.suffix not in (".json",".xml"):raise PolicyError("REPORT_FORMAT_REQUIRED")
    return value

def record(root,args,execute=subprocess.run):
    worktree=Path(args.worktree).resolve();ctx=read(worktree/".campus/task.local");member=ctx["member"]
    stage=args.stage
    if stage=="mutation" and ctx.get("mode")!="mutation":raise PolicyError("MUTATION_REQUIRES_ISOLATED_COPY")
    if ctx.get("mode")=="mutation" and stage!="mutation":raise PolicyError("MUTATION_COPY_IS_TEST_ONLY")
    command=list(args.test_command)
    if command and command[0]=="--":command=command[1:]
    allowed=("npm","node") if member["layer"]=="frontend" else ("mvn","java")
    if not command or command[0] not in allowed:raise PolicyError("COMMAND_OUTSIDE_LAYER")
    catalogue={q["id"]:q for q in read(Path(ctx["policy_root"])/"spec/requirements.json")["requirements"]}
    scenario=getattr(args,"scenario",None)
    pilot_scope=resolve_pilot(ctx["policy_root"],member,ctx["module"],scenario,args.requirement) if scenario else None
    module_run=args.requirement==ctx["module"]
    if module_run and stage not in ("baseline","regression","check"):raise PolicyError("MODULE_RUN_STAGE_INVALID")
    if not module_run and (args.requirement not in catalogue or catalogue[args.requirement]["module"]!=ctx["module"] or member["layer"] not in catalogue[args.requirement]["layers"]):raise PolicyError("REQUIREMENT_SCOPE_MISMATCH")
    output=Path(args.output).resolve()
    permitted=[Path(ctx["policy_root"]).resolve()/".campus",worktree/".campus"]
    if not any(output.is_relative_to(folder) for folder in permitted):raise PolicyError("RECEIPTS_MUST_STAY_IN_PRIVATE_CAMPUS_DIRECTORY")
    data=read(output) if output.exists() else {}
    validate_receipt_scope(data,pilot_scope)
    reports=[report_path(p,ctx) for p in (args.report or [])]
    if stage!="check" and not reports:raise PolicyError("ACTUAL_TEST_REPORT_REQUIRED")
    if len(reports)!=len(set(reports)):raise PolicyError("DUPLICATE_REPORT_PATH")
    run_id=str(uuid.uuid4());directory=output.parent/"runs"/run_id
    directory.mkdir(parents=True)
    source=source_snapshot(worktree,ctx,directory)
    if stage=="mutation":
        parent=Path(ctx["parent_worktree"]);parent_context=read(parent/".campus/task.local")
        if source_snapshot(parent,parent_context)["sha256"]!=ctx["parent_source_sha256"]:raise PolicyError("PARENT_CHANGED_DURING_MUTATION")
        source["parent_sha256"]=ctx["parent_source_sha256"]
    project=sandbox_project(ctx.get("policy_root",root),member,ctx["module"],ctx["base"],ctx.get("instance",""))
    compose=worktree/".campus/sandbox.local.json"
    prefix=docker(root)+["compose","-p",project,"-f",docker_path(compose),"exec","-T","runner"]
    if reports:
        execute(prefix+["mkdir","-p","/tmp/campus-reports"],check=True,capture_output=True)
        # Delete only validated report files in this task container, never arbitrary source or host paths.
        execute(prefix+["rm","-f","--",*reports],check=True,capture_output=True)
    started=now()
    try:
        process=execute(prefix+["timeout","--signal=TERM","--kill-after=5s","900",*command],capture_output=True,timeout=930)
        stdout=process.stdout.decode("utf-8",errors="replace");stderr=process.stderr.decode("utf-8",errors="replace");code=process.returncode
    except subprocess.TimeoutExpired as error:
        save(directory/"interrupted.local",{"run_id":run_id,"stage":stage,"started_at":started,"finished_at":now(),"result":"ERROR","reason":"TIMEOUT"})
        raise PolicyError("TEST_RUN_TIMEOUT") from error
    (directory/"stdout.txt").write_bytes(stdout.encode("utf-8"));(directory/"stderr.txt").write_bytes(stderr.encode("utf-8"))
    log=(stdout+stderr)[-200000:];parsed=[];report_entries=[]
    item={"run_id":run_id,"command":command,"exit_code":code,"log":log,"sha256":hashlib.sha256(log.encode()).hexdigest(),"expected_assertion":args.assertion,"source":source,"started_at":started,"finished_at":now(),"kind":args.kind or ("build" if stage=="check" else "component" if member["layer"]=="frontend" else "unit"),"verification_scope":"isolated"}
    try:
        for index,path in enumerate(reports):
            report=execute(prefix+["cat","--",path],capture_output=True,timeout=15)
            if report.returncode or len(report.stdout)>4_000_000:raise PolicyError("TEST_REPORT_MISSING_OR_TOO_LARGE")
            body=report.stdout;copy=directory/("report-"+str(index)+PurePosixPath(path).suffix);copy.write_bytes(body)
            parsed.append(parse_report(body,"vitest" if path.endswith(".json") else "junit"))
            report_entries.append({"path":path,"sha256":hashlib.sha256(body).hexdigest(),"archive":str(copy)})
        if reports:
            item["test_result"]=merge_reports(parsed,args.require_test or [])
            item["reports"]=report_entries
            witnesses="\n".join(case["id"]+" "+case["result"]+"\n"+case.get("details","") for case in item["test_result"]["cases"])
            log=(log+"\n[PARSED_TEST_REPORT]\n"+witnesses)[-200000:]
            item["log"]=log;item["sha256"]=hashlib.sha256(log.encode()).hexdigest()
            if stage=="red" and code==0:
                validate_result(item["test_result"],"baseline")
                stage="baseline";item["existing_behavior"]=True
            else:validate_result(item["test_result"],stage)
        expected=1 if stage in ("red","mutation") else 0
        if code!=expected:raise PolicyError("UNEXPECTED_TEST_EXIT: "+str(code))
        if not log.strip() and stage=="check":
            log="[PROCESS_EXIT] 0; build command produced no stdout/stderr";item["log"]=log;item["sha256"]=hashlib.sha256(log.encode()).hexdigest()
        if not log.strip():raise PolicyError("EMPTY_RUN_LOG")
        if stage in ("red","mutation") and (not args.assertion or args.assertion not in log):raise PolicyError("FAILURE_NOT_OBSERVED")
        item["result"]="FAIL" if expected else "PASS"
    except PolicyError as error:
        item["result"]="ERROR";item["reason"]=str(error);save(directory/"run.local",item)
        raise
    save(directory/"run.local",item)
    if pilot_scope:
        current=resolve_pilot(ctx["policy_root"],member,ctx["module"],scenario,args.requirement)
        if current["source"]["sha256"]!=pilot_scope["source"]["sha256"]:raise PolicyError("PILOT_SOURCE_CHANGED_DURING_RUN")
        data["scope"]="scenario_pilot"
        receipt=data.setdefault("scenarios",{}).setdefault(scenario,{**pilot_scope,"version":2,"stages":{}})
        receipt["completion_mode"]="baseline_satisfied" if item.get("existing_behavior") else args.completion_mode or receipt.get("completion_mode","tdd")
        receipt["stages"][stage]=item
        if item.get("existing_behavior"):receipt["stages"]["green"]=item
    elif module_run:data.setdefault("module",{})[stage]=item
    else:
        receipt=data.setdefault("requirements",{}).setdefault(args.requirement,{})
        receipt["version"]=2
        receipt["completion_mode"]="baseline_satisfied" if item.get("existing_behavior") else args.completion_mode or receipt.get("completion_mode","tdd")
        receipt[stage]=item
        if item.get("existing_behavior"):receipt["green"]=item
    save(output,data)
    print(json.dumps({"run_id":run_id,"stage":stage,"result":item["result"],"counts":item.get("test_result",{}).get("counts"),"receipt":str(output),**({"scope":"scenario_pilot","scenario":scenario,"parent_requirement":args.requirement,"status":"partial"} if pilot_scope else {})},ensure_ascii=False))

def mutation(root,args):
    parent=Path(args.worktree).resolve();context=read(parent/".campus/task.local")
    if context.get("mode")=="mutation":raise PolicyError("NESTED_MUTATION_COPY_FORBIDDEN")
    paths=guard(parent,load(context["policy_root"]),context["member"],context["module"],context["base"])
    identifier=uuid.uuid4().hex
    target=Path(context["policy_root"])/".campus/mutations"/identifier/"workspace"
    if not target.resolve().is_relative_to(Path(context["policy_root"]).resolve()/".campus/mutations"):raise PolicyError("MUTATION_PATH_ESCAPE")
    target.parent.mkdir(parents=True)
    snapshot=source_snapshot(parent,context)
    patch=git(parent,"diff","--binary","--no-renames",context["base"])
    git(parent,"worktree","add","--detach",str(target),context["base"])
    if patch:
        process=subprocess.run(["git","-c","safe.directory="+str(target),"-C",str(target),"apply","--binary","-"],input=patch,capture_output=True)
        if process.returncode:raise PolicyError("MUTATION_COPY_PATCH_FAILED: preserve the copy for review")
    for name in git(parent,"ls-files","--others","--exclude-standard","-z").decode().split("\0"):
        if name:
            if name not in paths:raise PolicyError("UNAUTHORIZED_UNTRACKED_INPUT")
            dest=target/name;dest.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(parent/name,dest)
    child={**context,"mode":"mutation","instance":"mutation-"+identifier[:12],"parent_worktree":str(parent),"parent_source_sha256":snapshot["sha256"]}
    save(target/".campus/task.local",child)
    print(json.dumps({"worktree":str(target),"mode":"mutation","parent_source_sha256":snapshot["sha256"]}))
