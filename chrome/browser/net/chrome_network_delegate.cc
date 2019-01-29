// Copyright (c) 2012 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "chrome/browser/net/chrome_network_delegate.h"

#include <stddef.h>
#include <stdlib.h>

#include <vector>

#include "base/base_paths.h"
#include "base/bind.h"
#include "base/command_line.h"
#include "base/debug/alias.h"
#include "base/debug/dump_without_crashing.h"
#include "base/logging.h"
#include "base/macros.h"
#include "base/metrics/user_metrics.h"
#include "base/path_service.h"
#include "base/strings/string_number_conversions.h"
#include "base/strings/string_util.h"
#include "base/task/post_task.h"
#include "base/sequenced_task_runner.h"
#include "base/time/time.h"
#include "brave_src/browser/brave_tab_url_web_contents_observer.h"
#include "build/build_config.h"
#include "chrome/browser/browser_process.h"
#include "chrome/browser/content_settings/cookie_settings_factory.h"
#include "chrome/browser/content_settings/tab_specific_content_settings.h"
#include "chrome/browser/custom_handlers/protocol_handler_registry.h"
#include "chrome/browser/net/blockers/blockers_worker.h"
#include "chrome/browser/net/chrome_extensions_network_delegate.h"
#include "chrome/browser/profiles/profile_manager.h"
#include "chrome/browser/stats_updater.h"
#include "chrome/browser/task_manager/task_manager_interface.h"
#include "chrome/common/buildflags.h"
#include "chrome/common/net/safe_search_util.h"
#include "chrome/common/pref_names.h"
#include "components/content_settings/core/browser/cookie_settings.h"
#include "components/variations/net/variations_http_headers.h"
#include "content/public/browser/browser_task_traits.h"
#include "content/public/browser/browser_thread.h"
#include "content/public/browser/render_frame_host.h"
#include "content/public/browser/render_view_host.h"
#include "content/public/browser/resource_request_info.h"
#include "content/public/browser/websocket_handshake_request_info.h"
#include "content/public/common/content_switches.h"
#include "content/public/common/process_type.h"
#include "content/public/common/resource_type.h"
#include "extensions/buildflags/buildflags.h"
#include "net/base/host_port_pair.h"
#include "net/base/net_errors.h"
#include "net/cookies/canonical_cookie.h"
#include "net/cookies/cookie_options.h"
#include "net/http/http_request_headers.h"
#include "net/http/http_response_headers.h"
#include "net/http/http_status_code.h"
#include "net/log/net_log.h"
#include "net/log/net_log_event_type.h"
#include "net/log/net_log_with_source.h"
#include "net/url_request/url_request.h"
#include "chrome/browser/net/blockers/shields_config.h"

#if defined(OS_ANDROID)
#include "base/android/path_utils.h"
#include "chrome/browser/io_thread.h"
#endif

#if defined(OS_CHROMEOS)
#include "base/system/sys_info.h"
#include "chrome/common/chrome_switches.h"
#endif

#if BUILDFLAG(ENABLE_EXTENSIONS)
#include "extensions/common/constants.h"
#endif

using content::BrowserThread;
using content::RenderViewHost;
using content::ResourceRequestInfo;

#define TRANSPARENT1PXGIF "data:image/gif;base64,R0lGODlhAQABAIAAAAAAAP///yH5BAEAAAAALAAAAAABAAEAAAIBRAA7"

namespace {

bool g_access_to_all_files_enabled = false;

// Gets called when the extensions finish work on the URL. If the extensions
// did not do a redirect (so |new_url| is empty) then we enforce the
// SafeSearch parameters. Otherwise we will get called again after the
// redirect and we enforce SafeSearch then.
void ForceGoogleSafeSearchCallbackWrapper(net::CompletionOnceCallback callback,
                                          net::URLRequest* request,
                                          GURL* new_url,
                                          int rv) {
  if (rv == net::OK && new_url->is_empty())
    safe_search_util::ForceGoogleSafeSearch(request->url(), new_url);
  std::move(callback).Run(rv);
}

bool IsAccessAllowedInternal(const base::FilePath& path,
                             const base::FilePath& profile_path) {
  if (g_access_to_all_files_enabled)
    return true;

#if !defined(OS_CHROMEOS) && !defined(OS_ANDROID)
  return true;
#else

  std::vector<base::FilePath> whitelist;
#if defined(OS_CHROMEOS)
  // Use a whitelist to only allow access to files residing in the list of
  // directories below.
  static const base::FilePath::CharType* const kLocalAccessWhiteList[] = {
      "/home/chronos/user/Downloads",
      "/home/chronos/user/MyFiles",
      "/home/chronos/user/log",
      "/home/chronos/user/WebRTC Logs",
      "/media",
      "/opt/oem",
      "/run/arc/sdcard/write/emulated/0",
      "/usr/share/chromeos-assets",
      "/var/log",
  };

  base::FilePath temp_dir;
  if (base::PathService::Get(base::DIR_TEMP, &temp_dir))
    whitelist.push_back(temp_dir);

  // The actual location of "/home/chronos/user/Xyz" is the Xyz directory under
  // the profile path ("/home/chronos/user' is a hard link to current primary
  // logged in profile.) For the support of multi-profile sessions, we are
  // switching to use explicit "$PROFILE_PATH/Xyz" path and here whitelist such
  // access.
  if (!profile_path.empty()) {
    const base::FilePath downloads = profile_path.AppendASCII("Downloads");
    whitelist.push_back(downloads);
    whitelist.push_back(profile_path.AppendASCII("MyFiles"));
    const base::FilePath webrtc_logs = profile_path.AppendASCII("WebRTC Logs");
    whitelist.push_back(webrtc_logs);
  }
#elif defined(OS_ANDROID)
  // Access to files in external storage is allowed.
  base::FilePath external_storage_path;
  base::PathService::Get(base::DIR_ANDROID_EXTERNAL_STORAGE,
                         &external_storage_path);
  if (external_storage_path.IsParent(path))
    return true;

  auto all_download_dirs = base::android::GetAllPrivateDownloadsDirectories();
  for (const auto& dir : all_download_dirs)
    whitelist.push_back(dir);

  // Whitelist of other allowed directories.
  static const base::FilePath::CharType* const kLocalAccessWhiteList[] = {
      "/sdcard", "/mnt/sdcard",
  };
#endif

  for (const auto* whitelisted_path : kLocalAccessWhiteList)
    whitelist.push_back(base::FilePath(whitelisted_path));

  for (const auto& whitelisted_path : whitelist) {
    // base::FilePath::operator== should probably handle trailing separators.
    if (whitelisted_path == path.StripTrailingSeparators() ||
        whitelisted_path.IsParent(path)) {
      return true;
    }
  }

#if defined(OS_CHROMEOS)
  // Allow access to DriveFS logs. These reside in
  // $PROFILE_PATH/GCache/v2/<opaque id>/Logs.
  base::FilePath path_within_gcache_v2;
  if (profile_path.Append("GCache/v2")
          .AppendRelativePath(path, &path_within_gcache_v2)) {
    std::vector<std::string> components;
    path_within_gcache_v2.GetComponents(&components);
    if (components.size() > 1 && components[1] == "Logs") {
      return true;
    }
  }
#endif  // defined(OS_CHROMEOS)

  DVLOG(1) << "File access denied - " << path.value().c_str();
  return false;
#endif  // !defined(OS_CHROMEOS) && !defined(OS_ANDROID)
}

}  // namespace

class PendingRequests {
public:
  void Insert(const uint64_t &request_identifier) {
    pending_requests_.insert(request_identifier);
  }
  void Destroy(const uint64_t &request_identifier) {
    pending_requests_.erase(request_identifier);
  }
  bool IsPendingAndAlive(const uint64_t &request_identifier) {
    bool isPending = pending_requests_.find(request_identifier) != pending_requests_.end();
    return isPending;
  }
private:
  std::set<uint64_t> pending_requests_;
  //no need synchronization, should be executed in the same thread content::BrowserThread::IO
};

struct OnBeforeURLRequestContext
{
  OnBeforeURLRequestContext(){}
  ~OnBeforeURLRequestContext(){}

  int adsBlocked = 0;
  int trackersBlocked = 0;
  int httpsUpgrades = 0;

  bool isGlobalBlockEnabled = true;
  bool blockAdsAndTracking = true;
  bool isAdBlockRegionalEnabled = true;
  bool isTPEnabled = true;
  bool isHTTPSEEnabled = true;
  bool isBlock3rdPartyCookies = true;

  bool shieldsSetExplicitly = false;

  bool needPerformAdBlock = false;
  bool needPerformTPBlock = false;
  bool needPerformHTTPSE = false;

  bool block = false;

  const ResourceRequestInfo* info = nullptr;
  bool isValidUrl = true;
  std::string firstparty_host = "";
  bool check_httpse_redirect = true;
  GURL UrlCopy;
  std::string newURL;

  bool pendingAtLeastOnce = false;
  uint64_t request_identifier = 0;

  DISALLOW_COPY_AND_ASSIGN(OnBeforeURLRequestContext);
};

ChromeNetworkDelegate::ChromeNetworkDelegate(
    extensions::EventRouterForwarder* event_router)
    : extensions_delegate_(
          ChromeExtensionsNetworkDelegate::Create(event_router)),
      enable_httpse_(nullptr),
      enable_tracking_protection_(nullptr),
      enable_ad_block_(nullptr),
      enable_ad_block_regional_(nullptr),
      experimental_web_platform_features_enabled_(
          base::CommandLine::ForCurrentProcess()->HasSwitch(
              switches::kEnableExperimentalWebPlatformFeatures)),
      reload_adblocker_(false),
      incognito_(false) {
  pending_requests_.reset(new PendingRequests());
}

ChromeNetworkDelegate::~ChromeNetworkDelegate() {}

void ChromeNetworkDelegate::set_extension_info_map(
    extensions::InfoMap* extension_info_map) {
  extensions_delegate_->set_extension_info_map(extension_info_map);
}

void ChromeNetworkDelegate::set_profile(void* profile) {
  extensions_delegate_->set_profile(profile);
}

void ChromeNetworkDelegate::set_cookie_settings(
    content_settings::CookieSettings* cookie_settings) {
  cookie_settings_ = cookie_settings;
}

void ChromeNetworkDelegate::set_blockers_worker(
  std::shared_ptr<net::blockers::BlockersWorker> blockers_worker) {
  blockers_worker_ = blockers_worker;
}

void ChromeNetworkDelegate::set_incognito(const bool &incognito) {
  incognito_ = incognito;
}

// static
void ChromeNetworkDelegate::InitializePrefsOnUIThread(
    BooleanPrefMember* enable_httpse,
    BooleanPrefMember* enable_tracking_protection,
    BooleanPrefMember* enable_ad_block,
    BooleanPrefMember* enable_ad_block_regional,
    PrefService* pref_service) {
  DCHECK_CURRENTLY_ON(BrowserThread::UI);
  if (enable_httpse) {
    enable_httpse->Init(prefs::kHTTPSEEnabled, pref_service);
    enable_httpse->MoveToThread(
        base::CreateSingleThreadTaskRunnerWithTraits({BrowserThread::IO}));
  }
  if (enable_tracking_protection) {
    enable_tracking_protection->Init(prefs::kTrackingProtectionEnabled, pref_service);
    enable_tracking_protection->MoveToThread(
        base::CreateSingleThreadTaskRunnerWithTraits({BrowserThread::IO}));
  }
  if (enable_ad_block) {
    enable_ad_block->Init(prefs::kAdBlockEnabled, pref_service);
    enable_ad_block->MoveToThread(
        base::CreateSingleThreadTaskRunnerWithTraits({BrowserThread::IO}));
  }
  if (enable_ad_block_regional) {
    enable_ad_block_regional->Init(prefs::kAdBlockRegionalEnabled, pref_service);
    enable_ad_block_regional->MoveToThread(
        base::CreateSingleThreadTaskRunnerWithTraits({BrowserThread::IO}));
  }
}

int ChromeNetworkDelegate::OnBeforeURLRequest(
    net::URLRequest* request,
    net::CompletionOnceCallback callback,
    GURL* new_url,
    bool call_callback) {
  std::shared_ptr<OnBeforeURLRequestContext> ctx(new OnBeforeURLRequestContext());

  int rv = OnBeforeURLRequest_PreBlockersWork(
       request,
       std::move(callback),
       new_url,
       ctx);

   return rv;
}

int ChromeNetworkDelegate::OnBeforeURLRequest_PreBlockersWork(
   net::URLRequest* request,
   net::CompletionOnceCallback callback,
   GURL* new_url,
   std::shared_ptr<OnBeforeURLRequestContext> ctx)
 {
   DCHECK_CURRENTLY_ON(content::BrowserThread::IO);

   ctx->firstparty_host = "";
   if (request) {
     ctx->firstparty_host = request->site_for_cookies().host();
     ctx->request_identifier = request->identifier();
   }
   // (TODO)find a better way to handle last first party
   if (0 == ctx->firstparty_host.length()) {
     ctx->firstparty_host = last_first_party_url_.host();
   } else if (request) {
     last_first_party_url_ = request->site_for_cookies();
   }
   // We want to block first party ads as well
   /*bool firstPartyUrl = false;
   if (request && (last_first_party_url_ == request->url())) {
     firstPartyUrl = true;
   }*/
   // Ad Block and tracking protection
   ctx->isGlobalBlockEnabled = true;
   ctx->blockAdsAndTracking = true;
   ctx->isHTTPSEEnabled = true;
   ctx->isBlock3rdPartyCookies = true;
   ctx->shieldsSetExplicitly = false;
   net::blockers::ShieldsConfig* shieldsConfig =
     net::blockers::ShieldsConfig::getShieldsConfig();
   if (request && nullptr != shieldsConfig) {
       std::string hostConfig = shieldsConfig->getHostSettings(incognito_, ctx->firstparty_host);
       // It is a length of ALL_SHIELDS_DEFAULT_MASK in ShieldsConfig.java
       if (hostConfig.length() == 11) {
         ctx->shieldsSetExplicitly  = true;
         if ('0' == hostConfig[0]) {
             ctx->isGlobalBlockEnabled = false;
         }
         if (ctx->isGlobalBlockEnabled) {
             if ('0' ==  hostConfig[2]) {
                 ctx->blockAdsAndTracking = false;
             }
             if ('0' ==  hostConfig[4]) {
                 ctx->isHTTPSEEnabled = false;
             }
             if ('0' ==  hostConfig[8]) {
                 ctx->isBlock3rdPartyCookies = false;
             }
         }
       }
   } else if (nullptr == shieldsConfig){
       ctx->isGlobalBlockEnabled = false;
   }

   ctx->isValidUrl = true;
   if (request) {
       ctx->isValidUrl = request->url().is_valid();
       std::string scheme = request->url().scheme();
       if (scheme.length()) {
           std::transform(scheme.begin(), scheme.end(), scheme.begin(), ::tolower);
           if ("http" != scheme && "https" != scheme/* && "blob" != scheme*/) {
               ctx->isValidUrl = false;
           }
       }
   }
   ctx->isTPEnabled = true;
   ctx->block = false;
   if (enable_tracking_protection_ && !ctx->shieldsSetExplicitly) {
     ctx->isTPEnabled = enable_tracking_protection_->GetValue();
   }

  ctx->adsBlocked = 0;
  ctx->trackersBlocked = 0;
  ctx->httpsUpgrades = 0;

  int rv = net::ERR_IO_PENDING;
  if (reload_adblocker_) {
    reload_adblocker_ = false;
    base::PostTaskWithTraits(FROM_HERE, {BrowserThread::UI},
      base::Bind(&ChromeNetworkDelegate::GetIOThread,
          base::Unretained(this), base::Unretained(request), base::Passed(&callback), new_url, ctx));
    if (nullptr != shieldsConfig) {
      shieldsConfig->resetUpdateAdBlockerFlag();
    }
    ctx->pendingAtLeastOnce = true;
    pending_requests_->Insert(request->identifier());
  } else {
    rv = OnBeforeURLRequest_TpBlockPreFileWork(request, std::move(callback), new_url, ctx);
    // Check do we need to reload adblocker. We will do that on next call
    base::PostTaskWithTraits(FROM_HERE, {BrowserThread::IO},
      base::Bind(base::IgnoreResult(&ChromeNetworkDelegate::CheckAdBlockerReload),
          base::Unretained(this), shieldsConfig));
  }

  return rv;
}

void ChromeNetworkDelegate::CheckAdBlockerReload(net::blockers::ShieldsConfig* shields_config) {
  if (nullptr == shields_config) {
    return;
  }
  reload_adblocker_ = shields_config->needUpdateAdBlocker();
}

void ChromeNetworkDelegate::GetIOThread(net::URLRequest* request,
    net::CompletionOnceCallback callback,
    GURL* new_url,
    std::shared_ptr<OnBeforeURLRequestContext> ctx) {
  scoped_refptr<base::SequencedTaskRunner> task_runner =
    base::CreateSequencedTaskRunnerWithTraits(
         {base::MayBlock(), base::TaskShutdownBehavior::SKIP_ON_SHUTDOWN});
  task_runner->PostTask(FROM_HERE, base::Bind(&ChromeNetworkDelegate::ResetBlocker,
        base::Unretained(this), g_browser_process->io_thread(), base::Unretained(request), base::Passed(&callback),
          new_url, ctx));
}

void ChromeNetworkDelegate::ResetBlocker(IOThread* io_thread, net::URLRequest* request,
    net::CompletionOnceCallback callback,
    GURL* new_url,
    std::shared_ptr<OnBeforeURLRequestContext> ctx) {
  blockers_worker_ = io_thread->ResetBlockersWorker();

  base::PostTaskWithTraits(FROM_HERE, {BrowserThread::IO},
    base::Bind(base::IgnoreResult(&ChromeNetworkDelegate::OnBeforeURLRequest_TpBlockPostFileWork),
        base::Unretained(this), base::Unretained(request), base::Passed(&callback), new_url, ctx)
      );
}

int ChromeNetworkDelegate::OnBeforeURLRequest_TpBlockPreFileWork(
  net::URLRequest* request,
  net::CompletionOnceCallback callback,
  GURL* new_url,
  std::shared_ptr<OnBeforeURLRequestContext> ctx)
{
  DCHECK_CURRENTLY_ON(content::BrowserThread::IO);
  ctx->needPerformTPBlock = false;
  if (request
      //&& !firstPartyUrl
      && ctx->isValidUrl
      && ctx->isGlobalBlockEnabled
      && ctx->blockAdsAndTracking
      && ctx->isTPEnabled) {

      ctx->needPerformTPBlock = true;
      if (!blockers_worker_->isTPInitialized() ) {
        scoped_refptr<base::SequencedTaskRunner> task_runner =
          base::CreateSequencedTaskRunnerWithTraits({base::MayBlock(), base::TaskShutdownBehavior::SKIP_ON_SHUTDOWN});
        task_runner->PostTaskAndReply(FROM_HERE,
          base::Bind(&ChromeNetworkDelegate::OnBeforeURLRequest_TpBlockFileWork,
              base::Unretained(this)),
          base::Bind(base::IgnoreResult(&ChromeNetworkDelegate::OnBeforeURLRequest_TpBlockPostFileWork),
              base::Unretained(this), base::Unretained(request), base::Passed(&callback), new_url, ctx));
        ctx->pendingAtLeastOnce = true;
        pending_requests_->Insert(request->identifier());
        return net::ERR_IO_PENDING;
      }
  }

  int rv = OnBeforeURLRequest_TpBlockPostFileWork(request, std::move(callback), new_url, ctx);
  return rv;
}

void ChromeNetworkDelegate::OnBeforeURLRequest_TpBlockFileWork() {
  base::AssertBlockingAllowed();
  blockers_worker_->InitTP();
}

int ChromeNetworkDelegate::OnBeforeURLRequest_TpBlockPostFileWork(
  net::URLRequest* request,
  net::CompletionOnceCallback callback,
  GURL* new_url,
  std::shared_ptr<OnBeforeURLRequestContext> ctx) {
  DCHECK_CURRENTLY_ON(content::BrowserThread::IO);

  if (PendedRequestIsDestroyedOrCancelled(ctx.get(), request)) {
    return net::OK;
  }

  if (ctx->needPerformTPBlock){
    if (blockers_worker_->shouldTPBlockUrl(
        ctx->firstparty_host,
        request->url().host())) {
      ctx->block = true;
      ctx->trackersBlocked++;
    }
  }

  int rv = OnBeforeURLRequest_AdBlockPreFileWork(request, std::move(callback), new_url, ctx);
  return rv;
}

int ChromeNetworkDelegate::OnBeforeURLRequest_AdBlockPreFileWork(
  net::URLRequest* request,
  net::CompletionOnceCallback callback,
  GURL* new_url,
  std::shared_ptr<OnBeforeURLRequestContext> ctx) {
  DCHECK_CURRENTLY_ON(content::BrowserThread::IO);
  bool isAdBlockEnabled = true;
  if (enable_ad_block_ && !ctx->shieldsSetExplicitly) {
    isAdBlockEnabled = enable_ad_block_->GetValue();
  }
  // Regional ad block flag
  ctx->isAdBlockRegionalEnabled = true;
  if (enable_ad_block_regional_ && !ctx->shieldsSetExplicitly) {
    ctx->isAdBlockRegionalEnabled = enable_ad_block_regional_->GetValue();
  }

  ctx->info = ResourceRequestInfo::ForRequest(request);
	if (!ctx->block
      //&& !firstPartyUrl
      && ctx->isValidUrl
      && ctx->isGlobalBlockEnabled
      && ctx->blockAdsAndTracking
      && isAdBlockEnabled
      && request
      && ctx->info
      && content::RESOURCE_TYPE_MAIN_FRAME != ctx->info->GetResourceType()) {
        ctx->needPerformAdBlock = true;
      if (!blockers_worker_->isAdBlockerInitialized() ||
        (ctx->isAdBlockRegionalEnabled && !blockers_worker_->isAdBlockerRegionalInitialized()) ) {

          scoped_refptr<base::SequencedTaskRunner> task_runner =
            base::CreateSequencedTaskRunnerWithTraits({base::MayBlock(), base::TaskShutdownBehavior::SKIP_ON_SHUTDOWN});
          task_runner->PostTaskAndReply(FROM_HERE,
          base::Bind(&ChromeNetworkDelegate::OnBeforeURLRequest_AdBlockFileWork,
              base::Unretained(this), ctx),
          base::Bind(base::IgnoreResult(&ChromeNetworkDelegate::OnBeforeURLRequest_AdBlockPostFileWork),
              base::Unretained(this), base::Unretained(request), base::Passed(&callback), new_url, ctx));
        ctx->pendingAtLeastOnce = true;
        pending_requests_->Insert(request->identifier());
        return net::ERR_IO_PENDING;
      }
  }

  int rv = OnBeforeURLRequest_AdBlockPostFileWork(request, std::move(callback), new_url, ctx);
  return rv;
}

void ChromeNetworkDelegate::OnBeforeURLRequest_AdBlockFileWork(std::shared_ptr<OnBeforeURLRequestContext> ctx) {
  base::AssertBlockingAllowed();
  blockers_worker_->InitAdBlock();

  if (ctx->isAdBlockRegionalEnabled &&
    !blockers_worker_->isAdBlockerRegionalInitialized()) {
    blockers_worker_->InitAdBlockRegional();
  }
}

int ChromeNetworkDelegate::OnBeforeURLRequest_AdBlockPostFileWork(
  net::URLRequest* request,
  net::CompletionOnceCallback callback,
  GURL* new_url,
  std::shared_ptr<OnBeforeURLRequestContext> ctx) {
  DCHECK_CURRENTLY_ON(content::BrowserThread::IO);

  if (PendedRequestIsDestroyedOrCancelled(ctx.get(), request)) {
    return net::OK;
  }

  if (ctx->needPerformAdBlock) {
    if (blockers_worker_->shouldAdBlockUrl(
        ctx->firstparty_host,
        request->url().spec(),
        (unsigned int)ctx->info->GetResourceType(),
        ctx->isAdBlockRegionalEnabled)) {
          ctx->block = true;
          ctx->adsBlocked++;
      }
  }

  ctx->check_httpse_redirect = true;
  if (ctx->block && ctx->info && content::RESOURCE_TYPE_IMAGE == ctx->info->GetResourceType()) {
    ctx->check_httpse_redirect = false;
    *new_url = GURL(TRANSPARENT1PXGIF);
  }

   int rv = OnBeforeURLRequest_HttpsePreFileWork(request, std::move(callback), new_url, ctx);
   return rv;
}

int ChromeNetworkDelegate::OnBeforeURLRequest_HttpsePreFileWork(
  net::URLRequest* request,
  net::CompletionOnceCallback callback,
  GURL* new_url,
  std::shared_ptr<OnBeforeURLRequestContext> ctx) {

  DCHECK_CURRENTLY_ON(content::BrowserThread::IO);
  ctx->needPerformHTTPSE = false;
  // HTTPSE work
  if (!ctx->block
      && request
      && ctx->isValidUrl
      && ctx->isGlobalBlockEnabled
      && ctx->isHTTPSEEnabled
      && ctx->check_httpse_redirect
      && (ctx->shieldsSetExplicitly || (enable_httpse_ && enable_httpse_->GetValue()))) {
    ctx->needPerformHTTPSE = true;
  }

  if (ctx->needPerformHTTPSE) {
    ctx->newURL = blockers_worker_->getHTTPSURLFromCacheOnly(&request->url(), request->identifier());
    if (ctx->newURL == request->url().spec()) {
      ctx->UrlCopy = request->url();
      scoped_refptr<base::SequencedTaskRunner> task_runner =
        base::CreateSequencedTaskRunnerWithTraits({base::MayBlock(), base::TaskShutdownBehavior::SKIP_ON_SHUTDOWN});
      task_runner->PostTaskAndReply(FROM_HERE,
        base::Bind(&ChromeNetworkDelegate::OnBeforeURLRequest_HttpseFileWork,
            base::Unretained(this), base::Unretained(request), ctx),
        base::Bind(base::IgnoreResult(&ChromeNetworkDelegate::OnBeforeURLRequest_HttpsePostFileWork),
            base::Unretained(this), base::Unretained(request), base::Passed(&callback), new_url, ctx));
      ctx->pendingAtLeastOnce = true;
      pending_requests_->Insert(request->identifier());
      return net::ERR_IO_PENDING;
    }
  }

  int rv = OnBeforeURLRequest_HttpsePostFileWork(request, std::move(callback), new_url, ctx);
  return rv;
}

void ChromeNetworkDelegate::OnBeforeURLRequest_HttpseFileWork(net::URLRequest* request, std::shared_ptr<OnBeforeURLRequestContext> ctx)
{
  base::AssertBlockingAllowed();
  DCHECK(ctx->request_identifier != 0);
  ctx->newURL = blockers_worker_->getHTTPSURL(&ctx->UrlCopy, ctx->request_identifier);
}

int ChromeNetworkDelegate::OnBeforeURLRequest_HttpsePostFileWork(net::URLRequest* request,net::CompletionOnceCallback callback,GURL* new_url,std::shared_ptr<OnBeforeURLRequestContext> ctx)
{
  DCHECK_CURRENTLY_ON(content::BrowserThread::IO);

  if (PendedRequestIsDestroyedOrCancelled(ctx.get(), request)) {
    return net::OK;
  }

  if (!ctx->newURL.empty() &&
    ctx->needPerformHTTPSE &&
    ctx->newURL != request->url().spec()) {
    *new_url = GURL(ctx->newURL);
    if (last_first_party_url_ != request->url()) {
      ctx->httpsUpgrades++;
    }
  }

  int rv = OnBeforeURLRequest_PostBlockers(request, std::move(callback), new_url, ctx);
  return rv;
}

int ChromeNetworkDelegate::OnBeforeURLRequest_PostBlockers(
    net::URLRequest* request,
    net::CompletionOnceCallback callback,
    GURL* new_url,
    std::shared_ptr<OnBeforeURLRequestContext> ctx)
{
  DCHECK_CURRENTLY_ON(content::BrowserThread::IO);
  net::blockers::ShieldsConfig* shieldsConfig =
     net::blockers::ShieldsConfig::getShieldsConfig();
  if (nullptr != shieldsConfig && (0 != ctx->trackersBlocked || 0 != ctx->adsBlocked || 0 != ctx->httpsUpgrades)) {
    shieldsConfig->setBlockedCountInfo(last_first_party_url_.spec()
        , ctx->trackersBlocked
        , ctx->adsBlocked
        , ctx->httpsUpgrades
        , 0
        , 0);
  }

  if (ctx->block && (nullptr == ctx->info || content::RESOURCE_TYPE_IMAGE != ctx->info->GetResourceType())) {
    *new_url = GURL("");
    if (ctx->pendingAtLeastOnce) {
      std::move(callback).Run(net::ERR_BLOCKED_BY_ADMINISTRATOR);
    }
    return net::ERR_BLOCKED_BY_ADMINISTRATOR;
  }

  extensions_delegate_->ForwardStartRequestStatus(request);

  ShouldBlockReferrer(ctx, request);

  // The non-redirect case is handled in GoogleURLLoaderThrottle.
  bool force_safe_search =
      (force_google_safe_search_ && force_google_safe_search_->GetValue() &&
       request->is_redirecting());

  net::CompletionOnceCallback wrapped_callback = std::move(callback);

  if (force_safe_search) {
    wrapped_callback = base::BindOnce(
        &ForceGoogleSafeSearchCallbackWrapper, std::move(wrapped_callback),
        base::Unretained(request), base::Unretained(new_url));
  }

  int rv = extensions_delegate_->NotifyBeforeURLRequest(
      request, std::move(wrapped_callback), new_url, ctx->pendingAtLeastOnce);

  if (force_safe_search && rv == net::OK && new_url->is_empty())
    safe_search_util::ForceGoogleSafeSearch(request->url(), new_url);

  return rv;
}

namespace {

void SetCustomHeaders(
    net::URLRequest* request,
    net::HttpRequestHeaders* headers) {
  if (request && headers) {
    // Look for and setup custom headers
    std::string customHeaders = stats_updater::GetCustomHeadersForHost(
        request->url().host());
    if (customHeaders.size()) {
      std::string key;
      std::string value;
      size_t pos = customHeaders.find("\n");
      if (pos == std::string::npos) {
          key = customHeaders;
          value = "";
      } else {
          key = customHeaders.substr(0, pos);
          value = customHeaders.substr(pos + 1);
      }
      if (key.size()) {
          headers->SetHeader(key, value);
      }
    }
  }
}

}  // namespace

int ChromeNetworkDelegate::OnBeforeStartTransaction(
    net::URLRequest* request,
    net::CompletionOnceCallback callback,
    net::HttpRequestHeaders* headers) {

  SetCustomHeaders(request, headers);

  return extensions_delegate_->NotifyBeforeStartTransaction(
      request, std::move(callback), headers);
}

void ChromeNetworkDelegate::OnStartTransaction(
    net::URLRequest* request,
    const net::HttpRequestHeaders& headers) {
  extensions_delegate_->NotifyStartTransaction(request, headers);
}

int ChromeNetworkDelegate::OnHeadersReceived(
    net::URLRequest* request,
    net::CompletionOnceCallback callback,
    const net::HttpResponseHeaders* original_response_headers,
    scoped_refptr<net::HttpResponseHeaders>* override_response_headers,
    GURL* allowed_unsafe_redirect_url) {
  return extensions_delegate_->NotifyHeadersReceived(
      request, std::move(callback), original_response_headers,
      override_response_headers, allowed_unsafe_redirect_url);
}

void ChromeNetworkDelegate::OnBeforeRedirect(net::URLRequest* request,
                                             const GURL& new_location) {
  extensions_delegate_->NotifyBeforeRedirect(request, new_location);
  variations::StripVariationsHeaderIfNeeded(new_location, request);
}

void ChromeNetworkDelegate::OnResponseStarted(net::URLRequest* request,
                                              int net_error) {
  extensions_delegate_->NotifyResponseStarted(request, net_error);
}

void ChromeNetworkDelegate::OnNetworkBytesReceived(net::URLRequest* request,
                                                   int64_t bytes_received) {
#if !defined(OS_ANDROID)
  // Note: Currently, OnNetworkBytesReceived is only implemented for HTTP jobs,
  // not FTP or other types, so those kinds of bytes will not be reported here.
  task_manager::TaskManagerInterface::OnRawBytesRead(*request, bytes_received);
#endif  // !defined(OS_ANDROID)
}

void ChromeNetworkDelegate::OnNetworkBytesSent(net::URLRequest* request,
                                               int64_t bytes_sent) {
#if !defined(OS_ANDROID)
  // Note: Currently, OnNetworkBytesSent is only implemented for HTTP jobs,
  // not FTP or other types, so those kinds of bytes will not be reported here.
  task_manager::TaskManagerInterface::OnRawBytesSent(*request, bytes_sent);
#endif  // !defined(OS_ANDROID)
}

void ChromeNetworkDelegate::OnCompleted(net::URLRequest* request,
                                        bool started,
                                        int net_error) {
  extensions_delegate_->NotifyCompleted(request, started, net_error);
  extensions_delegate_->ForwardDoneRequestStatus(request);
}

void ChromeNetworkDelegate::OnURLRequestDestroyed(net::URLRequest* request) {
  extensions_delegate_->NotifyURLRequestDestroyed(request);
  pending_requests_->Destroy(request->identifier());
}

net::NetworkDelegate::AuthRequiredResponse
ChromeNetworkDelegate::OnAuthRequired(net::URLRequest* request,
                                      const net::AuthChallengeInfo& auth_info,
                                      AuthCallback callback,
                                      net::AuthCredentials* credentials) {
  return extensions_delegate_->NotifyAuthRequired(
      request, auth_info, std::move(callback), credentials);
}

namespace {

// Taken from brave-core/components/brave_rewards/browser/net/network_delegate_helper.cc
// TODO(alexeyb) remove when browser-android-tabs will be merged with brave-core
void GetRenderFrameInfo(const net::URLRequest* request,
                        int* render_frame_id,
                        int* render_process_id,
                        int* frame_tree_node_id) {
  DCHECK_CURRENTLY_ON(content::BrowserThread::IO);
  *render_frame_id = -1;
  *render_process_id = -1;
  *frame_tree_node_id = -1;

  // PlzNavigate requests have a frame_tree_node_id, but no render_process_id
  auto* request_info = content::ResourceRequestInfo::ForRequest(request);
  if (request_info) {
    *frame_tree_node_id = request_info->GetFrameTreeNodeId();
  }
  if (!content::ResourceRequestInfo::GetRenderFrameForRequest(
          request, render_process_id, render_frame_id)) {
    const content::WebSocketHandshakeRequestInfo* websocket_info =
      content::WebSocketHandshakeRequestInfo::ForRequest(request);
    if (websocket_info) {
      *render_frame_id = websocket_info->GetRenderFrameId();
      *render_process_id = websocket_info->GetChildId();
    }
  }
}

GURL GetTabUrl(const net::URLRequest* request) {
  DCHECK(request);
  GURL tab_url;
  if (!request->site_for_cookies().is_empty()) {
    tab_url = request->site_for_cookies();
  } else {
    int render_process_id;
    int render_frame_id;
    int frame_tree_node_id;
    GetRenderFrameInfo(request,
                       &render_frame_id,
                       &render_process_id,
                       &frame_tree_node_id);
    // We can not always use site_for_cookies since it can be empty in certain
    // cases. See the comments in url_request.h
    tab_url = brave::BraveTabUrlWebContentsObserver::
        GetTabURLFromRenderFrameInfo(render_process_id,
                                     render_frame_id,
                                     frame_tree_node_id).GetOrigin();
  }

  return tab_url;
}

}  // namespace

bool ChromeNetworkDelegate::OnCanGetCookies(const net::URLRequest& request,
                                            const net::CookieList& cookie_list,
                                            bool allowed_from_caller) {
  // TODO(alexeyb): set cookie_settings_ for all requests
  if (allowed_from_caller && cookie_settings_) {
    GURL tab_url = GetTabUrl(&request);
    bool allowed_from_shields = cookie_settings_->IsCookieAccessAllowed(
        request.url(), tab_url);
    if (!allowed_from_shields) {
      return false;
    }
  }
  ResourceRequestInfo* info = ResourceRequestInfo::ForRequest(&request);
  if (info) {
    base::PostTaskWithTraits(
        FROM_HERE, {BrowserThread::UI},
        base::BindOnce(&TabSpecificContentSettings::CookiesRead,
                       info->GetWebContentsGetterForRequest(), request.url(),
                       request.site_for_cookies(), cookie_list,
                       !allowed_from_caller));
  }
  return allowed_from_caller;
}

bool ChromeNetworkDelegate::OnCanSetCookie(const net::URLRequest& request,
                                           const net::CanonicalCookie& cookie,
                                           net::CookieOptions* options,
                                           bool allowed_from_caller) {
  // TODO(alexeyb): set cookie_settings_ for all requests
  if (allowed_from_caller && cookie_settings_) {
    GURL tab_url = GetTabUrl(&request);
    bool allowed_from_shields = cookie_settings_->IsCookieAccessAllowed(
        request.url(), tab_url);
    if (!allowed_from_shields) {
      return false;
    }
  }
  ResourceRequestInfo* info = ResourceRequestInfo::ForRequest(&request);
  if (info) {
    base::PostTaskWithTraits(
        FROM_HERE, {BrowserThread::UI},
        base::BindOnce(&TabSpecificContentSettings::CookieChanged,
                       info->GetWebContentsGetterForRequest(), request.url(),
                       request.site_for_cookies(), cookie,
                       !allowed_from_caller));
  }
  return allowed_from_caller;
}

bool ChromeNetworkDelegate::OnCanAccessFile(
    const net::URLRequest& request,
    const base::FilePath& original_path,
    const base::FilePath& absolute_path) const {
  return IsAccessAllowed(original_path, absolute_path, profile_path_);
}

// static
bool ChromeNetworkDelegate::IsAccessAllowed(
    const base::FilePath& path,
    const base::FilePath& profile_path) {
  return IsAccessAllowedInternal(path, profile_path);
}

// static
bool ChromeNetworkDelegate::IsAccessAllowed(
    const base::FilePath& path,
    const base::FilePath& absolute_path,
    const base::FilePath& profile_path) {
#if defined(OS_ANDROID)
  // Android's whitelist relies on symbolic links (ex. /sdcard is whitelisted
  // and commonly a symbolic link), thus do not check absolute paths.
  return IsAccessAllowedInternal(path, profile_path);
#else
  return (IsAccessAllowedInternal(path, profile_path) &&
          IsAccessAllowedInternal(absolute_path, profile_path));
#endif
}

// static
void ChromeNetworkDelegate::EnableAccessToAllFilesForTesting(bool enabled) {
  g_access_to_all_files_enabled = enabled;
}

bool ChromeNetworkDelegate::OnCancelURLRequestWithPolicyViolatingReferrerHeader(
    const net::URLRequest& request,
    const GURL& target_url,
    const GURL& referrer_url) const {
  // These errors should be handled by the NetworkDelegate wrapper created by
  // the owning NetworkContext.
  NOTREACHED();
  return true;
}

bool ChromeNetworkDelegate::OnCanQueueReportingReport(
    const url::Origin& origin) const {
  if (!cookie_settings_)
    return false;

  return cookie_settings_->IsCookieAccessAllowed(origin.GetURL(),
                                                 origin.GetURL());
}

void ChromeNetworkDelegate::OnCanSendReportingReports(
    std::set<url::Origin> origins,
    base::OnceCallback<void(std::set<url::Origin>)> result_callback) const {
  if (!reporting_permissions_checker_) {
    origins.clear();
    std::move(result_callback).Run(std::move(origins));
    return;
  }

  reporting_permissions_checker_->FilterReportingOrigins(
      std::move(origins), std::move(result_callback));
}

bool ChromeNetworkDelegate::OnCanSetReportingClient(
    const url::Origin& origin,
    const GURL& endpoint) const {
  if (!cookie_settings_)
    return false;

  return cookie_settings_->IsCookieAccessAllowed(endpoint, origin.GetURL());
}

bool ChromeNetworkDelegate::OnCanUseReportingClient(
    const url::Origin& origin,
    const GURL& endpoint) const {
  if (!cookie_settings_)
    return false;

  return cookie_settings_->IsCookieAccessAllowed(endpoint, origin.GetURL());
}

bool ChromeNetworkDelegate::PendedRequestIsDestroyedOrCancelled(OnBeforeURLRequestContext* ctx, net::URLRequest* request) {
  if (ctx->pendingAtLeastOnce) {
    if ( !pending_requests_->IsPendingAndAlive(ctx->request_identifier)
      || request->status().status() == net::URLRequestStatus::CANCELED) {
      return true;
    }
  }
  return false;
}

void ChromeNetworkDelegate::ShouldBlockReferrer(std::shared_ptr<OnBeforeURLRequestContext> ctx, net::URLRequest* request) {
  /*if (!ctx || !request) {
    return;
  }
  GURL target_origin = GURL(request->url()).GetOrigin();
  GURL tab_origin = request->site_for_cookies().GetOrigin();
  bool allow_referrers = !ctx->isBlock3rdPartyCookies;
  bool shields_up = ctx->isGlobalBlockEnabled;
  std::string original_referrer(request->referrer());
  content::Referrer new_referrer;
  if (net::blockers::BlockersWorker::ShouldSetReferrer(allow_referrers, shields_up,
          GURL(original_referrer), tab_origin, request->url(), target_origin,
          content::Referrer::NetReferrerPolicyToBlinkReferrerPolicy(
              request->referrer_policy()), &new_referrer)) {
      request->SetReferrer(new_referrer.url.spec());
  }*/
}
