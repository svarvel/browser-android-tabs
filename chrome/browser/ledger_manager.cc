/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

#include "ledger_manager.h"
#include "bat/ledger/ledger.h"
#include "content/public/browser/web_contents_ledger_observer.h"
#include "chrome/browser/browser_process.h"
#include "chrome/browser/io_thread.h"

bool walletCreated = false;

// Wallet creation
void CreateWallet(IOThread* io_thread) {
  if (walletCreated) {
    return;
  }
  walletCreated = true;
  io_thread->globals()->ledger_->CreateWallet();
}
//

LedgerManager::LedgerManager() {

}

LedgerManager::~LedgerManager() {

}

void LedgerManager::AddObserver(std::shared_ptr<content::WebContentsLedgerObserver> observer) {
  for (size_t i = 0; i < web_contents_ledger_observers_.size(); i++) {
    if (web_contents_ledger_observers_[i]->IsBeingDestroyed()) {
      web_contents_ledger_observers_.erase(web_contents_ledger_observers_.begin() + i);
      i--;
    }
  }

  // TODO debug, call from the correct place
  content::BrowserThread::PostTask(
      content::BrowserThread::IO, FROM_HERE,
      base::Bind(&CreateWallet, g_browser_process->io_thread()));
  //
  web_contents_ledger_observers_.push_back(observer);
}
