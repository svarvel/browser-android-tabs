/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

 #ifndef LEDGER_MANAGER_H_
 #define LEDGER_MANAGER_H_

 #include <vector>

 namespace content {
 class WebContentsLedgerObserver;
 }

 class LedgerManager {
 public:
   LedgerManager();
   ~LedgerManager();

   void AddObserver(std::shared_ptr<content::WebContentsLedgerObserver> observer);

 private:
   std::vector<std::shared_ptr<content::WebContentsLedgerObserver>> web_contents_ledger_observers_;
 };


 #endif //LEDGER_MANAGER_H_
