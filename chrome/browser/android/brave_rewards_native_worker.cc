/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */
#include "brave_rewards_native_worker.h"
#include "base/android/jni_android.h"
#include "base/android/jni_string.h"
#include "base/android/jni_array.h"
#include "brave/components/brave_rewards/browser/rewards_service_factory.h"
#include "brave/components/brave_rewards/browser/rewards_service.h"
#include "chrome/browser/profiles/profile.h"
#include "chrome/browser/profiles/profile_manager.h"
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
    brave_rewards::RewardsNotificationService* notification_service = 
      brave_rewards_service_->GetNotificationService();
    if (notification_service) {
      notification_service->AddObserver(this);
    }
  }
}

BraveRewardsNativeWorker::~BraveRewardsNativeWorker() {
}

void BraveRewardsNativeWorker::Destroy(JNIEnv* env, const
        base::android::JavaParamRef<jobject>& jcaller) {
  if (brave_rewards_service_) {
    brave_rewards_service_->RemoveObserver(this);
    brave_rewards_service_->RemovePrivateObserver(this);
    brave_rewards::RewardsNotificationService* notification_service = 
      brave_rewards_service_->GetNotificationService();
    if (notification_service) {
      notification_service->RemoveObserver(this);
    }
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
    brave_rewards_service_->GetPublisherActivityFromUrl(tabId,
      base::android::ConvertJavaStringToUTF8(env, host), "");
  }
}

void BraveRewardsNativeWorker::OnGetPublisherActivityFromUrl(
      brave_rewards::RewardsService* rewards_service,
      int error_code,
      std::unique_ptr<ledger::PublisherInfo> info,
      uint64_t tabId) {
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
  base::android::ScopedJavaLocalRef<jstring> res = base::android::ConvertUTF8ToJavaString(env, "");

  std::map<uint64_t, ledger::PublisherInfo>::const_iterator iter(map_publishers_info_.find(tabId));
  if (iter != map_publishers_info_.end()) {
    res = base::android::ConvertUTF8ToJavaString(env, iter->second.url);
  }

  return res;
}

base::android::ScopedJavaLocalRef<jstring> BraveRewardsNativeWorker::GetPublisherFavIconURL(JNIEnv* env, 
        const base::android::JavaParamRef<jobject>& obj, uint64_t tabId) {
  base::android::ScopedJavaLocalRef<jstring> res = base::android::ConvertUTF8ToJavaString(env, "");

  std::map<uint64_t, ledger::PublisherInfo>::const_iterator iter(map_publishers_info_.find(tabId));
  if (iter != map_publishers_info_.end()) {
    res = base::android::ConvertUTF8ToJavaString(env, iter->second.favicon_url);
  }

  return res; 
}

base::android::ScopedJavaLocalRef<jstring> BraveRewardsNativeWorker::GetPublisherName(JNIEnv* env, 
        const base::android::JavaParamRef<jobject>& obj, uint64_t tabId) {
  base::android::ScopedJavaLocalRef<jstring> res = base::android::ConvertUTF8ToJavaString(env, "");

  std::map<uint64_t, ledger::PublisherInfo>::const_iterator iter(map_publishers_info_.find(tabId));
  if (iter != map_publishers_info_.end()) {
    res = base::android::ConvertUTF8ToJavaString(env, iter->second.name);
  }

  return res;
}

base::android::ScopedJavaLocalRef<jstring> BraveRewardsNativeWorker::GetPublisherId(JNIEnv* env, 
        const base::android::JavaParamRef<jobject>& obj, uint64_t tabId) {
  base::android::ScopedJavaLocalRef<jstring> res = base::android::ConvertUTF8ToJavaString(env, "");

  std::map<uint64_t, ledger::PublisherInfo>::const_iterator iter(map_publishers_info_.find(tabId));
  if (iter != map_publishers_info_.end()) {
    res = base::android::ConvertUTF8ToJavaString(env, iter->second.id);
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

bool BraveRewardsNativeWorker::GetPublisherVerified(JNIEnv* env, 
        const base::android::JavaParamRef<jobject>& obj, uint64_t tabId) {
  bool res = false;

  std::map<uint64_t, ledger::PublisherInfo>::const_iterator iter(map_publishers_info_.find(tabId));
  if (iter != map_publishers_info_.end()) {
    res = iter->second.verified;
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
  if (wallet_properties) {
    wallet_properties_ = *wallet_properties;
  }

  JNIEnv* env = base::android::AttachCurrentThread();
  Java_BraveRewardsNativeWorker_OnWalletProperties(env, 
        weak_java_brave_rewards_native_worker_.get(env), error_code);
}

double BraveRewardsNativeWorker::GetWalletBalance(JNIEnv* env, 
    const base::android::JavaParamRef<jobject>& obj) {
  return wallet_properties_.balance;
}

double BraveRewardsNativeWorker::GetWalletRate(JNIEnv* env, 
    const base::android::JavaParamRef<jobject>& obj,
    const base::android::JavaParamRef<jstring>& rate) {
  std::map<std::string, double>::const_iterator iter = wallet_properties_.rates.find(
    base::android::ConvertJavaStringToUTF8(env, rate));
  if (iter != wallet_properties_.rates.end()) {
    return iter->second;
  }

  return 0.0;
}

bool BraveRewardsNativeWorker::WalletExist(JNIEnv* env,
        const base::android::JavaParamRef<jobject>& jcaller) {
  if (brave_rewards_service_) {
    return brave_rewards_service_->IsWalletCreated();
  }

  return false;
}

void BraveRewardsNativeWorker::GetCurrentBalanceReport(JNIEnv* env, 
        const base::android::JavaParamRef<jobject>& obj) {
  if (brave_rewards_service_) {
    return brave_rewards_service_->GetCurrentBalanceReport();
  }
}

void BraveRewardsNativeWorker::OnGetCurrentBalanceReport(
        brave_rewards::RewardsService* rewards_service,
        const brave_rewards::BalanceReport& balance_report) {
  std::vector<std::string> values;
  values.push_back(balance_report.opening_balance);
  values.push_back(balance_report.closing_balance);
  values.push_back(balance_report.deposits);
  values.push_back(balance_report.grants);
  values.push_back(balance_report.earning_from_ads);
  values.push_back(balance_report.auto_contribute);
  values.push_back(balance_report.recurring_donation);
  values.push_back(balance_report.one_time_donation);
  values.push_back(balance_report.total);

  JNIEnv* env = base::android::AttachCurrentThread();
  base::android::ScopedJavaLocalRef<jobjectArray> java_array =
      base::android::ToJavaArrayOfStrings(env, values);

  Java_BraveRewardsNativeWorker_OnGetCurrentBalanceReport(env, 
        weak_java_brave_rewards_native_worker_.get(env), java_array);
}

void BraveRewardsNativeWorker::Donate(JNIEnv* env, 
        const base::android::JavaParamRef<jobject>& obj,
        const base::android::JavaParamRef<jstring>& publisher_key, 
        int amount, bool recurring) {
  if (brave_rewards_service_) {
    brave_rewards_service_->OnDonate(base::android::ConvertJavaStringToUTF8(env, publisher_key), 
      amount, recurring);
  }
}

void BraveRewardsNativeWorker::GetAllNotifications(JNIEnv* env, 
        const base::android::JavaParamRef<jobject>& obj) {
  brave_rewards::RewardsNotificationService* notification_service = 
    brave_rewards_service_->GetNotificationService();
  if (notification_service) {
    notification_service->GetAllNotifications();
  }
}

void BraveRewardsNativeWorker::DeleteNotification(JNIEnv* env, 
        const base::android::JavaParamRef<jobject>& obj,
        const base::android::JavaParamRef<jstring>& notification_id) {
  brave_rewards::RewardsNotificationService* notification_service = 
    brave_rewards_service_->GetNotificationService();
  if (notification_service) {
    notification_service->DeleteNotification(
      base::android::ConvertJavaStringToUTF8(env, notification_id));
  }
}

void BraveRewardsNativeWorker::GetGrant(JNIEnv* env, const base::android::JavaParamRef<jobject>& obj) {
  if (brave_rewards_service_) {
    brave_rewards_service_->GetGrantViaSafetynetCheck();
  }
}

void BraveRewardsNativeWorker::OnNotificationAdded(
      brave_rewards::RewardsNotificationService* rewards_notification_service,
      const brave_rewards::RewardsNotificationService::RewardsNotification& notification) {
  JNIEnv* env = base::android::AttachCurrentThread();

  Java_BraveRewardsNativeWorker_OnNotificationAdded(env, 
        weak_java_brave_rewards_native_worker_.get(env),
        base::android::ConvertUTF8ToJavaString(env, notification.id_), 
        notification.type_,
        notification.timestamp_,
        base::android::ToJavaArrayOfStrings(env, notification.args_));
}

void BraveRewardsNativeWorker::OnGetAllNotifications(
      brave_rewards::RewardsNotificationService* rewards_notification_service,
      const brave_rewards::RewardsNotificationService::RewardsNotificationsList&
          notifications_list) {
  JNIEnv* env = base::android::AttachCurrentThread();
  
  // Notify about notifications count
  Java_BraveRewardsNativeWorker_OnNotificationsCount(env, 
        weak_java_brave_rewards_native_worker_.get(env),
        notifications_list.size());

  brave_rewards::RewardsNotificationService::RewardsNotificationsList::const_iterator iter = 
    std::max_element(notifications_list.begin(), notifications_list.end(),
      [](const brave_rewards::RewardsNotificationService::RewardsNotification& notification_a,
        const brave_rewards::RewardsNotificationService::RewardsNotification& notification_b) {
      return notification_a.timestamp_ > notification_b.timestamp_;
    });

  if (iter != notifications_list.end()) {
    Java_BraveRewardsNativeWorker_OnGetLatestNotification(env, 
        weak_java_brave_rewards_native_worker_.get(env),
        base::android::ConvertUTF8ToJavaString(env, iter->id_), 
        iter->type_,
        iter->timestamp_,
        base::android::ToJavaArrayOfStrings(env, iter->args_));
  }
}

void BraveRewardsNativeWorker::OnNotificationDeleted(
      brave_rewards::RewardsNotificationService* rewards_notification_service,
      const brave_rewards::RewardsNotificationService::RewardsNotification& notification) {
  JNIEnv* env = base::android::AttachCurrentThread();

  Java_BraveRewardsNativeWorker_OnNotificationDeleted(env, 
        weak_java_brave_rewards_native_worker_.get(env),
        base::android::ConvertUTF8ToJavaString(env, notification.id_));
}

void BraveRewardsNativeWorker::OnGrant(brave_rewards::RewardsService* rewards_service, 
      unsigned int result, brave_rewards::Grant grant) {
  // TODO what we need to do here? We receive notification about deletion
}

static void JNI_BraveRewardsNativeWorker_Init(JNIEnv* env, const
    base::android::JavaParamRef<jobject>& jcaller) {
  new BraveRewardsNativeWorker(env, jcaller);
}

}
}
