/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */

#ifndef BRAVE_BROWSER_UI_WEBUI_REWARDS_UI_H_
#define BRAVE_BROWSER_UI_WEBUI_REWARDS_UI_H_

#include "base/macros.h"
#include "base/memory/weak_ptr.h"
#include "build/build_config.h"
#include "content/public/browser/web_ui_controller.h"
#include "ui/base/layout.h"

namespace base {
class RefCountedMemory;
}

class BraveRewardsUI : public content::WebUIController {
 public:
  explicit BraveRewardsUI(content::WebUI* web_ui);
  ~BraveRewardsUI() override;

 private:
  base::WeakPtrFactory<BraveRewardsUI> weak_factory_;

  DISALLOW_COPY_AND_ASSIGN(BraveRewardsUI);
};

#endif  // BRAVE_BROWSER_UI_WEBUI_REWARDS_UI_H_
