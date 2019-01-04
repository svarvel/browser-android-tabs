/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

#ifndef BRAVE_REWARDS_NATIVE_WORKER_H_
#define BRAVE_REWARDS_NATIVE_WORKER_H_

#include <jni.h>
#include <memory>
#include <map>
#include "base/android/jni_weak_ref.h"
#include "brave/components/brave_rewards/browser/balance_report.h"
#include "brave/components/brave_rewards/browser/rewards_service_observer.h"
#include "brave/components/brave_rewards/browser/rewards_service_private_observer.h"
#include "brave/components/brave_rewards/browser/wallet_properties.h"
#include "brave/vendor/bat-native-ledger/include/bat/ledger/publisher_info.h"

namespace brave_rewards {
  class RewardsService;
}

namespace chrome {
namespace android {

class BraveRewardsNativeWorker : public brave_rewards::RewardsServiceObserver,
    public brave_rewards::RewardsServicePrivateObserver {
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

    void GetPublisherInfo(JNIEnv* env, const
        base::android::JavaParamRef<jobject>& jcaller, int tabId,
        const base::android::JavaParamRef<jstring>& host);

    double GetWalletBalance(JNIEnv* env, const base::android::JavaParamRef<jobject>& obj);

    double GetWalletRate(JNIEnv* env, const base::android::JavaParamRef<jobject>& obj,
        const base::android::JavaParamRef<jstring>& rate);

    base::android::ScopedJavaLocalRef<jstring> GetPublisherURL(JNIEnv* env, 
        const base::android::JavaParamRef<jobject>& obj, uint64_t tabId);

    base::android::ScopedJavaLocalRef<jstring> GetPublisherFavIconURL(JNIEnv* env, 
        const base::android::JavaParamRef<jobject>& obj, uint64_t tabId);

    base::android::ScopedJavaLocalRef<jstring> GetPublisherName(JNIEnv* env, 
        const base::android::JavaParamRef<jobject>& obj, uint64_t tabId);

    base::android::ScopedJavaLocalRef<jstring> GetPublisherId(JNIEnv* env, 
        const base::android::JavaParamRef<jobject>& obj, uint64_t tabId);

    int GetPublisherPercent(JNIEnv* env, const base::android::JavaParamRef<jobject>& obj,
        uint64_t tabId);

    bool GetPublisherExcluded(JNIEnv* env, const base::android::JavaParamRef<jobject>& obj,
        uint64_t tabId);

    bool GetPublisherVerified(JNIEnv* env, const base::android::JavaParamRef<jobject>& obj,
        uint64_t tabId);

    void GetCurrentBalanceReport(JNIEnv* env, const base::android::JavaParamRef<jobject>& obj);

    void IncludeInAutoContribution(JNIEnv* env, const base::android::JavaParamRef<jobject>& obj,
        uint64_t tabId, bool exclude);

    void RemovePublisherFromMap(JNIEnv* env, 
        const base::android::JavaParamRef<jobject>& obj, uint64_t tabId);

    void OnWalletInitialized(brave_rewards::RewardsService* rewards_service,
        int error_code) override;

    void OnWalletProperties(brave_rewards::RewardsService* rewards_service,
        int error_code, 
        std::unique_ptr<brave_rewards::WalletProperties> wallet_properties) override;

    void OnGetPublisherActivityFromUrl(
        brave_rewards::RewardsService* rewards_service,
        int error_code,
        std::unique_ptr<ledger::PublisherInfo> info,
        uint64_t tabId) override;

    void OnGetCurrentBalanceReport(brave_rewards::RewardsService* rewards_service,
        const brave_rewards::BalanceReport& balance_report) override;

private:
    JavaObjectWeakGlobalRef weak_java_brave_rewards_native_worker_;
    brave_rewards::RewardsService* brave_rewards_service_;
    std::unique_ptr<brave_rewards::WalletProperties> wallet_properties_;
    std::map<uint64_t, ledger::PublisherInfo> map_publishers_info_;
};
}
}

#endif // BRAVE_REWARDS_NATIVE_WORKER_H_
