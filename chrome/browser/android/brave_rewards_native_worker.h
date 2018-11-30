/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

#ifndef BRAVE_REWARDS_NATIVE_WORKER_H_
#define BRAVE_REWARDS_NATIVE_WORKER_H_

#include <jni.h>
#include "base/android/jni_weak_ref.h"

namespace brave_rewards {
  class RewardsService;
}

namespace chrome {
namespace android {

class BraveRewardsNativeWorker {
public:
    BraveRewardsNativeWorker(JNIEnv* env, const base::android::JavaRef<jobject>& obj);
    ~BraveRewardsNativeWorker();

    void Destroy(JNIEnv* env, const
        base::android::JavaParamRef<jobject>& jcaller);

    void CreateWallet(JNIEnv* env, const
        base::android::JavaParamRef<jobject>& jcaller);

private:
    JavaObjectWeakGlobalRef weak_java_brave_rewards_native_worker_;
    brave_rewards::RewardsService* brave_rewards_service_;
};
}
}

#endif // BRAVE_REWARDS_NATIVE_WORKER_H_