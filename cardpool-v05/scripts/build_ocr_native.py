#!/usr/bin/env python3
from pathlib import Path
import os,subprocess,shutil
root=Path(__file__).resolve().parents[1]
sdk=Path(os.environ.get('ANDROID_HOME',os.environ.get('ANDROID_SDK_ROOT','/nonexistent')))
ndk=Path(os.environ.get('ANDROID_NDK_HOME',os.environ.get('ANDROID_NDK_ROOT','/nonexistent')))
cmake=sdk/'cmake/3.22.1/bin/cmake';ninja=sdk/'cmake/3.22.1/bin/ninja'
if not cmake.exists():raise SystemExit('Install Android SDK package cmake;3.22.1')
source=root/'work/ocr/Tesseract4Android-4.9.0/tesseract4android/src/main/cpp'
for abi in ['arm64-v8a','armeabi-v7a','x86_64','x86']:
 build=root/'work/ocr-build'/abi
 subprocess.run([str(cmake),'-S',str(source),'-B',str(build),'-G','Ninja','-DCMAKE_MAKE_PROGRAM='+str(ninja),'-DCMAKE_TOOLCHAIN_FILE='+str(ndk/'build/cmake/android.toolchain.cmake'),'-DANDROID_ABI='+abi,'-DANDROID_PLATFORM=android-26','-DANDROID_STL=c++_static','-DANDROID_SUPPORT_FLEXIBLE_PAGE_SIZES=ON','-DCMAKE_BUILD_TYPE=Release'],check=True)
 subprocess.run([str(cmake),'--build',str(build),'--parallel','3'],check=True)
 out=root/'app/src/main/jniLibs'/abi;out.mkdir(parents=True,exist_ok=True)
 for library in build.rglob('*.so'):
  target=out/library.name;shutil.copy2(library,target);subprocess.run([str(ndk/'toolchains/llvm/prebuilt/linux-x86_64/bin/llvm-strip'),'--strip-unneeded',str(target)],check=True)
 print('Built verified native OCR for',abi)
