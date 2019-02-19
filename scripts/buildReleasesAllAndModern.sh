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

KEYSTORE_PATH=$1
KEYSTOREPASSWORD=$2
KEYPASSWORD=$3

echo "---------------------------------"
echo "build ARM release classic"
sh ./scripts/buildReleaseForDirWithArgsAndTarget.sh out/DefaultR scripts/gn/argsReleaseArmClassic.gn chrome_public_apk $KEYSTORE_PATH $KEYSTOREPASSWORD $KEYPASSWORD
rc=$?
if [ $rc != 0 ]
then
	echo "build ARM release classic failed ($rc)"
	exit $rc
else
	echo "build ARM release classic succeeded"
	mv out/DefaultR/apks/Brave_aligned.apk out/DefaultR/apks/Bravearm.apk
fi
echo "packing symbols for ARM release classic"
rm out/DefaultRArmClassic.tar.7z
sh ./scripts/makeArchive7z.sh out/DefaultR out/DefaultRArmClassic

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
echo "build ARM release mono"
sh ./scripts/buildReleaseForDirWithArgsAndTarget.sh out/DefaultR scripts/gn/argsReleaseArmMono.gn monochrome_public_apk $KEYSTORE_PATH $KEYSTOREPASSWORD $KEYPASSWORD
rc=$?
if [ $rc != 0 ]
then
	echo "build ARM release mono failed ($rc)"
	exit $rc
else
	echo "build ARM release mono succeeded"
	mv out/DefaultR/apks/BraveMono_aligned.apk out/DefaultR/apks/BraveMonoarm.apk
fi
echo "packing symbols for ARM release mono"
rm out/DefaultRArmMono.tar.7z
sh ./scripts/makeArchive7z.sh out/DefaultR out/DefaultRArmMono

echo "---------------------------------"
echo "build X86 release classic"
sh ./scripts/buildReleaseForDirWithArgsAndTarget.sh out/Defaultx86 scripts/gn/argsReleaseX86Classic.gn chrome_public_apk $KEYSTORE_PATH $KEYSTOREPASSWORD $KEYPASSWORD
rc=$?
if [ $rc != 0 ]
then
	echo "build X86 release classic failed ($rc)"
	echo "---------------------------------"
	exit $rc
else
	echo "build X86 release classic succeeded"
	mv out/Defaultx86/apks/Brave_aligned.apk out/Defaultx86/apks/Bravex86.apk
fi
rm out/Defaultx86Classic.tar.7z
echo "packing symbols for X86 release classic"
sh ./scripts/makeArchive7z.sh out/Defaultx86 out/Defaultx86Classic

echo "---------------------------------"
echo "build X86 release modern"
sh ./scripts/buildReleaseForDirWithArgsAndTarget.sh out/Defaultx86 scripts/gn/argsReleaseX86Modern.gn chrome_modern_public_apk $KEYSTORE_PATH $KEYSTOREPASSWORD $KEYPASSWORD
rc=$?
if [ $rc != 0 ]
then
	echo "build X86 release modern failed ($rc)"
	echo "---------------------------------"
	exit $rc
else
	echo "build X86 release modern succeeded"
	mv out/Defaultx86/apks/BraveModern_aligned.apk out/Defaultx86/apks/BraveModernx86.apk
fi
rm out/Defaultx86Modern.tar.7z
echo "packing symbols for X86 modern"
sh ./scripts/makeArchive7z.sh out/Defaultx86 out/Defaultx86Modern

echo "---------------------------------"
echo "all builds arm and x86, classic and modern succeeded"
echo "out/DefaultR/apks/Bravearm.apk"
echo "out/DefaultR/apks/BraveModernarm.apk"
echo "out/Defaultx86/apks/Bravex86.apk"
echo "out/Defaultx86/apks/BraveModernx86.apk"
echo "================================="
