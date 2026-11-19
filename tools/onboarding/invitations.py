"""Coordinator-only bridge between authenticated slot registrations and GitHub invitations."""
import argparse
import json
import re
import subprocess
from pathlib import Path
import sys
sys.path.insert(0,str(Path(__file__).resolve().parent))
from join import REPO, SLOTS, buffer


def gh(*args):
    output = subprocess.check_output(["gh","api","--hostname","github.com",*args],text=True,encoding="utf-8")
    return json.loads(output) if output.strip() else None


def sync(owner_file):
    owner = json.loads(owner_file.read_text(encoding="utf-8-sig"))
    admin = buffer("/api/me",owner["code"])
    if admin.get("admin") is not True or gh("user")["login"].casefold() != REPO.split("/")[0].casefold():
        raise RuntimeError("请在协调人的电脑使用仓库所有者账号运行。")
    prefix = "repos/"+REPO
    rows = buffer("/api/team/github",owner["code"])
    if not rows:
        print("已核对，当前没有待登记成员。")
        return
    members = {entry["id"] for page in gh("--paginate","--slurp",prefix+"/collaborators") for entry in page if entry.get("permissions",{}).get("push")}
    pending = {entry["invitee"]["id"] for page in gh("--paginate","--slurp",prefix+"/invitations") for entry in page if entry.get("invitee")}
    for record in rows:
        slot, login, uid = record["member_id"],record["github_login"],record["github_id"]
        if slot not in SLOTS or not re.fullmatch(r"[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,37}[a-zA-Z0-9])?",login):
            raise RuntimeError("登记数据不符合六人分工。")
        if uid in members or uid in pending:
            continue
        if gh("users/"+login)["id"] != uid:
            raise RuntimeError("GitHub 账号已改名，请该成员重新登记。")
        result = gh("--method","PUT",prefix+"/collaborators/"+login,"-f","permission=push")
        if result and result.get("invitee",{}).get("id") != uid:
            raise RuntimeError("邀请接收人不匹配，请检查。")
        print(f"{slot} 号成员的仓库邀请已处理。")
    print(f"已核对 {len(rows)} 个已登记名额。")


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("owner",type=Path)
    sync(parser.parse_args().owner)
