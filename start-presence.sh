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

# Locate or build the native Mac JAR
SMARTVISION_JAR="target/smartvision-1.0.0.jar"
if [ ! -f "$SMARTVISION_JAR" ]; then
    echo "📦 Native Mac JAR not found. Building for your processor ($(uname -m))..."
    if [[ $(uname -m) == 'arm64' ]]; then
        mvn -Djavacpp.platform=macosx-arm64 clean package -DskipTests
    else
        mvn -Djavacpp.platform=macosx-x86_64 clean package -DskipTests
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
