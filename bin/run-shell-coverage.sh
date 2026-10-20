#!/usr/bin/env bash
#
# Run the bats suite under kcov and leave a merged report in coverage/shell/.
#
# kcov is used through the test helper rather than wrapped around bats: running kcov *over*
# bats collects nothing (bats executes the scripts in grandchild processes — kcov issue #462).
# Instead test/test_helper.bash notices COVERAGE_DIR and runs the script under test through
# kcov, which accumulates into one output directory across every invocation. kcov must be given
# the *script* as its target, not `bash script`, or it instruments the bash binary instead.
#
# Uses a locally installed kcov and bats when both are present, and otherwise reruns itself in
# the pinned kcov container, which is what CI does since kcov is not packaged for Ubuntu 24.04.
#
# Stock kcov on macOS cannot trace release.sh: it runs scripts with /bin/bash (3.2) whatever
# their shebang, and given --bash-parser it fails with "Failed to exchange stderr for pipe" when
# the open-file soft limit exceeds kern.maxfilesperproc. Tests then fail on bash 4+ syntax that
# 3.2 rejects, not on a fault in the script. Wrap kcov to pass --bash-parser and cap
# `ulimit -n`, or set KCOV_FORCE_DOCKER=1 to use the container.
#
# Set JUNIT_DIR (repo-relative) to also write bats's JUnit report there, for Codecov's test
# analytics.
set -euo pipefail

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# Pinned by digest so the reported percentage cannot drift when the tag moves. Image: kcov/kcov
# v44-pre-test3 (2026-04-02); update deliberately, then re-check the gate in coverage.toml.
KCOV_IMAGE=${KCOV_IMAGE:-kcov/kcov@sha256:481289ae32e55e5b733019515acd10948a4f76dfed381765577db909664fc603}

cd "$root"
# kcov does not create missing parent directories of its output directory.
rm -rf coverage/shell
mkdir -p coverage/shell

# Relative because the container sees the repository at /src, not at its host path.
if [ -n "${JUNIT_DIR:-}" ]; then
  case "$JUNIT_DIR" in
    /*) echo "run-shell-coverage: JUNIT_DIR must be repo-relative" >&2; exit 2;;
  esac
  mkdir -p "$JUNIT_DIR"
fi

# Runs the suite under kcov and leaves the merged report in coverage/shell/kcov-merged/.
run_suite() {
  local report=() merged
  if [ -n "${JUNIT_DIR:-}" ]; then report=(--report-formatter junit --output "$JUNIT_DIR"); fi
  COVERAGE_DIR="$root/coverage/shell" bats "${report[@]}" test/
  # kcov writes kcov-merged/ only once a second script is traced, and the shared coverage
  # tooling reads the merged report, so merge explicitly; it holds the same figures either way.
  merged=$(mktemp -d)
  kcov --merge "$merged" coverage/shell/release.sh.*
  rm -rf coverage/shell/kcov-merged
  cp -R "$merged/kcov-merged" coverage/shell/kcov-merged
  rm -rf "$merged"
}

if [ -n "${KCOV_IN_CONTAINER:-}" ]; then
  run_suite
  # The container runs as root; keep what it wrote readable by the host user and later steps.
  chmod -R a+rX coverage
  if [ -n "${JUNIT_DIR:-}" ]; then chmod -R a+rX "$JUNIT_DIR"; fi
elif [ -z "${KCOV_FORCE_DOCKER:-}" ] && command -v kcov >/dev/null && command -v bats >/dev/null; then
  echo "Running the suite under the locally installed kcov …"
  run_suite
else
  echo "Running the suite in $KCOV_IMAGE …"
  docker run --rm -v "$root":/src -w /src -e KCOV_IN_CONTAINER=1 -e "JUNIT_DIR=${JUNIT_DIR:-}" \
    "$KCOV_IMAGE" bash -c '
      set -eu
      apt-get update -qq >/dev/null
      DEBIAN_FRONTEND=noninteractive apt-get install -y -qq bats git >/dev/null
      bin/run-shell-coverage.sh
    '
fi
