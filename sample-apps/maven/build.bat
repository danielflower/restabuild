echo "build parameter 1: --%1--"
echo "build parameter 2: --%2--"
call mvn --version
call mvn releaser:release
