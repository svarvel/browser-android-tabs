/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */


package org.chromium.chrome.browser.init;

import android.content.SharedPreferences;

import org.chromium.base.ContextUtils;
import org.chromium.base.Log;
import org.chromium.chrome.browser.MixPanelWorker;


public class InstallationSourceInformer {
  private static final String PREF_MIXPANEL_INSTALL_SOURCE_INFORMED = "mixpanel_installation_source_informed";

  public static void InformFromOther() {
    //Disabled until we not ensured InformFromPromo works well
    //Inform("Others");
    Log.i("TAG", "InstallationSourceInformer.InformFromOther skip send info");
  }

  public static void InformFromPlayMarket() {
    //Disabled until we not ensured InformFromPromo works well
    //Inform("Google Play");
    Log.i("TAG", "InstallationSourceInformer.InformFromPlayMarket skip send info");
  }

  public static void InformFromPromo() {
    //Disabled because event receiver doesn't work yet
    //Inform("Promo");
    Log.i("TAG", "InstallationSourceInformer.InformFromPromo skip send info");
  }

  private static synchronized void Inform(String sourceName) {
    Log.i("TAG", "InstallationSourceInformer, sourceName=" + sourceName);
    if (IsAlreadyInformed()) {
      Log.i("TAG", "InstallationSourceInformer, already informed");
      return;
    }

    MixPanelWorker.SendEvent("Installed from " + sourceName);
    SetAlreadyInformed();
  }

  private static boolean IsAlreadyInformed() {
    boolean installSourceInformed = ContextUtils.getAppSharedPreferences().getBoolean(
      PREF_MIXPANEL_INSTALL_SOURCE_INFORMED, false);
    return installSourceInformed;
  }

  private static void SetAlreadyInformed() {
    SharedPreferences.Editor sharedPreferencesEditor = ContextUtils.getAppSharedPreferences().edit();
    sharedPreferencesEditor.putBoolean(PREF_MIXPANEL_INSTALL_SOURCE_INFORMED, true);
    sharedPreferencesEditor.apply();
  }
}
