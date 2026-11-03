#!/usr/bin/env python3
"""Apply an exact, public APK signature-block delta. Contains no signing key.
The cloud-built ZIP entries remain identical; a fixed APK can be re-signed locally
with the original private key without ever sending that key to GitHub.
"""
import base64,gzip,hashlib,io,json,sys
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
def sha(data):return hashlib.sha256(data).hexdigest()
def source_hash():
 h=hashlib.sha256()
 files=[ROOT/'app/build.gradle',*(ROOT/'app/src/main').rglob('*')]
 for p in sorted(files):
  if p.is_file() and 'jniLibs' not in p.parts:h.update(str(p.relative_to(ROOT)).encode());h.update(b'\0');h.update(p.read_bytes());h.update(b'\0')
 return h.hexdigest()
if len(sys.argv)!=5:sys.exit('signature_patch.py make|apply INPUT.apk SIGNED.apk PATCH.json')
mode,original,signed,patch=sys.argv[1:];original=Path(original);signed=Path(signed);patch=Path(patch)
if mode=='make':
 a=original.read_bytes();b=signed.read_bytes();start=0
 while start<min(len(a),len(b)) and a[start]==b[start]:start+=1
 end=0
 while end<min(len(a),len(b))-start and a[len(a)-end-1]==b[len(b)-end-1]:end+=1
 payload=b[start:len(b)-end if end else len(b)]
 if len(payload)>2000000:sys.exit('Changes exceed signing-block delta limit')
 patch.parent.mkdir(parents=True,exist_ok=True)
 patch.write_text(json.dumps({'original':sha(a),'signed':sha(b),'source':source_hash(),'prefix':start,'suffix':end,'gzip_base64':base64.b64encode(gzip.compress(payload,mtime=0)).decode()},indent=2)+'\n')
 print(json.dumps({'input_bytes':len(a),'signed_bytes':len(b),'delta_bytes':len(payload),'patch_bytes':patch.stat().st_size}))
elif mode=='apply':
 spec=json.loads(patch.read_text())
 if spec['source']!=source_hash():sys.exit('Source changed since the verified cloud build: a new signature patch is required')
 if signed.exists() and sha(signed.read_bytes())==spec['signed']:print('Verified existing compatible APK');sys.exit(0)
 a=original.read_bytes()
 if sha(a)!=spec['original']:sys.exit('Cloud-built APK checksum mismatch')
 start,end=spec['prefix'],spec['suffix']
 if not isinstance(start,int) or not isinstance(end,int) or min(start,end)<0 or start+end>len(a):sys.exit('Invalid delta bounds')
 encoded=spec['gzip_base64']
 if len(encoded)>4000000:sys.exit('Delta too large')
 with gzip.GzipFile(fileobj=io.BytesIO(base64.b64decode(encoded,validate=True))) as z:payload=z.read(2000001)
 if len(payload)>2000000:sys.exit('Expanded delta too large')
 b=a[:start]+payload+(a[len(a)-end:] if end else b'')
 if sha(b)!=spec['signed']:sys.exit('Output checksum mismatch')
 signed.write_bytes(b);print('Reconstructed original-key signed APK:',spec['signed'])
else:sys.exit('Unknown mode')
