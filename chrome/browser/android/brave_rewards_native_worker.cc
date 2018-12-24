/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */
#include "brave_rewards_native_worker.h"
#include "base/android/jni_android.h"
#include "base/android/jni_string.h"
#include "brave/components/brave_rewards/browser/rewards_service_factory.h"
#include "brave/components/brave_rewards/browser/rewards_service.h"
#include "chrome/browser/profiles/profile.h"
#include "chrome/browser/profiles/profile_manager.h"
//#include "chrome/browser/ui/webui/favicon_source.h"
#include "content/public/browser/url_data_source.h"
#include "jni/BraveRewardsNativeWorker_jni.h"

namespace chrome {
namespace android {

BraveRewardsNativeWorker::BraveRewardsNativeWorker(JNIEnv* env, const base::android::JavaRef<jobject>& obj):
    weak_java_brave_rewards_native_worker_(env, obj),
    brave_rewards_service_(nullptr) {

  Java_BraveRewardsNativeWorker_setNativePtr(env, obj, reinterpret_cast<intptr_t>(this));

  brave_rewards_service_ = brave_rewards::RewardsServiceFactory::GetForProfile(
      ProfileManager::GetActiveUserProfile()->GetOriginalProfile());
  if (brave_rewards_service_) {
    brave_rewards_service_->AddObserver(this);
    brave_rewards_service_->AddPrivateObserver(this);
  }
  // For favicons cache
  //content::URLDataSource::Add(ProfileManager::GetActiveUserProfile()->GetOriginalProfile(), 
   // std::make_unique<FaviconSource>(ProfileManager::GetActiveUserProfile()->GetOriginalProfile()));
  //
}

BraveRewardsNativeWorker::~BraveRewardsNativeWorker() {
}

void BraveRewardsNativeWorker::Destroy(JNIEnv* env, const
        base::android::JavaParamRef<jobject>& jcaller) {
  if (brave_rewards_service_) {
    brave_rewards_service_->RemoveObserver(this);
    brave_rewards_service_->RemovePrivateObserver(this);
  }
  delete this;
}

void BraveRewardsNativeWorker::CreateWallet(JNIEnv* env, const
        base::android::JavaParamRef<jobject>& jcaller) {
  if (brave_rewards_service_) {
    brave_rewards_service_->CreateWallet();
  }
}

void BraveRewardsNativeWorker::GetWalletProperties(JNIEnv* env, const
        base::android::JavaParamRef<jobject>& jcaller) {
  if (brave_rewards_service_) {
    brave_rewards_service_->FetchWalletProperties();
  }
}

void BraveRewardsNativeWorker::GetPublisherInfo(JNIEnv* env, const
        base::android::JavaParamRef<jobject>& jcaller, int tabId,
        const base::android::JavaParamRef<jstring>& host) {
  if (brave_rewards_service_) {
    LOG(ERROR) << "!!!getting publusher info";
    brave_rewards_service_->GetPublisherActivityFromUrl(tabId,
      base::android::ConvertJavaStringToUTF8(env, host), "");
  }
}

void BraveRewardsNativeWorker::OnGetPublisherActivityFromUrl(
      brave_rewards::RewardsService* rewards_service,
      int error_code,
      std::unique_ptr<ledger::PublisherInfo> info,
      uint64_t tabId) {
  LOG(ERROR) << "!!!in OnGetPublisherActivityFromUrl info == " << info.get();
  if (!info) {
    return;
  }

  map_publishers_info_[tabId] = *info;
  JNIEnv* env = base::android::AttachCurrentThread();
  Java_BraveRewardsNativeWorker_OnPublisherInfo(env, 
        weak_java_brave_rewards_native_worker_.get(env), tabId);
}

base::android::ScopedJavaLocalRef<jstring> BraveRewardsNativeWorker::GetPublisherURL(JNIEnv* env, 
        const base::android::JavaParamRef<jobject>& obj, uint64_t tabId) {
  base::android::ScopedJavaLocalRef<jstring> res;

  std::map<uint64_t, ledger::PublisherInfo>::const_iterator iter(map_publishers_info_.find(tabId));
  if (iter != map_publishers_info_.end()) {
    res = base::android::ConvertUTF8ToJavaString(env, iter->second.url);
  }

  return res;
}

base::android::ScopedJavaLocalRef<jstring> BraveRewardsNativeWorker::GetPublisherFavIconURL(JNIEnv* env, 
        const base::android::JavaParamRef<jobject>& obj, uint64_t tabId) {
  base::android::ScopedJavaLocalRef<jstring> res;

  std::map<uint64_t, ledger::PublisherInfo>::const_iterator iter(map_publishers_info_.find(tabId));
  if (iter != map_publishers_info_.end()) {
    res = base::android::ConvertUTF8ToJavaString(env, iter->second.favicon_url);
  }

  return res; 
}

base::android::ScopedJavaLocalRef<jstring> BraveRewardsNativeWorker::GetPublisherName(JNIEnv* env, 
        const base::android::JavaParamRef<jobject>& obj, uint64_t tabId) {
  base::android::ScopedJavaLocalRef<jstring> res;

  std::map<uint64_t, ledger::PublisherInfo>::const_iterator iter(map_publishers_info_.find(tabId));
  if (iter != map_publishers_info_.end()) {
    res = base::android::ConvertUTF8ToJavaString(env, iter->second.name);
  }

  return res;
}

int BraveRewardsNativeWorker::GetPublisherPercent(JNIEnv* env, 
        const base::android::JavaParamRef<jobject>& obj, uint64_t tabId) {
  int res = 0;

  std::map<uint64_t, ledger::PublisherInfo>::const_iterator iter(map_publishers_info_.find(tabId));
  if (iter != map_publishers_info_.end()) {
    res = iter->second.percent;
  }

  return res;
}

bool BraveRewardsNativeWorker::GetPublisherExcluded(JNIEnv* env, 
        const base::android::JavaParamRef<jobject>& obj, uint64_t tabId) {
  bool res = false;

  std::map<uint64_t, ledger::PublisherInfo>::const_iterator iter(map_publishers_info_.find(tabId));
  if (iter != map_publishers_info_.end()) {
    res = iter->second.excluded == ledger::PUBLISHER_EXCLUDE::EXCLUDED;
  }

  return res;
}

void BraveRewardsNativeWorker::IncludeInAutoContribution(JNIEnv* env, 
        const base::android::JavaParamRef<jobject>& obj, uint64_t tabId, bool exclude) {
  std::map<uint64_t, ledger::PublisherInfo>::iterator iter(map_publishers_info_.find(tabId));
  if (iter != map_publishers_info_.end()) {
    if (exclude) {
      iter->second.excluded = ledger::PUBLISHER_EXCLUDE::EXCLUDED;
    } else {
      iter->second.excluded = ledger::PUBLISHER_EXCLUDE::INCLUDED;
    }
    if (brave_rewards_service_) {
      brave_rewards_service_->SetContributionAutoInclude(iter->second.id, exclude, tabId);
    }
  }
}

void BraveRewardsNativeWorker::RemovePublisherFromMap(JNIEnv* env, 
        const base::android::JavaParamRef<jobject>& obj, uint64_t tabId) {
  std::map<uint64_t, ledger::PublisherInfo>::const_iterator iter(map_publishers_info_.find(tabId));
  if (iter != map_publishers_info_.end()) {
    map_publishers_info_.erase(iter);
  }
}

void BraveRewardsNativeWorker::OnWalletInitialized(brave_rewards::RewardsService* rewards_service,
        int error_code) {
  JNIEnv* env = base::android::AttachCurrentThread();
  
  Java_BraveRewardsNativeWorker_OnWalletInitialized(env, 
        weak_java_brave_rewards_native_worker_.get(env), error_code);
}

void BraveRewardsNativeWorker::OnWalletProperties(brave_rewards::RewardsService* rewards_service,
        int error_code, 
        std::unique_ptr<brave_rewards::WalletProperties> wallet_properties) {
  wallet_properties_.reset(wallet_properties.release());

  JNIEnv* env = base::android::AttachCurrentThread();
  Java_BraveRewardsNativeWorker_OnWalletProperties(env, 
        weak_java_brave_rewards_native_worker_.get(env), error_code);
}

double BraveRewardsNativeWorker::GetWalletBalance(JNIEnv* env, 
    const base::android::JavaParamRef<jobject>& obj) {
  return wallet_properties_->balance;
}

double BraveRewardsNativeWorker::GetWalletRate(JNIEnv* env, 
    const base::android::JavaParamRef<jobject>& obj,
    const base::android::JavaParamRef<jstring>& rate) {
  std::map<std::string, double>::const_iterator iter = wallet_properties_->rates.find(
    base::android::ConvertJavaStringToUTF8(env, rate));
  if (iter != wallet_properties_->rates.end()) {
    return iter->second;
  }

  return 0.0;
}

bool BraveRewardsNativeWorker::WalletExist(JNIEnv* env, const
        base::android::JavaParamRef<jobject>& jcaller) {
  if (brave_rewards_service_) {
    return brave_rewards_service_->IsWalletCreated();
  }

  return false;
}

static void JNI_BraveRewardsNativeWorker_Init(JNIEnv* env, const
    base::android::JavaParamRef<jobject>& jcaller) {
  new BraveRewardsNativeWorker(env, jcaller);
}

}
}
