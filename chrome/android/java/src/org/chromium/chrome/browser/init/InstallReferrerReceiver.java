/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.chromium.chrome.browser.init;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import org.chromium.base.Log;

import org.chromium.chrome.browser.init.InstallationSourceInformer;

public class InstallReferrerReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(final Context context, Intent intent) {
      String referrer = intent.getStringExtra("referrer");
      Log.i("TAG", "InstallReferrerReceiver: referrer: " + referrer);

      if (referrer == null) {
        InstallationSourceInformer.InformFromPlayMarket();
        return;
      }

      Uri uri = Uri.parse("http://www.stub.co/?"+referrer);
      String utm_medium_value = uri.getQueryParameter("utm_medium");
      Log.i("TAG", "InstallReferrerReceiver: utm_medium_value: <" + utm_medium_value+">");
      if (utm_medium_value != null && !utm_medium_value.isEmpty() && !utm_medium_value.equals("organic")) {
        InstallationSourceInformer.InformFromPromo();
      } else {
        InstallationSourceInformer.InformFromPlayMarket();
      }

      //in any way update stats with promo name
      String utm_campaign_value = uri.getQueryParameter("utm_campaign");
      Log.i("TAG", "InstallReferrerReceiver: utm_campaign: <" + utm_campaign_value+">");
      InstallationSourceInformer.InformStatsPromo(utm_campaign_value);
    }
}
