package com.campus.buffer;
public class GateException extends RuntimeException {
 public final int status; public final String code;
 public GateException(int status,String code){super(code);this.status=status;this.code=code;}
}
