 /* This Source Code Form is subject to the terms of the Mozilla Public
  * License, v. 2.0. If a copy of the MPL was not distributed with this file,
  * You can obtain one at http://mozilla.org/MPL/2.0/. */

 #ifndef BRAVE_REWARDS_SERVICE_IMPL_
 #define BRAVE_REWARDS_SERVICE_IMPL_

#include <functional>
#include <map>
#include <memory>
#include <string>

#include "bat/ledger/ledger.h"
#include "bat/ledger/wallet_info.h"
#include "base/files/file_path.h"
#include "base/observer_list.h"
#include "base/memory/weak_ptr.h"
#include "base/timer/timer.h"
#include "bat/ledger/ledger_client.h"
#include "brave_rewards_service.h"
#include "content/public/browser/browser_thread.h"
//#include "extensions/common/one_shot_event.h" TODO
#include "net/url_request/url_fetcher_delegate.h"
#include "balance_report.h"

namespace base {
class SequencedTaskRunner;
}  // namespace base

namespace ledger {
class Ledger;
class LedgerCallbackHandler;
struct LedgerMediaPublisherInfo;
}  // namespace ledger

namespace leveldb {
class DB;
}  // namespace leveldb

namespace net {
class URLFetcher;
}

class Profile;

namespace brave_rewards {

class PublisherInfoDatabase;
class MediaPublisherInfoBackend;

class BraveRewardsServiceImpl : public BraveRewardsService,
                            public ledger::LedgerClient,
                            public net::URLFetcherDelegate,
                            public base::SupportsWeakPtr<BraveRewardsServiceImpl> {
public:
  static bool IsMediaLink(const std::string& url, const std::string& first_party_url, const std::string& referrer);

  BraveRewardsServiceImpl(Profile* profile);
  ~BraveRewardsServiceImpl() override;

  // KeyedService:
  void Shutdown() override;

  void Init();
  void CreateWallet() override;
  void GetWalletProperties() override;
  void GetGrant(const std::string& lang, const std::string& paymentId) override;
  void GetGrantCaptcha() override;
  void SolveGrantCaptcha(const std::string& solution) const override;
  std::string GetWalletPassphrase() const override;
  unsigned int GetNumExcludedSites() const override;
  void RecoverWallet(const std::string passPhrase) const override;
  void GetContentSiteList(uint32_t start,
                          uint32_t limit,
     const GetContentSiteListCallback& callback) override;
  void OnLoad(const std::string& _tld,
            const std::string& _domain,
            const std::string& _path,
            uint32_t tab_id) override;
  void OnUnload(uint32_t tab_id) override;
  void OnShow(uint32_t tab_id) override;
  void OnHide(uint32_t tab_id) override;
  void OnForeground(uint32_t tab_id) override;
  void OnBackground(uint32_t tab_id) override;
  void OnMediaStart(uint32_t tab_id) override;
  void OnMediaStop(uint32_t tab_id) override;
  void OnXHRLoad(uint32_t tab_id,
                 const GURL& url,
                 const std::string & first_party_url,
                 const std::string & referrer) override;
  void OnPostData(/*SessionID tab_id,*/
                  const GURL& url,
                  const std::string & first_party_url,
                  const std::string & referrer,
                  const std::string& post_data) override;

  void SetPublisherMinVisitTime(uint64_t duration_in_milliseconds) override;
  void SetPublisherMinVisits(unsigned int visits) override;
  void SetPublisherAllowNonVerified(bool allow) override;
  void SetContributionAmount(double amount) override;
  void SetBalanceReport(const ledger::BalanceReportInfo& report_info) override;

  uint64_t GetPublisherMinVisitTime() const override; // In milliseconds
  unsigned int GetPublisherMinVisits() const override;
  bool GetPublisherAllowNonVerified() const override;
  double GetContributionAmount() const override;
  bool GetBalanceReport(ledger::BalanceReportInfo* report_info) const override;

  std::string URIEncode(const std::string& value) override;
  uint64_t GetReconcileStamp() const override;
  std::map<std::string, std::string> GetAddresses() const override;
  void LoadMediaPublisherInfo(
      const std::string& media_key,
      ledger::PublisherInfoCallback callback) override;
  void SaveMediaPublisherInfo(const std::string& media_key, const std::string& publisher_id) override;
  void ExcludePublisher(const std::string publisherKey) const override;
  void RestorePublishers() override;
  std::map<std::string, brave_rewards::BalanceReport> GetAllBalanceReports() override;
  bool IsWalletCreated() override;

 private:
  friend void RunIOTaskCallback(
      base::WeakPtr<BraveRewardsServiceImpl>,
      std::function<void(void)>);
  typedef base::Callback<void(int, const std::string&, const std::map<std::string, std::string>& headers)> FetchCallback;

  //const extensions::OneShotEvent& ready() const { return ready_; } TODO
  void OnLedgerStateSaved(ledger::LedgerCallbackHandler* handler,
                          bool success);
  void OnLedgerStateLoaded(ledger::LedgerCallbackHandler* handler,
                              const std::string& data);
  void OnPublisherStateSaved(ledger::LedgerCallbackHandler* handler,
                             bool success);
  void OnPublisherStateLoaded(ledger::LedgerCallbackHandler* handler,
                              const std::string& data);
  void TriggerOnWalletInitialized(int error_code);
  void TriggerOnWalletProperties(int error_code,
                                 std::unique_ptr<ledger::WalletInfo> result);
  void TriggerOnGrant(ledger::Result result, const ledger::Grant& grant);
  void TriggerOnGrantCaptcha(const std::string& image, const std::string& hint);
  void TriggerOnRecoverWallet(ledger::Result result,
                              double balance,
                              const std::vector<ledger::Grant>& grants);
  void TriggerOnGrantFinish(ledger::Result result, const ledger::Grant& grant);
  void OnPublisherInfoSaved(ledger::PublisherInfoCallback callback,
                            std::unique_ptr<ledger::PublisherInfo> info,
                            bool success);
  void OnPublisherInfoLoaded(ledger::PublisherInfoCallback callback,
                             const ledger::PublisherInfoList list);
  void OnMediaPublisherInfoSaved(bool success);
  void OnMediaPublisherInfoLoaded(ledger::PublisherInfoCallback callback,
                             std::unique_ptr<ledger::PublisherInfo> info);
  void OnPublisherInfoListLoaded(uint32_t start,
                                 uint32_t limit,
                                 ledger::GetPublisherInfoListCallback callback,
                                 const ledger::PublisherInfoList& list);
  void OnPublishersListSaved(ledger::LedgerCallbackHandler* handler,
                             bool success);
  void OnTimer(uint32_t timer_id);
  void TriggerOnContentSiteUpdated();
  void OnPublisherListLoaded(ledger::LedgerCallbackHandler* handler,
                             const std::string& data);

  // ledger::LedgerClient
  std::string GenerateGUID() const override;
  void OnWalletInitialized(ledger::Result result) override;
  void OnWalletProperties(ledger::Result result,
                          std::unique_ptr<ledger::WalletInfo> info) override;
  void OnGrant(ledger::Result result, const ledger::Grant& grant) override;
  void OnGrantCaptcha(const std::string& image, const std::string& hint) override;
  void OnRecoverWallet(ledger::Result result,
                      double balance,
                      const std::vector<ledger::Grant>& grants) override;
  void OnReconcileComplete(ledger::Result result,
                           const std::string& viewing_id,
                           ledger::PUBLISHER_CATEGORY category,
                           const std::string& probi) override;
  void OnGrantFinish(ledger::Result result,
                     const ledger::Grant& grant) override;
  void LoadLedgerState(ledger::LedgerCallbackHandler* handler) override;
  void LoadPublisherState(ledger::LedgerCallbackHandler* handler) override;
  void SaveLedgerState(const std::string& ledger_state,
                       ledger::LedgerCallbackHandler* handler) override;
  void SavePublisherState(const std::string& publisher_state,
                          ledger::LedgerCallbackHandler* handler) override;

  void SavePublisherInfo(std::unique_ptr<ledger::PublisherInfo> publisher_info,
                         ledger::PublisherInfoCallback callback) override;
  void LoadPublisherInfo(ledger::PublisherInfoFilter filter,
                         ledger::PublisherInfoCallback callback) override;
  void LoadPublisherInfoList(
      uint32_t start,
      uint32_t limit,
      ledger::PublisherInfoFilter filter,
      ledger::GetPublisherInfoListCallback callback) override;
  void SavePublishersList(const std::string& publishers_list,
                          ledger::LedgerCallbackHandler* handler) override;
  void SetTimer(uint64_t time_offset, uint32_t& timer_id) override;
  void LoadPublisherList(ledger::LedgerCallbackHandler* handler) override;

  std::unique_ptr<ledger::LedgerURLLoader> LoadURL(const std::string& url,
                   const std::vector<std::string>& headers,
                   const std::string& content,
                   const std::string& contentType,
                   const ledger::URL_METHOD& method,
                   ledger::LedgerCallbackHandler* handler) override;
  void RunIOTask(std::unique_ptr<ledger::LedgerTaskRunner> task) override;
  void SetRewardsMainEnabled(bool enabled) const override;
  void SetPublisherAllowVideos(bool allow) override;
  void SetUserChangedContribution() const override;
  void SetAutoContribute(bool enabled) override;
  void OnPublisherActivity(ledger::Result result,
                          std::unique_ptr<ledger::PublisherInfo> info,
                          uint64_t windowId) override;
  void OnExcludedSitesChanged() override;

  void OnIOTaskComplete(std::function<void(void)> callback);

  // URLFetcherDelegate impl
  void OnURLFetchComplete(const net::URLFetcher* source) override;

  void LoadNicewareList(ledger::GetNicewareListCallback callback) override;
  void LoadCurrentPublisherInfoList(
      uint32_t start,
      uint32_t limit,
      ledger::PublisherInfoFilter filter,
      ledger::GetPublisherInfoListCallback callback) override;
  void FetchFavIcon(const std::string& url, const std::string& favicon_key) override;
  void SaveContributionInfo(const std::string& probi,
                            const int month,
                            const int year,
                            const uint32_t date,
                            const std::string& publisher_key,
                            const ledger::PUBLISHER_CATEGORY category) override;
  void GetRecurringDonations(ledger::RecurringDonationCallback callback) override;
  void OnRemoveRecurring(const std::string& publisher_key, ledger::RecurringRemoveCallback callback) override;

  Profile* profile_;  // NOT OWNED
  std::unique_ptr<ledger::Ledger> ledger_;
  const scoped_refptr<base::SequencedTaskRunner> file_task_runner_;
  const base::FilePath ledger_state_path_;
  const base::FilePath publisher_state_path_;
  const base::FilePath publisher_info_db_path_;
  const base::FilePath publisher_list_path_;
  std::unique_ptr<PublisherInfoDatabase> publisher_info_backend_;

  //extensions::OneShotEvent ready_; TODO
  std::map<const net::URLFetcher*, FetchCallback> fetchers_;
  std::map<uint32_t, std::unique_ptr<base::OneShotTimer>> timers_;

  uint32_t next_timer_id_;

  DISALLOW_COPY_AND_ASSIGN(BraveRewardsServiceImpl);
};

}  // namespace brave_rewards

#endif  // BRAVE_REWARDS_SERVICE_IMPL_
