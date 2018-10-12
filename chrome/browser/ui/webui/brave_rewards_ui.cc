/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */

#include "chrome/browser/ui/webui/brave_rewards_ui.h"

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

#include "base/values.h"
#include "base/base64.h"


using content::WebContents;
using content::WebUIMessageHandler;

namespace {

// The handler for Javascript messages for Brave about: pages
class RewardsDOMHandler : public WebUIMessageHandler,
                          public brave_rewards::BraveRewardsServiceObserver {
 public:
  RewardsDOMHandler() {};
  ~RewardsDOMHandler() override;

  void Init();

  // WebUIMessageHandler implementation.
  void RegisterMessages() override;

private:
  void GetAllBalanceReports();
  void HandleCreateWalletRequested(const base::ListValue* args);
  void GetWalletProperties(const base::ListValue* args);
  void GetGrant(const base::ListValue* args);
  void GetGrantCaptcha(const base::ListValue* args);
  void GetWalletPassphrase(const base::ListValue* args);
  void RecoverWallet(const base::ListValue* args);
  void SolveGrantCaptcha(const base::ListValue* args);
  void GetReconcileStamp(const base::ListValue* args);
  void GetAddresses(const base::ListValue* args);
  void SaveSetting(const base::ListValue* args);
  void OnGetContentSiteList(std::unique_ptr<brave_rewards::ContentSiteList>, uint32_t record);
  void GetBalanceReports(const base::ListValue* args);
  void ExcludePublisher(const base::ListValue* args);
  void RestorePublishers(const base::ListValue* args);
  void WalletExists(const base::ListValue* args);
  void GetContributionAmount(const base::ListValue* args);
  void HandleSetPublisherMinVisitTime(const base::ListValue* args);
  void HandleGetPublisherMinVisitTime(const base::ListValue* args);

  void HandleSetPublisherMinVisits(const base::ListValue* args);
  void HandleGetPublisherMinVisits(const base::ListValue* args);

  void HandleSetPublisherAllowNonVerified(const base::ListValue* args);
  void HandleGetPublisherAllowNonVerified(const base::ListValue* args);

  void HandleSetContributionAmount(const base::ListValue* args);
  void HandleGetContributionAmount(const base::ListValue* args);

	  // RewardsServiceObserver implementation
  void OnWalletInitialized(brave_rewards::BraveRewardsService* rewards_service,
                       int error_code) override;
  void OnWalletProperties(brave_rewards::BraveRewardsService* rewards_service,
      int error_code,
      std::unique_ptr<brave_rewards::WalletProperties> wallet_properties) override;
  void OnGrant(brave_rewards::BraveRewardsService* rewards_service,
                   unsigned int error_code,
                   ledger::Grant result) override;
  void OnGrantCaptcha(brave_rewards::BraveRewardsService* rewards_service,
                          std::string image, std::string hint) override;
  void OnRecoverWallet(brave_rewards::BraveRewardsService* rewards_service,
                       unsigned int result,
                       double balance,
                       std::vector<ledger::Grant> grants) override;
  void OnGrantFinish(brave_rewards::BraveRewardsService* rewards_service,
                       unsigned int result,
                       ledger::Grant grant) override;
  void OnContentSiteUpdated(brave_rewards::BraveRewardsService* rewards_service) override;
  void OnExcludedSitesChanged(brave_rewards::BraveRewardsService* rewards_service) override;
  void OnReconcileComplete(brave_rewards::BraveRewardsService* rewards_service,
                           unsigned int result,
                           const std::string& viewing_id,
                           const std::string& probi) override;


  brave_rewards::BraveRewardsService* rewards_service_;

  DISALLOW_COPY_AND_ASSIGN(RewardsDOMHandler);
};

RewardsDOMHandler::~RewardsDOMHandler() {
  if (rewards_service_)
    rewards_service_->RemoveObserver(this);
}

void RewardsDOMHandler::RegisterMessages() {
  web_ui()->RegisterMessageCallback("brave_rewards.createWalletRequested",
      base::BindRepeating(&RewardsDOMHandler::HandleCreateWalletRequested,
                          base::Unretained(this)));
  web_ui()->RegisterMessageCallback("brave_rewards.getWalletProperties",
      base::BindRepeating(&RewardsDOMHandler::GetWalletProperties,
                          base::Unretained(this)));

    web_ui()->RegisterMessageCallback("brave_rewards.getGrant",
                                    base::BindRepeating(&RewardsDOMHandler::GetGrant,
                                                        base::Unretained(this)));
  web_ui()->RegisterMessageCallback("brave_rewards.getGrantCaptcha",
                                    base::BindRepeating(&RewardsDOMHandler::GetGrantCaptcha,
                                                        base::Unretained(this)));
  web_ui()->RegisterMessageCallback("brave_rewards.getWalletPassphrase",
                                    base::BindRepeating(&RewardsDOMHandler::GetWalletPassphrase,
                                                        base::Unretained(this)));
  web_ui()->RegisterMessageCallback("brave_rewards.recoverWallet",
                                    base::BindRepeating(&RewardsDOMHandler::RecoverWallet,
                                                        base::Unretained(this)));
  web_ui()->RegisterMessageCallback("brave_rewards.solveGrantCaptcha",
                                    base::BindRepeating(&RewardsDOMHandler::SolveGrantCaptcha,
                                                        base::Unretained(this)));
  web_ui()->RegisterMessageCallback("brave_rewards.getReconcileStamp",
                                    base::BindRepeating(&RewardsDOMHandler::GetReconcileStamp,
                                                        base::Unretained(this)));
  web_ui()->RegisterMessageCallback("brave_rewards.getAddresses",
                                    base::BindRepeating(&RewardsDOMHandler::GetAddresses,
                                                        base::Unretained(this)));
  web_ui()->RegisterMessageCallback("brave_rewards.saveSetting",
                                    base::BindRepeating(&RewardsDOMHandler::SaveSetting,
                                                        base::Unretained(this)));
  web_ui()->RegisterMessageCallback("brave_rewards.getBalanceReports",
                                    base::BindRepeating(&RewardsDOMHandler::GetBalanceReports,
                                                        base::Unretained(this)));
  web_ui()->RegisterMessageCallback("brave_rewards.excludePublisher",
                                    base::BindRepeating(&RewardsDOMHandler::ExcludePublisher,
                                                        base::Unretained(this)));
  web_ui()->RegisterMessageCallback("brave_rewards.restorePublishers",
                                    base::BindRepeating(&RewardsDOMHandler::RestorePublishers,
                                                        base::Unretained(this)));
  web_ui()->RegisterMessageCallback("brave_rewards.checkWalletExistence",
                                    base::BindRepeating(&RewardsDOMHandler::WalletExists,
                                                        base::Unretained(this)));
  web_ui()->RegisterMessageCallback("brave_rewards.getContributionAmount",
                                    base::BindRepeating(&RewardsDOMHandler::GetContributionAmount,
                                                        base::Unretained(this)));

  web_ui()->RegisterMessageCallback(
    "setpublisherminvisittime",
    base::BindRepeating(&RewardsDOMHandler::HandleSetPublisherMinVisitTime,
      base::Unretained(this)));

  web_ui()->RegisterMessageCallback(
    "getpublisherminvisittime",
    base::BindRepeating(&RewardsDOMHandler::HandleGetPublisherMinVisitTime,
      base::Unretained(this)));

  web_ui()->RegisterMessageCallback(
    "setpublisherminvisits",
    base::BindRepeating(&RewardsDOMHandler::HandleSetPublisherMinVisits,
      base::Unretained(this)));

  web_ui()->RegisterMessageCallback(
    "getpublisherminvisits",
    base::BindRepeating(&RewardsDOMHandler::HandleGetPublisherMinVisits,
      base::Unretained(this)));

  web_ui()->RegisterMessageCallback(
    "setpublisherallownonverified",
    base::BindRepeating(&RewardsDOMHandler::HandleSetPublisherAllowNonVerified,
      base::Unretained(this)));

  web_ui()->RegisterMessageCallback(
    "getpublisherallownonverified",
    base::BindRepeating(&RewardsDOMHandler::HandleGetPublisherAllowNonVerified,
      base::Unretained(this)));

  web_ui()->RegisterMessageCallback(
    "setcontributionamount",
    base::BindRepeating(&RewardsDOMHandler::HandleSetContributionAmount,
      base::Unretained(this)));

  web_ui()->RegisterMessageCallback(
    "getcontributionamount",
    base::BindRepeating(&RewardsDOMHandler::HandleGetContributionAmount,
      base::Unretained(this)));
}


void RewardsDOMHandler::Init() {
  Profile* profile = Profile::FromWebUI(web_ui());
  rewards_service_ = BraveRewardsServiceFactory::GetForProfile(profile);
  if (rewards_service_)
    rewards_service_->AddObserver(this);
}

void RewardsDOMHandler::GetAllBalanceReports() {
  if (rewards_service_ && web_ui()->CanCallJavascript()) {
    std::map<std::string, brave_rewards::BalanceReport> reports = rewards_service_->GetAllBalanceReports();

    if (reports.empty()) {
      return;
    }

    base::DictionaryValue newReports;

    for (auto const& report : reports) {
      const brave_rewards::BalanceReport oldReport = report.second;
      auto newReport = std::make_unique<base::DictionaryValue>();
      newReport->SetString("opening", oldReport.opening_balance);
      newReport->SetString("closing", oldReport.closing_balance);
      newReport->SetString("grant", oldReport.grants);
      newReport->SetString("deposit", oldReport.deposits);
      newReport->SetString("ads", oldReport.earning_from_ads);
      newReport->SetString("contribute", oldReport.auto_contribute);
      newReport->SetString("donation", oldReport.recurring_donation);
      newReport->SetString("tips", oldReport.one_time_donation);
      newReport->SetString("total", oldReport.total);
      newReports.SetDictionary(report.first, std::move(newReport));
    }

    web_ui()->CallJavascriptFunctionUnsafe("brave_rewards.balanceReports", newReports);
  }
}

void RewardsDOMHandler::HandleCreateWalletRequested(const base::ListValue* args) {
  if (!rewards_service_)
    return;

  rewards_service_->CreateWallet();
}

void RewardsDOMHandler::GetWalletProperties(const base::ListValue* args) {
  if (!rewards_service_)
    return;

  rewards_service_->GetWalletProperties();
}

void RewardsDOMHandler::OnWalletInitialized(
    brave_rewards::BraveRewardsService* rewards_service,
    int error_code) {
  if (!web_ui()->CanCallJavascript())
    return;

  if (error_code == 0)
    web_ui()->CallJavascriptFunctionUnsafe("brave_rewards.walletCreated");
  else
    web_ui()->CallJavascriptFunctionUnsafe("brave_rewards.walletCreateFailed");
}

void RewardsDOMHandler::OnWalletProperties(
    brave_rewards::BraveRewardsService* rewards_service,
    int error_code,
    std::unique_ptr<brave_rewards::WalletProperties> wallet_properties) {

  if (web_ui()->CanCallJavascript()) {
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

    web_ui()->CallJavascriptFunctionUnsafe("brave_rewards.walletProperties", result);
  }
}

void RewardsDOMHandler::OnGrant(
    brave_rewards::BraveRewardsService* rewards_service,
    unsigned int result,
    ledger::Grant grant) {
  if (web_ui()->CanCallJavascript()) {
    base::DictionaryValue* newGrant = new base::DictionaryValue();
    newGrant->SetInteger("status", result);
    newGrant->SetString("promotionId", grant.promotionId);

    web_ui()->CallJavascriptFunctionUnsafe("brave_rewards.grant", *newGrant);
  }
}

void RewardsDOMHandler::GetGrant(const base::ListValue* args) {
  if (rewards_service_) {
    std::string lang;
    std::string paymentId;
    args->GetString(0, &lang);
    args->GetString(1, &paymentId);
    rewards_service_->GetGrant(lang, paymentId);
  }
}

void RewardsDOMHandler::OnGrantCaptcha(
    brave_rewards::BraveRewardsService* rewards_service,
    std::string image,
    std::string hint) {
  if (web_ui()->CanCallJavascript()) {
    std::string encoded_string;
    base::Base64Encode(image, &encoded_string);

    base::DictionaryValue captcha;
    captcha.SetString("image", std::move(encoded_string));
    captcha.SetString("hint", hint);

    web_ui()->CallJavascriptFunctionUnsafe("brave_rewards.grantCaptcha", captcha);
  }
}

void RewardsDOMHandler::GetGrantCaptcha(const base::ListValue* args) {
  if (rewards_service_) {
    rewards_service_->GetGrantCaptcha();
  }
}

void RewardsDOMHandler::GetWalletPassphrase(const base::ListValue* args) {
  if (rewards_service_ && web_ui()->CanCallJavascript()) {
    std::string pass = rewards_service_->GetWalletPassphrase();

    web_ui()->CallJavascriptFunctionUnsafe("brave_rewards.walletPassphrase", base::Value(pass));
  }
}

void RewardsDOMHandler::RecoverWallet(const base::ListValue *args) {
  if (rewards_service_) {
    std::string passPhrase;
    args->GetString(0, &passPhrase);
    rewards_service_->RecoverWallet(passPhrase);
  }
}

void RewardsDOMHandler::OnRecoverWallet(
    brave_rewards::BraveRewardsService* rewards_service,
    unsigned int result,
    double balance,
    std::vector<ledger::Grant> grants) {
  if (web_ui()->CanCallJavascript()) {
    base::DictionaryValue* recover = new base::DictionaryValue();
    recover->SetInteger("result", result);
    recover->SetDouble("balance", balance);

    auto newGrants = std::make_unique<base::ListValue>();
    for (auto const& item : grants) {
      auto grant = std::make_unique<base::DictionaryValue>();
      grant->SetString("probi", item.probi);
      grant->SetInteger("expiryTime", item.expiryTime);
      newGrants->Append(std::move(grant));
    }
    recover->SetList("grants", std::move(newGrants));

    web_ui()->CallJavascriptFunctionUnsafe("brave_rewards.recoverWalletData", *recover);
  }
}

void RewardsDOMHandler::SolveGrantCaptcha(const base::ListValue *args) {
  if (rewards_service_) {
    std::string solution;
    args->GetString(0, &solution);
    rewards_service_->SolveGrantCaptcha(solution);
  }
}

void RewardsDOMHandler::OnGrantFinish(
    brave_rewards::BraveRewardsService* rewards_service,
    unsigned int result,
    ledger::Grant grant) {
  if (web_ui()->CanCallJavascript()) {
    base::DictionaryValue* finish = new base::DictionaryValue();
    finish->SetInteger("status", result);
    finish->SetInteger("expiryTime", grant.expiryTime);
    finish->SetString("probi", grant.probi);

    web_ui()->CallJavascriptFunctionUnsafe("brave_rewards.grantFinish", *finish);
    GetAllBalanceReports();
  }
}

void RewardsDOMHandler::GetReconcileStamp(const base::ListValue* args) {
  if (rewards_service_ && web_ui()->CanCallJavascript()) {
    std::string stamp = std::to_string(rewards_service_->GetReconcileStamp());

    web_ui()->CallJavascriptFunctionUnsafe("brave_rewards.reconcileStamp", base::Value(stamp));
  }
}

void RewardsDOMHandler::GetAddresses(const base::ListValue* args) {
  if (rewards_service_ && web_ui()->CanCallJavascript()) {
    std::map<std::string, std::string> addresses = rewards_service_->GetAddresses();

    base::DictionaryValue data;
    data.SetString("BAT", addresses["BAT"]);
    data.SetString("BTC", addresses["BTC"]);
    data.SetString("ETH", addresses["ETH"]);
    data.SetString("LTC", addresses["LTC"]);

    web_ui()->CallJavascriptFunctionUnsafe("brave_rewards.addresses", data);
  }
}

void RewardsDOMHandler::OnContentSiteUpdated(brave_rewards::BraveRewardsService* rewards_service) {
  rewards_service_->GetContentSiteList(0, 0, base::Bind(&RewardsDOMHandler::OnGetContentSiteList, base::Unretained(this)));
}

void RewardsDOMHandler::OnExcludedSitesChanged(brave_rewards::BraveRewardsService* rewards_service) {
  if (rewards_service_ && web_ui()->CanCallJavascript()) {
    int num = (int)rewards_service_->GetNumExcludedSites();
    web_ui()->CallJavascriptFunctionUnsafe("brave_rewards.numExcludedSites", base::Value(num));
  }
}

void RewardsDOMHandler::SaveSetting(const base::ListValue* args) {
  if (rewards_service_) {
    std::string key;
    std::string value;
    args->GetString(0, &key);
    args->GetString(1, &value);

    if (key == "enabledMain") {
      rewards_service_->SetRewardsMainEnabled(value == "true");
    }

    if (key == "contributionMonthly") {
      rewards_service_->SetUserChangedContribution();
      rewards_service_->SetContributionAmount(std::stod(value));
      GetAllBalanceReports();
    }

    if (key == "contributionMinTime") {
      rewards_service_->SetPublisherMinVisitTime(std::stoull(value));
    }

    if (key == "contributionMinVisits") {
      rewards_service_->SetPublisherMinVisits(std::stoul(value));
    }

    if (key == "contributionNonVerified") {
      rewards_service_->SetPublisherAllowNonVerified(value == "true");
    }

    if (key == "contributionVideos") {
      rewards_service_->SetPublisherAllowVideos(value == "true");
    }

    if (key == "enabledContribute") {
      rewards_service_->SetAutoContribute(value == "true");
    }
  }
}

void RewardsDOMHandler::ExcludePublisher(const base::ListValue *args) {
  if (rewards_service_) {
    std::string publisherKey;
    args->GetString(0, &publisherKey);
    rewards_service_->ExcludePublisher(publisherKey);
  }
}

void RewardsDOMHandler::RestorePublishers(const base::ListValue *args) {
  if (rewards_service_) {
    rewards_service_->RestorePublishers();
  }
}

void RewardsDOMHandler::OnGetContentSiteList(std::unique_ptr<brave_rewards::ContentSiteList> list, uint32_t record) {
  if (web_ui()->CanCallJavascript()) {
    auto publishers = std::make_unique<base::ListValue>();
    for (auto const& item : *list) {
      auto publisher = std::make_unique<base::DictionaryValue>();
      publisher->SetString("id", item.id);
      publisher->SetDouble("percentage", item.percentage);
      publisher->SetString("publisherKey", item.id);
      publisher->SetBoolean("verified", item.verified);
      publisher->SetInteger("excluded", item.excluded);
      publisher->SetString("name", item.name);
      publisher->SetString("provider", item.provider);
      publisher->SetString("url", item.url);
      publisher->SetString("favIcon", item.favicon_url);
      publishers->Append(std::move(publisher));
    }

    web_ui()->CallJavascriptFunctionUnsafe("brave_rewards.contributeList", *publishers);
  }
}


void RewardsDOMHandler::GetBalanceReports(const base::ListValue* args) {
  GetAllBalanceReports();
}

void RewardsDOMHandler::WalletExists(const base::ListValue* args) {
  if (rewards_service_ && web_ui()->CanCallJavascript()) {
    bool exist = rewards_service_->IsWalletCreated();

    web_ui()->CallJavascriptFunctionUnsafe("brave_rewards.walletExists", base::Value(exist));
  }
}

void RewardsDOMHandler::GetContributionAmount(const base::ListValue* args) {
  if (rewards_service_ && web_ui()->CanCallJavascript()) {
    double amount = rewards_service_->GetContributionAmount();

    web_ui()->CallJavascriptFunctionUnsafe("brave_rewards.contributionAmount", base::Value(amount));
  }
}

void RewardsDOMHandler::OnReconcileComplete(brave_rewards::BraveRewardsService* rewards_service,
  unsigned int result,
  const std::string& viewing_id,
  const std::string& probi) {
  GetAllBalanceReports();
}

void RewardsDOMHandler::HandleSetPublisherMinVisitTime(
  const base::ListValue* args) {
  LOG(ERROR) << "!!!HandleSetPublisherMinVisitTime";

  Profile* profile = Profile::FromWebUI(web_ui());
  brave_rewards::BraveRewardsService* brave_rewards_service =
    BraveRewardsServiceFactory::GetForProfile(profile);

  if (brave_rewards_service) {
    std::string value_str;
    uint64_t value = -1;
    if ( !args->GetString(0, &value_str) || !base::StringToUint64(value_str, &value)) {
      LOG(ERROR) << "Failed to extract HandleSetPublisherMinVisitTime value.";
      return;
    }
    else{
      brave_rewards_service->SetPublisherMinVisitTime(value);
    }
  }
}

void RewardsDOMHandler::HandleGetPublisherMinVisitTime(
  const base::ListValue* args) {
  LOG(ERROR) << "!!!HandleGetPublisherMinVisitTime";

  Profile* profile = Profile::FromWebUI(web_ui());
  brave_rewards::BraveRewardsService* brave_rewards_service =
    BraveRewardsServiceFactory::GetForProfile(profile);

  if (brave_rewards_service) {
    uint64_t value = brave_rewards_service->GetPublisherMinVisitTime();

    //base::Value doesn't accept uint64_t type
    base::Value v( (int)value);
    web_ui()->CallJavascriptFunctionUnsafe("returnPublisherMinVisitTime", v);
  }
}

void RewardsDOMHandler::HandleSetPublisherMinVisits(
  const base::ListValue* args) {
  LOG(ERROR) << "!!!HandleSetPublisherMinVisits";

  Profile* profile = Profile::FromWebUI(web_ui());
  brave_rewards::BraveRewardsService* brave_rewards_service =
    BraveRewardsServiceFactory::GetForProfile(profile);

  if (brave_rewards_service) {
    std::string value_str;
    unsigned int value = -1;
    if (!args->GetString(0, &value_str) || !base::StringToUint(value_str, &value)) {
      LOG(ERROR) << "Failed to extract HandleSetPublisherMinVisits value.";
      return;
    }
    else {
      brave_rewards_service->SetPublisherMinVisits(value);
    }
  }
}

void RewardsDOMHandler::HandleGetPublisherMinVisits(
  const base::ListValue* args) {
  LOG(ERROR) << "!!!HandleGetPublisherMinVisits";

  Profile* profile = Profile::FromWebUI(web_ui());
  brave_rewards::BraveRewardsService* brave_rewards_service =
    BraveRewardsServiceFactory::GetForProfile(profile);

  if (brave_rewards_service) {
    unsigned int value = brave_rewards_service->GetPublisherMinVisits();

    //base::Value doesn't accept unsigned int type
    base::Value v((int)value);
    web_ui()->CallJavascriptFunctionUnsafe("returnPublisherMinVisits", v);
  }
}

void RewardsDOMHandler::HandleSetPublisherAllowNonVerified(
  const base::ListValue* args) {
  LOG(ERROR) << "!!!HandleSetPublisherAllowNonVerified";

  Profile* profile = Profile::FromWebUI(web_ui());
  brave_rewards::BraveRewardsService* brave_rewards_service =
    BraveRewardsServiceFactory::GetForProfile(profile);

  if (brave_rewards_service) {
    std::string value_str;
    if (!args->GetString(0, &value_str) || (value_str != "true" && value_str != "false") ) {
      LOG(ERROR) << "Failed to extract HandleSetPublisherAllowNonVerified value.";
      return;
    }
    else {
      brave_rewards_service->SetPublisherAllowNonVerified(value_str == "true" ? true : false);
    }
  }
}

void RewardsDOMHandler::HandleGetPublisherAllowNonVerified(
  const base::ListValue* args) {
  LOG(ERROR) << "!!!HandleGetPublisherAllowNonVerified";

  Profile* profile = Profile::FromWebUI(web_ui());
  brave_rewards::BraveRewardsService* brave_rewards_service =
    BraveRewardsServiceFactory::GetForProfile(profile);

  if (brave_rewards_service) {
    bool value = brave_rewards_service->GetPublisherAllowNonVerified();
    base::Value v(value);
    web_ui()->CallJavascriptFunctionUnsafe("returnPublisherAllowNonVerified", v);
  }
}

void RewardsDOMHandler::HandleSetContributionAmount(
  const base::ListValue* args) {
  LOG(ERROR) << "!!!HandleSetContributionAmount";

  Profile* profile = Profile::FromWebUI(web_ui());
  brave_rewards::BraveRewardsService* brave_rewards_service =
    BraveRewardsServiceFactory::GetForProfile(profile);

  if (brave_rewards_service) {
    std::string value_str;
    double value = -1.0;
    if (!args->GetString(0, &value_str) || !base::StringToDouble(value_str, &value)) {
      LOG(ERROR) << "Failed to extract HandleSetContributionAmount value.";
      return;
    }
    else {
      brave_rewards_service->SetContributionAmount(value);
    }
  }
}

void RewardsDOMHandler::HandleGetContributionAmount(
  const base::ListValue* args) {
  LOG(ERROR) << "!!!HandleGetContributionAmount";

  Profile* profile = Profile::FromWebUI(web_ui());
  brave_rewards::BraveRewardsService* brave_rewards_service =
    BraveRewardsServiceFactory::GetForProfile(profile);

  if (brave_rewards_service) {
    double value = brave_rewards_service->GetContributionAmount();
    base::Value v(value);
    web_ui()->CallJavascriptFunctionUnsafe("returnContributionAmount", v);
  }
}

}  // namespace

///////////////////////////////////////////////////////////////////////////////
//
// BraveRewardsUI
//
///////////////////////////////////////////////////////////////////////////////

BraveRewardsUI::BraveRewardsUI(content::WebUI* web_ui, const std::string& name)
    : BasicUI(web_ui, name, chrome::kRewardsJS,
        IDR_BRAVE_REWARDS_UI_JS, IDR_BRAVE_REWARDS_UI_HTML) {

  auto handler_owner = std::make_unique<RewardsDOMHandler>();
  RewardsDOMHandler * handler = handler_owner.get();
  web_ui->AddMessageHandler(std::move(handler_owner));
  handler->Init();
}


BraveRewardsUI::~BraveRewardsUI() {
}
