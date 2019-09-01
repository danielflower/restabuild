#!/bin/bash

set -e
echo "build parameter: $1 $2"
mvn --version
mvn releaser:release
