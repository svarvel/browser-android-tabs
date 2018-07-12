// Copyright (c) 2012 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.


/**
 * Asks the C++ RewardsDOMHandler to create wallet.
 */
function createWallet() {
  chrome.send('createWallet');
}

document.addEventListener('DOMContentLoaded', function() {
  $('create_wallet').onclick = createWallet;
});