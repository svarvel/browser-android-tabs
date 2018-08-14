/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

#include "ledger_manager.h"
#include "bat/ledger/ledger.h"
#include "content/public/browser/web_contents_ledger_observer.h"
#include "chrome/browser/browser_process.h"
#include "chrome/browser/io_thread.h"


LedgerManager::LedgerManager() {

}

LedgerManager::~LedgerManager() {

}

void LedgerManager::AddObserver(std::shared_ptr<content::WebContentsLedgerObserver> observer) {
  LOG(ERROR) << "!!!LedgerManager::AddObserver";
  for (size_t i = 0; i < web_contents_ledger_observers_.size(); i++) {
    if (web_contents_ledger_observers_[i]->IsBeingDestroyed()) {
      web_contents_ledger_observers_.erase(web_contents_ledger_observers_.begin() + i);
      i--;
    }
  }

  web_contents_ledger_observers_.push_back(observer);
  LOG(ERROR) << "!!!LedgerManager::AddObserver exit";
}
