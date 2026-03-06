#!/usr/bin/env bash
#
# Release script for urlbuilder
#
# Updates version in build.gradle.kts, commits, tags, and creates a GitHub release
# which triggers the release.yml workflow to publish to Maven Central.
#
# Usage: ./scripts/release.sh [version]
#
# If version is not provided, prompts interactively with the current version as default.
#

set -euo pipefail

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

info() { echo -e "${GREEN}==>${NC} $1"; }
warn() { echo -e "${YELLOW}==>${NC} $1"; }
error() { echo -e "${RED}==>${NC} $1" >&2; }

# Ensure we're in the project root
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"
cd "$PROJECT_ROOT"

# Check for required tools
for cmd in git gh; do
    if ! command -v "$cmd" &> /dev/null; then
        error "$cmd is required but not installed."
        exit 1
    fi
done

# Ensure working directory is clean
if [[ -n "$(git status --porcelain)" ]]; then
    error "Working directory is not clean. Please commit or stash changes first."
    exit 1
fi

# Ensure we're on the main branch
CURRENT_BRANCH=$(git branch --show-current)
if [[ "$CURRENT_BRANCH" != "master" && "$CURRENT_BRANCH" != "main" ]]; then
    warn "You're on branch '$CURRENT_BRANCH', not master/main."
    read -p "Continue anyway? [y/N] " -n 1 -r
    echo
    if [[ ! $REPLY =~ ^[Yy]$ ]]; then
        exit 1
    fi
fi

# Get current version from build.gradle.kts
CURRENT_VERSION=$(grep -E '^version\s*=' build.gradle.kts | sed -E 's/version\s*=\s*"([^"]+)".*/\1/')

if [[ -z "$CURRENT_VERSION" ]]; then
    error "Could not determine current version from build.gradle.kts"
    exit 1
fi

info "Current version: $CURRENT_VERSION"

# Get new version (from argument or prompt)
if [[ $# -ge 1 ]]; then
    NEW_VERSION="$1"
else
    read -p "Enter new version [$CURRENT_VERSION]: " NEW_VERSION
    NEW_VERSION="${NEW_VERSION:-$CURRENT_VERSION}"
fi

# Validate version format (semver-ish)
if [[ ! "$NEW_VERSION" =~ ^[0-9]+\.[0-9]+\.[0-9]+(-[a-zA-Z0-9.]+)?$ ]]; then
    error "Invalid version format: $NEW_VERSION"
    error "Expected format: X.Y.Z or X.Y.Z-suffix"
    exit 1
fi

# Check if tag already exists
if git rev-parse "$NEW_VERSION" &> /dev/null; then
    error "Tag $NEW_VERSION already exists!"
    exit 1
fi

info "New version: $NEW_VERSION"
echo

# Show what will happen
echo "This will:"
echo "  1. Update version in build.gradle.kts to $NEW_VERSION"
echo "  2. Commit the version change"
echo "  3. Create git tag: $NEW_VERSION"
echo "  4. Push commit and tag to origin"
echo "  5. Create GitHub release (triggers Maven Central publish)"
echo

read -p "Proceed? [y/N] " -n 1 -r
echo
if [[ ! $REPLY =~ ^[Yy]$ ]]; then
    info "Aborted."
    exit 0
fi

# Update version in build.gradle.kts
info "Updating build.gradle.kts..."
sed -i.bak -E "s/^(version\s*=\s*\")[^\"]+\"/\1$NEW_VERSION\"/" build.gradle.kts
rm -f build.gradle.kts.bak

# Verify the change
UPDATED_VERSION=$(grep -E '^version\s*=' build.gradle.kts | sed -E 's/version\s*=\s*"([^"]+)".*/\1/')
if [[ "$UPDATED_VERSION" != "$NEW_VERSION" ]]; then
    error "Failed to update version in build.gradle.kts"
    git checkout build.gradle.kts
    exit 1
fi

# Commit the change
info "Committing version change..."
git add build.gradle.kts
git commit -m "chore: bump version to $NEW_VERSION"

# Create tag
info "Creating tag $NEW_VERSION..."
git tag -a "$NEW_VERSION" -m "Release $NEW_VERSION"

# Push commit and tag
info "Pushing to origin..."
git push origin "$CURRENT_BRANCH"
git push origin "$NEW_VERSION"

# Create GitHub release
info "Creating GitHub release..."
gh release create "$NEW_VERSION" \
    --title "v$NEW_VERSION" \
    --generate-notes

echo
info "Release $NEW_VERSION created successfully!"
info "GitHub Actions will now publish to Maven Central."
echo
info "Monitor the release workflow at:"
gh release view "$NEW_VERSION" --web || echo "  https://github.com/$(gh repo view --json nameWithOwner -q .nameWithOwner)/actions"
