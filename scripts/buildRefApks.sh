#!/usr/bin/bash
KEYSTORE_PATH=$1
KEYSTOREPASSWORD=$2
KEYPASSWORD=$3
REFCODES="$4"
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
    echo "Building apks for $refcode..."
    # Replace pervious ref code with new ref code in the 10th line
    sed -i "10s/\"$prevrefcode\"/\"$refcode\"/" $config
    # Double check that new ref code was applied
    if ! grep -q "public static final String REFERRER_CODE = \"$refcode\";" "$config"; then
        echo "Something is wrong with '$config' file. You should normalize it manually."
        exit 3
    fi
    prevrefcode=$refcode
    # Build apks
    sh ./scripts/buildReleasesAll.js $KEYSTORE_PATH $KEYSTOREPASSWORD $KEYPASSWORD
    rc=$?
    if [ $rc != 0 ] 
    then 
        echo "Build failed ($rc)"
	# Normalize $config file
        sed -i "10s/\"$prevrefcode\"/\"\"/" $config
        exit $rc 
    else
        echo "Build succeeded"
	# Move apks to appropriate folders
	mkdir -p "./out/DefaultR/apks/$refcode"
        mv "./out/DefaultR/apks/Bravearm.apk" "./out/DefaultR/apks/$refcode/Bravearm.apk"
	mkdir -p "./out/Defaultx86/apks/$refcode"
        mv "./out/Defaultx86/apks/Bravex86.apk" "./out/Defaultx86/apks/$refcode/Bravex86.apk"
    fi
done < "$REFCODES"
# Normalize $config file
sed -i "10s/\"$prevrefcode\"/\"\"/" $config
