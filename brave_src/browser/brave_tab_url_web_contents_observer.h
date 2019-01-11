/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */

#ifndef BRAVE_BROWSER_BRAVE_TAB_URL_WEB_CONTENTS_OBSERVER_H_
#define BRAVE_BROWSER_BRAVE_TAB_URL_WEB_CONTENTS_OBSERVER_H_

#include "base/macros.h"
#include "base/synchronization/lock.h"
#include "content/public/browser/web_contents_observer.h"
#include "content/public/browser/web_contents_user_data.h"

namespace content {
class WebContents;
}

class PrefRegistrySimple;

namespace brave {

// Taken from brave-core/components/brave_shields/browser/brave_shields_web_contents_observer.h
// TODO(alexeyb) on merge browser-android-tabs and brave-core either keep this
// or re-use BraveShieldsWebContentsObserver with a ton of
// `#if !defined(OS_ANDROID)`
class BraveTabUrlWebContentsObserver : public content::WebContentsObserver,
    public content::WebContentsUserData<BraveTabUrlWebContentsObserver> {
 public:
  BraveTabUrlWebContentsObserver(content::WebContents*);
  ~BraveTabUrlWebContentsObserver() override;

  static GURL GetTabURLFromRenderFrameInfo(int render_process_id,
                                           int render_frame_id,
                                           int render_frame_tree_node_id);

 protected:
    // A set of identifiers that uniquely identifies a RenderFrame.
  struct RenderFrameIdKey {
    RenderFrameIdKey();
    RenderFrameIdKey(int render_process_id, int frame_routing_id);

    // The process ID of the renderer that contains the RenderFrame.
    int render_process_id;

    // The routing ID of the RenderFrame.
    int frame_routing_id;

    bool operator<(const RenderFrameIdKey& other) const;
    bool operator==(const RenderFrameIdKey& other) const;
  };

  // content::WebContentsObserver overrides.
  void RenderFrameCreated(content::RenderFrameHost* host) override;
  void RenderFrameDeleted(content::RenderFrameHost* render_frame_host) override;
  void RenderFrameHostChanged(content::RenderFrameHost* old_host,
                              content::RenderFrameHost* new_host) override;
  void DidFinishNavigation(
      content::NavigationHandle* navigation_handle) override;

  // TODO(iefremov): Refactor this away or at least put into base::NoDestructor.
  // Protects global maps below from being concurrently written on the UI thread
  // and read on the IO thread.
  static base::Lock frame_data_map_lock_;
  static std::map<RenderFrameIdKey, GURL> frame_key_to_tab_url_;
  static std::map<int, GURL> frame_tree_node_id_to_tab_url_;

 private:
  friend class content::WebContentsUserData<BraveTabUrlWebContentsObserver>;

  DISALLOW_COPY_AND_ASSIGN(BraveTabUrlWebContentsObserver);
};

}  // namespace brave

#endif  // BRAVE_BROWSER_BRAVE_TAB_URL_WEB_CONTENTS_OBSERVER_H_
