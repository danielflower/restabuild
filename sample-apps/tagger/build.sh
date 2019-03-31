git tag -a annnotated1 -m "Annotated tag 1"
git tag lightweight1

echo Hello > hello.txt
git add hello.txt
git commit -m "Adding hello.txt during build"

git tag lightweight2
git tag -a annnotated2 -m "Annotated tag 2"

echo Goodbye > goodbye.txt
git add goodbye.txt
git commit -m "Adding goodbye.txt during build"

git tag -a annnotated3 -m "Annotated tag 3"
git tag lightweight3
