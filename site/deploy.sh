#!/bin/bash
# Deployment script for archlens site
# Usage: ./deploy.sh <user@host> <remote-path>
# Example: ./deploy.sh deploy@example.com /var/www/archlens

set -euo pipefail

if [ -z "${1:-}" ] || [ -z "${2:-}" ]; then
    echo "Usage: $0 <user@host> <remote-path>"
    exit 1
fi

VPS_USER_HOST="$1"
REMOTE_PATH="$2"

# Resolved before any cd, so the script works from every directory.
SITE_DIR="$(cd "$(dirname "$0")" && pwd)"
REPO_ROOT="$(dirname "$SITE_DIR")"
cd "$SITE_DIR"

echo "Deploying archlens site to ${VPS_USER_HOST}:${REMOTE_PATH}"
echo ""

echo "📚 Generating API docs (Doxygen)..."
DOXYGEN_TMP=""
trap '[ -z "$DOXYGEN_TMP" ] || rm -rf "$DOXYGEN_TMP"' EXIT
BUNDLED_DOXYGEN="$(find "$REPO_ROOT" -maxdepth 1 -name 'doxygen-*.linux.bin.tar.gz' | sort -V | tail -1)"
if [ -z "${DOXYGEN:-}" ] && [ -n "$BUNDLED_DOXYGEN" ]; then
    echo "  Extracting bundled $(basename "$BUNDLED_DOXYGEN")..."
    DOXYGEN_TMP="$(mktemp -d)"
    tar -xzf "$BUNDLED_DOXYGEN" -C "$DOXYGEN_TMP"
    DOXYGEN="$(find "$DOXYGEN_TMP" -name doxygen -type f -print -quit)"
fi
DOXYGEN_BIN="${DOXYGEN:-doxygen}"
# rsync --delete would remove the live /api/, so deploying without it is never right.
if ! command -v "$DOXYGEN_BIN" &>/dev/null; then
    echo "❌ doxygen not found (set DOXYGEN=/path/to/doxygen or install it). Nothing was deployed."
    exit 1
fi
ARCHLENS_VERSION="$(sed -n 's|.*<version>\(.*\)</version>.*|\1|p' "$REPO_ROOT/pom.xml" | sed -n '1p')"
export ARCHLENS_VERSION
rm -rf "${SITE_DIR:?}/public/api"
mkdir -p "$SITE_DIR/public/api"
if ! (cd "$REPO_ROOT" && "$DOXYGEN_BIN" Doxyfile); then
    echo "❌ Doxygen generation failed! Nothing was deployed."
    exit 1
fi
echo "✅ API docs generated to public/api/"
echo ""

echo "📦 Building prod bundle..."
if ! npm run build; then
    echo "❌ Build failed! Nothing was deployed."
    exit 1
fi
if [ ! -f dist/api/index.html ]; then
    echo "❌ The build has no API reference. Nothing was deployed."
    exit 1
fi
echo "✅ Build successful!"
echo ""

echo "🚀 Deploying to ${VPS_USER_HOST}:${REMOTE_PATH}..."
if ! rsync -avz --delete \
    --exclude='.git' \
    --exclude='node_modules' \
    dist/ "${VPS_USER_HOST}:${REMOTE_PATH}/"; then
    echo "❌ Deployment failed!"
    exit 1
fi

echo "🤖 Uploading robots.txt..."
if ! rsync -avz robots.txt "${VPS_USER_HOST}:${REMOTE_PATH}/robots.txt"; then
    echo "❌ robots.txt upload failed!"
    exit 1
fi

echo ""
echo "✅ Deployment successful!"
echo "Site deployed to: ${VPS_USER_HOST}:${REMOTE_PATH}"
echo ""
