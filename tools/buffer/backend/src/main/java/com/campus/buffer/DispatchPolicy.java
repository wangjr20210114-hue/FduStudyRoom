package com.campus.buffer;
public final class DispatchPolicy {
 public enum Action { NONE, PRESSURE_FIRST, SCHEDULED }
 public static Action decide(boolean enabled,boolean pressure,boolean periodicDue,boolean pressureDue){
  if(!enabled)return Action.NONE;
  if(pressure)return pressureDue ? Action.PRESSURE_FIRST : Action.NONE;
  return periodicDue ? Action.SCHEDULED : Action.NONE;
 }
 private DispatchPolicy(){}
}
