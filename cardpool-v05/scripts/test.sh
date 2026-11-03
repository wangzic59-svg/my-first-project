#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
mkdir -p work/tests
c++ -std=c++17 -Wall -Wextra -Werror tests/engine_test.cpp -o work/tests/engine_test
work/tests/engine_test
TASK_JAVA_HOME="${JAVA_HOME:-$(java -XshowSettings:properties -version 2>&1 | sed -n 's/^ *java.home = //p')}"
c++ -std=c++17 -O2 -shared -fPIC -I"$TASK_JAVA_HOME/include" -I"$TASK_JAVA_HOME/include/linux" app/src/main/cpp/jni.cpp -o work/tests/libcardpool.so
javac -d work/tests tests/android/content/*.java tests/android/os/*.java app/src/main/java/com/example/cardpool/{GameState,DataSource,DataManager,NativeEngine,Prediction,Calculator,CardPoolTracker,CardPoolRepository}.java tests/{V05Test,CalculatorTest,TrackerTest,QuickEditTest}.java
for test in CalculatorTest TrackerTest QuickEditTest V05Test; do
 java -ea -Djava.library.path=work/tests -cp work/tests "$test"
done
