#!/bin/bash

set -x -e

mkdir -p target
sbt scalastyle > target/scalastyle.out

cat target/scalastyle.out

grep -q 'Found 0 errors' target/scalastyle.out
grep -q 'Found 0 warnings' target/scalastyle.out
grep -q 'Found 0 infos' target/scalastyle.out
