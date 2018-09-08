/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */

#include "rewards_helper.h"

#include "brave_rewards_service.h"
#include "brave_rewards_service_factory.h"
#include "chrome/browser/profiles/profile.h"
#include "chrome/browser/sessions/session_tab_helper.h"
#include "chrome/browser/ui/browser.h"
#include "chrome/browser/ui/browser_list.h"
#include "chrome/browser/ui/tabs/tab_strip_model.h"
#include "content/public/browser/navigation_entry.h"
#include "content/public/browser/navigation_handle.h"
#include "content/public/browser/render_frame_host.h"
#include "content/public/common/resource_load_info.mojom.h"
#include "content/public/common/resource_type.h"
#include "content/public/browser/web_contents.h"
#include "content/public/browser/web_contents_user_data.h"
#include "net/base/registry_controlled_domains/registry_controlled_domain.h"

using content::ResourceType;

// DEFINE_WEB_CONTENTS_USER_DATA_KEY(brave_rewards::RewardsHelper);

namespace brave_rewards {

RewardsHelper::RewardsHelper(content::WebContents* web_contents)
    : WebContentsObserver(web_contents),
      tab_id_(SessionTabHelper::IdForTab(web_contents)) {
  if (!tab_id_.is_valid())
    return;
  //TODO
  //BrowserList::AddObserver(this);
  Profile* profile = Profile::FromBrowserContext(
      web_contents->GetBrowserContext());
  rewards_service_ = BraveRewardsServiceFactory::GetForProfile(profile);
}

RewardsHelper::~RewardsHelper() {
  //TODO
  //BrowserList::RemoveObserver(this);
}

void RewardsHelper::DidFinishLoad(
    content::RenderFrameHost* render_frame_host,
    const GURL& validated_url) {
  using namespace net::registry_controlled_domains;

  if (!rewards_service_ || render_frame_host->GetParent())
    return;

    auto origin = validated_url.GetOrigin();
    const std::string tld = GetDomainAndRegistry(origin.host(), PrivateRegistryFilter::INCLUDE_PRIVATE_REGISTRIES);
    rewards_service_->OnLoad(tld, validated_url.host(), validated_url.path(), tab_id_.id());
}

void RewardsHelper::DidFinishNavigation(
    content::NavigationHandle* handle) {
  if (!rewards_service_ ||
      !handle->IsInMainFrame() ||
      !handle->HasCommitted() ||
      handle->IsDownload())
    return;

  rewards_service_->OnUnload(tab_id_.id());
}

void RewardsHelper::ResourceLoadComplete(
    content::RenderFrameHost* render_frame_host,
    const content::mojom::ResourceLoadInfo& resource_load_info) {
  if (!rewards_service_ || !render_frame_host)
    return;

  if (resource_load_info.resource_type == content::RESOURCE_TYPE_MEDIA ||
      resource_load_info.resource_type == content::RESOURCE_TYPE_XHR ||
      resource_load_info.resource_type == content::RESOURCE_TYPE_IMAGE ||
      resource_load_info.resource_type == content::RESOURCE_TYPE_SCRIPT) {
    rewards_service_->OnXHRLoad(
        tab_id_.id(),
        GURL(resource_load_info.url),
        web_contents()->GetURL().spec(),
        resource_load_info.referrer.spec());
  }
}

void RewardsHelper::DidAttachInterstitialPage() {
  if (rewards_service_)
    rewards_service_->OnUnload(tab_id_.id());
}

void RewardsHelper::MediaStartedPlaying(const MediaPlayerInfo& video_type,
                         const MediaPlayerId& id) {
  if (rewards_service_)
    rewards_service_->OnMediaStart(tab_id_.id());
}

void RewardsHelper::MediaStoppedPlaying(
    const MediaPlayerInfo& video_type,
    const MediaPlayerId& id,
    WebContentsObserver::MediaStoppedReason reason) {
  if (rewards_service_)
    rewards_service_->OnMediaStop(tab_id_.id());
}

void RewardsHelper::OnVisibilityChanged(content::Visibility visibility) {
  if (!rewards_service_)
    return;

  if (visibility == content::Visibility::HIDDEN) {
    rewards_service_->OnHide(tab_id_.id());
  } else if (visibility == content::Visibility::OCCLUDED) {
    rewards_service_->OnBackground(tab_id_.id());
  } else if (visibility == content::Visibility::VISIBLE) {
    rewards_service_->OnShow(tab_id_.id());
  }
}

void RewardsHelper::WebContentsDestroyed() {
  if (rewards_service_)
    rewards_service_->OnUnload(tab_id_.id());
}

void RewardsHelper::OnBrowserSetLastActive(Browser* browser) {
  if (!rewards_service_)
    return;
  //TODO
  //if (browser->tab_strip_model()->GetIndexOfWebContents(web_contents()) !=
  //    TabStripModel::kNoTab) {
    rewards_service_->OnForeground(tab_id_.id());
  //}
}

void RewardsHelper::OnBrowserNoLongerActive(Browser* browser) {
  if (!rewards_service_)
    return;

  //TODO
  //if (browser->tab_strip_model()->GetIndexOfWebContents(web_contents()) !=
  //    TabStripModel::kNoTab) {
    rewards_service_->OnBackground(tab_id_.id());
  //}
}

}  // namespace brave_rewards
