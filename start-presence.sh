#!/bin/bash
# ==============================================================================
# SmartVision — One-Click Automated Launcher for macOS / Linux
# Automatically boots the server on port 8081 and opens the browser.
# ==============================================================================

# Check Java
if ! command -v java &> /dev/null; then
    echo "❌ Error: Java 21 is not found."
    echo "Please install Java 21: brew install openjdk@21"
    exit 1
fi

# Free port 8081 if previously occupied
OLD_PID=$(lsof -ti:8081 2>/dev/null)
if [ -n "$OLD_PID" ]; then
    echo "⚠️ Port 8081 was already in use by process $OLD_PID. Freeing it..."
    kill -9 "$OLD_PID" 2>/dev/null || true
    sleep 1
fi

# Detect target Mac processor architecture
if [[ $(uname -m) == 'arm64' ]]; then
    TARGET_PLATFORM="macosx-arm64"
else
    TARGET_PLATFORM="macosx-x86_64"
fi

SMARTVISION_JAR="target/smartvision-1.0.0.jar"

# Verify if JAR exists AND contains native binaries for this Mac architecture
NEED_BUILD=0
if [ ! -f "$SMARTVISION_JAR" ]; then
    echo "📦 Native Mac JAR not found."
    NEED_BUILD=1
elif ! jar tf "$SMARTVISION_JAR" 2>/dev/null | grep -q "$TARGET_PLATFORM"; then
    echo "⚠️ Existing JAR does not contain native libraries for $TARGET_PLATFORM."
    NEED_BUILD=1
fi

if [ $NEED_BUILD -eq 1 ]; then
    echo "📦 Building SmartVision for $TARGET_PLATFORM with Maven..."
    mvn -Djavacpp.platform="$TARGET_PLATFORM" clean package -DskipTests
    if [ $? -ne 0 ]; then
        echo "❌ Maven build failed. Check error above."
        exit 1
    fi
fi

echo "=========================================================="
echo "  🚀 Starting SmartVision at http://127.0.0.1:8081"
echo "  🌐 Your browser will open automatically in 4 seconds..."
echo "  🛑 Press Ctrl+C in this terminal to stop the server"
echo "=========================================================="

# Automatically launch browser in background after 4s
(sleep 4 && open "http://127.0.0.1:8081" 2>/dev/null || xdg-open "http://127.0.0.1:8081" 2>/dev/null) &

# Run the application
java -jar "$SMARTVISION_JAR" --server.port=8081 --presence.camera-index=0 "$@"
