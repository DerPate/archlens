#!/bin/bash
# Deployment script for archlens site
# Usage: ./deploy.sh <user@host> <remote-path>
# Example: ./deploy.sh deploy@example.com /var/www/archlens

if [ -z "$1" ] || [ -z "$2" ]; then
    echo "Usage: $0 <user@host> <remote-path>"
    exit 1
fi

VPS_USER_HOST="$1"
REMOTE_PATH="$2"

# Always run from the site/ directory regardless of where the script is called from
cd "$(dirname "$0")"

echo "Deploying archlens site to ${VPS_USER_HOST}:${REMOTE_PATH}"
echo ""

echo "📚 Generating API docs (Doxygen)..."
REPO_ROOT="$(dirname "$0")/.."
BUNDLED_DOXYGEN="$(ls "$REPO_ROOT"/doxygen-*.linux.bin.tar.gz 2>/dev/null | sort -V | tail -1)"
if [ -z "${DOXYGEN:-}" ] && [ -n "$BUNDLED_DOXYGEN" ]; then
    echo "  Extracting bundled $(basename "$BUNDLED_DOXYGEN")..."
    DOXYGEN_TMP="$(mktemp -d)"
    tar -xzf "$BUNDLED_DOXYGEN" -C "$DOXYGEN_TMP"
    DOXYGEN="$(find "$DOXYGEN_TMP" -name "doxygen" -type f | head -1)"
fi
DOXYGEN_BIN="${DOXYGEN:-doxygen}"
if ! command -v "$DOXYGEN_BIN" &>/dev/null; then
    echo "⚠️  doxygen not found — skipping API docs (set DOXYGEN=/path/to/doxygen or install doxygen)"
else
    (cd "$REPO_ROOT" && "$DOXYGEN_BIN" Doxyfile)
    if [ $? -ne 0 ]; then
        echo "❌ Doxygen generation failed!"
        exit 1
    fi
    echo "✅ API docs generated to public/api/"
fi
[ -n "${DOXYGEN_TMP:-}" ] && rm -rf "$DOXYGEN_TMP"
echo ""

echo "📦 Building prod bundle..."
npm run build
if [ $? -ne 0 ]; then
    echo "❌ Build failed!"
    exit 1
fi
echo "✅ Build successful!"
echo ""

echo "🚀 Deploying to ${VPS_USER_HOST}:${REMOTE_PATH}..."
rsync -avz --delete \
    --exclude='.git' \
    --exclude='node_modules' \
    dist/ "${VPS_USER_HOST}:${REMOTE_PATH}/"
if [ $? -ne 0 ]; then
    echo "❌ Deployment failed!"
    exit 1
fi

echo "🤖 Uploading robots.txt..."
rsync -avz robots.txt "${VPS_USER_HOST}:${REMOTE_PATH}/robots.txt"
if [ $? -ne 0 ]; then
    echo "❌ robots.txt upload failed!"
    exit 1
fi

echo ""
echo "✅ Deployment successful!"
echo "Site deployed to: ${VPS_USER_HOST}:${REMOTE_PATH}"
echo ""
