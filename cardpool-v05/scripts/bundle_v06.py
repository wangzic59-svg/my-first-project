#!/usr/bin/env python3
from pathlib import Path
import zipfile,hashlib
root=Path(__file__).resolve().parents[1];signed=root/'downloads/JCC_CardPool_0.6_compatible.apk'
if not signed.exists():raise SystemExit('Compatible APK not present')
source=root/'work/CardPool_V0.6_source.zip';source.parent.mkdir(exist_ok=True)
with zipfile.ZipFile(source,'w',zipfile.ZIP_DEFLATED) as z:
 for p in sorted(root.rglob('*')):
  if not p.is_file():continue
  rel=p.relative_to(root)
  if any(x in ('.gradle','build','work','jniLibs','downloads','tessdata') for x in rel.parts) or p.name=='local.properties' or p.suffix=='.keystore':continue
  z.write(p,'cardpool-v05/'+str(rel))
complete=root/'downloads/CardPool_V0.6_complete.zip'
with zipfile.ZipFile(complete,'w',zipfile.ZIP_DEFLATED) as z:
 z.write(signed,signed.name);z.write(source,source.name)
 for f in ['README.md','BUILD_REPORT.md','reports/ocr-accuracy.txt']:z.write(root/f,Path(f).name)
with zipfile.ZipFile(complete) as z:
 assert z.testzip() is None
 assert hashlib.sha256(z.read(signed.name)).digest()==hashlib.sha256(signed.read_bytes()).digest()
(root/'downloads/V06_SHA256SUMS').write_text(''.join(hashlib.sha256(p.read_bytes()).hexdigest()+'  '+p.name+'\n' for p in (signed,complete)))
print('Packaged',complete.name,complete.stat().st_size,'bytes; no private signing material')
