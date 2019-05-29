/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

#include "bat/ledger/ledger.h"
#include "brave/components/brave_rewards/browser/rewards_service.h"
#include "brave/components/brave_rewards/browser/rewards_service_factory.h"
#include "chrome/browser/browser_process.h"
#include "chrome/browser/io_thread.h"
#include "chrome/browser/profiles/profile.h"
#include "chrome/browser/profiles/profile_manager.h"
#include "chrome/browser/sessions/session_tab_helper.h"
#include "chrome/browser/ui/browser_list.h"
#include "content/public/browser/navigation_handle.h"
#include "content/public/common/favicon_url.h"
#include "net/base/registry_controlled_domains/registry_controlled_domain.h"
#include "web_contents_ledger_observer.h"
#include "brave_src/browser/web_contents_ledger_observer.h"
#include "content/public/browser/web_contents.h"
#include "content/public/browser/web_contents_user_data.h"
#include "content/public/common/resource_load_info.mojom.h"

void FavIconUpdated(IOThread* io_thread, const std::string& url, const std::string& favicon_url) {
  //io_thread->globals()->ledger_->favIconUpdated(url, favicon_url);
}

using content::WebContents;

namespace brave {

WebContentsLedgerObserver::WebContentsLedgerObserver(WebContents* web_contents)
    : WebContentsObserver(web_contents),
    web_contents_(web_contents),
    brave_rewards_service_(nullptr),
    is_being_destroyed_(false) {
  bool incognito = web_contents->GetBrowserContext()->IsOffTheRecord();
  if (incognito == false) {
    brave_rewards_service_ = brave_rewards::RewardsServiceFactory::GetForProfile(
     ProfileManager::GetActiveUserProfile()->GetOriginalProfile());
  }
}

WebContentsLedgerObserver::~WebContentsLedgerObserver() {
}

void WebContentsLedgerObserver::OnVisibilityChanged(content::Visibility visibility) {
  if (!brave_rewards_service_) {
    return;
  }

  if (content::Visibility::VISIBLE == visibility) {
    brave_rewards_service_->OnShow(SessionTabHelper::IdForTab(web_contents_));
  } else if (content::Visibility::HIDDEN == visibility) {
    brave_rewards_service_->OnHide(SessionTabHelper::IdForTab(web_contents_));
  }
}

void WebContentsLedgerObserver::WebContentsDestroyed() {
  if (brave_rewards_service_) {
    brave_rewards_service_->OnUnload(SessionTabHelper::IdForTab(web_contents_));
  }
  is_being_destroyed_ = true;
}

void WebContentsLedgerObserver::DidFinishLoad(content::RenderFrameHost* render_frame_host,
                           const GURL& validated_url) {
  if (!brave_rewards_service_ || render_frame_host->GetParent()) {
    return;
  }

  brave_rewards_service_->OnLoad(SessionTabHelper::IdForTab(web_contents_),
    validated_url);
}

void WebContentsLedgerObserver::DidUpdateFaviconURL(const std::vector<content::FaviconURL>& candidates) {
  for (size_t i = 0; i < candidates.size(); i++) {
    switch(candidates[i].icon_type) {
      case content::FaviconURL::IconType::kFavicon:
        // TODO, we probably don't need that at all
        /*content::BrowserThread::PostTask(
            content::BrowserThread::IO, FROM_HERE,
            base::Bind(&FavIconUpdated, g_browser_process->io_thread(), current_domain_,
            candidates[i].icon_url.spec()));*/

        return;
      default:
        break;
    }
  }
}

void WebContentsLedgerObserver::DidAttachInterstitialPage() {
  if (brave_rewards_service_)
    brave_rewards_service_->OnUnload(SessionTabHelper::IdForTab(web_contents_));
}

void WebContentsLedgerObserver::DidFinishNavigation(content::NavigationHandle* navigation_handle) {
  if (!brave_rewards_service_ ||
      !navigation_handle->IsInMainFrame() ||
      !navigation_handle->HasCommitted() ||
      navigation_handle->IsDownload()) {
    return;
  }

  brave_rewards_service_->OnUnload(SessionTabHelper::IdForTab(web_contents_)); 
}

void WebContentsLedgerObserver::ResourceLoadComplete(
    content::RenderFrameHost* render_frame_host,
    const content::GlobalRequestID& request_id,
    const content::mojom::ResourceLoadInfo& resource_load_info) {
  if (!brave_rewards_service_ || !render_frame_host) {
    return;
  }

  if (resource_load_info.resource_type == content::ResourceType::kMedia ||
      resource_load_info.resource_type == content::ResourceType::kXhr ||
      resource_load_info.resource_type == content::ResourceType::kImage ||
      resource_load_info.resource_type == content::ResourceType::kScript) {

    // TODO fill first_party_url and referrer with actual values
    brave_rewards_service_->OnXHRLoad(SessionTabHelper::IdForTab(web_contents_), 
      resource_load_info.url, render_frame_host->GetLastCommittedURL(), 
      resource_load_info.referrer);
  }
}

bool WebContentsLedgerObserver::IsBeingDestroyed() {
  return is_being_destroyed_;
}

WEB_CONTENTS_USER_DATA_KEY_IMPL(WebContentsLedgerObserver)

}
