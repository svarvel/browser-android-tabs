/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

#ifndef SHIELDS_CONFIG_H_
#define SHIELDS_CONFIG_H_

#include <jni.h>
#include "url/gurl.h"
#include "content/public/common/referrer.h"
#include "../../../../base/android/jni_weak_ref.h"

namespace net {
namespace blockers {

class ShieldsConfig {
public:
    ShieldsConfig(JNIEnv* env, jobject obj);
    ~ShieldsConfig();

    std::string getHostSettings(const bool &incognitoTab, const std::string& host);
    void setBlockedCountInfo(const std::string& url, int trackersBlocked, int adsBlocked, int httpsUpgrades,
            int scriptsBlocked, int fingerprintingBlocked);
    bool needUpdateAdBlocker();
    void resetUpdateAdBlockerFlag();

    static ShieldsConfig* getShieldsConfig();
    static bool shouldSetReferrer(bool allow_referrers, bool shields_up,
        const GURL& original_referrer, const GURL& tab_origin,
        const GURL& target_url, const GURL& new_referrer_url,
        blink::WebReferrerPolicy policy, content::Referrer *output_referrer);
    // Register the ShieldsConfig's native methods through JNI.
    //static bool RegisterShieldsConfig(JNIEnv* env);

private:
    JavaObjectWeakGlobalRef weak_java_shields_config_;
};
}
}

#endif //SHIELDS_CONFIG_H_
