import copy,hashlib,json,shutil,sys,tempfile,unittest
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2];sys.path.insert(0,str(ROOT/"tools/team"))
from policy import PolicyError,git,evidence_valid
from test_reports import parse_report,merge_reports,validate_result
from workflow import *
from recording import mutation
from types import SimpleNamespace
from contextlib import redirect_stdout
import io

class ReportTests(unittest.TestCase):
 def test_real_counts_come_from_cases_not_success_flag(self):
  body=json.dumps({"success":True,"numTotalTests":1,"testResults":[{"name":"a.test.ts","assertionResults":[{"fullName":"creates room","status":"passed"}]}]})
  result=merge_reports([parse_report(body,"vitest")],["a.test.ts::creates room"])
  self.assertTrue(validate_result(result,"green"))
  with self.assertRaises(PolicyError):validate_result(merge_reports([parse_report('{"success":true,"numTotalTests":0,"testResults":[]}',"vitest")]),"green")
 def test_false_summary_and_skipped_are_rejected(self):
  with self.assertRaises(PolicyError):parse_report('{"numTotalTests":5,"testResults":[]}',"vitest")
  result=merge_reports([parse_report('<testsuite tests="1"><testcase classname="Room" name="saves"><skipped/></testcase></testsuite>',"junit")])
  with self.assertRaises(PolicyError):validate_result(result,"green")
 def test_junit_error_is_not_a_behavioral_red(self):
  body='<testsuite tests="1"><testcase classname="Room" name="saves"><error>driver failed</error></testcase></testsuite>'
  with self.assertRaises(PolicyError):validate_result(merge_reports([parse_report(body,"junit")]),"red")
 def test_red_must_fail_a_required_case_and_all_required_cases_must_run(self):
  body='<testsuite tests="2"><testcase classname="Room" name="required"/><testcase classname="Room" name="extra"><failure>wrong</failure></testcase></testsuite>'
  result=merge_reports([parse_report(body,"junit")],["Room#required"])
  with self.assertRaises(PolicyError):validate_result(result,"red")
  result["required_tests"]=["Room#missing"]
  with self.assertRaises(PolicyError):validate_result(result,"green")
 def test_duplicate_case_cannot_inflate_coverage(self):
  report=parse_report('<testsuite tests="1"><testcase classname="Room" name="saves"/></testsuite>',"junit")
  with self.assertRaises(PolicyError):merge_reports([report,report])

class WorkflowTests(unittest.TestCase):
 def setUp(self):
  self.temp=tempfile.TemporaryDirectory();self.root=Path(self.temp.name)
  (self.root/"spec").mkdir();shutil.copyfile(ROOT/"spec/modules.json",self.root/"spec/modules.json")
  self.q={"id":"RES-01","module":"M02","title":"Fixture only","layers":["frontend"],"source":{"kind":"user","note":"isolated test fixture"},"acceptance":["save a room"],"state":"pending","execution_owner":"member"}
  save(self.root/"spec/requirements.json",{"requirements":[self.q]})
  for name in SKILLS:
   p=self.root/"skills"/name/"SKILL.md";p.parent.mkdir(parents=True);p.write_text("fixture "+name)
  (self.root/".gitignore").write_text(".campus/\n")
  (self.root/"frontend/src/modules/resource").mkdir(parents=True)
  (self.root/"frontend/src/modules/resource/Room.vue").write_text("<template>green</template>\n")
  git(self.root,"init");git(self.root,"config","user.name","Isolated fixture");git(self.root,"config","user.email","fixture@example.invalid");git(self.root,"add",".");git(self.root,"commit","-m","isolated fixture baseline")
  self.base=git(self.root,"rev-parse","HEAD").decode().strip()
  self.member={"id":"frontend-a","layer":"frontend","modules":["M02","M03"],"baseline":self.base}
  self.state=open_workflow(self.root,self.member,[self.q])
  self.work=Path(self.state["modules"]["M02"]["worktree"])
  git(self.root,"worktree","add","--detach",str(self.work),self.base)
  save(self.work/".campus/task.local",{"member":self.member,"module":"M02","base":self.base,"policy_root":str(self.root)})
 def tearDown(self):self.temp.cleanup()
 def analysis(self):
  return {"version":2,"module":"M02","layer":"frontend","requirements":[{"id":"RES-01","source":self.q["source"],"acceptance":self.q["acceptance"],"scenarios":[{"id":"RES-01.S01","covers":[1],"preconditions":["test account"],"actions":["save"],"expected":["save a room"],"objects":[{"key":"/rooms","status":"existing","evidence":"fixture routes"}],"depends_on":[],"cells":[{"id":"RES-01.S01#1","criterion":1,"basis":"save a room","kind":"component","test_ids":["Room#saves"],"expected":"room saved","sample":"one room","observation":"row","comparison":"exact name"}]}]}]}
 def test_resume_and_completed_requirement_skip(self):
  again=open_workflow(self.root,self.member,[self.q])
  self.assertEqual(self.state["id"],again["id"])
  self.assertEqual("workspace",next_action(again)["stage"])
  done=open_workflow(self.root,self.member,[{**self.q,"state":"accepted"}])
  self.assertEqual("development_complete",next_action(done)["stage"])
 def test_wrong_baseline_and_module_are_rejected(self):
  with self.assertRaises(PolicyError):open_workflow(self.root,{**self.member,"baseline":"f"*40},[self.q])
  with self.assertRaises(PolicyError):open_workflow(self.root,self.member,[self.q],"M05")
 def test_coverage_and_expectation_cannot_drift(self):
  value=self.analysis()
  mapping=validate_analysis(value,{"RES-01":self.q},self.member,"M02",["RES-01"],testing=True)
  self.assertEqual({"Room#saves"},mapping["RES-01"])
  value["requirements"][0]["scenarios"][0]["cells"][0]["basis"]="invented rule"
  with self.assertRaises(PolicyError):validate_analysis(value,{"RES-01":self.q},self.member,"M02",["RES-01"],testing=True)
 def test_missing_criterion_and_dependency_cycle_are_rejected(self):
  value=self.analysis();value["requirements"][0]["scenarios"][0]["covers"]=[]
  with self.assertRaises(PolicyError):validate_analysis(value,{"RES-01":self.q},self.member,"M02",["RES-01"])
  value=self.analysis();value["requirements"][0]["scenarios"][0]["depends_on"]=["RES-01.S01"]
  with self.assertRaises(PolicyError):validate_analysis(value,{"RES-01":self.q},self.member,"M02",["RES-01"])
 def test_checkpoint_cannot_jump_to_tdd_or_claim_human_review(self):
  for stage in ("tdd","approved"):
   with self.assertRaises(PolicyError):checkpoint(self.root,self.state["id"],"M02",stage)
  state=checkpoint(self.root,self.state["id"],"M02","workspace")
  self.assertEqual("baseline",next_action(state)["stage"])
 def test_source_change_invalidates_current_test_proof(self):
  context=read(self.work/".campus/task.local");before=source_snapshot(self.work,context)
  (self.work/"frontend/src/modules/resource/Room.vue").write_text("<template>changed</template>\n")
  after=source_snapshot(self.work,context)
  self.assertNotEqual(before["sha256"],after["sha256"])
  with self.assertRaises(PolicyError):validate_run({"run_id":"fixture","source":before,"exit_code":0,"log":"ok","sha256":digest(b"ok")}, "check",after,reports=False)
 def test_waiting_module_does_not_block_other_assigned_module(self):
  state=read(state_path(self.root,self.state["id"]));state["modules"]["M02"]["submission"]={"id":"fixture","state":"DRAFT"};state["modules"]["M03"]["targets"]=["M1-01"]
  self.assertEqual("M03",next_action(state)["module"])
 def test_external_block_preserves_reason_and_releases_other_work(self):
  state=block(self.root,self.state["id"],"M02","RES-01","external","no API","authorized endpoint reachable")
  self.assertEqual("blocked",next_action(state)["stage"])
  self.assertEqual("no API",state["modules"]["M02"]["blocked"]["RES-01"]["reason"])
 def test_pilot_engine_change_invalidates_workflow_fingerprint(self):
  engine=self.root/"tools/team/pilot.py";engine.parent.mkdir(parents=True,exist_ok=True);engine.write_text("pilot changed")
  with self.assertRaisesRegex(PolicyError,"WORKFLOW_SKILL_VERSION_CHANGED"):
   open_workflow(self.root,self.member,[self.q])
 def test_skill_change_requires_explicit_upgrade(self):
  (self.root/"skills/campus-test/SKILL.md").write_text("changed fixture")
  with self.assertRaises(PolicyError):open_workflow(self.root,self.member,[self.q])
 def test_mutation_copy_is_independent_and_preserves_parent_inputs(self):
  original=self.work/"frontend/src/modules/resource/Room.vue"
  original.write_text("<template>current green</template>\n")
  with redirect_stdout(io.StringIO()) as output:mutation(self.root,SimpleNamespace(worktree=str(self.work)))
  clone=Path(json.loads(output.getvalue())["worktree"])
  self.assertEqual(original.read_bytes(),(clone/"frontend/src/modules/resource/Room.vue").read_bytes())
  (clone/"frontend/src/modules/resource/Room.vue").write_text("<template>mutant</template>\n")
  self.assertIn("current green",original.read_text())
  self.assertEqual("mutation",read(clone/".campus/task.local")["mode"])
  self.assertEqual(self.base,git(self.work,"rev-parse","HEAD").decode().strip())

 def receipt(self,source,negative=False):
  log="fixture assertion failed" if negative else "fixture tests passed"
  source={**source,"parent_sha256":source["sha256"],"sha256":digest(b"fixture mutant"),"product_sha256":digest(b"fixture mutant product")} if negative else source
  return {"run_id":"fixture-"+uuid.uuid4().hex,"started_at":now(),"source":source,"exit_code":1 if negative else 0,"log":log,"sha256":digest(log.encode()),"command":["npm","test"],"expected_assertion":"fixture assertion","test_result":merge_reports([parse_report('<testsuite tests="1"><testcase classname="Room" name="saves">'+('<failure/>' if negative else '')+'</testcase></testsuite>',"junit")],["Room#saves"])}
 def test_full_artifact_pipeline_upload_wait_and_verified_resume(self):
  identifier=self.state["id"];context=read(self.work/".campus/task.local")
  state=checkpoint(self.root,identifier,"M02","workspace")
  source=source_snapshot(self.work,context);artifact=self.work/".campus/tdd.local"
  proof={"module":{"baseline":self.receipt(source)}}
  save(artifact,proof);checkpoint(self.root,identifier,"M02","baseline",artifact)
  save(analysis_path(self.root,state,"M02"),self.analysis())
  checkpoint(self.root,identifier,"M02","requirements");checkpoint(self.root,identifier,"M02","test-plan")
  proof["requirements"]={"RES-01":{"version":2,"completion_mode":"baseline_satisfied","baseline":self.receipt(source),"green":self.receipt(source),"mutation":self.receipt(source,True)}}
  proof["module"].update(check=self.receipt(source))
  save(artifact,proof)
  checkpoint(self.root,identifier,"M02","tdd",artifact)
  state=checkpoint(self.root,identifier,"M02","regression",artifact)
  self.assertEqual(proof["requirements"]["RES-01"]["green"]["run_id"],state["modules"]["M02"]["checkpoints"]["regression"]["regression_reused_run_id"])
  self.assertEqual("upload",next_action(state)["stage"])
  before_upload(self.root,identifier,"M02",self.work,artifact,["RES-01"])
  row={"id":str(uuid.uuid4()),"state":"DRAFT","module_id":"M02","member_id":"frontend-a"}
  submitted(self.root,identifier,"M02",row)
  self.assertEqual("waiting",next_action(read(state_path(self.root,identifier)))["stage"])
  row["state"]="REJECTED"
  state=refresh_submissions(self.root,state,[row])
  with self.assertRaisesRegex(PolicyError,"FRESH_TEST_EVIDENCE_REQUIRED_AFTER_REVIEW"):
   checkpoint(self.root,identifier,"M02","tdd",artifact)
  proof["requirements"]["RES-01"].update(green=self.receipt(source),mutation=self.receipt(source,True))
  proof["module"]["check"]=self.receipt(source);save(artifact,proof)
  checkpoint(self.root,identifier,"M02","tdd",artifact);checkpoint(self.root,identifier,"M02","regression",artifact)
  row={**row,"id":str(uuid.uuid4()),"state":"DRAFT"};submitted(self.root,identifier,"M02",row)
  row["state"]="VERIFIED"
  state=refresh_submissions(self.root,state,[row])
  self.assertEqual("development_complete",next_action(state)["stage"])
 def test_recorder_clears_stale_reports_instead_of_reusing_success(self):
  from recording import record
  from unittest.mock import patch
  from subprocess import CompletedProcess
  report="/tmp/campus-reports/room.json";storage={report:b'{"numTotalTests":1,"testResults":[{"name":"room.test.ts","assertionResults":[{"fullName":"saves","status":"passed"}]}]}'}
  args=SimpleNamespace(worktree=str(self.work),stage="green",test_command=["npm","test"],requirement="RES-01",output=str(self.work/".campus/tdd.local"),report=[report],require_test=["room.test.ts::saves"],assertion="",kind="component",completion_mode="tdd")
  def execute(argv,**kwargs):
   if "rm" in argv:storage.clear();return CompletedProcess(argv,0,b"",b"")
   if "cat" in argv:return CompletedProcess(argv,1,b"",b"missing fresh report")
   return CompletedProcess(argv,0,b"fixture command completed",b"")
  with patch("recording.docker",return_value=["fixture-docker"]),self.assertRaises(PolicyError):
   record(self.root,args,execute)
  self.assertFalse(Path(args.output).exists())
  runs=list((self.work/".campus/runs").glob("*/run.local"))
  self.assertEqual("ERROR",read(runs[0])["result"])

 def recorder(self,stage,negative=False,completion_mode=None):
  from recording import record
  from unittest.mock import patch
  from subprocess import CompletedProcess
  path="/tmp/campus-reports/room.json"
  body=json.dumps({"numTotalTests":1,"testResults":[{"name":"room.test.ts","assertionResults":[{"fullName":"saves","status":"failed" if negative else "passed","failureMessages":["fixture behavior assertion failed"] if negative else []}]}]}).encode()
  args=SimpleNamespace(worktree=str(self.work),stage=stage,test_command=["npm","test"],requirement="M02" if stage=="check" else "RES-01",output=str(self.work/".campus/tdd.local"),report=[] if stage=="check" else [path],require_test=["room.test.ts::saves"],assertion="fixture behavior assertion",kind="component",completion_mode=completion_mode)
  def execute(argv,**kwargs):
   return CompletedProcess(argv,0 if "cat" in argv or "timeout" not in argv or not negative else 1,body if "cat" in argv else b"",b"")
  with patch("recording.docker",return_value=["fixture-docker"]),redirect_stdout(io.StringIO()):record(self.root,args,execute)
  return read(args.output)
 def test_json_only_assertion_is_captured_from_fresh_report(self):
  value=self.recorder("red",negative=True)
  self.assertIn("fixture behavior assertion failed",value["requirements"]["RES-01"]["red"]["log"])
 def test_existing_behavior_is_classified_without_fabricating_red(self):
  value=self.recorder("red")
  receipt=value["requirements"]["RES-01"]
  self.assertEqual("baseline_satisfied",receipt["completion_mode"])
  self.assertNotIn("red",receipt)
  self.assertEqual(receipt["baseline"]["run_id"],receipt["green"]["run_id"])
  value=self.recorder("green")
  self.assertEqual("baseline_satisfied",value["requirements"]["RES-01"]["completion_mode"])
 def test_successful_quiet_build_is_recorded_as_process_exit(self):
  value=self.recorder("check")
  self.assertEqual(0,value["module"]["check"]["exit_code"])
  self.assertIn("[PROCESS_EXIT]",value["module"]["check"]["log"])
 def test_mutation_must_correspond_to_same_tests_and_green_input(self):
  source=source_snapshot(self.work,read(self.work/".campus/task.local"))
  value={"version":2,"completion_mode":"baseline_satisfied","baseline":self.receipt(source),"green":self.receipt(source),"mutation":self.receipt(source,True)}
  self.assertTrue(evidence_valid(value,strict=True))
  value["mutation"]["source"]["parent_sha256"]="unrelated"
  with self.assertRaises(PolicyError):evidence_valid(value,strict=True)
  value["mutation"]=self.receipt(source,True);value["mutation"]["command"]=["npm","run","different"]
  with self.assertRaises(PolicyError):evidence_valid(value,strict=True)

if __name__=="__main__":unittest.main()
