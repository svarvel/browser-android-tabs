/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

#ifndef WEB_CONTENTS_LEDGER_H_
#define WEB_CONTENTS_LEDGER_H_

#include "web_contents_observer.h"

namespace brave_rewards {
  class RewardsService;
}

namespace content {

class WebContents;

class WebContentsLedgerObserver : public WebContentsObserver {
public:
  WebContentsLedgerObserver(WebContents* web_contents);
  ~WebContentsLedgerObserver() override;

  // Invoked every time the WebContents changes visibility.
  void OnVisibilityChanged(Visibility visibility) override;

  void WebContentsDestroyed() override;
  void DidFinishLoad(RenderFrameHost* render_frame_host,
                             const GURL& validated_url) override;
  void DidUpdateFaviconURL(const std::vector<FaviconURL>& candidates) override;
  bool IsBeingDestroyed();
  void DidAttachInterstitialPage() override;
  void DidFinishNavigation(content::NavigationHandle* navigation_handle) override;
  void ResourceLoadComplete(RenderFrameHost* render_frame_host,
      const GlobalRequestID& request_id,
      const mojom::ResourceLoadInfo& resource_load_info) override;

private:
  WebContents* web_contents_;
  brave_rewards::RewardsService* brave_rewards_service_;
  bool is_being_destroyed_;
};

}

#endif  //WEB_CONTENTS_LEDGER_H_
