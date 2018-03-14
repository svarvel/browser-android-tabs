// Copyright (c) 2012 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#ifndef CHROME_BROWSER_NET_CHROME_NETWORK_DELEGATE_H_
#define CHROME_BROWSER_NET_CHROME_NETWORK_DELEGATE_H_

#include <stdint.h>

#include <memory>
#include <set>
#include <string>

#include "base/compiler_specific.h"
#include "base/files/file_path.h"
#include "base/macros.h"
#include "base/memory/ref_counted.h"
#include "base/values.h"
#include "build/build_config.h"
#include "chrome/browser/net/reporting_permissions_checker.h"
#include "components/prefs/pref_member.h"
#include "net/base/network_delegate_impl.h"

class ChromeExtensionsNetworkDelegate;

namespace content_settings {
class CookieSettings;
}

namespace extensions {
class EventRouterForwarder;
class InfoMap;
}

namespace net {
class URLRequest;
namespace blockers {
class BlockersWorker;
class ShieldsConfig;
}
}

struct OnBeforeURLRequestContext;
class PendingRequests;
class IOThread;

// ChromeNetworkDelegate is the central point from within the chrome code to
// add hooks into the network stack.
class ChromeNetworkDelegate : public net::NetworkDelegateImpl {
 public:
  explicit ChromeNetworkDelegate(
      extensions::EventRouterForwarder* event_router);
  ~ChromeNetworkDelegate() override;

  // Pass through to ChromeExtensionsNetworkDelegate::set_extension_info_map().
  void set_extension_info_map(extensions::InfoMap* extension_info_map);

  void ResetBlocker(IOThread* io_thread, net::URLRequest* request,
      const net::CompletionCallback& callback,
      GURL* new_url,
      std::shared_ptr<OnBeforeURLRequestContext> ctx);
  void GetIOThread(net::URLRequest* request,
      const net::CompletionCallback& callback,
      GURL* new_url,
      std::shared_ptr<OnBeforeURLRequestContext> ctx);
  void CheckAdBlockerReload(net::blockers::ShieldsConfig* shields_config);

  // If |profile| is nullptr or not set, events will be broadcast to all
  // profiles, otherwise they will only be sent to the specified profile.
  // Also pass through to ChromeExtensionsNetworkDelegate::set_profile().
  void set_profile(void* profile);

  // |profile_path| is used to locate profile specific paths such as the
  // "Downloads" folder on Chrome OS. If it is set, folders like Downloads
  // for the profile are added to the whitelist for accesses via file: scheme.
  void set_profile_path(const base::FilePath& profile_path) {
    profile_path_ = profile_path;
  }

  // If |cookie_settings| is nullptr or not set, all cookies are enabled,
  // otherwise the settings are enforced on all observed network requests.
  // Not inlined because we assign a scoped_refptr, which requires us to include
  // the header file. Here we just forward-declare it.
  void set_cookie_settings(content_settings::CookieSettings* cookie_settings);

  void set_force_google_safe_search(
      BooleanPrefMember* force_google_safe_search) {
    force_google_safe_search_ = force_google_safe_search;
  }

  void set_enable_httpse(BooleanPrefMember* enable_httpse) {
    enable_httpse_ = enable_httpse;
  }

  void set_enable_tracking_protection(BooleanPrefMember* enable_tracking_protection) {
    enable_tracking_protection_ = enable_tracking_protection;
  }

  void set_enable_ad_block(BooleanPrefMember* enable_ad_block) {
    enable_ad_block_ = enable_ad_block;
  }

  void set_enable_ad_block_regional(BooleanPrefMember* enable_ad_block_regional) {
    enable_ad_block_regional_ = enable_ad_block_regional;
  }

  void set_reporting_permissions_checker(
      std::unique_ptr<ReportingPermissionsChecker>
          reporting_permissions_checker) {
    reporting_permissions_checker_ = std::move(reporting_permissions_checker);
  }

  ReportingPermissionsChecker* reporting_permissions_checker() {
    return reporting_permissions_checker_.get();
  }

  void set_blockers_worker(
        std::shared_ptr<net::blockers::BlockersWorker> blockers_worker);

  void set_incognito(const bool &incognito);

  // Binds the pref members to |pref_service| and moves them to the IO thread.
  // All arguments can be nullptr. This method should be called on the UI
  // thread.
  static void InitializePrefsOnUIThread(
      BooleanPrefMember* enable_httpse,
      BooleanPrefMember* enable_tracking_protection,
      BooleanPrefMember* enable_ad_block,
      BooleanPrefMember* enable_ad_block_regional,
      PrefService* pref_service);

  // Returns true if access to |path| is allowed. |profile_path| is used to
  // locate certain paths on Chrome OS. See set_profile_path() for details.
  static bool IsAccessAllowed(const base::FilePath& path,
                              const base::FilePath& profile_path);

  // Like above, but also takes |path|'s absolute path in |absolute_path| to
  // further validate access.
  static bool IsAccessAllowed(const base::FilePath& path,
                              const base::FilePath& absolute_path,
                              const base::FilePath& profile_path);

  // Enables access to all files for testing purposes. This function is used
  // to bypass the access control for file: scheme. Calling this function
  // with false brings back the original (production) behaviors.
  static void EnableAccessToAllFilesForTesting(bool enabled);

 private:
  // NetworkDelegate implementation.
  int OnBeforeURLRequest(net::URLRequest* request,
                         net::CompletionOnceCallback callback,
                         GURL* new_url) override;
  int OnBeforeStartTransaction(net::URLRequest* request,
                               net::CompletionOnceCallback callback,
                               net::HttpRequestHeaders* headers) override;
  void OnStartTransaction(net::URLRequest* request,
                          const net::HttpRequestHeaders& headers) override;
  int OnHeadersReceived(
      net::URLRequest* request,
      net::CompletionOnceCallback callback,
      const net::HttpResponseHeaders* original_response_headers,
      scoped_refptr<net::HttpResponseHeaders>* override_response_headers,
      GURL* allowed_unsafe_redirect_url) override;
  void OnBeforeRedirect(net::URLRequest* request,
                        const GURL& new_location) override;
  void OnResponseStarted(net::URLRequest* request, int net_error) override;
  void OnNetworkBytesReceived(net::URLRequest* request,
                              int64_t bytes_received) override;
  void OnNetworkBytesSent(net::URLRequest* request,
                          int64_t bytes_sent) override;
  void OnCompleted(net::URLRequest* request,
                   bool started,
                   int net_error) override;
  void OnURLRequestDestroyed(net::URLRequest* request) override;
  net::NetworkDelegate::AuthRequiredResponse OnAuthRequired(
      net::URLRequest* request,
      const net::AuthChallengeInfo& auth_info,
      AuthCallback callback,
      net::AuthCredentials* credentials) override;
  bool OnCanGetCookies(const net::URLRequest& request,
                       const net::CookieList& cookie_list,
                       bool allowed_from_caller) override;
  bool OnCanSetCookie(const net::URLRequest& request,
                      const net::CanonicalCookie& cookie,
                      net::CookieOptions* options,
                      bool allowed_from_caller) override;
  bool OnCanAccessFile(const net::URLRequest& request,
                       const base::FilePath& original_path,
                       const base::FilePath& absolute_path) const override;
  bool OnCancelURLRequestWithPolicyViolatingReferrerHeader(
      const net::URLRequest& request,
      const GURL& target_url,
      const GURL& referrer_url) const override;
  bool OnCanQueueReportingReport(const url::Origin& origin) const override;
  void OnCanSendReportingReports(std::set<url::Origin> origins,
                                 base::OnceCallback<void(std::set<url::Origin>)>
                                     result_callback) const override;
  bool OnCanSetReportingClient(const url::Origin& origin,
                               const GURL& endpoint) const override;
  bool OnCanUseReportingClient(const url::Origin& origin,
                               const GURL& endpoint) const override;

  // Separate IO and FILE thread workers for blocker
  int OnBeforeURLRequest_PreBlockersWork(
            net::URLRequest* request,
            const net::CompletionCallback& callback,
            GURL* new_url,
            std::shared_ptr<OnBeforeURLRequestContext> ctx);
  int OnBeforeURLRequest_TpBlockPreFileWork(
            net::URLRequest* request,
            const net::CompletionCallback& callback,
            GURL* new_url,
            std::shared_ptr<OnBeforeURLRequestContext> ctx);
  void OnBeforeURLRequest_TpBlockFileWork();
  int OnBeforeURLRequest_TpBlockPostFileWork(
            net::URLRequest* request,
            const net::CompletionCallback& callback,
            GURL* new_url,
            std::shared_ptr<OnBeforeURLRequestContext> ctx);
  int OnBeforeURLRequest_AdBlockPreFileWork(
            net::URLRequest* request,
            const net::CompletionCallback& callback,
            GURL* new_url,
            std::shared_ptr<OnBeforeURLRequestContext> ctx);
  void OnBeforeURLRequest_AdBlockFileWork(std::shared_ptr<OnBeforeURLRequestContext> ctx);
  int OnBeforeURLRequest_AdBlockPostFileWork(
            net::URLRequest* request,
            const net::CompletionCallback& callback,
            GURL* new_url,
            std::shared_ptr<OnBeforeURLRequestContext> ctx);
  int OnBeforeURLRequest_HttpsePreFileWork(
            net::URLRequest* request,
            const net::CompletionCallback& callback,
            GURL* new_url,
            std::shared_ptr<OnBeforeURLRequestContext> ctx);
  void OnBeforeURLRequest_HttpseFileWork(
            net::URLRequest* request,
            std::shared_ptr<OnBeforeURLRequestContext> ctx);
  int OnBeforeURLRequest_HttpsePostFileWork(
            net::URLRequest* request,
            const net::CompletionCallback& callback,
            GURL* new_url,
            std::shared_ptr<OnBeforeURLRequestContext> ctx);
  int OnBeforeURLRequest_PostBlockers(
            net::URLRequest* request,
            const net::CompletionCallback& callback,
            GURL* new_url,
            std::shared_ptr<OnBeforeURLRequestContext> ctx);
  bool PendedRequestIsDestroyedOrCancelled(
            OnBeforeURLRequestContext* ctx,
            net::URLRequest* request);

  std::unique_ptr<ChromeExtensionsNetworkDelegate> extensions_delegate_;

  base::FilePath profile_path_;
  scoped_refptr<content_settings::CookieSettings> cookie_settings_;

  // Weak, owned by our owner.
  BooleanPrefMember* force_google_safe_search_ = nullptr;
  BooleanPrefMember* enable_httpse_;
  BooleanPrefMember* enable_tracking_protection_;
  BooleanPrefMember* enable_ad_block_;
  BooleanPrefMember* enable_ad_block_regional_;

  std::unique_ptr<ReportingPermissionsChecker> reporting_permissions_checker_;

  bool experimental_web_platform_features_enabled_;

  // Blockers
  std::shared_ptr<net::blockers::BlockersWorker> blockers_worker_;
  bool reload_adblocker_;

  // (TODO)find a better way to handle last first party
  GURL last_first_party_url_;

  std::auto_ptr<PendingRequests> pending_requests_;

  bool incognito_;

  DISALLOW_COPY_AND_ASSIGN(ChromeNetworkDelegate);
};

#endif  // CHROME_BROWSER_NET_CHROME_NETWORK_DELEGATE_H_
