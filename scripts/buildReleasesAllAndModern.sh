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
if [ -z "$4" ]
  then
    BUILD_TYPE='all'
  else
    BUILD_TYPE=$4
fi

echo "build type specified: $BUILD_TYPE"

if [ \( $BUILD_TYPE != 'all' -a $BUILD_TYPE != 'arm' -a $BUILD_TYPE != 'arm64' -a $BUILD_TYPE != 'x86' -a $BUILD_TYPE != 'x86_64' \) ]
  then
    echo "unknown build type is specified"
    exit 3
fi

if [ $BUILD_TYPE = 'arm' ] || [ $BUILD_TYPE = 'all' ]
  then
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
fi

if [ $BUILD_TYPE = 'x86' ] || [ $BUILD_TYPE = 'all' ]
	then
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
		echo "build X86 release mono"
		sh ./scripts/buildReleaseForDirWithArgsAndTarget.sh out/Defaultx86 scripts/gn/argsReleaseX86Mono.gn monochrome_public_apk $KEYSTORE_PATH $KEYSTOREPASSWORD $KEYPASSWORD
		rc=$?
		if [ $rc != 0 ]
		then
			echo "build X86 release mono failed ($rc)"
			exit $rc
		else
			echo "build X86 release mono succeeded"
			mv out/Defaultx86/apks/BraveMono_aligned.apk out/Defaultx86/apks/BraveMonox86.apk
		fi
		echo "packing symbols for X86 release mono"
		rm out/Defaultx86Mono.tar.7z
		sh ./scripts/makeArchive7z.sh out/Defaultx86 out/Defaultx86Mono
fi

if [ $BUILD_TYPE = 'arm64' ] || [ $BUILD_TYPE = 'all' ]
  then
		echo "---------------------------------"
		echo "build ARM64 release mono"
		sh ./scripts/buildReleaseForDirWithArgsAndTarget.sh out/DefaultR64 scripts/gn/argsReleaseArm64Mono.gn monochrome_public_apk $KEYSTORE_PATH $KEYSTOREPASSWORD $KEYPASSWORD
		rc=$?
		if [ $rc != 0 ]
		then
			echo "build ARM64 release mono failed ($rc)"
			exit $rc
		else
			echo "build ARM64 release mono succeeded"
			mv out/DefaultR64/apks/BraveMono_aligned.apk out/DefaultR64/apks/BraveMonoarm64.apk
		fi
		echo "packing symbols for ARM64 release mono"
		rm out/DefaultRArm64Mono.tar.7z
		sh ./scripts/makeArchive7z.sh out/DefaultR64 out/DefaultRArm64Mono
fi

if [ $BUILD_TYPE = 'x86_64' ] || [ $BUILD_TYPE = 'all' ]
  then
		echo "---------------------------------"
		echo "build X86_64 release mono"
		sh ./scripts/buildReleaseForDirWithArgsAndTarget.sh out/Defaultx86_64 scripts/gn/argsReleaseX86_64Mono.gn monochrome_public_apk $KEYSTORE_PATH $KEYSTOREPASSWORD $KEYPASSWORD
		rc=$?
		if [ $rc != 0 ]
		then
			echo "build X86_64 release mono failed ($rc)"
			exit $rc
		else
			echo "build X86_64 release mono succeeded"
			mv out/Defaultx86_64/apks/BraveMono_aligned.apk out/Defaultx86_64/apks/BraveMonox86_64.apk
		fi
		echo "packing symbols for X86_64 release mono"
		rm out/Defaultx86_64Mono.tar.7z
		sh ./scripts/makeArchive7z.sh out/Defaultx86_64 out/Defaultx86_64Mono
fi

echo "---------------------------------"
echo "build(s) succeeded"
echo "================================="
