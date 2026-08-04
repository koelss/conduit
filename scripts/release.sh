#!/usr/bin/env bash
# release.sh — Tag the current release and let CI publish it.
#
# Reads conduit.version from gradle.properties, creates an annotated `v<version>`
# tag on the target branch of the remote, and pushes it. Pushing a `v*` tag
# triggers the "Publish release" step in .github/workflows/build.yml, which
# builds conduit-<version>.jar and creates the GitHub Release with generated
# notes.
#
# Usage (from a clone of the repo):
#   ./scripts/release.sh                 # tag v<conduit.version> on origin/main
#   ./scripts/release.sh 1.4.0           # explicit version
#   REF=main REMOTE=origin ./scripts/release.sh
#
# Requires: git, and push access to the remote.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
cd "$ROOT_DIR"

REMOTE="${REMOTE:-origin}"
REF="${REF:-main}"
VERSION="${1:-$(grep '^conduit.version=' gradle.properties | cut -d= -f2)}"

if [[ -z "$VERSION" ]]; then
  echo "ERROR: conduit.version not found in gradle.properties (and no version argument given)" >&2
  exit 1
fi

TAG="v$VERSION"
echo "==> Releasing $TAG from $REMOTE/$REF"

git fetch "$REMOTE" "$REF" --tags
TARGET="$(git rev-parse "$REMOTE/$REF")"
echo "    Target commit: $TARGET"

if git ls-remote --tags "$REMOTE" "refs/tags/$TAG" | grep -q "$TAG"; then
  echo "ERROR: tag $TAG already exists on $REMOTE. Bump conduit.version or delete the tag first." >&2
  exit 1
fi

git tag -a "$TAG" "$TARGET" -m "Conduit $TAG"
git push "$REMOTE" "refs/tags/$TAG"

echo ""
echo "==> Pushed $TAG. GitHub Actions will build conduit-$VERSION.jar and publish the release."
echo "    Watch:   https://github.com/tame-gg/conduit/actions"
echo "    Release: https://github.com/tame-gg/conduit/releases/tag/$TAG"
