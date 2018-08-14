 /* This Source Code Form is subject to the terms of the Mozilla Public
  * License, v. 2.0. If a copy of the MPL was not distributed with this file,
  * You can obtain one at http://mozilla.org/MPL/2.0/. */

#ifndef BRAVE_REWARDS_SERVICE_OBSERVER_H_
#define BRAVE_REWARDS_SERVICE_OBSERVER_H_

namespace brave_rewards {

class BraveRewardsService;

class BraveRewardsServiceObserver {
 public:
  virtual ~BraveRewardsServiceObserver() {}

  virtual void OnWalletInitialized(BraveRewardsService* payment_service,
                               int error_code) {};
};

}  // namespace brave_rewards

#endif  // BRAVE_REWARDS_SERVICE_OBSERVER_H_
