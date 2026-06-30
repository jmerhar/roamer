#!/usr/bin/env bash
#
# Build a signed release APK and create a GitHub Release.
#
# Bumps the version in app/build.gradle.kts, builds a signed release APK,
# commits the bump, tags it, pushes, and publishes a GitHub Release with the
# APK attached.
#
# Release notes are mandatory — every release must explain what changed.
#
# Usage:
#   ./bin/release.sh 1.1 -n notes.md           # release with notes from a file
#   ./bin/release.sh 1.1 -n notes.md --draft   # same but creates a draft release
#
set -euo pipefail

# --- Pre-flight checks ---

BRANCH=$(git rev-parse --abbrev-ref HEAD)
if [[ "$BRANCH" != "main" ]]; then
    echo "ERROR: Must be on the main branch to release (currently on '$BRANCH')."
    exit 1
fi

if ! git diff --quiet || ! git diff --cached --quiet; then
    echo "ERROR: Working tree has uncommitted changes. Commit or stash them first."
    exit 1
fi

# Portable in-place sed (macOS needs '' after -i, GNU sed does not)
sedi() {
    if sed --version >/dev/null 2>&1; then
        sed -i "$@"
    else
        sed -i '' "$@"
    fi
}

VERSION="${1:?Usage: ./bin/release.sh <version> -n <notes-file> [--draft]}"
shift

NOTES_FILE=""
DRAFT_FLAG=""
while [[ $# -gt 0 ]]; do
    case "$1" in
        -n) NOTES_FILE="${2:?-n requires a file path}"; shift 2 ;;
        --draft) DRAFT_FLAG="--draft"; shift ;;
        *) echo "Unknown option: $1"; exit 1 ;;
    esac
done

if [[ -z "$NOTES_FILE" ]]; then
    echo "ERROR: Release notes are required. Usage: ./bin/release.sh <version> -n <notes-file> [--draft]"
    exit 1
fi

if [[ ! -f "$NOTES_FILE" ]]; then
    echo "ERROR: Notes file not found: $NOTES_FILE"
    exit 1
fi

GRADLE_FILE="app/build.gradle.kts"
TAG="v${VERSION}"

if git rev-parse -q --verify "refs/tags/$TAG" >/dev/null; then
    echo "ERROR: Tag $TAG already exists."
    exit 1
fi

# --- Bump version ---

# Read current versionCode and increment
CURRENT_CODE=$(sed -n 's/.*versionCode = \([0-9]*\).*/\1/p' "$GRADLE_FILE")
NEW_CODE=$((CURRENT_CODE + 1))

echo "Bumping versionCode $CURRENT_CODE → $NEW_CODE, versionName → $VERSION"

sedi "s/versionCode = $CURRENT_CODE/versionCode = $NEW_CODE/" "$GRADLE_FILE"
sedi "s/versionName = \".*\"/versionName = \"$VERSION\"/" "$GRADLE_FILE"

# --- Build signed release APK ---

echo "Building release APK..."
./gradlew assembleRelease

APK_PATH="app/build/outputs/apk/release/roamer-release.apk"
if [[ ! -f "$APK_PATH" ]]; then
    echo "ERROR: Release APK not found at $APK_PATH"
    exit 1
fi

# Rename APK to include version
NAMED_APK="app/build/outputs/apk/release/roamer-${VERSION}.apk"
cp "$APK_PATH" "$NAMED_APK"

# --- Commit and tag ---

git add "$GRADLE_FILE"
git commit -m "chore: release v${VERSION}"
git tag -a "$TAG" -m "Release ${VERSION}"

echo "Pushing commit and tag..."
git push
git push origin "$TAG"

# --- Create GitHub Release ---

echo "Creating GitHub Release ${TAG}..."

# The new tag is the most recent; the previous tag (if any) is the second.
PREV_TAG=$(git tag --sort=-v:refname | sed -n '2p')
REPO_URL=$(gh repo view --json url -q '.url')

# Release notes from the file, plus a full-changelog link when there's a prior tag.
BODY=$(cat "$NOTES_FILE")
if [[ -n "$PREV_TAG" ]]; then
    BODY="${BODY}

**Full Changelog**: ${REPO_URL}/compare/${PREV_TAG}...${TAG}"
fi

gh release create "$TAG" "$NAMED_APK" \
    --title "Roamer ${VERSION}" \
    --notes "$BODY" \
    $DRAFT_FLAG

echo ""
echo "Done! Release ${TAG} created."
echo "APK: ${NAMED_APK}"
