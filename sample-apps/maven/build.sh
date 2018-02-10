#!/bin/bash

set -e

mvn --version
mvn releaser:next
