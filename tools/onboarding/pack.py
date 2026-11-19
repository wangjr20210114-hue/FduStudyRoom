"""Package six private member kits from a reviewed commit. Never ship coordinator secrets."""
import argparse
import hashlib
import json
import os
import secrets
import tempfile
import zipfile
from pathlib import Path
from join import BASE, SLOTS, KIT, save, read


def pack(bundle, private, desktop):
    private.mkdir(parents=True,exist_ok=True, mode=0o700)
    desktop.mkdir(parents=True,exist_ok=True)
    codes = private / "slots.local"
    if not codes.exists():
        with codes.open("x",encoding="utf-8") as f:
            os.chmod(codes,0o600)
            json.dump({slot:secrets.token_urlsafe(32) for slot in SLOTS}, f)
    tokens = read(codes)
    if set(tokens) != set(SLOTS) or len(set(tokens.values())) != 6:
        raise ValueError("Missing or duplicated slot access codes.")
    profiles = []
    for slot, module in SLOTS.items():
        token = tokens[slot]
        profiles.append({"id":slot,"modules":[module],"layer":"fullstack","tokenHash":hashlib.sha256(token.encode()).hexdigest()})
        with tempfile.NamedTemporaryFile(dir=desktop,delete=False) as f:
            path=Path(f.name)
        with zipfile.ZipFile(path,"w",zipfile.ZIP_DEFLATED) as zipout:
            for file in ("join.py","start.cmd","start.command"):
                entry=zipfile.ZipInfo(file)
                entry.create_system=3
                entry.external_attr=(0o100755 if file=="start.command" else 0o100600)<<16
                data=(KIT/file).read_bytes().replace(b"\r\n",b"\n")
                zipout.writestr(entry,data.replace(b"\n",b"\r\n") if file.endswith(".cmd") else data)
            zipout.write(bundle,"source.bundle")
            zipout.write(KIT/"campus-join/SKILL.md",".agents/skills/campus-join/SKILL.md")
            zipout.writestr("member.json",json.dumps({"slot":slot,"access_code":token}))
            zipout.writestr("先读我.txt",f"你的分工是 {slot} / {module}。请单独保管这个包。解压后 Windows 打开 start.cmd，macOS 在终端运行 bash start.command。已有 Codex 的成员也可以在 IDE 打开解压目录，输入 $campus-join 帮我入组。用自己的 GitHub、Codex 账号登录。系统若提示重启，完成后再次运行即可。项目目录为用户主目录下的 FduStudyRoom/{slot}。")
        os.replace(path,desktop/f"FduStudyRoom-{slot}-{module}.zip")
    save(private/"profiles.json",{"baseline":BASE,"members":profiles})


if __name__=="__main__":
    parser=argparse.ArgumentParser()
    parser.add_argument("bundle",type=Path)
    parser.add_argument("private",type=Path)
    parser.add_argument("output",type=Path)
    args=parser.parse_args()
    pack(args.bundle,args.private,args.output)
