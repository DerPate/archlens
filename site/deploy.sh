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
if ! command -v doxygen &>/dev/null; then
    echo "⚠️  doxygen not found — skipping API docs (install doxygen to include /api/)"
else
    # Run from repo root so Doxyfile paths resolve correctly
    (cd "$(dirname "$0")/.." && doxygen Doxyfile)
    if [ $? -ne 0 ]; then
        echo "❌ Doxygen generation failed!"
        exit 1
    fi
    echo "✅ API docs generated to public/api/"
fi
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
