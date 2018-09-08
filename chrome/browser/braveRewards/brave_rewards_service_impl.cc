/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */
#include "brave_rewards_service_impl.h"
#include "wallet_properties.h"
#include "balance_report.h"

#include <functional>
#include <limits.h>

#include "base/bind.h"
#include "base/guid.h"
#include "base/files/file_util.h"
#include "base/files/important_file_writer.h"
#include "base/i18n/time_formatting.h"
#include "base/sequenced_task_runner.h"
#include "base/strings/string_number_conversions.h"
#include "base/strings/utf_string_conversions.h"
#include "base/task_runner_util.h"
#include "base/task_scheduler/post_task.h"
#include "base/threading/sequenced_task_runner_handle.h"
#include "bat/ledger/ledger.h"
#include "bat/ledger/publisher_info.h"
#include "bat/ledger/wallet_info.h"
#include "brave_rewards_service_observer.h"
#include "publisher_info_database.h"

#include "chrome/browser/browser_process_impl.h"
#include "chrome/browser/profiles/profile.h"
#include "net/base/escape.h"
#include "net/base/url_util.h"
#include "net/url_request/url_fetcher.h"
#include "url/gurl.h"
#include "url/url_canon_stdstring.h"

 // TODO, just for test purpose
static bool created_wallet = false;
//


using namespace std::placeholders;


namespace brave_rewards {

namespace {

class LedgerURLLoaderImpl : public ledger::LedgerURLLoader {
 public:
  LedgerURLLoaderImpl(uint64_t request_id, net::URLFetcher* fetcher) :
    request_id_(request_id),
    fetcher_(fetcher) {}
  ~LedgerURLLoaderImpl() override = default;

  void Start() override {
    fetcher_->Start();
  }

  uint64_t request_id() override {
    return request_id_;
  }

private:
  uint64_t request_id_;
  net::URLFetcher* fetcher_;  // NOT OWNED
};

ledger::PUBLISHER_MONTH GetPublisherMonth(const base::Time& time) {
  base::Time::Exploded exploded;
  time.LocalExplode(&exploded);
  return (ledger::PUBLISHER_MONTH)exploded.month;
}

int GetPublisherYear(const base::Time& time) {
  base::Time::Exploded exploded;
  time.LocalExplode(&exploded);
  return exploded.year;
}

ContentSite PublisherInfoToContentSite(
    const ledger::PublisherInfo& publisher_info) {
  ContentSite content_site(publisher_info.id);
  content_site.percentage = publisher_info.percent;
  content_site.verified = publisher_info.verified;
  content_site.name = publisher_info.name;
  content_site.url = publisher_info.url;
  content_site.provider = publisher_info.provider;
  content_site.favicon_url = publisher_info.favicon_url;
  return content_site;
}

net::URLFetcher::RequestType URLMethodToRequestType(ledger::URL_METHOD method) {
  switch(method) {
    case ledger::URL_METHOD::GET:
      return net::URLFetcher::RequestType::GET;
    case ledger::URL_METHOD::POST:
      return net::URLFetcher::RequestType::POST;
    case ledger::URL_METHOD::PUT:
      return net::URLFetcher::RequestType::PUT;
    default:
      NOTREACHED();
      return net::URLFetcher::RequestType::GET;
  }
}

std::string LoadStateOnFileTaskRunner(
    const base::FilePath& path) {
  std::string data;
  bool success = base::ReadFileToString(path, &data);

  // Make sure the file isn't empty.
  if (!success || data.empty()) {
    LOG(ERROR) << "Failed to read file: " << path.MaybeAsASCII();
    return std::string();
  }
  return data;
}

bool SaveMediaPublisherInfoOnFileTaskRunner(
    const std::string& media_key,
    const std::string& publisher_id,
    PublisherInfoDatabase* backend) {
  if (backend && backend->InsertOrUpdateMediaPublisherInfo(media_key, publisher_id))
    return true;

  return false;
}

std::unique_ptr<ledger::PublisherInfo>
LoadMediaPublisherInfoListOnFileTaskRunner(
    const std::string media_key,
    PublisherInfoDatabase* backend) {
  std::unique_ptr<ledger::PublisherInfo> info;
  if (!backend)
    return info;

  info = backend->GetMediaPublisherInfo(media_key);
  return info;
}

bool SavePublisherInfoOnFileTaskRunner(
    const ledger::PublisherInfo publisher_info,
    PublisherInfoDatabase* backend) {
  if (backend && backend->InsertOrUpdatePublisherInfo(publisher_info))
    return true;

  return false;
}

ledger::PublisherInfoList LoadPublisherInfoListOnFileTaskRunner(
    uint32_t start,
    uint32_t limit,
    ledger::PublisherInfoFilter filter,
    PublisherInfoDatabase* backend) {
  ledger::PublisherInfoList list;
  if (!backend)
    return list;

  ignore_result(backend->Find(start, limit, filter, &list));
  return list;
}

// `callback` has a WeakPtr so this won't crash if the file finishes
// writing after BraveRewardsServiceImpl has been destroyed
void PostWriteCallback(
    const base::Callback<void(bool success)>& callback,
    scoped_refptr<base::SequencedTaskRunner> reply_task_runner,
    bool write_success) {
  // We can't run |callback| on the current thread. Bounce back to
  // the |reply_task_runner| which is the correct sequenced thread.
  reply_task_runner->PostTask(FROM_HERE,
                              base::Bind(callback, write_success));
}

void GetContentSiteListInternal(
    uint32_t start,
    uint32_t limit,
    const GetContentSiteListCallback& callback,
    const ledger::PublisherInfoList& publisher_list,
    uint32_t next_record) {
  std::unique_ptr<ContentSiteList> site_list(new ContentSiteList);
  for (ledger::PublisherInfoList::const_iterator it =
      publisher_list.begin(); it != publisher_list.end(); ++it) {
    site_list->push_back(PublisherInfoToContentSite(*it));
  }
  callback.Run(std::move(site_list), next_record);
}

time_t GetCurrentTimestamp() {
  return base::Time::NowFromSystemTime().ToTimeT();
}

static uint64_t next_id = 1;

}  // namespace


bool BraveRewardsServiceImpl::IsMediaLink(const std::string& url, const std::string& first_party_url, const std::string& referrer) {
  return ledger::Ledger::IsMediaLink(url, first_party_url, referrer);
}


BraveRewardsServiceImpl::BraveRewardsServiceImpl(Profile* profile) :
    profile_(profile),
    ledger_(ledger::Ledger::CreateInstance(this)),
    file_task_runner_(base::CreateSequencedTaskRunnerWithTraits(
        {base::MayBlock(), base::TaskPriority::BACKGROUND,
         base::TaskShutdownBehavior::BLOCK_SHUTDOWN})),
    ledger_state_path_(profile_->GetPath().Append("ledger_state")),
    publisher_state_path_(profile_->GetPath().Append("publisher_state")),
    publisher_info_db_path_(profile->GetPath().Append("publisher_info_db")),
    publisher_list_path_(profile->GetPath().Append("publishers_list")),
    publisher_info_backend_(new PublisherInfoDatabase(publisher_info_db_path_)),
    next_timer_id_(0) {
// TODO(bridiver) - production/verbose should
// also be controllable by command line flags
#if defined(IS_OFFICIAL_BUILD)
ledger::is_production = true;
#else
ledger::is_production = false;
#endif

#if defined(NDEBUG)
ledger::is_verbose = false;
#else
ledger::is_verbose = true;
#endif
}

BraveRewardsServiceImpl::~BraveRewardsServiceImpl() {
  file_task_runner_->DeleteSoon(FROM_HERE, publisher_info_backend_.release());
}

void BraveRewardsServiceImpl::Init() {
  ledger_->Initialize();
}

void BraveRewardsServiceImpl::CreateWallet() {

  if (created_wallet) {
    return;
  }
  ledger_->CreateWallet();
  created_wallet = true;

  /* TODO:
  if (ready().is_signaled()) {
    ledger_->CreateWallet();
  } else {
    ready().Post(FROM_HERE,
        base::Bind(&brave_rewards::BraveRewardsService::CreateWallet,
            base::Unretained(this)));
  }
  */
}

void BraveRewardsServiceImpl::GetContentSiteList(
    uint32_t start, uint32_t limit,
    const GetContentSiteListCallback& callback) {
  auto now = base::Time::Now();
  ledger::PublisherInfoFilter filter;
  filter.category = ledger::PUBLISHER_CATEGORY::AUTO_CONTRIBUTE;
  filter.month = GetPublisherMonth(now);
  filter.year = GetPublisherYear(now);
  filter.order_by.push_back(std::pair<std::string, bool>("percent", false));

  ledger_->GetPublisherInfoList(start, limit,
      filter,
      std::bind(&GetContentSiteListInternal,
                start,
                limit,
                callback, _1, _2));
}

void BraveRewardsServiceImpl::SetBalanceReport(const ledger::BalanceReportInfo& report_info) {
  auto now = base::Time::Now();
  ledger_->SetBalanceReport(GetPublisherMonth(now), GetPublisherYear(now), report_info);
}

bool BraveRewardsServiceImpl::GetBalanceReport(ledger::BalanceReportInfo* report_info) const {
  auto now = base::Time::Now();
  return ledger_->GetBalanceReport(GetPublisherMonth(now), GetPublisherYear(now), report_info);
}

void BraveRewardsServiceImpl::OnLoad(const std::string& _tld,
            const std::string& _domain,
            const std::string& _path,
            uint32_t tab_id) {

  if (_tld == "")
    return;

  auto now = base::Time::Now();
  ledger::VisitData data(_tld,
                         _domain,
                         _path,
                         tab_id,
                         GetPublisherMonth(now),
                         GetPublisherYear(now),
                         _tld, 	//TODO: check
                         _tld,
                         "",
                         "");
  ledger_->OnLoad(data, GetCurrentTimestamp());
}

void BraveRewardsServiceImpl::OnUnload(uint32_t tab_id) {

  ledger_->OnUnload(tab_id, GetCurrentTimestamp());
}

void BraveRewardsServiceImpl::OnShow(uint32_t tab_id) {
  ledger_->OnShow(tab_id, GetCurrentTimestamp());
}

void BraveRewardsServiceImpl::OnHide(uint32_t tab_id) {
  ledger_->OnHide(tab_id, GetCurrentTimestamp());
}

void BraveRewardsServiceImpl::OnForeground(uint32_t tab_id) {
  ledger_->OnForeground(tab_id, GetCurrentTimestamp());
}

void BraveRewardsServiceImpl::OnBackground(uint32_t tab_id) {
  ledger_->OnBackground(tab_id, GetCurrentTimestamp());
}

void BraveRewardsServiceImpl::OnMediaStart(uint32_t tab_id) {
  ledger_->OnMediaStart(tab_id, GetCurrentTimestamp());
}

void BraveRewardsServiceImpl::OnMediaStop(uint32_t tab_id) {
  ledger_->OnMediaStop(tab_id, GetCurrentTimestamp());
}

void BraveRewardsServiceImpl::OnPostData(/*SessionID tab_id,*/
                                    const GURL& url,
                                    const std::string & first_party_url,
                                    const std::string & referrer,
                                    const std::string& post_data) {
  std::string output;
  url::RawCanonOutputW<1024> canonOutput;
  url::DecodeURLEscapeSequences(post_data.c_str(),
                                post_data.length(),
                                &canonOutput);
  output = base::UTF16ToUTF8(base::StringPiece16(canonOutput.data(),
                                                 canonOutput.length()));

  if (output.empty())
    return;

  auto now = base::Time::Now();
  ledger::VisitData visit_data(
      "",
      "",
      url.spec(),
      /*tab_id.id(),*/ 0, //TODO
      GetPublisherMonth(now),
      GetPublisherYear(now),
      "",
      "",
      "",
      "");

  ledger_->OnPostData(url.spec(),
                      first_party_url,
                      referrer,
                      output,
                      visit_data);
}

void BraveRewardsServiceImpl::OnXHRLoad(uint32_t tab_id,
                                   const GURL& url,
                                   const std::string & first_party_url,
                                   const std::string & referrer) {
  std::map<std::string, std::string> parts;

  for (net::QueryIterator it(url); !it.IsAtEnd(); it.Advance()) {
    parts[it.GetKey()] = it.GetUnescapedValue();
  }

  auto now = base::Time::Now();
  ledger::VisitData data("", "", url.spec(), tab_id,
                         GetPublisherMonth(now), GetPublisherYear(now),
                         "", "", "", "");

  ledger_->OnXHRLoad(tab_id,
                     url.spec(),
                     parts,
                     first_party_url,
                     referrer,
                     data);
}

void BraveRewardsServiceImpl::LoadMediaPublisherInfo(
    const std::string& media_key,
    ledger::PublisherInfoCallback callback) {
  base::PostTaskAndReplyWithResult(file_task_runner_.get(), FROM_HERE,
      base::Bind(&LoadMediaPublisherInfoListOnFileTaskRunner,
          media_key, publisher_info_backend_.get()),
      base::Bind(&BraveRewardsServiceImpl::OnMediaPublisherInfoLoaded,
                     AsWeakPtr(),
                     callback));
}

void BraveRewardsServiceImpl::OnMediaPublisherInfoLoaded(
    ledger::PublisherInfoCallback callback,
    std::unique_ptr<ledger::PublisherInfo> info) {
  if (!info) {
    callback(ledger::Result::NOT_FOUND, std::move(info));
    return;
  }

  callback(ledger::Result::OK, std::move(info));
}

void BraveRewardsServiceImpl::SaveMediaPublisherInfo(
    const std::string& media_key,
    const std::string& publisher_id) {
base::PostTaskAndReplyWithResult(file_task_runner_.get(), FROM_HERE,
      base::Bind(&SaveMediaPublisherInfoOnFileTaskRunner,
                    media_key,
                    publisher_id,
                    publisher_info_backend_.get()),
      base::Bind(&BraveRewardsServiceImpl::OnMediaPublisherInfoSaved,
                     AsWeakPtr()));
}

void BraveRewardsServiceImpl::OnMediaPublisherInfoSaved(bool success) {
  if (!success) {
    VLOG(1) << "Error in OnMediaPublisherInfoSaved";
  }
}

std::string BraveRewardsServiceImpl::URIEncode(const std::string& value) {
  return net::EscapeQueryParamValue(value, false);
}
void BraveRewardsServiceImpl::SetPublisherMinVisitTime(uint64_t duration_in_milliseconds) {
  ledger_->SetPublisherMinVisitTime(duration_in_milliseconds);
}

void BraveRewardsServiceImpl::SetPublisherMinVisits(unsigned int visits) {
  ledger_->SetPublisherMinVisits(visits);
}

void BraveRewardsServiceImpl::SetPublisherAllowNonVerified(bool allow) {
  ledger_->SetPublisherAllowNonVerified(allow);
}

void BraveRewardsServiceImpl::SetContributionAmount(double amount) {
  ledger_->SetContributionAmount(amount);
}

uint64_t BraveRewardsServiceImpl::GetPublisherMinVisitTime() const {
  return ledger_->GetPublisherMinVisitTime();
}

unsigned int BraveRewardsServiceImpl::GetPublisherMinVisits() const {
  return ledger_->GetPublisherMinVisits();
}

bool BraveRewardsServiceImpl::GetPublisherAllowNonVerified() const {
  return ledger_->GetPublisherAllowNonVerified();
}

double BraveRewardsServiceImpl::GetContributionAmount() const {
  return ledger_->GetContributionAmount();
}

std::string BraveRewardsServiceImpl::GenerateGUID() const {
  return base::GenerateGUID();
}

void BraveRewardsServiceImpl::Shutdown() {
  fetchers_.clear();
  ledger_.reset();
  BraveRewardsService::Shutdown();
}

void BraveRewardsServiceImpl::OnWalletInitialized(ledger::Result result) {
  //TODO:
  //if (!ready_.is_signaled())
  //  ready_.Signal();
  TriggerOnWalletInitialized(result);
}

void BraveRewardsServiceImpl::OnWalletProperties(ledger::Result result,
    std::unique_ptr<ledger::WalletInfo> wallet_info) {
  TriggerOnWalletProperties(result, std::move(wallet_info));
}

void BraveRewardsServiceImpl::OnGrant(ledger::Result result,
                                 const ledger::Grant& grant) {
  TriggerOnGrant(result, grant);
}

void BraveRewardsServiceImpl::OnGrantCaptcha(const std::string& image) {
  TriggerOnGrantCaptcha(image);
}

void BraveRewardsServiceImpl::OnRecoverWallet(ledger::Result result,
                                    double balance,
                                    const std::vector<ledger::Grant>& grants) {
  TriggerOnRecoverWallet(result, balance, grants);
}

void BraveRewardsServiceImpl::OnGrantFinish(ledger::Result result,
                                       const ledger::Grant& grant) {
  ledger::BalanceReportInfo report_info;
  auto now = base::Time::Now();
  ledger_->GetBalanceReport(GetPublisherMonth(now), GetPublisherYear(now), &report_info);
  report_info.grants_ += 10.0; // TODO NZ convert probi to
  ledger_->SetBalanceReport(GetPublisherMonth(now), GetPublisherYear(now), report_info);
  TriggerOnGrantFinish(result, grant);
}

void BraveRewardsServiceImpl::OnReconcileComplete(ledger::Result result,
                                              const std::string& viewing_id) {
  LOG(ERROR) << "reconcile complete " << viewing_id;
  // TODO - TriggerOnReconcileComplete
}

void BraveRewardsServiceImpl::LoadLedgerState(
    ledger::LedgerCallbackHandler* handler) {
  base::PostTaskAndReplyWithResult(file_task_runner_.get(), FROM_HERE,
      base::Bind(&LoadStateOnFileTaskRunner, ledger_state_path_),
      base::Bind(&BraveRewardsServiceImpl::OnLedgerStateLoaded,
                     AsWeakPtr(),
                     base::Unretained(handler)));
}

void BraveRewardsServiceImpl::OnLedgerStateLoaded(
    ledger::LedgerCallbackHandler* handler,
    const std::string& data) {
  handler->OnLedgerStateLoaded(data.empty() ? ledger::Result::ERROR
                                            : ledger::Result::OK,
                               data);
}

void BraveRewardsServiceImpl::LoadPublisherState(
    ledger::LedgerCallbackHandler* handler) {
  base::PostTaskAndReplyWithResult(file_task_runner_.get(), FROM_HERE,
      base::Bind(&LoadStateOnFileTaskRunner, publisher_state_path_),
      base::Bind(&BraveRewardsServiceImpl::OnPublisherStateLoaded,
                     AsWeakPtr(),
                     base::Unretained(handler)));
}

void BraveRewardsServiceImpl::OnPublisherStateLoaded(
    ledger::LedgerCallbackHandler* handler,
    const std::string& data) {
  handler->OnPublisherStateLoaded(
      data.empty() ? ledger::Result::NO_PUBLISHER_STATE
                   : ledger::Result::OK,
      data);
}

void BraveRewardsServiceImpl::SaveLedgerState(const std::string& ledger_state,
                                      ledger::LedgerCallbackHandler* handler) {
  base::ImportantFileWriter writer(
      ledger_state_path_, file_task_runner_);

  writer.RegisterOnNextWriteCallbacks(
      base::Closure(),
      base::Bind(
        &PostWriteCallback,
        base::Bind(&BraveRewardsServiceImpl::OnLedgerStateSaved, AsWeakPtr(),
            base::Unretained(handler)),
        base::SequencedTaskRunnerHandle::Get()));

  writer.WriteNow(std::make_unique<std::string>(ledger_state));
}

void BraveRewardsServiceImpl::OnLedgerStateSaved(
    ledger::LedgerCallbackHandler* handler,
    bool success) {
  handler->OnLedgerStateSaved(success ? ledger::Result::OK
                                      : ledger::Result::NO_LEDGER_STATE);
}

void BraveRewardsServiceImpl::SavePublisherState(const std::string& publisher_state,
                                      ledger::LedgerCallbackHandler* handler) {
  base::ImportantFileWriter writer(publisher_state_path_, file_task_runner_);

  writer.RegisterOnNextWriteCallbacks(
      base::Closure(),
      base::Bind(
        &PostWriteCallback,
        base::Bind(&BraveRewardsServiceImpl::OnPublisherStateSaved, AsWeakPtr(),
            base::Unretained(handler)),
        base::SequencedTaskRunnerHandle::Get()));

  writer.WriteNow(std::make_unique<std::string>(publisher_state));
}

void BraveRewardsServiceImpl::OnPublisherStateSaved(
    ledger::LedgerCallbackHandler* handler,
    bool success) {
  handler->OnPublisherStateSaved(success ? ledger::Result::OK
                                         : ledger::Result::ERROR);
}

void BraveRewardsServiceImpl::SavePublisherInfo(
    std::unique_ptr<ledger::PublisherInfo> publisher_info,
    ledger::PublisherInfoCallback callback) {
  base::PostTaskAndReplyWithResult(file_task_runner_.get(), FROM_HERE,
      base::Bind(&SavePublisherInfoOnFileTaskRunner,
                    *publisher_info,
                    publisher_info_backend_.get()),
      base::Bind(&BraveRewardsServiceImpl::OnPublisherInfoSaved,
                     AsWeakPtr(),
                     callback,
                     base::Passed(std::move(publisher_info))));

}

void BraveRewardsServiceImpl::OnPublisherInfoSaved(
    ledger::PublisherInfoCallback callback,
    std::unique_ptr<ledger::PublisherInfo> info,
    bool success) {
  callback(success ? ledger::Result::OK
                   : ledger::Result::ERROR, std::move(info));

  TriggerOnContentSiteUpdated();
}

void BraveRewardsServiceImpl::LoadPublisherInfo(
    ledger::PublisherInfoFilter filter,
    ledger::PublisherInfoCallback callback) {
  base::PostTaskAndReplyWithResult(file_task_runner_.get(), FROM_HERE,
      base::Bind(&LoadPublisherInfoListOnFileTaskRunner,
          // set limit to 2 to make sure there is
          // only 1 valid result for the filter
          0, 2, filter, publisher_info_backend_.get()),
      base::Bind(&BraveRewardsServiceImpl::OnPublisherInfoLoaded,
                     AsWeakPtr(),
                     callback));
}

void BraveRewardsServiceImpl::OnPublisherInfoLoaded(
    ledger::PublisherInfoCallback callback,
    const ledger::PublisherInfoList list) {
  if (list.size() == 0) {
    callback(ledger::Result::NOT_FOUND,
        std::unique_ptr<ledger::PublisherInfo>());
    return;
  } else if (list.size() > 1) {
    callback(ledger::Result::TOO_MANY_RESULTS,
        std::unique_ptr<ledger::PublisherInfo>());
    return;
  }

  callback(ledger::Result::OK,
      std::make_unique<ledger::PublisherInfo>(list[0]));
}

void BraveRewardsServiceImpl::LoadPublisherInfoList(
    uint32_t start,
    uint32_t limit,
    ledger::PublisherInfoFilter filter,
    ledger::GetPublisherInfoListCallback callback) {
  base::PostTaskAndReplyWithResult(file_task_runner_.get(), FROM_HERE,
      base::Bind(&LoadPublisherInfoListOnFileTaskRunner,
                    start, limit, filter,
                    publisher_info_backend_.get()),
      base::Bind(&BraveRewardsServiceImpl::OnPublisherInfoListLoaded,
                    AsWeakPtr(),
                    start,
                    limit,
                    callback));
}

void BraveRewardsServiceImpl::OnPublisherInfoListLoaded(
    uint32_t start,
    uint32_t limit,
    ledger::GetPublisherInfoListCallback callback,
    const ledger::PublisherInfoList& list) {
  uint32_t next_record = 0;
  if (list.size() == limit)
    next_record = start + limit + 1;

  callback(std::cref(list), next_record);
}

std::unique_ptr<ledger::LedgerURLLoader> BraveRewardsServiceImpl::LoadURL(
    const std::string& url,
    const std::vector<std::string>& headers,
    const std::string& content,
    const std::string& contentType,
    const ledger::URL_METHOD& method,
    ledger::LedgerCallbackHandler* handler) {
  net::URLFetcher::RequestType request_type = URLMethodToRequestType(method);

  net::URLFetcher* fetcher = net::URLFetcher::Create(
      GURL(url), request_type, this).release();
  fetcher->SetRequestContext(g_browser_process->system_request_context());

  for (size_t i = 0; i < headers.size(); i++)
    fetcher->AddExtraRequestHeader(headers[i]);

  if (!content.empty())
    fetcher->SetUploadData(contentType, content);

  if (VLOG_IS_ON(2)) {
    std::string printMethod;
    switch (method) {
      case ledger::URL_METHOD::POST:
        printMethod = "POST";
        break;
      case ledger::URL_METHOD::PUT:
        printMethod = "PUT";
        break;
      default:
        printMethod = "GET";
        break;
    }
    VLOG(2) << "[ REQUEST ]";
    VLOG(2) << "> url: " << url;
    VLOG(2) << "> method: " << printMethod;
    VLOG(2) << "> content: " << content;
    VLOG(2) << "> contentType: " << contentType;
    for (size_t i = 0; i < headers.size(); i++) {
      VLOG(2) << "> headers: " << headers[i];
    }
    VLOG(2) << "[ END REQUEST ]";
  }

  FetchCallback callback = base::Bind(
      &ledger::LedgerCallbackHandler::OnURLRequestResponse,
      base::Unretained(handler),
      next_id,
      url);
  fetchers_[fetcher] = callback;

  std::unique_ptr<ledger::LedgerURLLoader> loader(
      new LedgerURLLoaderImpl(next_id++, fetcher));

  return loader;
}

void BraveRewardsServiceImpl::OnURLFetchComplete(
    const net::URLFetcher* source) {
  if (fetchers_.find(source) == fetchers_.end())
    return;

  auto callback = fetchers_[source];
  fetchers_.erase(source);

  int response_code = source->GetResponseCode();
  std::string body;
  if (response_code != net::URLFetcher::ResponseCode::RESPONSE_CODE_INVALID &&
      source->GetStatus().is_success()) {
    source->GetResponseAsString(&body);
  }

  callback.Run(response_code, body);
}

void BraveRewardsServiceImpl::RunIOTask(
    std::unique_ptr<ledger::LedgerTaskRunner> task) {
  file_task_runner_->PostTask(FROM_HERE,
      base::BindOnce(&ledger::LedgerTaskRunner::Run, std::move(task)));
}

void BraveRewardsServiceImpl::RunTask(
      std::unique_ptr<ledger::LedgerTaskRunner> task) {
  content::BrowserThread::PostTask(content::BrowserThread::UI, FROM_HERE,
      base::BindOnce(&ledger::LedgerTaskRunner::Run,
                     std::move(task)));
}

void BraveRewardsServiceImpl::TriggerOnWalletInitialized(int error_code) {
  for (auto& observer : observers_)
    observer.OnWalletInitialized(this, error_code);
}

void BraveRewardsServiceImpl::TriggerOnWalletProperties(int error_code,
    std::unique_ptr<ledger::WalletInfo> wallet_info) {
  std::unique_ptr<brave_rewards::WalletProperties> wallet_properties;

  if (wallet_info) {
    wallet_properties.reset(new brave_rewards::WalletProperties);
    wallet_properties->probi = wallet_info->probi_;
    wallet_properties->balance = wallet_info->balance_;
    wallet_properties->rates = wallet_info->rates_;
    wallet_properties->parameters_choices = wallet_info->parameters_choices_;
    wallet_properties->parameters_range = wallet_info->parameters_range_;
    wallet_properties->parameters_days = wallet_info->parameters_days_;

    for (size_t i = 0; i < wallet_info->grants_.size(); i ++) {
      ledger::Grant grant;

      grant.altcurrency = wallet_info->grants_[i].altcurrency;
      grant.probi = wallet_info->grants_[i].probi;
      grant.expiryTime = wallet_info->grants_[i].expiryTime;

      wallet_properties->grants.push_back(grant);
    }
  }

  for (auto& observer : observers_)
    observer.OnWalletProperties(this, error_code, std::move(wallet_properties));
}

void BraveRewardsServiceImpl::GetWalletProperties() {
  ledger_->GetWalletProperties();

  /*
  TODO
  if (ready().is_signaled()) {
    ledger_->GetWalletProperties();
  } else {
    ready().Post(FROM_HERE,
        base::Bind(&brave_rewards::BraveRewardsService::GetWalletProperties,
            base::Unretained(this)));
  }
  */
}

void BraveRewardsServiceImpl::GetGrant(const std::string& lang,
    const std::string& payment_id) {
  ledger_->GetGrant(lang, payment_id);
}

void BraveRewardsServiceImpl::TriggerOnGrant(ledger::Result result,
                                        const ledger::Grant& grant) {
  ledger::Grant properties;

  properties.promotionId = grant.promotionId;
  properties.altcurrency = grant.altcurrency;
  properties.probi = grant.probi;
  properties.expiryTime = grant.expiryTime;

  for (auto& observer : observers_)
    observer.OnGrant(this, result, properties);
}

void BraveRewardsServiceImpl::GetGrantCaptcha() {
  ledger_->GetGrantCaptcha();
}

void BraveRewardsServiceImpl::TriggerOnGrantCaptcha(const std::string& image) {
  for (auto& observer : observers_)
    observer.OnGrantCaptcha(this, image);
}

std::string BraveRewardsServiceImpl::GetWalletPassphrase() const {
  return ledger_->GetWalletPassphrase();
}

void BraveRewardsServiceImpl::RecoverWallet(const std::string passPhrase) const {
  return ledger_->RecoverWallet(passPhrase);
}

void BraveRewardsServiceImpl::TriggerOnRecoverWallet(ledger::Result result,
                                                double balance,
                                    const std::vector<ledger::Grant>& grants) {
  std::vector<ledger::Grant> newGrants;
  for (size_t i = 0; i < grants.size(); i ++) {
    ledger::Grant grant;

    grant.altcurrency = grants[i].altcurrency;
    grant.probi = grants[i].probi;
    grant.expiryTime = grants[i].expiryTime;

    newGrants.push_back(grant);
  }
  for (auto& observer : observers_)
    observer.OnRecoverWallet(this, result, balance, newGrants);
}

void BraveRewardsServiceImpl::SolveGrantCaptcha(const std::string& solution) const {
  return ledger_->SolveGrantCaptcha(solution);
}

void BraveRewardsServiceImpl::TriggerOnGrantFinish(ledger::Result result,
                                              const ledger::Grant& grant) {
  ledger::Grant properties;

  properties.promotionId = grant.promotionId;
  properties.altcurrency = grant.altcurrency;
  properties.probi = grant.probi;
  properties.expiryTime = grant.expiryTime;

  for (auto& observer : observers_)
    observer.OnGrantFinish(this, result, properties);
}

uint64_t BraveRewardsServiceImpl::GetReconcileStamp() const {
  return ledger_->GetReconcileStamp();
}

std::map<std::string, std::string> BraveRewardsServiceImpl::GetAddresses() const {
  std::map<std::string, std::string> addresses;
  addresses.emplace("BAT", ledger_->GetBATAddress());
  addresses.emplace("BTC", ledger_->GetBTCAddress());
  addresses.emplace("ETH", ledger_->GetETHAddress());
  addresses.emplace("LTC", ledger_->GetLTCAddress());
  return addresses;
}


void BraveRewardsServiceImpl::SetPublisherAllowVideos(bool allow) {
  return ledger_->SetPublisherAllowVideos(allow);
}

void BraveRewardsServiceImpl::SetAutoContribute(bool enabled) {
  return ledger_->SetAutoContribute(enabled);
}

void BraveRewardsServiceImpl::TriggerOnContentSiteUpdated() {
  for (auto& observer : observers_)
    observer.OnContentSiteUpdated(this);
}

void BraveRewardsServiceImpl::SavePublishersList(const std::string& publishers_list,
                                      ledger::LedgerCallbackHandler* handler) {
  base::ImportantFileWriter writer(
      publisher_list_path_, file_task_runner_);

  writer.RegisterOnNextWriteCallbacks(
      base::Closure(),
      base::Bind(
        &PostWriteCallback,
        base::Bind(&BraveRewardsServiceImpl::OnPublishersListSaved, AsWeakPtr(),
            base::Unretained(handler)),
        base::SequencedTaskRunnerHandle::Get()));

  writer.WriteNow(std::make_unique<std::string>(publishers_list));
}

void BraveRewardsServiceImpl::OnPublishersListSaved(
    ledger::LedgerCallbackHandler* handler,
    bool success) {
  handler->OnPublishersListSaved(success ? ledger::Result::OK
                                         : ledger::Result::ERROR);
}

void BraveRewardsServiceImpl::SetTimer(uint64_t time_offset,
                                  uint32_t& timer_id) {
  if (next_timer_id_ == std::numeric_limits<uint32_t>::max())
    next_timer_id_ = 1;
  else
    ++next_timer_id_;

  timer_id = next_timer_id_;

  timers_[next_timer_id_] = std::make_unique<base::OneShotTimer>();
  timers_[next_timer_id_]->Start(FROM_HERE,
      base::TimeDelta::FromSeconds(time_offset),
      base::Bind(
          &BraveRewardsServiceImpl::OnTimer, AsWeakPtr(), next_timer_id_));
}

void BraveRewardsServiceImpl::OnTimer(uint32_t timer_id) {
  ledger_->OnTimer(timer_id);
  timers_.erase(timer_id);
}

void BraveRewardsServiceImpl::LoadPublisherList(
    ledger::LedgerCallbackHandler* handler) {
  base::PostTaskAndReplyWithResult(file_task_runner_.get(), FROM_HERE,
                                   base::Bind(&LoadStateOnFileTaskRunner, publisher_list_path_),
                                   base::Bind(&BraveRewardsServiceImpl::OnPublisherListLoaded,
                                              AsWeakPtr(),
                                              base::Unretained(handler)));
}

void BraveRewardsServiceImpl::OnPublisherListLoaded(
    ledger::LedgerCallbackHandler* handler,
    const std::string& data) {
  handler->OnPublisherListLoaded(
      data.empty() ? ledger::Result::NO_PUBLISHER_LIST
                   : ledger::Result::OK,
      data);
}

std::map<std::string, brave_rewards::BalanceReport> BraveRewardsServiceImpl::GetAllBalanceReports() {
  std::map<std::string, ledger::BalanceReportInfo> reports = ledger_->GetAllBalanceReports();

  std::map<std::string, brave_rewards::BalanceReport> newReports;
  for (auto const& report : reports) {
    brave_rewards::BalanceReport newReport;
    const ledger::BalanceReportInfo oldReport = report.second;
    newReport.opening_balance = oldReport.opening_balance_;
    newReport.closing_balance = oldReport.closing_balance_;
    newReport.grants = oldReport.grants_;
    newReport.earning_from_ads = oldReport.earning_from_ads_;
    newReport.auto_contribute = oldReport.auto_contribute_;
    newReport.recurring_donation = oldReport.recurring_donation_;
    newReport.one_time_donation = oldReport.one_time_donation_;

    newReports[report.first] = newReport;
  }

  return newReports;
}

}  // namespace brave_rewards
