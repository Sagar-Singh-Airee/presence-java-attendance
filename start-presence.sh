#!/bin/bash
# ==============================================================================
# SmartVision — One-Click Automated Launcher for macOS / Linux
# Automatically boots the server on port 8081 and opens the browser.
# ==============================================================================

# Ensure we run from the project root directory regardless of where script was invoked
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

# Automatically locate Java 21 from Homebrew if not already in PATH
if [ -d "/opt/homebrew/opt/openjdk@21/bin" ]; then
    export PATH="/opt/homebrew/opt/openjdk@21/bin:$PATH"
    export JAVA_HOME="/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home"
elif [ -d "/usr/local/opt/openjdk@21/bin" ]; then
    export PATH="/usr/local/opt/openjdk@21/bin:$PATH"
    export JAVA_HOME="/usr/local/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home"
fi

# Check Java
if ! command -v java &> /dev/null; then
    echo "❌ Error: Java 21 is not found."
    echo "Please install Java 21: brew install openjdk@21"
    exit 1
fi

# Check Maven
if ! command -v mvn &> /dev/null; then
    echo "❌ Error: Maven is not installed or not in PATH."
    echo "Please install Maven: brew install maven"
    exit 1
fi

# Free port 8081 if previously occupied
OLD_PID=$(lsof -ti:8081 2>/dev/null)
if [ -n "$OLD_PID" ]; then
    echo "⚠️ Port 8081 was already in use by process $OLD_PID. Freeing it..."
    kill -9 "$OLD_PID" 2>/dev/null || true
    sleep 1
fi

# Detect operating system and architecture
OS_TYPE="$(uname -s)"
ARCH_TYPE="$(uname -m)"

if [[ "$OS_TYPE" == "Darwin" ]]; then
    if [[ "$ARCH_TYPE" == "arm64" || "$ARCH_TYPE" == "aarch64" ]]; then
        TARGET_PLATFORM="macosx-arm64"
    else
        TARGET_PLATFORM="macosx-x86_64"
    fi
elif [[ "$OS_TYPE" == "Linux" ]]; then
    if [[ "$ARCH_TYPE" == "aarch64" || "$ARCH_TYPE" == "arm64" ]]; then
        TARGET_PLATFORM="linux-arm64"
    else
        TARGET_PLATFORM="linux-x86_64"
    fi
else
    TARGET_PLATFORM="windows-x86_64"
fi

echo "🖥️  Detected OS: $OS_TYPE ($ARCH_TYPE) -> Platform: $TARGET_PLATFORM"

SMARTVISION_JAR="target/smartvision-1.0.0.jar"

# Verify if JAR exists AND contains native OpenCV binaries for this architecture
NEED_BUILD=0
if [ ! -f "$SMARTVISION_JAR" ]; then
    echo "📦 Application JAR not found."
    NEED_BUILD=1
elif ! jar tf "$SMARTVISION_JAR" 2>/dev/null | grep -q "$TARGET_PLATFORM"; then
    echo "⚠️ Existing JAR does not contain native libraries for $TARGET_PLATFORM."
    NEED_BUILD=1
fi

if [ $NEED_BUILD -eq 1 ]; then
    echo "🔨 Building SmartVision for $TARGET_PLATFORM with Maven (this only runs once)..."
    mvn -Djavacpp.platform="$TARGET_PLATFORM" clean package -DskipTests
    if [ $? -ne 0 ]; then
        echo "❌ Maven build failed. Check error output above."
        exit 1
    fi
fi

echo "=========================================================="
echo "  🚀 Starting SmartVision at http://127.0.0.1:8081"
echo "  🌐 Opening your browser in 4 seconds..."
echo "  🛑 Press Ctrl+C in this terminal to stop the server"
echo "=========================================================="

# Automatically launch browser in background after 4s
(sleep 4 && (open "http://127.0.0.1:8081" 2>/dev/null || xdg-open "http://127.0.0.1:8081" 2>/dev/null)) &

# Run the application
exec java -jar "$SMARTVISION_JAR" --server.port=8081 --presence.camera-index=0 "$@"
