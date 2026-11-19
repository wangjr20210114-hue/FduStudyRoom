"""Bootstrap a member checkout from the assigned private distribution."""
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

KIT = Path(__file__).resolve().parent
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


def account(saved):
    result = command(["gh","api","--hostname","github.com","user"], capture=True, check=False)
    if result.returncode:
        command(["gh","auth","login","--hostname","github.com","--git-protocol","https","--web"])
    me = json.loads(value(["gh","api","--hostname","github.com","user"]))
    typed = saved.get("github") or input(f"你的 GitHub 用户名（回车使用已登录的 {me['login']}）：").strip() or me["login"]
    if typed.casefold() != me["login"].casefold() or saved.get("github_id", me["id"]) != me["id"]:
        raise RuntimeError("填写账号与当前 GitHub 登录不同，请用 gh auth switch 切换自己的账号后重试。")
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


def development_tools(root):
    sys.path.insert(0,str(root/"tools/team"))
    from environment import tools, docker, docker_path, PG
    _, env, notices = tools(root, install=True)
    for note in notices:
        print(note)
    cli = shutil.which("codex.cmd" if os.name == "nt" else "codex", path=env["PATH"])
    private_prefix = root/".campus/codex"
    local_cli = private_prefix/("codex.cmd" if os.name == "nt" else "bin/codex")
    if not cli and not local_cli.is_file():
        npm = shutil.which("npm.cmd" if os.name == "nt" else "npm",path=env["PATH"])
        if not npm:
            raise RuntimeError("Node 24 已安装，但未找到配套 npm。")
        command([npm, "install","--prefix",private_prefix,"-g","@openai/codex@0.159.2"],env=env)
    cli = cli or str(local_cli)
    if command([cli,"login","status"],env=env,capture=True,check=False).returncode:
        command([cli,"login"],env=env)
    command([cli,"login","status"],env=env)
    team = [sys.executable,root/"scripts/team.py"]
    if command(team+["install-skills","--check"],capture=True,check=False,env=env).returncode:
        command(team+["install-skills"],env=env)
    command(team+["install-skills","--check"],env=env)
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
    command(dc+["pull",PG])
    return cli, env


def main():
    os.environ["PYTHONUTF8"]="1"
    kit = read(KIT/"member.json")
    slot, token = kit["slot"], kit["access_code"]
    if slot not in SLOTS or not isinstance(token,str) or len(token)<32:
        raise RuntimeError("分配文件无效，请重新解压你自己的成员包。")
    root = checkout(ROOT_HOME/slot)
    binding = root/".campus/member.local"
    saved = read(binding) if binding.exists() else {}
    if saved and (saved.get("identity") != slot or saved.get("buffer_url") != SITE or saved.get("token") != token):
        raise RuntimeError("同一目录已有其他成员配置。")
    github = account(saved)
    for layer in ("backend","frontend"):
        profile(slot,token,layer)
    buffer("/api/team/github",token,"backend",github)
    save(binding,{"identity":slot,"buffer_url":SITE,"token":token,"github":github["login"],"github_id":github["id"]})
    print("正在准备 Codex、五个项目技能和隔离测试环境。",flush=True)
    cli, env = development_tools(root)
    for layer in ("backend","frontend"):
        command([sys.executable,root/"scripts/team.py","workflow",slot,"--module",SLOTS[slot],
                 "--layer",layer,"--preview"],env=env)
    if not repository_access():
        raise RuntimeError("已登记账号，等待仓库邀请。稍后再次打开本程序即可接收。")
    command(["gh","auth","setup-git","--hostname","github.com"])
    print(f"成员 {slot} · {SLOTS[slot]} 项目就绪。IDE 打开 {root}。",flush=True)
    choice = input("回车打开 Codex 查看待办，输入 2 启动本地产品，输入 3 退出：").strip()
    if choice == "2":
        command([sys.executable,root/"scripts/team.py","build","--install","--start"],cwd=root,env=env)
    elif choice != "3":
        command([cli,"-C",root,f"$campus-workflow 我的身份是 {slot}，只查看本模块待办和已有交付证据。"],env=env)


if __name__ == "__main__":
    try:
        main()
    except (RuntimeError, ValueError, OSError, subprocess.CalledProcessError) as error:
        print(f"启动未完成：{error}",file=sys.stderr)
        sys.exit(1)
