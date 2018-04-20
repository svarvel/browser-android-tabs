if [ $# -lt 4 ]
then
	echo "Wrong arguments supplied"
	echo "Usage: buildReleaseForDir.js <OutDir> <KeyStorePath> <KeyStorePassword> <KeyPassword>" 
	echo "Example: buildReleaseForDir.js out/DefaultR out/DefaultR/apks/linkbubble_play_keystore 1234567 1234567"
	exit 1	
fi



BASEDIR=$1
KEYSTORE_PATH=$2
KEYSTOREPASSWORD=$3
KEYPASSWORD=$4

echo "Building apk..."  
START=$(date +%s.%N)
ninja -C $BASEDIR chrome_public_apk
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
third_party/android_tools/sdk/build-tools/27.0.3/zipalign -f -p 4 $BASEDIR/apks/Brave.apk $BASEDIR/apks/Brave_aligned.apk
rc=$?
if [ $rc != 0 ] 
then 
	echo "Apk aligning failed ($rc)"
	exit $rc 
else
	echo "Apk aligning succeeded"
fi

echo "Signing apk..."
#removed -verbose key
third_party/android_tools/sdk/build-tools/27.0.3/apksigner sign --in $BASEDIR/apks/Brave_aligned.apk --out $BASEDIR/apks/Brave_aligned.apk --ks $KEYSTORE_PATH --ks-key-alias linkbubble --ks-pass pass:$KEYSTOREPASSWORD
rc=$?
if [ $rc != 0 ] 
then 
	echo "Apk signing failed ($rc)"
	exit $rc 
else
	echo "Apk signing succeeded"
fi

echo "Apk build, sign and align succeeded for $BASEDIR"


