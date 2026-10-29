"""Run against a dedicated password-auth test database. Creates and changes test accounts."""
import argparse
import base64
import hashlib
import hmac
import http.cookiejar
import json
import os
import time
import urllib.error
import urllib.request
import uuid

class Client:
    def __init__(self, base):
        self.base = base.rstrip("/")
        self.jar = http.cookiejar.CookieJar()
        self.opener = urllib.request.build_opener(urllib.request.ProxyHandler({}), urllib.request.HTTPCookieProcessor(self.jar))
    def request(self, path, method="GET", body=None, csrf=True, headers=None):
        request_headers = dict(headers or {})
        if method != "GET" and csrf:
            status, token, _ = self.request("/api/auth/csrf")
            assert status == 200
            request_headers[token["headerName"]] = token["token"]
        data = None if body is None else json.dumps(body).encode()
        if data is not None: request_headers["Content-Type"] = "application/json"
        request = urllib.request.Request(self.base + path, data=data, method=method, headers=request_headers)
        try: response = self.opener.open(request, timeout=15)
        except urllib.error.HTTPError as error: response = error
        with response:
            raw=response.read()
            return response.status, json.loads(raw) if raw else None, response.headers

def main():
    parser=argparse.ArgumentParser()
    parser.add_argument("--base-url",default="http://127.0.0.1:8080")
    parser.add_argument("--admin-login",default="admin")
    args=parser.parse_args()
    admin_password=os.environ.get("CAMPUS_SMOKE_ADMIN_PASSWORD")
    if not admin_password: raise SystemExit("Set CAMPUS_SMOKE_ADMIN_PASSWORD for a dedicated test administrator.")
    prefix="smoke-"+uuid.uuid4().hex[:10]
    password="Test-"+uuid.uuid4().hex[:18]
    user=Client(args.base_url);admin=Client(args.base_url)
    checks=[]
    def expect(condition,label):
        assert condition,label
        checks.append(label)

    status,config,_=user.request("/api/auth/config")
    expect(status==200 and config=={"mode":"password","available":True,"registrationOpen":True},"password mode and registration config")
    expect(user.request("/api/auth/me")[0]==401,"anonymous current user is rejected")
    expect(user.request("/api/admin/users")[0]==401,"anonymous management is rejected")
    expect(user.request("/api/auth/login","POST",{"loginName":prefix,"password":password},csrf=False)[0]==403,"login requires CSRF")
    expect(user.request("/api/auth/register","POST",{"loginName":prefix,"password":password},csrf=False)[0]==403,"registration requires CSRF")
    status,_,_=user.request("/api/auth/register","POST",{"loginName":prefix,"password":password,"role":"SYSTEM_ADMIN"})
    expect(status==400,"registration rejects a client-supplied role")
    expect(user.request("/api/auth/register","POST",{"loginName":"../bad","password":password})[0]==400,"invalid account format is rejected")
    status,error,_=user.request("/api/auth/register","POST",{"loginName":prefix,"password":"short"})
    expect(status==400 and error.get("code")=="INVALID_PASSWORD","registration explains invalid password length")
    status,error,_=user.request("/api/auth/login","POST",{"loginName":prefix,"password":"short"})
    expect(status==400 and error.get("code")=="INVALID_PASSWORD","login explains invalid password length")
    status,identity,headers=user.request("/api/auth/register","POST",{"loginName":prefix,"password":password})
    expect(status==201 and identity["role"]=="USER","registration creates an ordinary user")
    expect(not any("password" in key.lower() for key in identity),"credentials do not appear in the response")
    expect("identitySource" not in identity and "identityType" not in identity,"ordinary identity does not claim school verification")
    expect("HttpOnly" in " ".join(headers.get_all("Set-Cookie",[])),"login cookie is HttpOnly")
    expect(user.request("/api/auth/me")[1]["id"]==identity["id"],"registration establishes a usable session")
    expect(user.request("/api/admin/users")[0]==403,"ordinary user cannot manage accounts")
    second=Client(args.base_url)
    expect(second.request("/api/auth/register","POST",{"loginName":prefix.upper(),"password":password})[0]==409,"case-insensitive duplicate accounts are rejected")
    wrong=second.request("/api/auth/login","POST",{"loginName":prefix,"password":"Wrong-password-123"})
    missing=second.request("/api/auth/login","POST",{"loginName":prefix+"x","password":"Wrong-password-123"})
    expect(wrong[0]==missing[0]==401 and wrong[1]["code"]==missing[1]["code"]=="INVALID_CREDENTIALS","wrong and unknown credentials have the same failure")
    expect(second.request("/api/auth/login","POST",{"loginName":prefix.upper(),"password":password})[0]==200,"normalized account can log in")
    old_cookie="; ".join(cookie.name+"="+cookie.value for cookie in second.jar if "SESSION" in cookie.name)
    expect(user.request("/api/auth/logout","POST")[0]==204,"logout succeeds")
    expect(second.request("/api/auth/me")[0]==401,"logout invalidates another device")
    expect(second.request("/api/auth/login","POST",{"loginName":prefix,"password":password})[0]==200,"password login works after logout")
    expect(admin.request("/api/auth/login","POST",{"loginName":args.admin_login,"password":admin_password})[0]==200,"bootstrap administrator can log in")
    status,roles,_=admin.request("/api/admin/roles")
    expect(status==200,"administrator can read roles")
    maintainer=next(role for role in roles if role["code"]=="MAINTAINER")
    current=second.request("/api/auth/me")[1]
    access={"roleId":maintainer["id"],"enabled":True,"expectedAuthVersion":current["authVersion"]}
    expect(admin.request("/api/admin/users/"+identity["id"]+"/access","PATCH",access)[0]==204,"administrator grants maintenance role")
    expect(second.request("/api/auth/me")[0]==401,"role changes revoke the previous session")
    status,current,_=second.request("/api/auth/login","POST",{"loginName":prefix,"password":password})
    expect(status==200 and current["role"]=="MAINTAINER","assigned maintainer logs in with own password")
    access.update(enabled=False,expectedAuthVersion=current["authVersion"])
    expect(admin.request("/api/admin/users/"+identity["id"]+"/access","PATCH",access)[0]==204,"administrator can disable an account")
    expect(second.request("/api/auth/login","POST",{"loginName":prefix,"password":password})[0]==401,"disabled account cannot log in")
    users=admin.request("/api/admin/users")[1]
    expect(all(not any("password" in key.lower() for key in row) for row in users),"management responses never expose password hashes")
    for path in ["/api/auth/dev/login","/api/auth/fudan/login","/api/auth/fudan/callback"]:
        status,_,headers=Client(args.base_url).request(path)
        expect(status in (401,404) and not any("FDU_SESSION=" in h for h in headers.get_all("Set-Cookie",[])),"removed endpoint cannot authenticate: "+path)
    limiter=Client(args.base_url)
    codes=[limiter.request("/api/auth/login","POST",{"loginName":prefix+"-limit","password":"Wrong-password-123"})[0] for _ in range(11)]
    expect(codes[:10]==[401]*10 and codes[10]==429,"repeated password attempts are rate limited")
    print(json.dumps({"passed":len(checks),"checks":checks,"created_account":prefix},ensure_ascii=False))

if __name__=="__main__": main()
