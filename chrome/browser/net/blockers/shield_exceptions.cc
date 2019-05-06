/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

#include "chrome/browser/net/blockers/shield_exceptions.h"

#include <algorithm>
#include <map>
#include <vector>

#include "extensions/common/url_pattern.h"
#include "url/gurl.h"

namespace net {
namespace blockers {

bool IsWhitelistedCookieExeption(const GURL& first_party_url,
    const GURL& subresource_url) {
  // Note that there's already an exception for TLD+1, so don't add those here.
  // Check with the security team before adding exceptions.

  if (first_party_url.is_empty() || subresource_url.is_empty()) {
    return false;
  }

  std::vector<std::pair<URLPattern,
      std::vector<URLPattern>>> whitelist_patterns =
  {
    {
      URLPattern(URLPattern::SCHEME_ALL,
          "https://estore.htc.com/*"),
      {
        URLPattern(URLPattern::SCHEME_ALL,
            "https://www.htcsense.com/*"),
      }
    },
  };

  const auto i = std::find_if(whitelist_patterns.begin(),
        whitelist_patterns.end(),
        [&first_party_url](const std::pair<URLPattern,
            std::vector<URLPattern>> &info) {
          return info.first.MatchesURL(first_party_url);
        });

  if (i == whitelist_patterns.end()) {
    return false;
  }

  std::vector<URLPattern> &exceptions = i->second;
  return std::any_of(exceptions.begin(), exceptions.end(),
      [&subresource_url](const URLPattern& pattern) {
        return pattern.MatchesURL(subresource_url);
      });
}

}  // namespace blockers
}  // namespace net
