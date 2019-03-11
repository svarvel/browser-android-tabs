/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */

#include "brave_src/browser/brave_tab_helpers.h"

#include "brave_src/browser/brave_tab_url_web_contents_observer.h"
#include "brave_src/browser/web_contents_ledger_observer.h"
#include "content/public/browser/web_contents.h"

namespace brave {

void AttachTabHelpers(content::WebContents* web_contents) {
  brave::BraveTabUrlWebContentsObserver::CreateForWebContents(web_contents);
  brave::WebContentsLedgerObserver::CreateForWebContents(web_contents);
}

}  // namespace brave
