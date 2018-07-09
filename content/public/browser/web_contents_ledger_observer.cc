/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

#include "bat/ledger/ledger.h"
#include "chrome/browser/browser_process.h"
#include "chrome/browser/io_thread.h"
#include "web_contents_ledger_observer.h"
#include "web_contents_observer.h"
#include "web_contents.h"
#include "content/public/common/favicon_url.h"

void WebSiteWasHidden(IOThread* io_thread, const std::string& url, uint64_t duration) {
  //io_thread->globals()->ledger_->SaveVisit(url, duration, false);
}

void FavIconUpdated(IOThread* io_thread, const std::string& url, const std::string& favicon_url) {
  //io_thread->globals()->ledger_->favIconUpdated(url, favicon_url);
}

namespace content {

WebContentsLedgerObserver::WebContentsLedgerObserver(WebContents* web_contents)
    : WebContentsObserver(web_contents),
    is_being_destroyed_(false),
    web_contents_(web_contents) {
}

WebContentsLedgerObserver::~WebContentsLedgerObserver() {
}

void WebContentsLedgerObserver::OnVisibilityChanged(Visibility visibility) {
  if (visibility == Visibility::VISIBLE) {
    if (web_contents_->GetLastCommittedURL().is_valid() && web_contents_->GetLastCommittedURL().SchemeIsHTTPOrHTTPS()) {
      current_domain_ = web_contents_->GetLastCommittedURL().host();
    }
    last_active_time_ = web_contents_->GetLastActiveTime();
  } else {
    if (current_domain_.empty()) {
      return;
    }
    content::BrowserThread::PostTask(
        content::BrowserThread::IO, FROM_HERE,
        base::Bind(&WebSiteWasHidden, g_browser_process->io_thread(), current_domain_,
      (web_contents_->GetLastHiddenTime().since_origin() - last_active_time_.since_origin()).InMilliseconds()));
  }
}

/*void WebContentsLedgerObserver::WasShown() {
  if (web_contents_->GetLastCommittedURL().is_valid() && web_contents_->GetLastCommittedURL().SchemeIsHTTPOrHTTPS()) {
    current_domain_ = web_contents_->GetLastCommittedURL().host();
  }
  last_active_time_ = web_contents_->GetLastActiveTime();
}

void WebContentsLedgerObserver::WasHidden() {
  if (current_domain_.empty()) {
    return;
  }
  content::BrowserThread::PostTask(
      content::BrowserThread::IO, FROM_HERE,
      base::Bind(&WebSiteWasHidden, g_browser_process->io_thread(), current_domain_,
    (web_contents_->GetLastHiddenTime().since_origin() - last_active_time_.since_origin()).InMilliseconds()));
}*/

void WebContentsLedgerObserver::WebContentsDestroyed() {
  is_being_destroyed_ = true;
}

void WebContentsLedgerObserver::DidFinishLoad(RenderFrameHost* render_frame_host,
                           const GURL& validated_url) {
  if (web_contents_->GetLastCommittedURL().is_valid()
      && current_domain_ != web_contents_->GetLastCommittedURL().host()) {
    LOG(ERROR) << "!!!changed current_domain_ == " << current_domain_ << ", new domain == " << web_contents_->GetLastCommittedURL().spec();
    if (!web_contents_->GetLastCommittedURL().SchemeIsHTTPOrHTTPS()) {
      current_domain_ = "";
    } else {
      current_domain_ = web_contents_->GetLastCommittedURL().host();
    }
    last_active_time_ = base::TimeTicks::Now();
  }
}

void WebContentsLedgerObserver::DidUpdateFaviconURL(const std::vector<FaviconURL>& candidates) {
  for (size_t i = 0; i < candidates.size(); i++) {
    switch(candidates[i].icon_type) {
      case content::FaviconURL::IconType::kFavicon:
        content::BrowserThread::PostTask(
            content::BrowserThread::IO, FROM_HERE,
            base::Bind(&FavIconUpdated, g_browser_process->io_thread(), current_domain_,
            candidates[i].icon_url.spec()));

        return;
      default:
        break;
    }
  }
}

bool WebContentsLedgerObserver::IsBeingDestroyed() {
  return is_being_destroyed_;
}

}
