#!/usr/bin/env python3
"""Build inputs pinned by SHA256. No JitPack, credentials, or run-time model downloads."""
from pathlib import Path
import hashlib,shutil,subprocess,zipfile
ROOT=Path(__file__).resolve().parents[1];cache=ROOT/'work/ocr';cache.mkdir(parents=True,exist_ok=True)
def download(url,path,digest):
 if not path.exists() or hashlib.sha256(path.read_bytes()).hexdigest()!=digest:
  tmp=path.with_suffix(path.suffix+'.download');subprocess.run(['curl','-fsSL','--retry','2',url,'-o',str(tmp)],check=True)
  if hashlib.sha256(tmp.read_bytes()).hexdigest()!=digest:raise RuntimeError('OCR dependency checksum mismatch: '+str(path))
  tmp.replace(path)
archive=cache/'tesseract4android-4.9.0.zip'
download('https://codeload.github.com/adaptech-cz/Tesseract4Android/zip/refs/tags/4.9.0',archive,'bdf48ebf4bd84802f1578d2e4bc7a5b728b2d47387bc02f967be084a5678334a')
source=cache/'Tesseract4Android-4.9.0'
if not (source/'tesseract4android/src/main/cpp/CMakeLists.txt').exists():
 with zipfile.ZipFile(archive) as z:z.extractall(cache)
assets=ROOT/'app/src/main/assets/tessdata';assets.mkdir(parents=True,exist_ok=True)
models={'eng':'7d4322bd2a7749724879683fc3912cb542f19906c83bcc1a52132556427170b2','chi_sim':'a5fcb6f0db1e1d6d8522f39db4e848f05984669172e584e8d76b6b3141e1f730'}
for lang,digest in models.items():download('https://raw.githubusercontent.com/tesseract-ocr/tessdata_fast/87416418657359cb625c412a48b6e1d6d41c29bd/'+lang+'.traineddata',assets/(lang+'.traineddata'),digest)
generated=ROOT/'work/ocr-generated/cz/adaptech/tesseract4android';generated.mkdir(parents=True,exist_ok=True)
(generated/'BuildConfig.java').write_text('package cz.adaptech.tesseract4android; public final class BuildConfig { public static final String FLAVOR="standard"; }\n')
print('Verified Tesseract4Android 4.9.0 / Tesseract 5.5.1 and English/Chinese offline models')
