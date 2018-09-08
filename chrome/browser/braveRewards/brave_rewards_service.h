/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */

#ifndef BRAVE_REWARDS_SERVICE_
#define BRAVE_REWARDS_SERVICE_

#include "bat/ledger/ledger.h"
#include "base/macros.h"
#include "base/observer_list.h"
#include "content_site.h"
#include "balance_report.h"
#include "components/sessions/core/session_id.h"
#include "components/keyed_service/core/keyed_service.h"
#include "url/gurl.h"

#include "brave_rewards_service_observer.h"

namespace content {
class NavigationHandle;
}

namespace brave_rewards {

bool IsMediaLink(const GURL& url,
                 const GURL& first_party_url,
                 const GURL& referrer);

class RewardsServiceObserver;

using GetContentSiteListCallback =
    base::Callback<void(std::unique_ptr<ContentSiteList>,
        uint32_t /* next_record */)>;

class BraveRewardsService : public KeyedService {
public:
  BraveRewardsService();
  ~BraveRewardsService() override;

  virtual void CreateWallet() = 0;
  virtual void GetWalletProperties() = 0;
  virtual void GetContentSiteList(uint32_t start,
                                  uint32_t limit,
                                const GetContentSiteListCallback& callback) = 0;
  virtual void GetGrant(const std::string& lang, const std::string& paymentId) = 0;
  virtual void GetGrantCaptcha() = 0;
  virtual void SolveGrantCaptcha(const std::string& solution) const = 0;
  virtual std::string GetWalletPassphrase() const = 0;
  virtual void RecoverWallet(const std::string passPhrase) const = 0;
  virtual void OnLoad(const std::string& _tld,
            const std::string& _domain,
            const std::string& _path,
            uint32_t tab_id) = 0;
  virtual void OnUnload(uint32_t tab_id) = 0;
  virtual void OnShow(uint32_t tab_id) = 0;
  virtual void OnHide(uint32_t tab_id) = 0;
  virtual void OnForeground(uint32_t tab_id) = 0;
  virtual void OnBackground(uint32_t tab_id) = 0;
  virtual void OnMediaStart(uint32_t tab_id) = 0;
  virtual void OnMediaStop(uint32_t tab_id) = 0;
  virtual void OnXHRLoad(uint32_t tab_id,
                         const GURL& url,
                         const std::string & first_party_url,
                         const std::string & referrer) = 0;
  virtual void OnPostData(/*SessionID tab_id,*/
                          const GURL& url,
                          const std::string & first_party_url,
                          const std::string & referrer,
                          const std::string& post_data) = 0;

  virtual uint64_t GetReconcileStamp() const = 0;
  virtual std::map<std::string, std::string> GetAddresses() const = 0;
  virtual void SetPublisherMinVisitTime(uint64_t duration_in_seconds) = 0;
  virtual void SetPublisherMinVisits(unsigned int visits) = 0;
  virtual void SetPublisherAllowNonVerified(bool allow) = 0;
  virtual void SetPublisherAllowVideos(bool allow) = 0;
  virtual void SetContributionAmount(double amount) = 0;
  virtual void SetAutoContribute(bool enabled) = 0;
  virtual void SetBalanceReport(const ledger::BalanceReportInfo& report_info) = 0;

  virtual uint64_t GetPublisherMinVisitTime() const = 0; // In milliseconds
  virtual unsigned int GetPublisherMinVisits() const = 0;
  virtual bool GetPublisherAllowNonVerified() const = 0;
  virtual double GetContributionAmount() const = 0;
  virtual bool GetBalanceReport(ledger::BalanceReportInfo* report_info) const = 0;

  virtual void SetTimer(uint64_t time_offset, uint32_t& timer_id) = 0;
  virtual std::map<std::string, brave_rewards::BalanceReport> GetAllBalanceReports() = 0;

  void AddObserver(BraveRewardsServiceObserver* observer);
  void RemoveObserver(BraveRewardsServiceObserver* observer);

protected:
  base::ObserverList<BraveRewardsServiceObserver> observers_;

private:
  DISALLOW_COPY_AND_ASSIGN(BraveRewardsService);
};

}  // namespace brave_rewards

#endif  // BRAVE_REWARDS_SERVICE_
