#!/usr/bin/env python3
import os,sys,subprocess
from pathlib import Path
root=Path(__file__).resolve().parents[1]
ndk=os.environ.get('ANDROID_NDK_HOME') or os.environ.get('ANDROID_NDK_ROOT')
if not ndk:
 sdk=Path(os.environ.get('ANDROID_HOME',os.environ.get('ANDROID_SDK_ROOT','/nonexistent')))
 candidates=sorted((sdk/'ndk').glob('*'))
 if candidates:ndk=str(candidates[-1])
if not ndk:sys.exit('Set ANDROID_NDK_HOME to an installed Android NDK r27+ directory')
bin=Path(ndk)/'toolchains/llvm/prebuilt/linux-x86_64/bin'
for abi,target in [('arm64-v8a','aarch64-linux-android26'),('armeabi-v7a','armv7a-linux-androideabi26'),('x86_64','x86_64-linux-android26'),('x86','i686-linux-android26')]:
 out=root/'app/src/main/jniLibs'/abi/'libcardpool.so';out.parent.mkdir(parents=True,exist_ok=True)
 subprocess.run([str(bin/(target+'-clang++')),'-std=c++17','-O2','-Wall','-Wextra','-Werror','-shared','-fPIC','-fexceptions','-static-libstdc++','-Wl,-z,max-page-size=16384','-Wl,-z,common-page-size=16384',str(root/'app/src/main/cpp/jni.cpp'),'-o',str(out)],check=True)
 subprocess.run([str(bin/'llvm-strip'),'--strip-unneeded',str(out)],check=True)
 print('Built',abi)
