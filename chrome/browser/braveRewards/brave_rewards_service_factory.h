/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */

#ifndef BRAVE_REWARDS_SERVICE_FACTORY_H_
#define BRAVE_REWARDS_SERVICE_FACTORY_H_

#include "base/memory/singleton.h"
#include "components/keyed_service/content/browser_context_keyed_service_factory.h"

class Profile;

namespace brave_rewards {
class BraveRewardsService;
}

// Singleton that owns all BraveRewardsService and associates them with
// Profiles.
class BraveRewardsServiceFactory : public BrowserContextKeyedServiceFactory {
 public:
  static brave_rewards::BraveRewardsService* GetForProfile(Profile* profile);

  static BraveRewardsServiceFactory* GetInstance();

 private:
  friend struct base::DefaultSingletonTraits<BraveRewardsServiceFactory>;

  BraveRewardsServiceFactory();
  ~BraveRewardsServiceFactory() override;

  // BrowserContextKeyedServiceFactory:
  KeyedService* BuildServiceInstanceFor(
      content::BrowserContext* context) const override;
  content::BrowserContext* GetBrowserContextToUse(
      content::BrowserContext* context) const override;
  bool ServiceIsNULLWhileTesting() const override;

  DISALLOW_COPY_AND_ASSIGN(BraveRewardsServiceFactory);
};

#endif  // BRAVE_REWARDS_SERVICE_FACTORY_H_
