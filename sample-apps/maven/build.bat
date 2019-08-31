echo "build parameter: %1 %2"
call "mvn --version"
call mvn releaser:release
