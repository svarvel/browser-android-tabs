/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

#ifndef CHROME_BROWSER_NET_BLOCKERS_SHIELD_EXCEPTIONS_H_
#define CHROME_BROWSER_NET_BLOCKERS_SHIELD_EXCEPTIONS_H_

class GURL;

namespace net {
namespace blockers {

bool IsWhitelistedCookieExeption(const GURL& first_party_url,
    const GURL& subresource_url);

}  // namespace blockers
}  // namespace net

#endif  //CHROME_BROWSER_NET_BLOCKERS_SHIELD_EXCEPTIONS_H_
