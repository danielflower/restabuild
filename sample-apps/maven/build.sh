#!/bin/bash

set -e
echo "build parameter 1: --$1--"
echo "build parameter 2: --$2--"
mvn --version
mvn releaser:release
