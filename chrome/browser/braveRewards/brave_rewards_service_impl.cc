/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */
#include "brave_rewards_service_impl.h"

#include "base/bind.h"
#include "base/guid.h"
#include "base/files/file_util.h"
#include "base/files/important_file_writer.h"
#include "base/sequenced_task_runner.h"
#include "base/task_runner_util.h"
#include "base/task_scheduler/post_task.h"
#include "base/threading/sequenced_task_runner_handle.h"
#include "bat/ledger/ledger.h"
#include "brave_rewards_service_observer.h"
#include "publisher_info_backend.h"
#include "chrome/browser/browser_process_impl.h"
#include "chrome/browser/profiles/profile.h"
#include "net/base/escape.h"
#include "net/base/url_util.h"
#include "net/url_request/url_fetcher.h"
#include "url/gurl.h"

// TODO, just for test purpose
static bool created_wallet = false;
//

using namespace std::placeholders;


namespace brave_rewards {

namespace {

void GetLocalMonthYear(std::string& month, std::string& year) {
  base::Time::Exploded exploded;
  base::Time::Now().LocalExplode(&exploded);
  month = std::to_string(exploded.month);
  year = std::to_string(exploded.year);
}

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

bool SavePublisherInfoOnFileTaskRunner(
    ledger::PublisherInfo publisher_info,
    PublisherInfoBackend* backend) {
  if (backend && backend->Put(publisher_info.key, publisher_info.ToJSON())) {
    return true;
  }

  return false;
}

std::unique_ptr<ledger::PublisherInfo> LoadPublisherInfoOnFileTaskRunner(
    const std::string& key,
    PublisherInfoBackend* backend) {
  std::unique_ptr<ledger::PublisherInfo> info;
   std::string json;
  if (backend && backend->Get(key, &json)) {
    info.reset(
        new ledger::PublisherInfo(ledger::PublisherInfo::FromJSON(json)));
  }

  return info;
}

ledger::PublisherInfoList LoadPublisherInfoListOnFileTaskRunner(
    uint32_t start,
    uint32_t limit,
    ledger::PublisherInfoFilter filter,
    const std::vector<std::string>& prefix,
    PublisherInfoBackend* backend) {
  ledger::PublisherInfoList list;
   std::vector<const std::string> results;
  if (backend && backend->LoadWithPrefix(start, limit, prefix, results)) {
    for (std::vector<const std::string>::const_iterator it =
        results.begin(); it != results.end(); ++it) {
      list.push_back(ledger::PublisherInfo::FromJSON(*it));
    }
  }

  return list;
}

// Callback for publishers list
 // void GotContentSiteListInternal(
 //     uint32_t start,
 //     uint32_t limit,
 //     const ledger::PublisherInfoList& publisher_list,
 //     uint32_t next_record) {
 //   // TODO handle the publishers info list
 // }

// Callback for recurrent donations publishers list
// void GotRecurringDonationPublisherInfo(
//       ledger::Result result,
//       std::unique_ptr<ledger::PublisherInfo> publisher_info) {
//   if (result != ledger::Result::OK) {
//     // TODO error handling
//     return;
//   }
//   if (publisher_info) {
//     for (size_t i = 0; i < publisher_info->contributions.size(); i++) {
//       LOG(ERROR) << "!!!publisher " << publisher_info->contributions[i].publisher;
//       LOG(ERROR) << "!!!value " << publisher_info->contributions[i].value;
//     }
//   }
// }

static uint64_t next_id = 1;

}  // namespace

BraveRewardsServiceImpl::BraveRewardsServiceImpl(Profile* profile) :
    profile_(profile),
    ledger_(ledger::Ledger::CreateInstance(this)),
    file_task_runner_(base::CreateSequencedTaskRunnerWithTraits(
        {base::MayBlock(), base::TaskPriority::BACKGROUND,
         base::TaskShutdownBehavior::BLOCK_SHUTDOWN})),
    ledger_state_path_(profile_->GetPath().Append("ledger_state")),
    publisher_state_path_(profile_->GetPath().Append("publisher_state")),
    publisher_info_db_path_(profile->GetPath().Append("publisher_info")),
    publisher_info_backend_(new PublisherInfoBackend(publisher_info_db_path_)) {
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
  // TODO debug
  created_wallet = true;
  //
}

/*void BraveRewardsServiceImpl::SaveVisit(const std::string& publisher,
                 uint64_t duration,
                 bool ignoreMinTime) {
  ledger::VisitData visit_data(publisher, publisher, "", 0);
  visit_data.duration = duration;
  ledger_->OnVisit(visit_data);
}*/

void BraveRewardsServiceImpl::MakePayment(const ledger::PaymentData& payment_data) {
  ledger_->MakePayment(payment_data);
}

void BraveRewardsServiceImpl::AddRecurringPayment(const std::string& domain, const double& value) {
  ledger_->AddRecurringPayment(domain, value);
}

void BraveRewardsServiceImpl::SetBalanceReport(const ledger::BalanceReportInfo& report_info) {
  std::string month;
  std::string year;
  GetLocalMonthYear(month, year);
  ledger_->SetBalanceReport(year, month, report_info);
}

bool BraveRewardsServiceImpl::GetBalanceReport(ledger::BalanceReportInfo* report_info) const {
  std::string month;
  std::string year;
  GetLocalMonthYear(month, year);
  return ledger_->GetBalanceReport(year, month, report_info);
}

void BraveRewardsServiceImpl::OnLoad(const std::string& _tld,
            const std::string& _domain,
            const std::string& _path,
            uint32_t tab_id) {
  std::string month;
  std::string year;
  GetLocalMonthYear(month, year);
  ledger::VisitData visit_data(_tld, _domain, _path, tab_id, month, year);
  ledger_->OnLoad(visit_data, base::TimeTicks::Now().since_origin().InMilliseconds());
  // TODO adding a publisher to a Publishers List
  // {
  //   ledger::PaymentData payment_data("clifton.io", 10.2, base::Time::Now().ToTimeT(), 
  //     ledger::PUBLISHER_CATEGORY::TIPPING, month, year);
  //   MakePayment(payment_data);
  // }
  // {
  //   ledger::PaymentData payment_data("clifton.io", 10.9, base::Time::Now().ToTimeT(), 
  //     ledger::PUBLISHER_CATEGORY::DIRECT_DONATION, month, year);
  //   MakePayment(payment_data);
  // }
  //
  // TODO adding a publisher to a recurrent payment
  //AddRecurringPayment("clifton.io", 1.111);
  //
  // TODO sets the current balance
   // ledger::BalanceReportInfo report_info;
   // report_info.opening_balance_ = 1.0;
   // report_info.closing_balance_ = 2.0;
   // report_info.grants_avail_ = 3.0;
   // report_info.earning_from_ads_ = 4.0;
   // report_info.auto_contribute_ = 5.0;
   // report_info.recurring_donation_ = 6.0;
   // report_info.one_time_donation_ = 7.0;
   // SetBalanceReport(report_info);
  //
}

void BraveRewardsServiceImpl::OnUnload(uint32_t tab_id) {
  ledger_->OnUnload(tab_id, base::TimeTicks::Now().since_origin().InMilliseconds());
}

void BraveRewardsServiceImpl::OnShow(uint32_t tab_id) {
  ledger_->OnShow(tab_id, base::TimeTicks::Now().since_origin().InMilliseconds());
}

void BraveRewardsServiceImpl::OnHide(uint32_t tab_id) {
  ledger_->OnHide(tab_id, base::TimeTicks::Now().since_origin().InMilliseconds());
  // TODO retrieving Publishers List
   //std::string month;
   //std::string year;
   //GetLocalMonthYear(month, year);
   //unsigned int start_record = 0;
   //unsigned int limit_record = 100;
   //int category = ledger::PUBLISHER_CATEGORY::ALL_CATEGORIES;
   // int category = ledger::PUBLISHER_CATEGORY::TIPPING | ledger::PUBLISHER_CATEGORY::DIRECT_DONATION;
   // GetPublisherInfoList(start_record, limit_record, category, month, year,
   //   std::bind(&GotContentSiteListInternal,
   //               start_record, limit_record,
   //               _1, _2));
  //
  // TODO retrieving recurrent donation Publishers List
  //GetRecurringDonationPublisherInfo(std::bind(&GotRecurringDonationPublisherInfo, _1, _2));
  //
  // TODO gets the current balance
  // ledger::BalanceReportInfo report_info;
  // if (!GetBalanceReport(&report_info)) {
  //   // Something is wrong
  // } else {
  //   // Everything is good
  // }
  //
}

void BraveRewardsServiceImpl::OnForeground(uint32_t tab_id) {
  ledger_->OnForeground(tab_id, base::TimeTicks::Now().since_origin().InMilliseconds());
}

void BraveRewardsServiceImpl::OnBackground(uint32_t tab_id) {
  ledger_->OnBackground(tab_id, base::TimeTicks::Now().since_origin().InMilliseconds());
}

void BraveRewardsServiceImpl::OnMediaStart(uint32_t tab_id) {
  
}

void BraveRewardsServiceImpl::OnMediaStop(uint32_t tab_id) {
  
}

void BraveRewardsServiceImpl::OnXHRLoad(uint32_t tab_id,
      const std::string& url,
      const std::map<std::string, std::string>& parts,
      const uint64_t& current_time) {
  //ledger_->OnXHRLoad(tab_id, url); 
}

std::string BraveRewardsServiceImpl::URIEncode(const std::string& value) {
  return net::EscapeQueryParamValue(value, false);
}

void BraveRewardsServiceImpl::GetRecurringDonationPublisherInfo(ledger::PublisherInfoCallback callback) {
  ledger_->GetRecurringDonationPublisherInfo(callback);
}

void BraveRewardsServiceImpl::GetPublisherInfoList(
    uint32_t start, uint32_t limit, 
    int category,
    const std::string& month,
    const std::string& year,
    ledger::GetPublisherInfoListCallback callback) {
  ledger_->GetPublisherInfoList(start, limit, 
      ledger::PublisherInfoFilter::DEFAULT,
      category,
      month, year,
      callback);
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
}

void BraveRewardsServiceImpl::LoadPublisherInfo(
    const std::string& publisher_key,
    ledger::PublisherInfoCallback callback) {
  base::PostTaskAndReplyWithResult(file_task_runner_.get(), FROM_HERE,
      base::Bind(&LoadPublisherInfoOnFileTaskRunner,
          publisher_key, publisher_info_backend_.get()),
      base::Bind(&BraveRewardsServiceImpl::OnPublisherInfoLoaded,
                     AsWeakPtr(),
                     callback));
}

void BraveRewardsServiceImpl::OnPublisherInfoLoaded(
    ledger::PublisherInfoCallback callback,
    std::unique_ptr<ledger::PublisherInfo> info) {
  callback(ledger::Result::OK, std::move(info));
}

void BraveRewardsServiceImpl::LoadPublisherInfoList(
    uint32_t start,
    uint32_t limit,
    ledger::PublisherInfoFilter filter,
    const std::vector<std::string>& prefix,
    ledger::GetPublisherInfoListCallback callback) {
  base::PostTaskAndReplyWithResult(file_task_runner_.get(), FROM_HERE,
      base::Bind(&LoadPublisherInfoListOnFileTaskRunner,
                    start, limit, filter, prefix, publisher_info_backend_.get()),
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

  callback(list, next_record);
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
  TriggerOnWalletInitialized(result);
  //GetWalletProperties();
}

void BraveRewardsServiceImpl::OnReconcileComplete(ledger::Result result,
                                              const std::string& viewing_id) {
  LOG(ERROR) << "reconcile complete " << viewing_id;
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
  handler->OnPublisherStateLoaded(data.empty() ? ledger::Result::ERROR
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
                                      : ledger::Result::ERROR);
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

std::unique_ptr<ledger::LedgerURLLoader> BraveRewardsServiceImpl::LoadURL(const std::string& url,
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

  FetchCallback callback = base::Bind(
      &ledger::LedgerCallbackHandler::OnURLRequestResponse,
      base::Unretained(handler),
      next_id);
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

void BraveRewardsServiceImpl::OnWalletProperties(ledger::Result result,
                          std::unique_ptr<ledger::WalletInfo> info) {
  // TODO implement
  LOG(ERROR) << "!!!BraveRewardsServiceImpl::OnWalletProperties walletInfo.balance_ == " << info->balance_;
}

void BraveRewardsServiceImpl::GetWalletProperties() {
  ledger_->GetWalletProperties();
}

void BraveRewardsServiceImpl::OnPromotion(ledger::Promo result) {
  // TODO
}

void BraveRewardsServiceImpl::OnPromotionCaptcha(const std::string& image) {
  // TODO
}

void BraveRewardsServiceImpl::OnRecoverWallet(ledger::Result result, double balance) {
  // TODO
}

void BraveRewardsServiceImpl::OnPromotionFinish(ledger::Result result, unsigned int statusCode, uint64_t expirationDate) {
  // TODO
}

void BraveRewardsServiceImpl::GetPromotion(const std::string& lang, const std::string& paymentId) {
  // TODO
}

void BraveRewardsServiceImpl::GetPromotionCaptcha() {
  // TODO
}

/*void BraveRewardsServiceImpl::SolvePromotionCaptcha(const std::string& solution) const {
  // TODO
}

std::string BraveRewardsServiceImpl::GetWalletPassphrase() const {
  // TODO
}

void BraveRewardsServiceImpl::RecoverWallet(const std::string passPhrase) const {
  // TODO
} */

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

}  // namespace brave_rewards
