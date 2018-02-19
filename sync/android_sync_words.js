/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

function getBytesFromWords(words) {
    try {
      injectedObject.nicewareOutput(JSON.stringify(niceware.passphraseToBytes(words)))
    } catch(e) {
      injectedObject.nicewareOutput("")
    }
}

function getCodeWordsFromSeed(seed) {
    try {
      var buffer = new Uint8Array(seed)
      injectedObject.nicewareOutputCodeWords(JSON.stringify(niceware.bytesToPassphrase(buffer)))
    } catch(e) {
      injectedObject.nicewareOutputCodeWords("")
    }
}
