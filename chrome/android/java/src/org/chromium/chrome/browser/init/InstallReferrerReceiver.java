/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.chromium.chrome.browser.init;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import org.chromium.base.Log;

import org.chromium.chrome.browser.init.InstallationSourceInformer;

public class InstallReferrerReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(final Context context, Intent intent) {
      String referrer = intent.getStringExtra("referrer");
        Log.i("TAG", "InstallReferrerReceiver: referrer: " + referrer);

      if (referrer != null && referrer.contains("utm_source") ) {
        InstallationSourceInformer.InformFromPromo();
      } else {
        InstallationSourceInformer.InformFromPlayMarket();
      }
    }
}
