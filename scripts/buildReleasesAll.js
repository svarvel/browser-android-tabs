if [ $# -lt 3 ]
  then
	echo "Wrong arguments supplied"
	echo "Usage: buildReleasesAll.js <KeyStorePath> <KeyStorePassword> <KeyPassword>" 
	echo "Example: buildReleasesAll.js out/DefaultR/apks/linkbubble_play_keystore 1234567 1234567"
	exit 1	
fi

# Check that URPC_API_KEY was applied
config="chrome/android/java/src/org/chromium/chrome/browser/ConfigAPIs.java"
if grep -q "public static final String URPC_API_KEY = \"\";" "$config"; then
    echo "URPC_API_KEY is not applied. You should do it manually."
    exit 2
fi

KEYSTORE_PATH=$1
KEYSTOREPASSWORD=$2
KEYPASSWORD=$3

echo "---------------------------------"
echo "build ARM release"
sh ./scripts/buildReleaseForDir.js out/DefaultR $KEYSTORE_PATH $KEYSTOREPASSWORD $KEYPASSWORD
rc=$?
if [ $rc != 0 ] 
then 
	echo "build ARM release failed ($rc)" 
	exit $rc 
else
	echo "build ARM release succeeded"
	mv out/DefaultR/apks/Brave_aligned.apk out/DefaultR/apks/Bravearm.apk
fi


echo "---------------------------------"
echo "build X86 release"
sh ./scripts/buildReleaseForDir.js out/Defaultx86 $KEYSTORE_PATH $KEYSTOREPASSWORD $KEYPASSWORD
rc=$?
if [ $rc != 0 ] 
then 
	echo "build X86 release failed ($rc)" 
	echo "---------------------------------"
	exit $rc 
else
	echo "build X86 release succeeded"
	mv out/Defaultx86/apks/Brave_aligned.apk out/Defaultx86/apks/Bravex86.apk
fi
echo "---------------------------------"
echo "both builds arm and x86 succeeded"
echo "out/DefaultR/apks/Bravearm.apk"
echo "out/Defaultx86/apks/Bravex86.apk"
echo "================================="
