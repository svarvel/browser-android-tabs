/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */

#include "media_publisher_info_backend.h"

#include "base/files/file_util.h"
#include "third_party/leveldatabase/env_chromium.h"
#include "third_party/leveldatabase/src/include/leveldb/db.h"
#include "third_party/leveldatabase/src/include/leveldb/iterator.h"
#include "third_party/leveldatabase/src/include/leveldb/options.h"
#include "third_party/leveldatabase/src/include/leveldb/status.h"

namespace brave_rewards {

MediaPublisherInfoBackend::MediaPublisherInfoBackend(const base::FilePath& path) :
    path_(path) {
  DETACH_FROM_SEQUENCE(sequence_checker_);
}

MediaPublisherInfoBackend::~MediaPublisherInfoBackend() {}

bool MediaPublisherInfoBackend::Put(const std::string& key,
                               const std::string& value) {
  DCHECK_CALLED_ON_VALID_SEQUENCE(sequence_checker_);
  bool initialized = EnsureInitialized();
  DCHECK(initialized);

  if (!initialized)
    return false;

  leveldb::WriteOptions options;
  LOG(ERROR) << "!!!MediaPublisherInfoBackend::Put";
  leveldb::Status status = db_->Put(options, key, value);
  if (status.ok())
    return true;

  return false;
}

bool MediaPublisherInfoBackend::Get(const std::string& lookup,
                               std::string* value) {
  LOG(ERROR) << "!!!MediaPublisherInfoBackend::Get";
  DCHECK_CALLED_ON_VALID_SEQUENCE(sequence_checker_);
  bool initialized = EnsureInitialized();
  DCHECK(initialized);

  if (!initialized)
    return false;

  leveldb::ReadOptions options;
  leveldb::Status status = db_->Get(options, lookup, value);
  if (status.ok())
    return true;

  return false;
}

bool MediaPublisherInfoBackend::EnsureInitialized() {
  DCHECK_CALLED_ON_VALID_SEQUENCE(sequence_checker_);
  if (db_.get())
    return true;

  leveldb_env::Options options;
  options.create_if_missing = true;
  std::string path = path_.value();
  leveldb::Status status = leveldb_env::OpenDB(options, path, &db_);

  if (status.IsCorruption()) {
    LOG(ERROR) << "Deleting corrupt database";
    base::DeleteFile(path_, true);
    status = leveldb_env::OpenDB(options, path, &db_);
  }
  if (status.ok()) {
    CHECK(db_);
    return true;
  }
  LOG(WARNING) << "Unable to open " << path << ": "
               << status.ToString();
  return false;
}

}  // namespace brave_rewards
