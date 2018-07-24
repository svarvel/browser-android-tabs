// Copyright (c) 2012 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.


/**
 * Asks the C++ RewardsDOMHandler to create wallet.
 */
function createWallet() {
  chrome.send('createWallet');
}

//////////////////////////////////////////////////////////////////////////////////////

/**
 * Asks the C++ RewardsDOMHandler to set publisher min visit time
 */
function setPublisherMinVisitTime() {
  chrome.send('setpublisherminvisittime', [String($('setpublisherminvisittime_duration').value) ]);
}

/**
 * Asks the C++ RewardsDOMHandler to get publisher min visit time.
 * The RewardsDOMHandler should reply to returnPublisherMinVisitTime() (below).
 */
function getPublisherMinVisitTime() {
  chrome.send('getpublisherminvisittime');
}

/**
 * Callback from the backend with the publisher min visit time to display.
 * @param {number} value Publisher min visit time.
 */
function returnPublisherMinVisitTime(value) {
  $('getpublisherminvisittime_duration').innerText = value.toString();
}

//////////////////////////////////////////////////////////////////////////////////////

/**
 * Asks the C++ RewardsDOMHandler to set publisher min visits
 */
function setPublisherMinVisits() {
  chrome.send('setpublisherminvisits',[String($('setpublisherminvisits_visits').value)]);
}


/**
 * Asks the C++ RewardsDOMHandler to get publisher min visits.
 * The RewardsDOMHandler should reply to returnPublisherMinVisits() (below).
 */
function getPublisherMinVisits() {
  chrome.send('getpublisherminvisits');
}

/**
 * Callback from the backend with the publisher min visits to display.
 * @param {number} value Publisher min visits.
 */
function returnPublisherMinVisits(value) {
  $('getpublisherminvisits_visits').innerText = value.toString();
}

//////////////////////////////////////////////////////////////////////////////////////

/**
 * Asks the C++ RewardsDOMHandler to set publisher allow nonverified.
 */
function setPublisherAllowNonVerified() {
  chrome.send('setpublisherallownonverified', [String($('setpublisherallownonverified_allow').checked)]);
}

/**
 * Asks the C++ RewardsDOMHandler to get publisher allow nonverified.
 * The RewardsDOMHandler should reply to returnPublisherAllowNonVerified() (below).
 */
function getPublisherAllowNonVerified() {
  chrome.send('getpublisherallownonverified');
}

/**
 * Callback from the backend with the publisher allow nonverified value to display.
 * @param {boolean} value Publisher allow nonverified.
 */
function returnPublisherAllowNonVerified(value) {
  $('getpublisherallownonverified_allow').checked = value;
}

//////////////////////////////////////////////////////////////////////////////////////

/**
 * Asks the C++ RewardsDOMHandler to set contribution amount.
 */
function setContributionAmount() {
  chrome.send('setcontributionamount', [String($('setcontributionamount_amount').value)]);
}


/**
 * Asks the C++ RewardsDOMHandler to get contribution amount value.
 * The RewardsDOMHandler should reply to returnContributionAmount() (below).
 */
function getContributionAmount() {
  chrome.send('getcontributionamount');
}

/**
 * Callback from the backend with the contribution amount value to display.
 * @param {number} value Contribution amount.
 */
function returnContributionAmount(value) {
  $('getcontributionamount_amount').innerText = value.toString();
}


//////////////////////////////////////////////////////////////////////////////////////

document.addEventListener('DOMContentLoaded', function() {
  $('create_wallet').onclick = createWallet;

  $('btn_setpublisherminvisittime').onclick = setPublisherMinVisitTime;
  $('btn_getpublisherminvisittime').onclick = getPublisherMinVisitTime;

  $('btn_setpublisherminvisits').onclick = setPublisherMinVisits;
  $('btn_getpublisherminvisits').onclick = getPublisherMinVisits;

  $('btn_setpublisherallownonverified').onclick = setPublisherAllowNonVerified;
  $('btn_getpublisherallownonverified').onclick = getPublisherAllowNonVerified;

  $('btn_setcontributionamount').onclick = setContributionAmount;
  $('btn_getcontributionamount').onclick = getContributionAmount;
});
