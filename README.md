# Brave Android Browser

## Building the Browser

### System Requirements

- Any Linux Version
- [yarn](https://yarnpkg.com/lang/en/docs/install/#linux-tab)
- [ninja](https://ninja-build.org/)

### Preparing the Build Environment

1. Clone Chromium's depot_tools repository:

   `git clone https://chromium.googlesource.com/chromium/tools/depot_tools.git`

2. Add the absolute path of the cloned directory to the end of your PATH variable (You may want to put this in ~/.bashrc or ~/.zshrc.). Assuming you cloned depot_tools to /path/to/depot_tools:

   `export PATH=$PATH:/path/to/depot_tools`

3. Create a browser-android-tabs parent directory:

   `mkdir browser-android-tabs`

4. Switch to the directory you just created:

   `cd browser-android-tabs`

5. Clone the repository to the src subdirectory:

   `git clone https://github.com/brave/browser-android-tabs.git src`

6. Switch to the directory you just cloned:

   `cd src`

7. Execute the scripts/getThirdParties.js script:

   `sh scripts/getThirdParties.js`

8. Enter information as requested by the script. When asked to create a file for arguments, use [this gn file](https://github.com/brave/browser-android-tabs/wiki/Sample-gn-file-for-debug).

### Making the Build

#### Debug

   From the browser-android-tabs/src directory, execute the following:

      `ninja -C out/Default chrome_public_apk`

   And to deploy it to your Android device:

      `build/android/adb_install_apk.py out/Default/apks/Brave.apk`

#### Release (arm)

   Create configuration in new folder:

      `gn args out/DefaultR`
    
   Set [these settings](https://github.com/brave/browser-android-tabs/wiki/Sample-gn-file-for-arm-release) in `args.gn` file.

   From the browser-android-tabs/src directory, execute the following:

      `ninja -C out/DefaultR chrome_public_apk`

   And to deploy it to your Android device:

      `build/android/adb_install_apk.py out/DefaultR/apks/Brave.apk`

#### Release (x86)

   Create configuration in new folder:

      `gn args out/Defaultx86`
    
   Set [these settings](https://github.com/brave/browser-android-tabs/wiki/Sample-gn-file-for-x86-release) in `args.gn` file.

   From the browser-android-tabs/src directory, execute the following:

      `ninja -C out/Defaultx86 chrome_public_apk`

   And to deploy it to your Android device:

      `build/android/adb_install_apk.py out/Defaultx86/apks/Brave.apk`

### Known Limitations

- The browser will not compile in an encrypted file system.

## Debugging

- See [https://www.chromium.org/developers/how-tos/debugging-on-android](https://www.chromium.org/developers/how-tos/debugging-on-android) for the general debug process.

- See [https://www.chromium.org/developers/android-eclipse-dev](https://www.chromium.org/developers/android-eclipse-dev) to configure the Eclipse IDE.
