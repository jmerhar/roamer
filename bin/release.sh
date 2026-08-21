#!/usr/bin/env bash
#
# Build a signed release APK and create a GitHub Release.
#
# Bumps the version in app/build.gradle.kts, builds a signed release APK, commits the
# bump, tags it, pushes, and publishes a GitHub Release with the APK attached.
#
# Everything that can be checked is checked before the tree is touched, because the
# push and the tag are not reversible once they reach the remote. The version bump is
# rolled back if the build or commit fails, so a failed run leaves no stray edit.
#
# Release notes are mandatory — every release must explain what changed.
#
# Usage:
#   ./bin/release.sh 1.1 -n notes.md           # release with notes from a file
#   ./bin/release.sh 1.1 -n notes.md --draft   # same but creates a draft release
#
set -euo pipefail

GRADLE_FILE="app/build.gradle.kts"
APK_PATH="app/build/outputs/apk/release/roamer-release.apk"

die() {
    echo "ERROR: $*" >&2
    exit 1
}

# --- Arguments ---

VERSION="${1:?Usage: ./bin/release.sh <version> -n <notes-file> [--draft]}"
shift

NOTES_FILE=""
DRAFT_ARGS=()
while [[ $# -gt 0 ]]; do
    case "$1" in
        -n) NOTES_FILE="${2:?-n requires a file path}"; shift 2 ;;
        --draft) DRAFT_ARGS=(--draft); shift ;;
        *) die "Unknown option: $1" ;;
    esac
done

[[ "$VERSION" =~ ^[0-9]+\.[0-9]+$ ]] \
    || die "Version must look like 1.2 (got '$VERSION')."

TAG="v${VERSION}"

# --- Pre-flight checks: everything that can fail, before anything changes ---

BRANCH=$(git rev-parse --abbrev-ref HEAD)
[[ "$BRANCH" == "main" ]] \
    || die "Must be on the main branch to release (currently on '$BRANCH')."

if ! git diff --quiet || ! git diff --cached --quiet; then
    die "Working tree has uncommitted changes. Commit or stash them first."
fi

[[ -n "$NOTES_FILE" ]] \
    || die "Release notes are required. Usage: ./bin/release.sh <version> -n <notes-file> [--draft]"
[[ -f "$NOTES_FILE" ]] || die "Notes file not found: $NOTES_FILE"
[[ -s "$NOTES_FILE" ]] || die "Notes file is empty: $NOTES_FILE"

if git rev-parse -q --verify "refs/tags/$TAG" >/dev/null; then
    die "Tag $TAG already exists."
fi

command -v gh >/dev/null || die "The GitHub CLI (gh) is required: https://cli.github.com/"
gh auth status >/dev/null 2>&1 \
    || die "gh is not authenticated. Run: gh auth login"

# Signing must be configured, or assembleRelease silently emits an unsigned APK.
[[ -f keystore/release.keystore ]] \
    || die "Signing keystore not found at keystore/release.keystore (see README: Signing setup)."
[[ -f keystore.properties || -n "${ROAMER_KEYSTORE_PASSWORD:-}" ]] \
    || die "No signing credentials: create keystore.properties or set ROAMER_KEYSTORE_PASSWORD (see README)."

# A release must never ship code that fails its own tests.
echo "Running tests..."
./gradlew --quiet testDebugUnitTest || die "Tests failed — not releasing."

# --- Bump version (rolled back on any later failure until it is committed) ---

# Portable in-place sed (macOS needs '' after -i, GNU sed does not)
sedi() {
    if sed --version >/dev/null 2>&1; then
        sed -i "$@"
    else
        sed -i '' "$@"
    fi
}

CURRENT_CODE=$(sed -n 's/.*versionCode = \([0-9]*\).*/\1/p' "$GRADLE_FILE")
[[ "$(printf '%s' "$CURRENT_CODE" | grep -c .)" -eq 1 ]] \
    || die "Expected exactly one versionCode in $GRADLE_FILE, found: ${CURRENT_CODE:-none}"
NEW_CODE=$((CURRENT_CODE + 1))

restore_version() {
    # Unstage before restoring: `git checkout -- FILE` restores from the index, which would
    # keep the bump if the run failed between `git add` and `git commit`.
    git reset -q -- "$GRADLE_FILE" 2>/dev/null || true
    git checkout HEAD -- "$GRADLE_FILE" 2>/dev/null || true
}
trap restore_version EXIT

echo "Bumping versionCode $CURRENT_CODE → $NEW_CODE, versionName → $VERSION"
sedi "s/versionCode = $CURRENT_CODE/versionCode = $NEW_CODE/" "$GRADLE_FILE"
sedi "s/versionName = \".*\"/versionName = \"$VERSION\"/" "$GRADLE_FILE"

# --- Build signed release APK ---

echo "Building release APK..."
./gradlew assembleRelease

[[ -f "$APK_PATH" ]] || die "Release APK not found at $APK_PATH (an unsigned build produces roamer-release-unsigned.apk)."

# Confirm the APK really is signed rather than trusting that the config took effect.
# `|| true` matters: without it a missing build-tools directory makes this assignment fail
# under `set -e` and aborts the release instead of falling through to the warning below.
APKSIGNER=$(find "${ANDROID_HOME:-$HOME/Library/Android/sdk}/build-tools" \
    -name apksigner -type f 2>/dev/null | sort -V | tail -1 || true)
if [[ -n "$APKSIGNER" ]]; then
    "$APKSIGNER" verify "$APK_PATH" >/dev/null 2>&1 \
        || die "Built APK is not correctly signed: $APK_PATH"
    echo "APK signature verified."
else
    echo "WARNING: apksigner not found; skipping signature verification." >&2
fi

NAMED_APK="app/build/outputs/apk/release/roamer-${VERSION}.apk"
cp "$APK_PATH" "$NAMED_APK"

# --- Commit and tag ---

# The most recent tag reachable from HEAD, resolved before the new tag exists. Reachability
# rather than version sort, so releasing a fix while a newer major tag exists still compares
# against the right point in history. Empty on the very first release.
PREV_TAG=$(git describe --tags --abbrev=0 2>/dev/null || true)

git add "$GRADLE_FILE"
git commit -m "chore: release v${VERSION}"
# The bump is committed, so there is nothing left to roll back.
trap - EXIT

git tag -a "$TAG" -m "Release ${VERSION}"

echo "Pushing commit and tag..."
git push
git push origin "$TAG"

# --- Create GitHub Release ---

echo "Creating GitHub Release ${TAG}..."

BODY=$(cat "$NOTES_FILE")
if [[ -n "$PREV_TAG" ]]; then
    REPO_URL=$(gh repo view --json url -q '.url')
    BODY="${BODY}

**Full Changelog**: ${REPO_URL}/compare/${PREV_TAG}...${TAG}"
fi

# From here the commit and tag are already public, so a failure needs a manual finish
# rather than a rerun — rerunning would abort on the existing tag.
if ! gh release create "$TAG" "$NAMED_APK" \
    --title "Roamer ${VERSION}" \
    --notes "$BODY" \
    "${DRAFT_ARGS[@]}"; then
    echo "" >&2
    echo "ERROR: Tag $TAG is pushed but the GitHub Release was not created." >&2
    echo "Finish manually with:" >&2
    echo "  gh release create $TAG $NAMED_APK --title \"Roamer ${VERSION}\" --notes-file $NOTES_FILE" >&2
    exit 1
fi

echo ""
echo "Done! Release ${TAG} created."
echo "APK: ${NAMED_APK}"
