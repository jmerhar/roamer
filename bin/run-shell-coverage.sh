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
# Always measured in the pinned kcov container, locally too: kcov is not packaged for Ubuntu
# 24.04, and its macOS build ignores the shebang and runs scripts with /bin/bash, which is 3.2.
# Needs Docker running.
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

echo "Running the suite in $KCOV_IMAGE …"
docker run --rm -v "$root":/src -w /src -e "JUNIT_DIR=${JUNIT_DIR:-}" "$KCOV_IMAGE" bash -c '
  set -eu
  apt-get update -qq >/dev/null
  DEBIAN_FRONTEND=noninteractive apt-get install -y -qq bats git >/dev/null
  report=()
  if [ -n "$JUNIT_DIR" ]; then report=(--report-formatter junit --output "/src/$JUNIT_DIR"); fi
  COVERAGE_DIR=/src/coverage/shell bats "${report[@]}" test/
  # kcov writes kcov-merged/ only once a second script is traced, and the shared coverage
  # tooling reads the merged report, so merge explicitly; it holds the same figures either way.
  kcov --merge /tmp/merged /src/coverage/shell/release.sh.*
  rm -rf /src/coverage/shell/kcov-merged
  cp -R /tmp/merged/kcov-merged /src/coverage/shell/kcov-merged
  # The container runs as root; keep what it wrote readable by the host user and later steps.
  chmod -R a+rX /src/coverage
  if [ -n "$JUNIT_DIR" ]; then chmod -R a+rX "/src/$JUNIT_DIR"; fi
'
