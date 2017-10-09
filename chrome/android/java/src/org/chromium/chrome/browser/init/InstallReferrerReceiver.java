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
        InstallationSourceInformer.InformFromAdWords();
      } else {
        InstallationSourceInformer.InformFromPlayMarket();
      }
    }
}
