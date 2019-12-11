# Live TV

__Live TV__ is the Open Source reference application for watching TV on Android TVs.

## AOSP instructions

To install LiveTv

```bash
echo "Compiling"
m -j LiveTv
echo  "Installing"
adb install -r ${OUT}/system/priv-app/LiveTv/LiveTv.apk

```

If it is your first time installing LiveTv you will need to do

```bash
adb root
adb remount
adb push ${OUT}/system/priv-app/LiveTv/LiveTv.apk /system/priv-app/LiveTv/LiveTv.apk
adb reboot
```