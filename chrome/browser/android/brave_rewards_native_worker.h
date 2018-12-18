/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

#ifndef BRAVE_REWARDS_NATIVE_WORKER_H_
#define BRAVE_REWARDS_NATIVE_WORKER_H_

#include <jni.h>
#include <memory>

#include "base/android/jni_weak_ref.h"
#include "brave/components/brave_rewards/browser/rewards_service_observer.h"
#include "brave/components/brave_rewards/browser/wallet_properties.h"

namespace brave_rewards {
  class RewardsService;
}

namespace chrome {
namespace android {

class BraveRewardsNativeWorker : public brave_rewards::RewardsServiceObserver {
public:
    BraveRewardsNativeWorker(JNIEnv* env, const base::android::JavaRef<jobject>& obj);
    ~BraveRewardsNativeWorker() override;

    void Destroy(JNIEnv* env, const
        base::android::JavaParamRef<jobject>& jcaller);

    void CreateWallet(JNIEnv* env, const
        base::android::JavaParamRef<jobject>& jcaller);

    bool WalletExist(JNIEnv* env, const
        base::android::JavaParamRef<jobject>& jcaller);

    void GetWalletProperties(JNIEnv* env, const
        base::android::JavaParamRef<jobject>& jcaller);

    double GetWalletBalance(JNIEnv* env, const base::android::JavaParamRef<jobject>& obj);

    double GetWalletRate(JNIEnv* env, const base::android::JavaParamRef<jobject>& obj,
        const base::android::JavaParamRef<jstring>& rate);

    void OnWalletInitialized(brave_rewards::RewardsService* rewards_service,
        int error_code) override;

    void OnWalletProperties(brave_rewards::RewardsService* rewards_service,
        int error_code, 
        std::unique_ptr<brave_rewards::WalletProperties> wallet_properties) override;

private:
    JavaObjectWeakGlobalRef weak_java_brave_rewards_native_worker_;
    brave_rewards::RewardsService* brave_rewards_service_;
    std::unique_ptr<brave_rewards::WalletProperties> wallet_properties_;
};
}
}

#endif // BRAVE_REWARDS_NATIVE_WORKER_H_
