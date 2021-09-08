echo "build parameter 1: --%1--"
echo "build parameter 2: --%2--"
call mvn --version
@echo on
echo here
call mvn releaser:release
echo there
