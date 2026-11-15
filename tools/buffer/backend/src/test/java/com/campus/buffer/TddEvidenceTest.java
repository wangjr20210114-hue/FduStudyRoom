package com.campus.buffer;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class TddEvidenceTest {
 Map<String,Object> run(boolean negative){
  String log=negative?"Assertion failed: occupied seat":"All tests passed";
  var value=new LinkedHashMap<String,Object>();
  value.put("run_id",UUID.randomUUID().toString());value.put("exit_code",negative?1:0);
  value.put("command",List.of("npm","test"));value.put("log",log);
  value.put("sha256",BufferSettings.sha(log.getBytes(StandardCharsets.UTF_8)));
  value.put("expected_assertion","occupied seat");
  value.put("source",Map.of("sha256","a".repeat(64),"product_sha256","b".repeat(64)));
  value.put("test_result",results(negative?"FAIL":"PASS"));
  return value;
 }
 Map<String,Object> results(String status){
  boolean skip=status.equals("SKIPPED");
  return Map.of("counts",Map.of("total",1,"executed",skip?0:1,"passed",status.equals("PASS")?1:0,"failed",status.equals("FAIL")?1:0,"errors",0,"skipped",skip?1:0),"cases",List.of(Map.of("id","seat-case","result",status)),"required_tests",List.of("seat-case"));
 }
 Map<String,Object> receipt(){var mutation=run(true);mutation.put("source",Map.of("sha256","c".repeat(64),"product_sha256","d".repeat(64),"parent_sha256","a".repeat(64)));return new LinkedHashMap<>(Map.of("version",2,"red",run(true),"green",run(false),"mutation",mutation));}
 @SuppressWarnings("unchecked") Map<String,Object> stage(Map<String,Object> data,String stage){return (Map<String,Object>)data.get(stage);}
 @Test void validVersionedTddPasses(){assertDoesNotThrow(()->TddEvidence.check(receipt(),List.of("RES-01")));}
 @Test void zeroExecutedTestsRejected(){
  var data=receipt();stage(data,"green").put("test_result",Map.of("counts",Map.of("total",0,"executed",0,"passed",0,"failed",0,"errors",0,"skipped",0),"cases",List.of(),"required_tests",List.of("seat-case")));
  assertEquals("ZERO_EXECUTED_TESTS",assertThrows(GateException.class,()->TddEvidence.check(data,List.of("RES-01"))).code);
 }
 @Test void skippedRequiredTestRejected(){var data=receipt();stage(data,"green").put("test_result",results("SKIPPED"));assertThrows(GateException.class,()->TddEvidence.check(data,List.of("RES-01")));}
 @Test void existingBehaviorNeedsUnchangedProduct(){
  var data=receipt();data.put("completion_mode","baseline_satisfied");data.remove("red");data.put("baseline",run(false));
  assertDoesNotThrow(()->TddEvidence.check(data,List.of("RES-01")));
  stage(data,"green").put("source",Map.of("sha256","a".repeat(64),"product_sha256","c".repeat(64)));
  assertEquals("BASELINE_SATISFIED_PRODUCT_CHANGED",assertThrows(GateException.class,()->TddEvidence.check(data,List.of("RES-01"))).code);
 }
 @Test void missingRequiredCaseRejected(){
  var data=receipt();var result=new LinkedHashMap<>(results("PASS"));result.put("required_tests",List.of("never-ran"));stage(data,"green").put("test_result",result);
  assertThrows(GateException.class,()->TddEvidence.check(data,List.of("RES-01")));
 }
 @Test void modifiedFailureLogRejected(){var data=receipt();stage(data,"red").put("log","different");assertThrows(GateException.class,()->TddEvidence.check(data,List.of("RES-01")));}
 @Test void unrelatedMutationCannotProveGreenBehavior(){
  var data=receipt();stage(data,"mutation").put("source",Map.of("sha256","c".repeat(64),"product_sha256","d".repeat(64),"parent_sha256","unrelated"));
  assertEquals("MUTATION_INPUT_MISMATCH",assertThrows(GateException.class,()->TddEvidence.check(data,List.of("RES-01"))).code);
 }
 @Test void mutationCannotUseDifferentTestCommand(){
  var data=receipt();stage(data,"mutation").put("command",List.of("npm","run","different"));
  assertEquals("MUTATION_ASSERTIONS_CHANGED",assertThrows(GateException.class,()->TddEvidence.check(data,List.of("RES-01"))).code);
 }
 @Test void legacyTddStillAccepted(){var data=receipt();data.remove("version");for(String stage:List.of("red","green","mutation")){stage(data,stage).remove("test_result");stage(data,stage).remove("source");stage(data,stage).remove("run_id");}assertDoesNotThrow(()->TddEvidence.check(data,List.of("RES-01")));}
 @Test void scenarioPilotCannotBecomeFormalReceipt(){
  var data=receipt();data.put("scope","scenario_pilot");
  assertEquals("PILOT_EVIDENCE_IS_NOT_FORMAL_DELIVERY",assertThrows(GateException.class,()->TddEvidence.check(data,List.of("RES-01"))).code);
 }
 @Test void extractedPilotReceiptCannotBecomeFormalRequirement(){
  var data=receipt();data.put("scope","scenario_pilot");
  assertEquals("PILOT_EVIDENCE_IS_NOT_FORMAL_DELIVERY",assertThrows(GateException.class,()->TddEvidence.check(Map.of("requirements",Map.of("RES-01",data)),List.of("RES-01"))).code);
 }
}
