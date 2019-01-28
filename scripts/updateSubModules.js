git submodule update --init hashset-cpp
git submodule update --init tracking-protection
git submodule update --init bloom-filter-cpp
git submodule update --init ad-block
git submodule update --init braveSync
git submodule update --init brave-crypto
git submodule update hashset-cpp
git submodule update tracking-protection
git submodule update bloom-filter-cpp
git submodule update ad-block
git submodule update braveSync
git submodule update brave-crypto
cd braveSync
yarn install
yarn run build
cd ..
cd brave-crypto
yarn install
yarn run build
