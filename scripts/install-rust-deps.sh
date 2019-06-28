#!/bin/bash

###########################################################################
# The script downloads pre-created rust libraries and tools package 
# and generates standalone toolchains for (arm arm64 x86 x86_64) targets. 
###########################################################################

BUILD_ROOT=`pwd`
BASENAME=`basename $BUILD_ROOT`

RUST_VERSION=0.1.1
RUSTUP_ROOT=${BUILD_ROOT}/brave/build/rustup
RUSTUP_HOME=${RUSTUP_ROOT}/${RUST_VERSION}
CARGO_HOME=$RUSTUP_HOME
RUSTUP_BIN=${RUSTUP_HOME}/bin/rustup
RUST_CONFIG=${RUSTUP_HOME}/config
RUST_PKG_BRAVE_CORE=https://s3-us-west-2.amazonaws.com/rust-pkg-brave-core/rust_deps_linux_0.1.1.gz
TMP=`dirname $(mktemp -u)`
MAKE_STANDALONE_TOOLCHAIN=${BUILD_ROOT}/third_party/android_ndk/build/tools/make_standalone_toolchain.py
CLANG=clang
TMP_RUST=${TMP}/`basename $RUST_PKG_BRAVE_CORE`

declare -a ARCHS
ARCHS=(arm arm64 x86 x86_64)

declare -a TRIPLES
TRIPLES=(arm-linux-androideabi aarch64-linux-android i686-linux-android x86_64-linux-android)

declare -a APIS
APIS=(19 21 19 21)
ARCH_LEN=${#ARCHS[@]}

# make sure we run from 'src'
if [ $BASENAME != "src" ]
then
    echo Please run the script from root \'src\' directory
    exit 1
fi

# download rust/cargo package and place it into brave build directory
if [ ! -d $RUSTUP_ROOT ]
then
  rm -f $TMP_RUST
  wget -P $TMP $RUST_PKG_BRAVE_CORE
  if [ $? != 0 ]
  then
    echo Failed to download $RUST_PKG_BRAVE_CORE
    exit 1
  else
    mkdir $RUSTUP_ROOT
    tar -zxvf $TMP_RUST -C $RUSTUP_ROOT
    touch $RUST_CONFIG

    #edit rust config file
    for (( i=0; i<${ARCH_LEN}; i++ ));
    do
      TRIPLE=${TRIPLES[$i]}
      echo -e "[target.$TRIPLE]\nlinker = \"$TRIPLE-$CLANG\"\n" >> $RUST_CONFIG
    done
  fi
fi

#make-standalone-toolchains for all archs/triples
for (( i=0; i<${ARCH_LEN}; i++ ));
do
  ARCH=${ARCHS[$i]}
  TRIPLE=${TRIPLES[$i]}
  API=${APIS[$i]}
  STANDALONE_TOOLCHAIN_DIR=${RUSTUP_ROOT}/${TRIPLE}
  if [ ! -d $STANDALONE_TOOLCHAIN_DIR ]
  then
    echo Building tools at $STANDALONE_TOOLCHAIN_DIR
    $MAKE_STANDALONE_TOOLCHAIN  --arch $ARCH --api $API  --install-dir $STANDALONE_TOOLCHAIN_DIR
    if [ $? != 0 ]
    then
      echo 'Failed to build a standalone toolchain  for $TRIPLE'
      rm -fr $STANDALONE_TOOLCHAIN_DIR
      exit 1
    fi
  fi
done

exit 0
