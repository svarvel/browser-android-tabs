/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */

#include "chrome/browser/ui/webui/brave_rewards_ui.h"

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

content::WebUIDataSource* CreateBraveRewardsUIHTMLSource() {
  content::WebUIDataSource* source =
      content::WebUIDataSource::Create(chrome::kBraveRewardsHost);

  source->SetDefaultResource(IDR_BRAVE_REWARDS_UI_HTML);
  source->UseGzip();
  return source;
}

////////////////////////////////////////////////////////////////////////////////
//
// RewardsDOMHandler
//
////////////////////////////////////////////////////////////////////////////////

// The handler for Javascript messages for the about:flags page.
class RewardsDOMHandler : public WebUIMessageHandler {
 public:
  RewardsDOMHandler() {
  	LOG(ERROR) << "!!!here3";
  }
  ~RewardsDOMHandler() override {}

  // WebUIMessageHandler implementation.
  void RegisterMessages() override;

  // Callback for the "createWallet" message.
  void HandleCreateWallet(const base::ListValue* args);

 private:

  DISALLOW_COPY_AND_ASSIGN(RewardsDOMHandler);
};

void RewardsDOMHandler::RegisterMessages() {
	LOG(ERROR) << "!!!here4";
  web_ui()->RegisterMessageCallback(
      "createWallet",
      base::BindRepeating(&RewardsDOMHandler::HandleCreateWallet,
                          base::Unretained(this)));
}


void RewardsDOMHandler::HandleCreateWallet(
    const base::ListValue* args) {
	LOG(ERROR) << "!!!HandleCreateWallet1";
  /*DCHECK(flags_storage_);
  DCHECK_EQ(2u, args->GetSize());
  if (args->GetSize() != 2)
    return;

  std::string entry_internal_name;
  std::string enable_str;
  if (!args->GetString(0, &entry_internal_name) ||
      !args->GetString(1, &enable_str))
    return;

  about_flags::SetFeatureEntryEnabled(flags_storage_.get(), entry_internal_name,
                                      enable_str == "true");*/
}

}  // namespace

///////////////////////////////////////////////////////////////////////////////
//
// BraveRewardsUI
//
///////////////////////////////////////////////////////////////////////////////

BraveRewardsUI::BraveRewardsUI(content::WebUI* web_ui)
    : WebUIController(web_ui),
      weak_factory_(this) {
  Profile* profile = Profile::FromWebUI(web_ui);


  web_ui->AddMessageHandler(std::make_unique<RewardsDOMHandler>());

  // Set up the brave://rewards source.
  content::WebUIDataSource::Add(profile, CreateBraveRewardsUIHTMLSource());
}

BraveRewardsUI::~BraveRewardsUI() {
}
