#!/usr/bin/bash
KEYSTORE_PATH=$1
KEYSTOREPASSWORD=$2
KEYPASSWORD=$3
REFCODES="$4"
VERSION=$5
if [ ! -f "$REFCODES" ]; then
    echo "File '$REFCODES' not found!"
    exit 1
fi
config="chrome/android/java/src/org/chromium/chrome/browser/ConfigAPIs.java"
if [ ! -f "$config" ]; then
    echo "File '$config' not found!"
    exit 2
fi
prevrefcode=""
# Go through lines in $REFCODES and apply them as ref codes for build
while read -r line
do
    refcode="$line"
    echo "---------------------------------"
    echo "Configuring build for $refcode..."
    # Replace pervious ref code with new ref code in the 10th line
    sed -i "10s/\"$prevrefcode\"/\"$refcode\"/" $config
    # Double check that new ref code was applied
    if ! grep -q "public static final String REFERRER_CODE = \"$refcode\";" "$config"; then
        echo "Something is wrong with '$config' file. You should normalize it manually."
        exit 3
    fi
    prevrefcode=$refcode
    # Build arm apk
    sh ./scripts/buildReleaseForDir.js out/DefaultR $KEYSTORE_PATH $KEYSTOREPASSWORD $KEYPASSWORD
    rc=$?
    if [ $rc != 0 ] 
    then 
        echo "Build failed ($rc)"
	# Normalize $config file
        sed -i "10s/\"$prevrefcode\"/\"\"/" $config
        exit $rc 
    else
        echo "Build succeeded"
	# Move apk to appropriate file
        mv "./out/DefaultR/apks/Brave_aligned.apk" "./out/DefaultR/apks/Brave$VERSION$refcode.apk"
    fi
done < "$REFCODES"
# Normalize $config file
sed -i "10s/\"$prevrefcode\"/\"\"/" $config
