#!/bin/bash
gn_args="target_os=\"android\"\
 target_cpu=\"arm\"\
 is_debug=true\
 is_component_build=false\
 is_clang=true\
 symbol_level=1\
 enable_incremental_javac=true\
 v8_use_external_startup_data=true\
 fieldtrial_testing_like_official_build=true\
 icu_use_data_file=false\
 android_channel=\"stable\" "
echo "Generating targets..."
gn gen out/Default --args="$gn_args"

echo "Building apk..."
START=$(date +%s.%N)
autoninja -C out/Default  chrome_public_apk
END=$(date +%s.%N)
DIFF=$(echo "$END - $START" | bc)
echo "Building apk done. Took $DIFF sec"

mv out/Default/apks/Brave.apk out/Default/apks/com.brave.browser.apk
