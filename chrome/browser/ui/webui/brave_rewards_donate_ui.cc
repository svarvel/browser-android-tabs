/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */

#include "chrome/browser/ui/webui/brave_rewards_donate_ui.h"

#include "brave/components/brave_rewards/browser/rewards_service.h"
#include "brave/components/brave_rewards/browser/rewards_service_factory.h"
#include "brave/components/brave_rewards/browser/rewards_service_observer.h"
#include "brave/components/brave_rewards/browser/wallet_properties.h"

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

using content::WebUIMessageHandler;

namespace {


// The handler for Javascript messages for the chrome://rewards-panel page.
class RewardsDOMHandler : public WebUIMessageHandler,
                          public brave_rewards::RewardsServiceObserver {
 public:
  RewardsDOMHandler() {};
  ~RewardsDOMHandler() override;

  void Init();

  // WebUIMessageHandler implementation.
  void RegisterMessages() override;

private:
  brave_rewards::RewardsService* rewards_service_;

  void GetWalletProperties(const base::ListValue* args);
	void OnWalletProperties(brave_rewards::RewardsService* rewards_service,
	                         int error_code,
	                         brave_rewards::WalletProperties* wallet_properties) override;
  void GetRecurringDonations(const base::ListValue* args);

  void OnRecurringDonationUpdated(brave_rewards::RewardsService* rewards_service,
                                  brave_rewards::ContentSiteList) override;

  DISALLOW_COPY_AND_ASSIGN(RewardsDOMHandler);
};


RewardsDOMHandler::~RewardsDOMHandler() {
  if (rewards_service_)
    rewards_service_->RemoveObserver(this);
}

void RewardsDOMHandler::RegisterMessages() {
	web_ui()->RegisterMessageCallback("brave_rewards_donate.getWalletProperties",
      base::BindRepeating(&RewardsDOMHandler::GetWalletProperties,
                          base::Unretained(this)));
  web_ui()->RegisterMessageCallback("brave_rewards_donate.getRecurringDonations",
      base::BindRepeating(&RewardsDOMHandler::GetRecurringDonations,
                          base::Unretained(this)));
}

void RewardsDOMHandler::GetWalletProperties(const base::ListValue* args) {
  if (!rewards_service_)
    return;

  rewards_service_->FetchWalletProperties();
}

void RewardsDOMHandler::OnWalletProperties(
    brave_rewards::RewardsService* rewards_service,
    int error_code,
    brave_rewards::WalletProperties* wallet_properties) {
  if (!wallet_properties || !web_ui()->CanCallJavascript()) {
    return;
  }

  base::DictionaryValue result;
  result.SetInteger("status", error_code);
  auto walletInfo = std::make_unique<base::DictionaryValue>();

  if (error_code == 0 && wallet_properties) {
    walletInfo->SetDouble("balance", wallet_properties->balance);
    walletInfo->SetString("probi", wallet_properties->probi);

    auto rates = std::make_unique<base::DictionaryValue>();
    for (auto const& rate : wallet_properties->rates) {
      rates->SetDouble(rate.first, rate.second);
    }
    walletInfo->SetDictionary("rates", std::move(rates));

    auto choices = std::make_unique<base::ListValue>();
    for (double const& choice : wallet_properties->parameters_choices) {
      choices->AppendDouble(choice);
    }
    walletInfo->SetList("choices", std::move(choices));

    auto range = std::make_unique<base::ListValue>();
    for (double const& value : wallet_properties->parameters_range) {
      range->AppendDouble(value);
    }
    walletInfo->SetList("range", std::move(range));

    auto grants = std::make_unique<base::ListValue>();
    for (auto const& item : wallet_properties->grants) {
      auto grant = std::make_unique<base::DictionaryValue>();
      grant->SetString("probi", item.probi);
      grant->SetInteger("expiryTime", item.expiryTime);
      grants->Append(std::move(grant));
    }
    walletInfo->SetList("grants", std::move(grants));
  }

  result.SetDictionary("wallet", std::move(walletInfo));

  web_ui()->CallJavascriptFunctionUnsafe("brave_rewards_donate.walletProperties", result);
}

void RewardsDOMHandler::GetRecurringDonations(const base::ListValue *args) {
  if (rewards_service_) {
    rewards_service_->UpdateRecurringDonationsList();
  }
}

void RewardsDOMHandler::OnRecurringDonationUpdated(brave_rewards::RewardsService* rewards_service,
                                                   const brave_rewards::ContentSiteList list) {
  if (!web_ui()->CanCallJavascript()) {
    return;
  }
  auto publishers = std::make_unique<base::ListValue>();
  for (auto const& item : list) {
    publishers->AppendString(item.id);
  }

  web_ui()->CallJavascriptFunctionUnsafe("brave_rewards_donate.recurringDonations", *publishers);
}

void RewardsDOMHandler::Init() {
  Profile* profile = Profile::FromWebUI(web_ui());
  rewards_service_ = brave_rewards::RewardsServiceFactory::GetForProfile(profile);
  if (rewards_service_)
    rewards_service_->AddObserver(this);
 }
}  // namespace

///////////////////////////////////////////////////////////////////////////////
//
// BraveRewardsDonateUI
//
///////////////////////////////////////////////////////////////////////////////

BraveRewardsDonateUI::BraveRewardsDonateUI(content::WebUI* web_ui, const std::string& name)
    : BasicUI(web_ui, name, chrome::kRewardsDonateJS,
        IDR_BRAVE_REWARDS_DONATE_BUNDLE_JS, IDR_BRAVE_REWARDS_DONATE_HTML) {

  auto handler_owner = std::make_unique<RewardsDOMHandler>();
  RewardsDOMHandler * handler = handler_owner.get();
  web_ui->AddMessageHandler(std::move(handler_owner));
  handler->Init();
}

BraveRewardsDonateUI::~BraveRewardsDonateUI() {
}