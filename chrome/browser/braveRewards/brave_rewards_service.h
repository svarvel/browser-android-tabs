/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */

#ifndef BRAVE_REWARDS_SERVICE_
#define BRAVE_REWARDS_SERVICE_

#include "bat/ledger/ledger.h"
#include "base/macros.h"
#include "base/observer_list.h"
#include "content_site.h"
#include "components/keyed_service/core/keyed_service.h"

namespace brave_rewards {

class BraveRewardsServiceObserver;

//using GetPublishersListCallback =
    //std::function<void(const ledger::PublisherInfoList& list,
//        uint32_t /* next_record */)>;

class BraveRewardsService : public KeyedService {
public:
  BraveRewardsService();
  ~BraveRewardsService() override;

  // KeyedService:
  void Shutdown() override;

  virtual void CreateWallet() = 0;

  virtual void MakePayment(const ledger::PaymentData& paid_data) = 0;
  virtual void AddRecurringPayment(const std::string& domain, const double& value) = 0;
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
      const std::string& url,
      const std::map<std::string, std::string>& parts,
      const uint64_t& current_time) = 0;
  /*virtual void SaveVisit(const std::string& publisher,
                 uint64_t duration,
                 bool ignoreMinTime) = 0;*/

  virtual void GetRecurringDonationPublisherInfo(ledger::PublisherInfoCallback callback) = 0;
  virtual void GetPublisherInfoList(uint32_t start,
                                uint32_t limit,
                                ledger::PUBLISHER_CATEGORY category,
                                const std::string& month,
                                const std::string& year,
                                ledger::GetPublisherInfoListCallback callback) = 0;
  virtual void SetPublisherMinVisitTime(uint64_t duration_in_milliseconds) = 0;
  virtual void SetPublisherMinVisits(unsigned int visits) = 0;
  virtual void SetPublisherAllowNonVerified(bool allow) = 0;
  virtual void SetContributionAmount(double amount) = 0;
  virtual void SetBalanceReport(const ledger::BalanceReportInfo& report_info) = 0;

  virtual uint64_t GetPublisherMinVisitTime() const = 0; // In milliseconds
  virtual unsigned int GetPublisherMinVisits() const = 0;
  virtual bool GetPublisherAllowNonVerified() const = 0;
  virtual double GetContributionAmount() const = 0;
  virtual bool GetBalanceReport(ledger::BalanceReportInfo* report_info) const = 0;

  void AddObserver(BraveRewardsServiceObserver* observer);
  void RemoveObserver(BraveRewardsServiceObserver* observer);

protected:
  base::ObserverList<BraveRewardsServiceObserver> observers_;

private:
  DISALLOW_COPY_AND_ASSIGN(BraveRewardsService);
};

}  // namespace brave_rewards

#endif  // BRAVE_REWARDS_SERVICE_
