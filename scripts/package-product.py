#!/usr/bin/env python3
"""Package tested application outputs with a complete SHA256 manifest."""
import argparse,hashlib,json,tarfile,tempfile,shutil,re
from pathlib import Path

def package(root,output,commit,sequence,branch):
    if not re.fullmatch('[0-9a-f]{40}',commit) or sequence<0:raise ValueError('Invalid release identity')
    root=Path(root)
    with tempfile.TemporaryDirectory() as temp:
        stage=Path(temp)
        for service,target in [('resource-service','resource.jar'),('workflow-service','workflow.jar')]:
            jars=list((root/'backend'/service/'target').glob('*.jar'))
            if len(jars)!=1:raise ValueError('Exactly one executable JAR required: '+service)
            shutil.copyfile(jars[0],stage/target)
        shutil.copytree(root/'frontend/dist',stage/'web')
        (stage/'web/version.json').write_text(json.dumps({'commit':commit,'sequence':sequence,'branch':branch})+'\n',encoding='utf-8')
        files={p.relative_to(stage).as_posix():hashlib.sha256(p.read_bytes()).hexdigest() for p in stage.rglob('*') if p.is_file()}
        (stage/'manifest.json').write_text(json.dumps({'commit':commit,'sequence':sequence,'branch':branch,'files':files},ensure_ascii=False)+'\n',encoding='utf-8')
        with tarfile.open(output,'w:gz') as archive:
            for p in sorted(stage.rglob('*')):
                if p.is_file():archive.add(p,arcname=p.relative_to(stage).as_posix(),recursive=False)

if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('--root',default='.');p.add_argument('--output',required=True);p.add_argument('--commit',required=True);p.add_argument('--sequence',type=int,required=True);p.add_argument('--branch',required=True);a=p.parse_args()
    package(a.root,a.output,a.commit,a.sequence,a.branch)
