"""Build an upload for the coordinator's authenticated Tencent terminal."""
import argparse
import hashlib
import io
import json
from pathlib import Path
import tarfile

def package(root, staging):
    files={"upgrade_buffer.py":root/"tools/onboarding/upgrade_buffer.py",
           "app.jar":staging/"build-src/tools/buffer/backend/target/buffer-service-0.1.0-SNAPSHOT.jar",
           "modules.json":root/"spec/modules.json","requirements.json":root/"spec/requirements.json",
           "profiles.json":staging/"profiles.json"}
    checksums={name:hashlib.sha256(path.read_bytes()).hexdigest() for name,path in files.items() if name!="upgrade_buffer.py"}
    out=staging/"fdu-team-buffer-20261119.tar.gz"
    with tarfile.open(out,"w:gz") as archive:
        for name,path in files.items():
            archive.add(path,arcname=name,recursive=False)
        data=json.dumps(checksums).encode()
        info=tarfile.TarInfo("checksums.json")
        info.size=len(data)
        archive.addfile(info,io.BytesIO(data))
    print(out,hashlib.sha256(out.read_bytes()).hexdigest())

if __name__=="__main__":
    parser=argparse.ArgumentParser()
    parser.add_argument("staging",type=Path)
    package(Path(__file__).resolve().parents[2],parser.parse_args().staging)
