#ifndef STATS_UPDATER_H_
#define STATS_UPDATER_H_

#include <jni.h>
#include <string>

namespace stats_updater {

    std::string GetCustomHeadersForHost(const std::string& host);

}

#endif //STATS_UPDATER_H_
