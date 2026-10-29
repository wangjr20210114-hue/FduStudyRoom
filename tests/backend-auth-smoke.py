"""Exercise T04 against a dedicated development database; mutates the seeded student account."""
import argparse
import http.cookiejar
import json
import os
import urllib.error
import urllib.request

class Client:
    def __init__(self, base_url):
        self.base = base_url.rstrip("/")
        self.jar = http.cookiejar.CookieJar()
        self.opener = urllib.request.build_opener(urllib.request.ProxyHandler({}), urllib.request.HTTPCookieProcessor(self.jar))

    def call(self, method, path, body=None, headers=None):
        data = json.dumps(body).encode() if body is not None else (b"" if method in ("POST", "PATCH") else None)
        request = urllib.request.Request(self.base + path, data=data, method=method, headers=headers or {})
        if body is not None:
            request.add_header("Content-Type", "application/json")
        try:
            response = self.opener.open(request, timeout=10)
        except urllib.error.HTTPError as error:
            response = error
        with response:
            raw = response.read()
            return response.status, json.loads(raw) if raw else None, response.headers

    def csrf(self):
        status, body, _ = self.call("GET", "/api/auth/csrf")
        assert status == 200
        return {body["headerName"]: body["token"]}

    def login(self, account):
        return self.call("POST", "/api/auth/dev/login", {"account": account}, self.csrf())

    def session(self):
        return next(cookie.value for cookie in self.jar if cookie.name == "FDU_SESSION")

def run(base_url, production_url=None):
    checks = []
    def check(condition, name):
        assert condition, name
        checks.append(name)

    guest = Client(base_url)
    status, config, _ = guest.call("GET", "/api/auth/config")
    check(status == 200 and config["mode"] == "development" and config["available"], "explicit development configuration")
    check(len(config["developmentAccounts"]) == 3, "three development role choices")
    status, error, headers = guest.call("GET", "/api/auth/me", headers={"X-User-Id": "forged", "X-Role": "SYSTEM_ADMIN"})
    check(status == 401 and error["traceId"] == headers["X-Request-Id"], "anonymous access and forged identity headers rejected")
    check(guest.call("POST", "/api/auth/dev/login", {"account": "student"})[0] == 403, "login requires CSRF")
    bad_csrf = guest.csrf()
    bad_csrf = {key: "invalid" for key in bad_csrf}
    check(guest.call("POST", "/api/auth/dev/login", {"account": "student"}, bad_csrf)[0] == 403, "invalid CSRF rejected")
    check(guest.call("POST", "/api/auth/dev/login", {"account": "student", "role": "SYSTEM_ADMIN"}, guest.csrf())[0] == 400, "browser cannot submit a role")

    student = Client(base_url)
    status, me, headers = student.login("student")
    check(status == 200 and me["role"] == "USER" and me["identityType"] == "STUDENT" and me["identitySource"] == "DEVELOPMENT", "student identity resolved by server")
    check("token" not in me and "HttpOnly" in headers["Set-Cookie"] and "SameSite=Lax" in headers["Set-Cookie"], "login credential only in HttpOnly cookie")
    check(student.call("GET", "/api/auth/me")[0] == 200, "current user request uses login cookie")
    check(student.call("GET", "/api/admin/users")[0] == 403, "student cannot list accounts")
    check(student.call("GET", "/api/admin/roles")[0] == 403, "student cannot list roles")

    for account in ("staff-user", "inactive", "disabled"):
        check(Client(base_url).login(account)[0] == 403, account + " rejected")
    status, maintainer, _ = Client(base_url).login("maintainer")
    check(status == 200 and maintainer["role"] == "MAINTAINER" and maintainer["identityType"] == "STAFF", "assigned staff maintainer can log in")

    admin = Client(base_url)
    status, me_admin, _ = admin.login("admin")
    check(status == 200 and me_admin["role"] == "SYSTEM_ADMIN", "assigned staff administrator can log in")
    status, roles, _ = admin.call("GET", "/api/admin/roles")
    check(status == 200 and len(roles) >= 3, "administrator can read roles")
    role_ids = {role["code"]: role["id"] for role in roles}

    def student_row():
        status, users, _ = admin.call("GET", "/api/admin/users")
        assert status == 200
        return next(row for row in users if row["loginName"] == "DEV-STUDENT")

    before = student_row()
    path = "/api/admin/users/" + before["id"] + "/access"
    body = {"roleId": role_ids["USER"], "enabled": False, "expectedAuthVersion": before["authVersion"]}
    check(student.call("PATCH", path, body, student.csrf())[0] == 403, "student cannot change account access")
    check(admin.call("PATCH", path, body)[0] == 403, "administrator writes require CSRF")
    old_cookie = student.session()
    check(admin.call("PATCH", path, body, admin.csrf())[0] == 204, "administrator can disable an account")
    check(student.call("GET", "/api/auth/me")[0] == 401, "access change revokes existing credentials")
    check(Client(base_url).login("student")[0] == 403, "disabled account cannot log in again")
    check(admin.call("PATCH", path, body, admin.csrf())[0] == 409, "stale update version rejected")
    after = student_row()
    body = {"roleId": role_ids["MAINTAINER"], "enabled": True, "expectedAuthVersion": after["authVersion"]}
    check(admin.call("PATCH", path, body, admin.csrf())[0] == 204, "administrator can assign another allowed role")
    status, changed, _ = student.login("student")
    check(status == 200 and changed["role"] == "MAINTAINER", "new login reads current role")
    after = student_row()
    body = {"roleId": role_ids["USER"], "enabled": True, "expectedAuthVersion": after["authVersion"]}
    check(admin.call("PATCH", path, body, admin.csrf())[0] == 204, "restore seeded student role")

    first, second = Client(base_url), Client(base_url)
    check(first.login("student")[0] == 200 and second.login("student")[0] == 200, "two sessions may coexist")
    token = second.session()
    check(first.call("POST", "/api/auth/logout", headers=first.csrf())[0] == 204, "logout succeeds with CSRF")
    check(second.call("GET", "/api/auth/me")[0] == 401, "logout revokes other sessions")
    replay = Client(base_url)
    status, _, _ = replay.call("GET", "/api/auth/me", headers={"Cookie": "FDU_SESSION=" + token})
    check(status == 401, "logged out cookie cannot be replayed")
    check(Client(base_url).call("GET", "/api/auth/me", headers={"Cookie": "FDU_SESSION=malformed"})[0] == 401, "malformed credential rejected")

    service_key = os.environ.get("CAMPUS_AUTH_SERVICE_KEY")
    check(guest.call("POST", "/internal/auth/introspect", headers={"Authorization": "Bearer " + old_cookie})[0] == 401, "internal introspection requires service authentication")
    if service_key:
        live = Client(base_url)
        assert live.login("student")[0] == 200
        status, identity, _ = guest.call("POST", "/internal/auth/introspect", headers={"X-Service-Key": service_key, "Authorization": "Bearer " + live.session()})
        check(status == 200 and identity["id"] == before["id"], "internal introspection verifies current identity")
        status, _, _ = guest.call("POST", "/internal/auth/introspect", headers={"X-Service-Key": service_key, "Authorization": "Bearer " + token})
        check(status == 401, "internal introspection rejects revoked credentials")
    check(guest.call("GET", "/api/auth/fudan/callback?ticket=untrusted")[0] == 503, "unconfigured UIS callback never authenticates")
    if production_url:
        prod = Client(production_url)
        status, config, _ = prod.call("GET", "/api/auth/config")
        check(status == 200 and config["mode"] == "fudan" and not config["available"] and not config["developmentAccounts"], "production reports UIS unavailable")
        check(prod.call("GET", "/api/auth/dev/login")[0] == 404, "production has no development controller")
        check(prod.call("GET", "/api/auth/me", headers={"Cookie": "__Host-FDU_SESSION=" + token})[0] == 401, "production rejects development credentials")
        _, _, headers = prod.call("GET", "/api/auth/csrf")
        check("__Host-FDU_CSRF=" in headers["Set-Cookie"] and "Secure" in headers["Set-Cookie"] and "HttpOnly" in headers["Set-Cookie"], "production CSRF cookie is secure and host scoped")
    print(json.dumps({"passed": len(checks), "checks": checks}, ensure_ascii=False, indent=2))

if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--base-url", required=True)
    parser.add_argument("--production-url")
    args = parser.parse_args()
    run(args.base_url, args.production_url)
