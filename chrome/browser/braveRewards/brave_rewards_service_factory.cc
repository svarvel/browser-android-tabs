/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */

#include "brave_rewards_service_factory.h"

#include "brave_rewards_service.h"
#include "brave_rewards_service_impl.h"
#include "chrome/browser/profiles/incognito_helpers.h"
#include "chrome/browser/profiles/profile.h"
#include "components/keyed_service/content/browser_context_dependency_manager.h"

// static
brave_rewards::BraveRewardsService* BraveRewardsServiceFactory::GetForProfile(
    Profile* profile) {
  if (profile->IsOffTheRecord())
    return NULL;

  return static_cast<brave_rewards::BraveRewardsService*>(
      GetInstance()->GetServiceForBrowserContext(profile, true));
}

// static
BraveRewardsServiceFactory* BraveRewardsServiceFactory::GetInstance() {
  return base::Singleton<BraveRewardsServiceFactory>::get();
}

BraveRewardsServiceFactory::BraveRewardsServiceFactory()
    : BrowserContextKeyedServiceFactory(
          "BraveRewardsService",
          BrowserContextDependencyManager::GetInstance()) {
}

BraveRewardsServiceFactory::~BraveRewardsServiceFactory() {
}

KeyedService* BraveRewardsServiceFactory::BuildServiceInstanceFor(
    content::BrowserContext* context) const {
  std::unique_ptr<brave_rewards::BraveRewardsServiceImpl> brave_rewards_service(
      new brave_rewards::BraveRewardsServiceImpl(Profile::FromBrowserContext(context)));
  brave_rewards_service->Init();
  return brave_rewards_service.release();
}

content::BrowserContext* BraveRewardsServiceFactory::GetBrowserContextToUse(
    content::BrowserContext* context) const {
  if (context->IsOffTheRecord())
    return chrome::GetBrowserContextOwnInstanceInIncognito(context);
  // use original profile for session profiles
  return chrome::GetBrowserContextRedirectedInIncognito(context);
}

bool BraveRewardsServiceFactory::ServiceIsNULLWhileTesting() const {
  return true;
}

