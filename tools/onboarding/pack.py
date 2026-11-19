"""Package six private member kits from a reviewed commit. Never ship coordinator secrets."""
import argparse
import hashlib
import json
import os
import secrets
import shutil
import subprocess
import tempfile
import zipfile
from pathlib import Path
from join import BASE, ENTRY, SLOTS, KIT, save, read


def pack(bundle, private, desktop):
    private.mkdir(parents=True,exist_ok=True, mode=0o700)
    desktop.mkdir(parents=True,exist_ok=True)
    codes = private / "slots.local"
    if not codes.exists():
        if any(desktop.glob("FduStudyRoom-*-M*.zip")):
            raise ValueError("现有分发包已有成员，请先定位原来的私有配置目录。")
        with codes.open("x",encoding="utf-8") as f:
            os.chmod(codes,0o600)
            json.dump({slot:secrets.token_urlsafe(32) for slot in SLOTS}, f)
    tokens = read(codes)
    if set(tokens) != set(SLOTS) or len(set(tokens.values())) != 6:
        raise ValueError("Missing or duplicated slot access codes.")
    for slot, module in SLOTS.items():
        previous = desktop/f"FduStudyRoom-{slot}-{module}.zip"
        if previous.exists():
            with zipfile.ZipFile(previous) as archive:
                if json.loads(archive.read("member.json")) != {"slot":slot,"access_code":tokens[slot]}:
                    raise ValueError("成员包与既有名额不符，请检查 " + slot)
    profiles = []
    for slot, module in SLOTS.items():
        token = tokens[slot]
        profiles.append({"id":slot,"modules":[module],"layer":"fullstack","tokenHash":hashlib.sha256(token.encode()).hexdigest()})
        with tempfile.NamedTemporaryFile(dir=desktop,delete=False) as f:
            path=Path(f.name)
        with zipfile.ZipFile(path,"w",zipfile.ZIP_DEFLATED) as zipout:
            for file in ("SKILL.md","references/host.md"):
                zipout.write(KIT/"campus-join"/file,(ENTRY/file).as_posix())
            zipout.write(KIT/"join.py",(ENTRY/"scripts/join.py").as_posix())
            zipout.write(bundle,"source.bundle")
            zipout.writestr("member.json",json.dumps({"slot":slot,"access_code":token}))
            zipout.writestr("先读我.txt",f"你的分工是 {slot} / {module}。个人包只给本人使用。\n"
                "解压，用已启用并登录 Codex 的 IDE 打开包含本说明的整个文件夹。\n"
                "在 Codex 对话中输入：$campus-join 帮我入组并继续我的未完成需求。\n"
                "首次按提示确认自己的 GitHub 账号和系统安装。重启后在同一目录说：$campus-join 继续。\n"
                "如果技能列表尚未刷新，在对话中说：读取 .agents/skills/campus-join/SKILL.md，帮我入组。\n"
                f"以后也可以直接打开用户主目录下的 FduStudyRoom/{slot}，用同一个技能查看待办、开发、交付或启动本地产品。\n")
        os.replace(path,desktop/f"FduStudyRoom-{slot}-{module}.zip")
    save(private/"profiles.json",{"baseline":BASE,"members":profiles})


if __name__=="__main__":
    parser=argparse.ArgumentParser()
    parser.add_argument("bundle",type=Path)
    parser.add_argument("private",type=Path)
    parser.add_argument("output",type=Path)
    args=parser.parse_args()
    head = subprocess.check_output(["git","bundle","list-heads",str(args.bundle),"HEAD"],text=True).split()
    if head != [BASE,"HEAD"]:
        parser.error("源码包 HEAD 与成员冻结基线不符。")
    pack(args.bundle,args.private,args.output)
    shutil.copyfile(KIT.parents[1]/"docs/22-team-onboarding.md",args.output/"组长分发说明.md")
