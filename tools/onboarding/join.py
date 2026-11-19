"""Bootstrap a member checkout from the assigned private distribution."""
import argparse
import json
import os
import shutil
import subprocess
import sys
import tempfile
import time
import urllib.error
import urllib.request
from pathlib import Path

HERE = Path(__file__).resolve().parent
ENTRY = Path(".agents/skills/campus-join")
KIT = HERE.parents[3] if HERE.name == "scripts" and HERE.parent.name == "campus-join" else HERE
BASE = "2142af7a714377764fa32118fa99a92a8551a4d4"
REPO = "wangjr20210114-hue/FduStudyRoom"
ORIGIN = "https://github.com/" + REPO + ".git"
SITE = "https://gitbuffer.seatraces.com"
SLOTS = dict(zip("abcdef", ("M01","M02","M03","M04","M05","M06")))
ROOT_HOME = Path.home() / "FduStudyRoom"


def command(args, *, capture=False, check=True, cwd=None, env=None):
    return subprocess.run([str(x) for x in args], cwd=cwd, env=env, check=check,
                          text=True, encoding="utf-8", errors="replace", capture_output=capture)


def value(args, **kw):
    return command(args, capture=True, **kw).stdout.strip()


def read(path):
    return json.loads(path.read_text(encoding="utf-8-sig"))


def save(path, data):
    path.parent.mkdir(parents=True, exist_ok=True, mode=0o700)
    with tempfile.NamedTemporaryFile(mode="w", encoding="utf-8", dir=path.parent,
                                     delete=False) as f:
        json.dump(data, f, ensure_ascii=False)
    os.replace(f.name, path)


def buffer(path, token, layer=None, payload=None):
    headers = {"Authorization": "Bearer " + token}
    if layer:
        headers["X-Campus-Layer"] = layer
    data = None
    if payload is not None:
        headers["Content-Type"] = "application/json"
        data = json.dumps(payload).encode()
    request = urllib.request.Request(SITE + path, data=data, headers=headers)
    class SameOrigin(urllib.request.HTTPRedirectHandler):
        def redirect_request(self, *args, **kwargs):
            raise RuntimeError("缓冲站地址返回重定向，请协调人检查站点。")
    with urllib.request.build_opener(SameOrigin()).open(request, timeout=25) as response:
        return json.load(response)


def checkout(root):
    if not root.exists():
        root.parent.mkdir(parents=True, exist_ok=True)
        with tempfile.TemporaryDirectory(dir=root.parent) as tmp:
            source = Path(tmp) / "source"
            bundle = KIT / "source.bundle"
            command(["git", "-c", "core.autocrlf=false", "clone", "--no-checkout",
                     str(bundle) if bundle.exists() else ORIGIN, source])
            command(["git", "config", "core.autocrlf", "false"], cwd=source)
            command(["git", "config", "core.longpaths", "true"], cwd=source)
            if not bundle.exists():
                command(["git", "fetch", "origin", BASE], cwd=source)
            command(["git", "checkout", "--detach", BASE], cwd=source)
            command(["git", "remote", "set-url", "origin", ORIGIN], cwd=source)
            source.rename(root)
    if value(["git", "rev-parse", "HEAD"], cwd=root) != BASE \
        or value(["git","remote","get-url","origin"], cwd=root) != ORIGIN:
        raise RuntimeError("已有目录的仓库或版本不同，原目录已保留：" + str(root))
    if value(["git", "status", "--porcelain", "--untracked-files=no"], cwd=root):
        raise RuntimeError("基线目录有已跟踪的改动，请先核对。")
    return root


def profile(slot, token, layer):
    me = buffer("/api/me", token, layer)
    if me.get("id") != slot or me.get("modules") != [SLOTS[slot]] or me.get("admin") is not False \
            or me.get("layer") != layer or me.get("baseline") != BASE:
        raise RuntimeError("缓冲站返回的身份、模块、层或基线不匹配。")
    return me


class MemberAction(RuntimeError):
    """An IDE-visible step the member can resume in the same conversation."""


def account(saved, requested=None):
    result = command(["gh","api","--hostname","github.com","user"], capture=True, check=False)
    if result.returncode:
        if command(["gh","auth","status","--hostname","github.com"], capture=True, check=False).returncode:
            raise MemberAction("GITHUB_LOGIN_REQUIRED")
        raise RuntimeError("读取 GitHub 账号失败，请检查 GitHub 连通性。")
    me = json.loads(result.stdout)
    typed = requested or saved.get("github")
    if not typed:
        raise MemberAction("CONFIRM_GITHUB_ACCOUNT " + me["login"])
    if typed.casefold() != me["login"].casefold() or saved.get("github_id", me["id"]) != me["id"]:
        raise MemberAction("GITHUB_ACCOUNT_MISMATCH 当前登录 " + me["login"])
    return {"id":me["id"],"login":me["login"]}


def repository_access():
    repo_path = "repos/" + REPO
    def writable():
        return json.loads(value(["gh","api","--hostname","github.com",repo_path])).get("permissions",{}).get("push") is True
    for attempt in range(8):
        if writable():
            return True
        pages = json.loads(value(["gh","api","--hostname","github.com","--paginate","--slurp","user/repository_invitations"]))
        for invitation in (row for page in pages for row in page):
            if invitation["repository"]["full_name"].casefold() == REPO.casefold():
                command(["gh","api","--hostname","github.com","--method","PATCH",
                         "user/repository_invitations/"+str(int(invitation["id"])),"--silent"])
                break
        if attempt < 7:
            time.sleep(10)
    return writable()


def github_transport(root):
    """Use an existing system proxy only if direct Git fails, within this member's checkouts."""
    def reaches_github(extra=()):
        try:
            result = subprocess.run(["git",*extra,"ls-remote","--exit-code",ORIGIN,"HEAD"],
                                    cwd=root,capture_output=True,timeout=25,
                                    env={**os.environ,"GIT_TERMINAL_PROMPT":"0","GCM_INTERACTIVE":"Never"})
            return result.returncode == 0
        except subprocess.TimeoutExpired:
            return False

    if reaches_github():
        return
    from urllib.parse import urlsplit
    proxies = urllib.request.getproxies()
    for proxy in dict.fromkeys(proxies.get(k) for k in ("http","https","all")):
        if not proxy:
            continue
        endpoint = urlsplit(proxy)
        if endpoint.scheme not in ("http","https","socks5","socks5h") or not endpoint.hostname or endpoint.username or endpoint.password:
            continue
        key = "http.https://github.com/.proxy"
        if not reaches_github(("-c",key+"="+proxy)):
            continue
        fragment=root/".campus/github-network.gitconfig"
        fragment.parent.mkdir(exist_ok=True)
        command(["git","config","--file",fragment,key,proxy])
        include="includeIf.gitdir/i:"+root.resolve().as_posix().rstrip("/")+"/.path"
        saved=value(["git","config","--global","--get-all",include],check=False).splitlines()
        if fragment.as_posix() not in saved:
            command(["git","config","--global","--add",include,fragment.as_posix()])
        if reaches_github():
            return
    raise RuntimeError("Git 暂时连不到 GitHub。检查本机网络或系统代理，再重新启动。")



def install_project_skills(root, env):
    target = root/".agents/skills"
    args = [sys.executable, root/"scripts/team.py", "install-skills", "--skills-dir"]
    if command(args+[target,"--check"],capture=True,check=False,env=env).returncode == 0:
        return
    target.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(dir=target) as tmp:
        staged = Path(tmp)/"skills"
        command(args+[staged],env=env)
        for folder in staged.iterdir():
            existing = target/folder.name
            if existing.exists():
                for item in folder.rglob("*"):
                    if item.is_file():
                        local = existing/item.relative_to(folder)
                        if not local.is_file() or local.read_bytes() != item.read_bytes():
                            raise RuntimeError("已有项目技能内容不同，请核对 " + folder.name)
        for folder in staged.iterdir():
            if not (target/folder.name).exists():
                folder.rename(target/folder.name)
    command(args+[target,"--check"],env=env)


def development_tools(root):
    sys.path.insert(0,str(root/"tools/team"))
    from environment import tools, docker, docker_path, PG
    _, env, notices = tools(root, install=True)
    for note in notices:
        print(note)
    install_project_skills(root, env)
    dc = docker(root)
    server = value(dc+["info","--format","{{.ServerVersion}}"])
    if int(server.split(".")[0]) < 28:
        raise RuntimeError("请更新 Docker Desktop，项目隔离需要 Docker Engine 28 或以上。")
    command(dc+["compose","version"])
    tag = "fdu-campus-ci:baseline"
    label = "cn.seatraces.studyroom.source"
    inspect = command(dc+["image","inspect","--format",'{{ index .Config.Labels "'+label+'" }}',tag],capture=True,check=False)
    if inspect.returncode or inspect.stdout.strip() != BASE:
        if not inspect.returncode:
            known = {"sha256:46d8b5b79839d42ae390053db0fa2056ac665e34c0c1602788df9764dfbace66","sha256:3a08bbc379b62c2c1351d35e9b9b87cde9dd5f5809cf0a3cff7198ff7c5816d5"}
            if value(dc+["image","inspect","--format","{{.Id}}",tag]) not in known:
                raise RuntimeError("本机已有不同来源的测试镜像，请协调人核对后再切换。")
        else:
            context = docker_path(root)
            command(dc+["build","--platform","linux/amd64","-f",docker_path(root/"infra/team/ci.Dockerfile"),
                        "-t","fdu-campus-ci:core-"+BASE[:12],context])
            command(dc+["build","--platform","linux/amd64","--build-arg","BASE_IMAGE=fdu-campus-ci:core-"+BASE[:12],
                        "--label",label+"="+BASE,"-f",docker_path(root/"infra/team/browser.Dockerfile"),"-t",tag,context])
    command(dc+["run","--rm","--network","none","--platform","linux/amd64",tag,"sh","-ec",
                'node --version && java -version && "$CAMPUS_TEST_BROWSER" --version'])
    for image in (PG,"axllent/mailpit@sha256:23fe39e81bde596a918000b3135c0f85dc8bb4c9ea6be274b051143f3e300387"):
        if command(dc+["image","inspect",image],capture=True,check=False).returncode:
            command(dc+["pull",image])
    return env


def member_context():
    if (KIT/"member.json").is_file():
        kit = read(KIT/"member.json")
        slot, token = kit["slot"], kit["access_code"]
        if slot not in SLOTS:
            raise RuntimeError("无法识别成员包代号。")
        root = ROOT_HOME/slot
    else:
        root = KIT
        binding = root/".campus/member.local"
        if not binding.is_file():
            raise RuntimeError("请在 IDE 中打开已解压的个人包或已入组的项目目录。")
        saved = read(binding)
        slot, token = saved["identity"], saved["token"]
    if slot not in SLOTS or not isinstance(token,str) or len(token)<32:
        raise RuntimeError("分配文件无效，请重新解压你自己的成员包。")
    binding = root/".campus/member.local"
    saved = read(binding) if binding.exists() else {}
    if saved and (saved.get("identity") != slot or saved.get("buffer_url") != SITE or saved.get("token") != token):
        raise RuntimeError("同一目录已有其他成员配置。")
    return root, slot, token, saved


def install_entry(root):
    source, target = KIT/ENTRY, root/ENTRY
    if source != target:
        if target.exists():
            for name in ("SKILL.md", "references/host.md", "scripts/join.py"):
                if not (target/name).is_file() or (target/name).read_bytes() != (source/name).read_bytes():
                    raise RuntimeError("项目中的入组技能与个人包不同，请核对版本。")
        else:
            target.parent.mkdir(parents=True, exist_ok=True)
            with tempfile.TemporaryDirectory(dir=target.parent) as tmp:
                stage = Path(tmp)/"entry"
                shutil.copytree(source, stage, ignore=shutil.ignore_patterns("__pycache__"))
                stage.rename(target)
    exclude = root/".git/info/exclude"
    rules = exclude.read_text(encoding="utf-8") if exclude.is_file() else ""
    if "/.agents/skills/" not in rules.splitlines():
        with exclude.open("a", encoding="utf-8") as stream:
            stream.write("\n/.agents/skills/\n")


def main(argv=None):
    os.environ["PYTHONUTF8"] = "1"
    parser = argparse.ArgumentParser()
    parser.add_argument("action", choices=("status","prepare"))
    parser.add_argument("--github", help="GitHub username confirmed in the IDE")
    args = parser.parse_args(argv)
    root, slot, token, saved = member_context()
    ready = root/".campus/join-ready.local"
    if args.action == "status":
        stamp = read(ready) if ready.is_file() else {}
        prepared = bool(saved) and stamp == {"slot":slot,"github_id":saved.get("github_id"),"baseline":BASE}
        print(json.dumps({"project":str(root),"member":slot,"module":SLOTS[slot],"github":saved.get("github"),
                          "prepared":prepared},ensure_ascii=False))
        return
    checkout(root)
    github = account(saved, args.github)
    github_transport(root)
    for layer in ("backend","frontend"):
        profile(slot,token,layer)
    buffer("/api/team/github",token,"backend",github)
    save(root/".campus/member.local",{"identity":slot,"buffer_url":SITE,"token":token,"github":github["login"],"github_id":github["id"]})
    install_entry(root)
    env = development_tools(root)
    for layer in ("backend","frontend"):
        command([sys.executable,root/"scripts/team.py","workflow",slot,"--module",SLOTS[slot],
                 "--layer",layer,"--preview"],env=env)
    if not repository_access():
        raise MemberAction("INVITATION_PENDING 已登记账号，等待仓库邀请。")
    command(["gh","auth","setup-git","--hostname","github.com"])
    save(ready,{"slot":slot,"github_id":github["id"],"baseline":BASE})
    print(f"成员 {slot} · {SLOTS[slot]} 项目就绪。项目目录 {root}。",flush=True)


if __name__ == "__main__":
    try:
        main()
    except MemberAction as error:
        print(error, file=sys.stderr)
        sys.exit(2)
    except (RuntimeError, ValueError, OSError, subprocess.CalledProcessError) as error:
        print(f"启动未完成：{error}",file=sys.stderr)
        sys.exit(1)
