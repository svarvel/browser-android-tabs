/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */

#ifndef BRAVE_REWARDS_SERVICE_
#define BRAVE_REWARDS_SERVICE_

#include "base/macros.h"
#include "base/observer_list.h"
#include "components/keyed_service/core/keyed_service.h"

namespace brave_rewards {

class BraveRewardsServiceObserver;

class BraveRewardsService : public KeyedService {
public:
  BraveRewardsService();
  ~BraveRewardsService() override;

  // KeyedService:
  void Shutdown() override;

  virtual void CreateWallet() = 0;
  virtual void SaveVisit(const std::string& publisher,
                 uint64_t duration,
                 bool ignoreMinTime) = 0;

  virtual void SetPublisherMinVisitTime(uint64_t duration_in_milliseconds) = 0;
  virtual void SetPublisherMinVisits(unsigned int visits) = 0;
  virtual void SetPublisherAllowNonVerified(bool allow) = 0;
  virtual void SetContributionAmount(double amount) = 0;

  virtual uint64_t GetPublisherMinVisitTime() const = 0; // In milliseconds
  virtual unsigned int GetPublisherMinVisits() const = 0;
  virtual bool GetPublisherAllowNonVerified() const = 0;
  virtual double GetContributionAmount() const = 0;

  void AddObserver(BraveRewardsServiceObserver* observer);
  void RemoveObserver(BraveRewardsServiceObserver* observer);

protected:
  base::ObserverList<BraveRewardsServiceObserver> observers_;

private:
  DISALLOW_COPY_AND_ASSIGN(BraveRewardsService);
};

}  // namespace brave_rewards

#endif  // BRAVE_REWARDS_SERVICE_
