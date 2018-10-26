/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

#include "bat/ledger/ledger.h"
#include "chrome/browser/braveRewards/brave_rewards_service.h"
#include "chrome/browser/braveRewards/brave_rewards_service_factory.h"
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
#include "web_contents_observer.h"
#include "web_contents.h"

void FavIconUpdated(IOThread* io_thread, const std::string& url, const std::string& favicon_url) {
  //io_thread->globals()->ledger_->favIconUpdated(url, favicon_url);
}

namespace content {

WebContentsLedgerObserver::WebContentsLedgerObserver(WebContents* web_contents)
    : WebContentsObserver(web_contents),
    web_contents_(web_contents),
    brave_rewards_service_(nullptr),
    is_being_destroyed_(false) {
  brave_rewards_service_ = BraveRewardsServiceFactory::GetForProfile(
      ProfileManager::GetActiveUserProfile()->GetOriginalProfile());
}

WebContentsLedgerObserver::~WebContentsLedgerObserver() {
}

void WebContentsLedgerObserver::OnVisibilityChanged(Visibility visibility) {
  if (!brave_rewards_service_) {
    DCHECK(brave_rewards_service_);

    return;
  }

  if (Visibility::VISIBLE == visibility) {
    brave_rewards_service_->OnShow(SessionTabHelper::IdForTab(web_contents_).id());
  } else if (Visibility::HIDDEN == visibility) {
    brave_rewards_service_->OnHide(SessionTabHelper::IdForTab(web_contents_).id());
  }
}

void WebContentsLedgerObserver::WebContentsDestroyed() {
  if (brave_rewards_service_) {
    brave_rewards_service_->OnUnload(SessionTabHelper::IdForTab(web_contents_).id());
  }
  is_being_destroyed_ = true;
}

void WebContentsLedgerObserver::DidFinishLoad(RenderFrameHost* render_frame_host,
                           const GURL& validated_url) {
  if (!brave_rewards_service_ || render_frame_host->GetParent()) {
    return;
  }

  const std::string tld =
    GetDomainAndRegistry(validated_url.host(), net::registry_controlled_domains::INCLUDE_PRIVATE_REGISTRIES);

  if (tld == "") {
    return;
  }
  brave_rewards_service_->OnLoad(tld, 
    validated_url.host(), validated_url.spec(),
    SessionTabHelper::IdForTab(web_contents_).id());
}

void WebContentsLedgerObserver::DidUpdateFaviconURL(const std::vector<FaviconURL>& candidates) {
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
    brave_rewards_service_->OnUnload(SessionTabHelper::IdForTab(web_contents_).id());
}

void WebContentsLedgerObserver::DidFinishNavigation(content::NavigationHandle* navigation_handle) {
  if (!brave_rewards_service_ ||
      !navigation_handle->IsInMainFrame() ||
      !navigation_handle->HasCommitted() ||
      navigation_handle->IsDownload()) {
    return;
  }

  brave_rewards_service_->OnUnload(SessionTabHelper::IdForTab(web_contents_).id()); 
}

void WebContentsLedgerObserver::ResourceLoadComplete(
    RenderFrameHost* render_frame_host,
    const GlobalRequestID& request_id,
    const mojom::ResourceLoadInfo& resource_load_info) {
  if (!brave_rewards_service_ || !render_frame_host) {
    return;
  }

  if (resource_load_info.resource_type == content::RESOURCE_TYPE_MEDIA ||
      resource_load_info.resource_type == content::RESOURCE_TYPE_XHR ||
      resource_load_info.resource_type == content::RESOURCE_TYPE_IMAGE ||
      resource_load_info.resource_type == content::RESOURCE_TYPE_SCRIPT) {

    // TODO fill first_party_url and referrer with actual values
    brave_rewards_service_->OnXHRLoad(SessionTabHelper::IdForTab(web_contents_).id(), resource_load_info.url, 
      render_frame_host->GetLastCommittedURL().host(), resource_load_info.referrer.spec());
  }
}

bool WebContentsLedgerObserver::IsBeingDestroyed() {
  return is_being_destroyed_;
}

}
