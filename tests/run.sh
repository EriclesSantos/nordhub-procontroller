#!/bin/sh
set -eu
cd "$(dirname "$0")/.."
out=$(mktemp -d)
trap 'rm -rf "$out"' EXIT
qr=android-app/app/libs/zxing-core-3.5.4.jar
java com.sun.tools.javac.Main -encoding UTF-8 -cp "$qr" -d "$out" android-app/app/src/main/java/com/switchphone/controller/AdvancedInput.java android-app/app/src/main/java/com/switchphone/controller/MotionModel.java android-app/app/src/main/java/com/switchphone/controller/ControlFrame.java android-app/app/src/main/java/com/switchphone/controller/WirePacket.java android-app/app/src/main/java/com/switchphone/controller/ControllerState.java android-app/app/src/main/java/com/switchphone/controller/InputRouter.java android-app/app/src/main/java/com/switchphone/controller/PairingPulse.java android-app/app/src/main/java/com/switchphone/bluetooth/DiagnosticDescriptor.java android-app/app/src/main/java/com/switchphone/bluetooth/PairingFlow.java android-app/app/src/main/java/com/switchphone/bluetooth/BondMonitor.java android-app/app/src/main/java/com/switchphone/bluetooth/BondFailure.java android-app/app/src/main/java/com/switchphone/bluetooth/LinkExperiment.java android-app/app/src/main/java/com/switchphone/bluetooth/HidProfile.java android-app/app/src/main/java/com/switchphone/protocol/NintendoDescriptor.java android-app/app/src/main/java/com/switchphone/protocol/NintendoProtocol.java android-app/app/src/main/java/com/switchphone/layouts/ControllerGeometry.java android-app/app/src/main/java/com/switchphone/controller/PcFeedback.java android-app/app/src/main/java/com/switchphone/wifi/PairingQr.java android-app/app/src/main/java/com/switchphone/wifi/QrLuminance.java tests/*Test.java
for test in tests/*Test.java; do
  name=$(basename "$test" .java)
  java -cp "$out:$qr" "$name"
done
