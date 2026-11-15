package com.campus.buffer;
import java.nio.charset.StandardCharsets;
import java.util.*;
final class TddEvidence {
 private TddEvidence(){}
 static void fail(String code){throw new GateException(400,code);}
 @SuppressWarnings("unchecked") static void check(Map<String,Object> evidence,List<String> ids){
  if(evidence!=null&&"scenario_pilot".equals(evidence.get("scope")))fail("PILOT_EVIDENCE_IS_NOT_FORMAL_DELIVERY");
  if(evidence!=null&&evidence.get("requirements") instanceof Map<?,?>){
   var receipts=(Map<String,Map<String,Object>>)evidence.get("requirements");
   if(!receipts.keySet().equals(new HashSet<>(ids)))fail("TDD_REQUIREMENT_RECEIPTS_MISMATCH");
   for(var receipt:receipts.values())receipt(receipt);
  }else{if(ids.size()!=1)fail("PER_REQUIREMENT_TDD_RECEIPTS_REQUIRED");receipt(evidence);}
 }
 @SuppressWarnings("unchecked") static void receipt(Map<String,Object> evidence){
  if(evidence!=null&&"scenario_pilot".equals(evidence.get("scope")))fail("PILOT_EVIDENCE_IS_NOT_FORMAL_DELIVERY");
  if(evidence==null)fail("TDD_EVIDENCE_REQUIRED");
  String mode=Objects.toString(evidence.getOrDefault("completion_mode","tdd"));
  if(!Set.of("tdd","baseline_satisfied").contains(mode))fail("UNKNOWN_COMPLETION_MODE");
  String first=mode.equals("baseline_satisfied")?"baseline":"red";
  boolean strict=mode.equals("baseline_satisfied")||evidence.get("version") instanceof Number n&&n.intValue()==2;
  for(String stage:List.of(first,"green","mutation")){
   var item=(Map<String,Object>)evidence.get(stage);
   int expected=Set.of("red","mutation").contains(stage)?1:0;
   if(item==null||!(item.get("exit_code") instanceof Number code)||code.intValue()!=expected)fail("TDD_EXIT_INVALID");
   if(!(item.get("command") instanceof List<?> command)||command.isEmpty())fail("TDD_COMMAND_REQUIRED");
   String log=Objects.toString(item.get("log"),"");
   if(log.isBlank()||log.length()>200000||!BufferSettings.sha(log.getBytes(StandardCharsets.UTF_8)).equals(item.get("sha256")))fail("TDD_LOG_INVALID");
   if(expected==1){
    String assertion=Objects.toString(item.get("expected_assertion"),"");
    if(assertion.isBlank()||!log.contains(assertion))fail("TDD_ASSERTION_REQUIRED");
    if(List.of("compilation failure","command not found","connection refused","cannot find module").stream().anyMatch(log.toLowerCase()::contains))fail("INFRA_FAILURE_IS_NOT_RED");
   }
   if(strict){
    var source=(Map<String,Object>)item.get("source");
    if(source==null||Objects.toString(source.get("sha256"),"").isBlank()||Objects.toString(item.get("run_id"),"").isBlank())fail("VERSIONED_RUN_EVIDENCE_REQUIRED");
   }
   if(strict||item.containsKey("test_result"))result((Map<String,Object>)item.get("test_result"),expected==1);
  }
  var before=(Map<String,Object>)evidence.get(first);var green=(Map<String,Object>)evidence.get("green");
  if(!Objects.equals(before.get("command"),green.get("command")))fail("RED_GREEN_COMMAND_CHANGED");
  if(before.get("test_result") instanceof Map<?,?> a&&green.get("test_result") instanceof Map<?,?> b){
   if(!new HashSet<>((List<String>)a.get("required_tests")).equals(new HashSet<>((List<String>)b.get("required_tests"))))fail("RED_GREEN_ASSERTIONS_CHANGED");
  }
  if(strict){
   var mutation=(Map<String,Object>)evidence.get("mutation");
   var mutationResult=(Map<String,Object>)mutation.get("test_result");var greenResult=(Map<String,Object>)green.get("test_result");
   if(!Objects.equals(mutation.get("command"),green.get("command"))||!new HashSet<>((List<String>)mutationResult.get("required_tests")).equals(new HashSet<>((List<String>)greenResult.get("required_tests"))))fail("MUTATION_ASSERTIONS_CHANGED");
   var source=(Map<String,Object>)mutation.get("source");var parent=(Map<String,Object>)green.get("source");
   String product=Objects.toString(source.get("product_sha256"),"");
   if(!Objects.equals(source.get("parent_sha256"),parent.get("sha256"))||product.isBlank()||product.equals(parent.get("product_sha256")))fail("MUTATION_INPUT_MISMATCH");
  }
  if(mode.equals("baseline_satisfied")){
   var a=(Map<String,Object>)before.get("source");var b=(Map<String,Object>)green.get("source");
   String product=Objects.toString(a.get("product_sha256"),"");
   if(product.isBlank()||!product.equals(b.get("product_sha256")))fail("BASELINE_SATISFIED_PRODUCT_CHANGED");
  }
 }
 @SuppressWarnings("unchecked") static void result(Map<String,Object> result,boolean negative){
  if(result==null||!(result.get("cases") instanceof List<?>))fail("TEST_RESULT_REQUIRED");
  var cases=(List<Map<String,Object>>)result.get("cases");Map<String,String> byId=new HashMap<>();
  int passed=0,failed=0,errors=0,skipped=0;
  for(var item:cases){
   String id=Objects.toString(item.get("id"),""),status=Objects.toString(item.get("result"),"");
   if(id.isBlank()||byId.putIfAbsent(id,status)!=null)fail("DUPLICATE_OR_MISSING_TEST_ID");
   switch(status){case "PASS"->passed++;case "FAIL"->failed++;case "ERROR"->errors++;case "SKIPPED"->skipped++;default->fail("UNKNOWN_TEST_RESULT");}
  }
  int executed=passed+failed+errors;
  var counts=(Map<String,Object>)result.get("counts");
  var expected=Map.of("total",cases.size(),"executed",executed,"passed",passed,"failed",failed,"errors",errors,"skipped",skipped);
  if(counts==null||!counts.keySet().equals(expected.keySet()))fail("REPORT_COUNT_MISMATCH");
  for(var entry:expected.entrySet())if(!(counts.get(entry.getKey()) instanceof Number n)||n.intValue()!=entry.getValue())fail("REPORT_COUNT_MISMATCH");
  if(executed==0)fail("ZERO_EXECUTED_TESTS");
  if(errors>0||skipped>0)fail("ERROR_OR_SKIPPED_IS_NOT_VALID_TDD");
  var required=(List<String>)result.get("required_tests");
  if(required==null||required.isEmpty()||new HashSet<>(required).size()!=required.size()||!byId.keySet().containsAll(required))fail("REQUIRED_TEST_NOT_RUN");
  if(negative&&!required.stream().anyMatch(id->"FAIL".equals(byId.get(id))))fail("NO_REQUIRED_ASSERTION_FAILED");
  if(!negative&&(failed>0||required.stream().anyMatch(id->!"PASS".equals(byId.get(id)))))fail("GREEN_HAS_FAILURES");
 }
}
