#!/bin/bash
# ═══════════════════════════════════════════════════════════════
# SecureAI Gateway — Demo Screen Recording Script
#
# This script uses macOS built-in screen recording.
# Just run it and follow the instructions.
#
# Usage: bash record-demo.sh
# ═══════════════════════════════════════════════════════════════

OUTPUT_DIR="$HOME/Desktop"
RECORDING="$OUTPUT_DIR/SecureAI_Gateway_Demo_$(date +%Y%m%d_%H%M%S).mov"

echo ""
echo "  ╔═══════════════════════════════════════════════╗"
echo "  ║  SecureAI Gateway — Demo Recording            ║"
echo "  ╠═══════════════════════════════════════════════╣"
echo "  ║                                               ║"
echo "  ║  This will record your screen while you       ║"
echo "  ║  demo the dashboard at localhost:3333          ║"
echo "  ║                                               ║"
echo "  ║  Output: $RECORDING"
echo "  ║                                               ║"
echo "  ╚═══════════════════════════════════════════════╝"
echo ""
echo "  DEMO SCRIPT (follow this order):"
echo ""
echo "  1. Overview tab      — Show 5/5 services green"
echo "  2. Interactive Tester — Login as Admin"
echo "  3. Safe Prompt       — 'What is AI?' → 200 OK"
echo "  4. Harmful Content   — 'How to create virus?' → 422 BLOCKED"
echo "  5. PII Leakage       — 'Credit card 4532...' → 422 BLOCKED"
echo "  6. RBAC Test         — USER accessing /admin → 403"
echo "  7. Security Scorecard — Show 8 ring charts"
echo "  8. CI/CD Pipeline    — Show 15 stages"
echo "  9. Technology Stack  — Show 24 technologies"
echo "  10. Request Log      — Show live log stream"
echo ""
echo "  Press ENTER to start recording..."
read

echo ""
echo "  Opening dashboard..."
open http://localhost:3333

echo ""
echo "  ╔═══════════════════════════════════════╗"
echo "  ║  RECORDING STARTED                    ║"
echo "  ║                                       ║"
echo "  ║  Use Cmd+Shift+5 to start recording   ║"
echo "  ║  Select 'Record Entire Screen'        ║"
echo "  ║  Click 'Record'                       ║"
echo "  ║                                       ║"
echo "  ║  Follow the demo steps above          ║"
echo "  ║                                       ║"
echo "  ║  When done: click Stop in menu bar    ║"
echo "  ║  or press Cmd+Shift+5 → Stop          ║"
echo "  ╚═══════════════════════════════════════╝"
echo ""
echo "  OR use this automated QuickTime method:"
echo ""
echo "  Press ENTER when ready to auto-start QuickTime recording..."
read

# Start QuickTime screen recording via AppleScript
osascript -e '
tell application "QuickTime Player"
    activate
    set newScreenRecording to new screen recording
    delay 1
    tell newScreenRecording
        start
    end tell
end tell
' 2>/dev/null

echo ""
echo "  Recording started! Perform your demo now."
echo "  Press ENTER when finished to stop recording..."
read

# Stop recording
osascript -e '
tell application "QuickTime Player"
    stop (document 1)
    delay 1
end tell
' 2>/dev/null

echo ""
echo "  ✅ Recording saved! Check QuickTime Player to save the file."
echo "  Save it to: ~/Desktop/SecureAI_Gateway_Demo.mov"
echo ""
