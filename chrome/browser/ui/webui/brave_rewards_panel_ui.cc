/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */

#include "chrome/browser/ui/webui/brave_rewards_panel_ui.h"

#include "chrome/browser/braveRewards/brave_rewards_service.h"
#include "chrome/browser/braveRewards/brave_rewards_service_factory.h"

#include "chrome/browser/profiles/profile.h"
#include "chrome/common/webui_url_constants.h"
#include "components/grit/components_resources.h"
#include "components/grit/components_scaled_resources.h"
#include "components/strings/grit/components_chromium_strings.h"
#include "components/strings/grit/components_strings.h"

#include "content/public/browser/web_contents.h"
#include "content/public/browser/web_ui.h"
#include "content/public/browser/web_ui_data_source.h"
#include "content/public/browser/web_ui_message_handler.h"

using content::WebContents;
using content::WebUIMessageHandler;

namespace {


// The handler for Javascript messages for the chrome://rewards-panel page.
class RewardsDOMHandler : public WebUIMessageHandler,
                          public brave_rewards::BraveRewardsServiceObserver {
 public:
  RewardsDOMHandler() {};
  ~RewardsDOMHandler() override;

  void Init();

  // WebUIMessageHandler implementation.
  void RegisterMessages() override;

private:
   brave_rewards::BraveRewardsService* rewards_service_;

  DISALLOW_COPY_AND_ASSIGN(RewardsDOMHandler);
};


RewardsDOMHandler::~RewardsDOMHandler() {
  if (rewards_service_)
    rewards_service_->RemoveObserver(this);
}

void RewardsDOMHandler::RegisterMessages() {
}


void RewardsDOMHandler::Init() {
  Profile* profile = Profile::FromWebUI(web_ui());
  rewards_service_ = BraveRewardsServiceFactory::GetForProfile(profile);
  if (rewards_service_)
    rewards_service_->AddObserver(this);
}
}  // namespace

///////////////////////////////////////////////////////////////////////////////
//
// BraveRewardsPanelUI
//
///////////////////////////////////////////////////////////////////////////////

BraveRewardsPanelUI::BraveRewardsPanelUI(content::WebUI* web_ui, const std::string& name)
    : BasicUI(web_ui, name, chrome::kRewardsPanelJS,
        IDR_BRAVE_REWARDS_PANEL_UI_JS, IDR_BRAVE_REWARDS_PANEL_UI_HTML) {

  auto handler_owner = std::make_unique<RewardsDOMHandler>();
  RewardsDOMHandler * handler = handler_owner.get();
  web_ui->AddMessageHandler(std::move(handler_owner));
  handler->Init();
}

BraveRewardsPanelUI::~BraveRewardsPanelUI() {
}
