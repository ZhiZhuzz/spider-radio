#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
: "${ANDROID_HOME:?Set ANDROID_HOME to your Android SDK}"
: "${JAVA_HOME:?Set JAVA_HOME to your JDK 17}"
BT="$ANDROID_HOME/build-tools/35.0.0"
JAR="$ANDROID_HOME/platforms/android-35/android.jar"
rm -rf build/gen build/classes build/dex
mkdir -p build/gen build/classes build/dex
"$BT/aapt" package -f -m -J build/gen -M app/src/main/AndroidManifest.xml -S app/src/main/res -I "$JAR"
"$JAVA_HOME/bin/javac" -encoding UTF-8 -source 8 -target 8 -classpath "$JAR" -d build/classes app/src/main/java/radio/spider/app/*.java build/gen/radio/spider/app/R.java
"$JAVA_HOME/bin/jar" cf build/classes.jar -C build/classes .
"$BT/d8" --lib "$JAR" --min-api 26 --output build/dex build/classes.jar
"$BT/aapt" package -f -M app/src/main/AndroidManifest.xml -S app/src/main/res -A app/src/main/assets -I "$JAR" -0 wav -0 ogg -F build/unsigned.apk
(cd build/dex && "$BT/aapt" add ../unsigned.apk classes.dex)
"$BT/zipalign" -f 4 build/unsigned.apk build/aligned.apk
if [ "${1:-}" = "--unsigned" ]; then
 cp build/aligned.apk build/SpiderRadio-0.8.3-unsigned.apk
 echo 'Unsigned APK created; sign with the original key before installation.'
 exit 0
fi
if [ ! -f signing/spider-radio.jks ]; then
 echo 'Provide your private signing/spider-radio.jks. Use the original key to update an existing install.' >&2
 exit 1
fi
if [ -n "${SPIDER_SIGNING_PASSWORD:-}" ]; then
 signing_pass_source=env:SPIDER_SIGNING_PASSWORD
elif [ -f signing/password ]; then
 signing_pass_source=file:signing/password
else
 echo 'Set SPIDER_SIGNING_PASSWORD or restore the private signing/password file.' >&2
 exit 1
fi
"$BT/apksigner" sign --ks signing/spider-radio.jks --ks-pass "$signing_pass_source" --out build/SpiderRadio-0.8.3.apk build/aligned.apk
"$BT/apksigner" verify --verbose build/SpiderRadio-0.8.3.apk
