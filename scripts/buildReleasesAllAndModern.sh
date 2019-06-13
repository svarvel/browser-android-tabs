if [ $# -lt 3 ]
  then
	echo "Wrong arguments supplied"
	echo "Usage: buildReleasesAllAndModern.sh <KeyStorePath> <KeyStorePassword> <KeyPassword>"
	echo "Example: buildReleasesAllAndModern.sh out/DefaultR/apks/linkbubble_play_keystore 1234567 1234567"
	exit 1
fi

# Check that URPC_API_KEY was applied
config="chrome/android/java/src/org/chromium/chrome/browser/ConfigAPIs.java"
if grep -q "public static final String URPC_API_KEY = \"\";" "$config"; then
    echo "URPC_API_KEY is not applied. You should do it manually."
    exit 2
fi

# Check that GS_API_KEY was applied
config="chrome/android/java/src/org/chromium/chrome/browser/ConfigAPIs.java"
if grep -q "public static final String GS_API_KEY = \"\";" "$config"; then
    echo "GS_API_KEY is not applied. You should do it manually."
    exit 2
fi

# Check that QA_CODE was applied
config="chrome/android/java/src/org/chromium/chrome/browser/ConfigAPIs.java"
if grep -q "public static final String QA_CODE = \"\";" "$config"; then
    echo "QA_CODE is not applied. You should do it manually."
    exit 2
fi

KEYSTORE_PATH=$1
KEYSTOREPASSWORD=$2
KEYPASSWORD=$3


echo "---------------------------------"
echo "build ARM release modern"
sh ./scripts/buildReleaseForDirWithArgsAndTarget.sh out/DefaultR scripts/gn/argsReleaseArmModern.gn chrome_modern_public_apk $KEYSTORE_PATH $KEYSTOREPASSWORD $KEYPASSWORD
rc=$?
if [ $rc != 0 ]
then
	echo "build ARM release modern failed ($rc)"
	exit $rc
else
	echo "build ARM release modern succeeded"
	mv out/DefaultR/apks/BraveModern_aligned.apk out/DefaultR/apks/BraveModernarm.apk
fi
echo "packing symbols for ARM release modern"
rm out/DefaultRArmModern.tar.7z
sh ./scripts/makeArchive7z.sh out/DefaultR out/DefaultRArmModern

echo "---------------------------------"
echo "all builds arm and x86, classic and modern succeeded"
echo "out/DefaultR/apks/BraveModernarm.apk"
echo "================================="
