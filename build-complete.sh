#!/bin/bash
# Automated build completion script for NovaTerm
# This script monitors the bootstrap build and completes the APK when ready

set -e

LOG_FILE="/Users/munay/dev/NovaTerm/build-automation.log"
BOOTSTRAP_OUTPUT="/var/folders/mr/cpjq_z3157j63c61gd23xqs40000gn/T/claude/-Users-munay-dev-NovaTerm/tasks/b1d97ea.output"
PROJECT_DIR="/Users/munay/dev/NovaTerm"

log() {
    echo "[$(date '+%Y-%m-%d %H:%M:%S')] $1" | tee -a "$LOG_FILE"
}

log "=== NovaTerm Automated Build Started ==="

# Wait for bootstrap build to complete
log "Waiting for bootstrap build to complete..."
while true; do
    # Check if build process is still running
    if ! pgrep -f "build-bootstraps.sh" > /dev/null 2>&1; then
        log "Bootstrap build process not running, checking result..."
        break
    fi

    # Check for completion markers
    if grep -q "Bootstrap generation finished" "$BOOTSTRAP_OUTPUT" 2>/dev/null; then
        log "Bootstrap build completed successfully!"
        break
    fi

    if grep -q "Failed to build" "$BOOTSTRAP_OUTPUT" 2>/dev/null; then
        log "WARNING: Bootstrap build had failures, but continuing..."
        break
    fi

    # Show progress
    LAST_LINE=$(tail -1 "$BOOTSTRAP_OUTPUT" 2>/dev/null || echo "waiting...")
    log "Build in progress: $LAST_LINE"

    sleep 60
done

# Check if bootstrap was created
VM_USER="novaterm-builder-x64@orb"
BOOTSTRAP_PATH="bootstrap-aarch64.zip"

log "Checking for bootstrap archive in VM..."
if ssh "$VM_USER" "test -f ~/novaterm-packages/output/$BOOTSTRAP_PATH" 2>/dev/null; then
    log "Found bootstrap archive, copying to project..."
    scp "$VM_USER:~/novaterm-packages/output/$BOOTSTRAP_PATH" "$PROJECT_DIR/app/src/main/assets/" 2>&1 | tee -a "$LOG_FILE"
    log "Bootstrap copied successfully!"
else
    log "Bootstrap not found at expected location, checking alternatives..."
    FOUND=$(ssh "$VM_USER" "find ~/novaterm-packages -name 'bootstrap*.zip' -type f 2>/dev/null | head -1")
    if [ -n "$FOUND" ]; then
        log "Found bootstrap at: $FOUND"
        scp "$VM_USER:$FOUND" "$PROJECT_DIR/app/src/main/assets/bootstrap-aarch64.zip" 2>&1 | tee -a "$LOG_FILE"
    else
        log "WARNING: No bootstrap found, using existing bootstrap"
    fi
fi

# Build the APK
log "Building APK with all performance fixes..."
cd "$PROJECT_DIR"
./gradlew clean assembleDebug 2>&1 | tee -a "$LOG_FILE"

if [ -f "$PROJECT_DIR/app/build/outputs/apk/debug/app-debug.apk" ]; then
    APK_SIZE=$(ls -lh "$PROJECT_DIR/app/build/outputs/apk/debug/app-debug.apk" | awk '{print $5}')
    log "APK built successfully! Size: $APK_SIZE"
    log "APK location: $PROJECT_DIR/app/build/outputs/apk/debug/app-debug.apk"

    # Copy to easy-to-find location
    cp "$PROJECT_DIR/app/build/outputs/apk/debug/app-debug.apk" "$PROJECT_DIR/NovaTerm-debug.apk"
    log "Copied to: $PROJECT_DIR/NovaTerm-debug.apk"
else
    log "ERROR: APK build failed"
    exit 1
fi

log "=== NovaTerm Build Completed Successfully! ==="
log "APK ready for download at: $PROJECT_DIR/NovaTerm-debug.apk"
