if [ $# -lt 6 ]
then
	echo "Wrong arguments supplied"
	echo "Usage: buildReleaseForDirWithArgsAndTarget.sh <OutDir> <ArgsFilePath> <chrome_public_apk|chrome_modern_public_apk> <KeyStorePath> <KeyStorePassword> <KeyPassword>"
	echo "Example: buildReleaseForDirWithArgsAndTarget.sh out/DefaultR scripts/gn/argsReleaseArmClassic.gn chrome_public_apk out/DefaultR/apks/linkbubble_play_keystore 1234567 1234567"
	exit 1
fi

# Check that URPC_API_KEY was applied
config="chrome/android/java/src/org/chromium/chrome/browser/ConfigAPIs.java"
if grep -q "public static final String URPC_API_KEY = \"\";" "$config"; then
    echo "URPC_API_KEY is not applied. You should do it manually."
    exit 2
fi

BASEDIR=$1
GN_FILE_PATH=$2
TARGET_NAME=$3
KEYSTORE_PATH=$4
KEYSTOREPASSWORD=$5
KEYPASSWORD=$6

# Verify target name
if [ "$TARGET_NAME" = "chrome_public_apk" ] ;
then
	APK_FILE_NAME="Brave"
elif [ "$TARGET_NAME" = "chrome_modern_public_apk" ] ;
then
	APK_FILE_NAME="BraveModern"
else
	echo "3rd argument target name can be only chrome_public_apk or chrome_modern_public_apk"
	exit 3
fi


echo "Applying args.gn..."
# Copy
cp -f -v $GN_FILE_PATH $BASEDIR/args.gn
rc=$?
if [ $rc != 0 ]
then
	echo "Copy of args.gn failed"
	exit $rc
fi

# Apply
gn gen $BASEDIR

echo "Building apk of $TARGET_NAME target..."
START=$(date +%s.%N)
autoninja -C $BASEDIR $TARGET_NAME
rc=$?
END=$(date +%s.%N)
DIFF=$(echo "$END - $START" | bc)
if [ $rc != 0 ]
then
	echo "Build of apk failed ($rc) and took $DIFF sec"
	exit $rc
else
	echo "Build of apk succeeded and took $DIFF sec"
fi

echo "Aligning apk..."
#removed -v key
third_party/android_tools/sdk/build-tools/27.0.3/zipalign -f -p 4 $BASEDIR/apks/${APK_FILE_NAME}.apk $BASEDIR/apks/${APK_FILE_NAME}_aligned.apk
rc=$?
if [ $rc != 0 ]
then
	echo "Apk aligning failed ($rc)"
	exit $rc
else
	echo "Apk aligning succeeded"
fi

rm $BASEDIR/apks/${APK_FILE_NAME}.apk

echo "Signing apk..."
third_party/android_tools/sdk/build-tools/27.0.3/apksigner sign --in $BASEDIR/apks/${APK_FILE_NAME}_aligned.apk --out $BASEDIR/apks/${APK_FILE_NAME}_aligned.apk --ks $KEYSTORE_PATH --ks-key-alias linkbubble --ks-pass pass:$KEYSTOREPASSWORD --key-pass pass:$KEYPASSWORD
rc=$?
if [ $rc != 0 ]
then
	echo "Apk signing failed ($rc)"
	exit $rc
else
	echo "Apk signing succeeded"
fi

echo "Apk build, sign and align succeeded for $BASEDIR"
