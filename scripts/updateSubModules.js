git submodule update --init braveSync
git submodule update --init brave-crypto
git submodule update braveSync
git submodule update brave-crypto
cd braveSync
npm install
npm run build
cd ..
cd brave-crypto
npm install
npm run build
