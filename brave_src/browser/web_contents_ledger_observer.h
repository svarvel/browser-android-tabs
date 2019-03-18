/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

#ifndef WEB_CONTENTS_LEDGER_H_
#define WEB_CONTENTS_LEDGER_H_

#include "content/public/browser/web_contents_observer.h"
#include "content/public/browser/web_contents_user_data.h"

namespace brave_rewards {
  class RewardsService;
}

namespace content {
class WebContents;
}

namespace brave {

class WebContentsLedgerObserver : public content::WebContentsObserver,
    public content::WebContentsUserData<WebContentsLedgerObserver> {
public:
  WebContentsLedgerObserver(content::WebContents* web_contents);
  ~WebContentsLedgerObserver() override;

  // Invoked every time the WebContents changes visibility.
  void OnVisibilityChanged(content::Visibility visibility) override;

  void WebContentsDestroyed() override;
  void DidFinishLoad(content::RenderFrameHost* render_frame_host,
                             const GURL& validated_url) override;
  void DidUpdateFaviconURL(const std::vector<content::FaviconURL>& candidates) override;
  bool IsBeingDestroyed();
  void DidAttachInterstitialPage() override;
  void DidFinishNavigation(content::NavigationHandle* navigation_handle) override;
  void ResourceLoadComplete(content::RenderFrameHost* render_frame_host,
      const content::GlobalRequestID& request_id,
      const content::mojom::ResourceLoadInfo& resource_load_info) override;
  WEB_CONTENTS_USER_DATA_KEY_DECL();
private:
  content::WebContents* web_contents_;
  brave_rewards::RewardsService* brave_rewards_service_;
  bool is_being_destroyed_;
};

}

#endif  //WEB_CONTENTS_LEDGER_H_
