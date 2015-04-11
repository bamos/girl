#!/bin/bash
#
# This script can be called by your init daemon (like systemd)
# to run girl as a system service.
# `girl.service` provides an example systemd configuration.
#
# Brandon Amos <http://github.com/bamos>
# 2015-04-11

cd $(dirname $0)

# Place your GitHub API token in .private as:
#   export GITHUB_TOKEN='YOUR_TOKEN'
source /home/bamos/.private

sbt '~re-start'
