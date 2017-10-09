
package org.chromium.chrome.browser.init;

import android.content.SharedPreferences;

import org.chromium.base.ContextUtils;
import org.chromium.base.Log;
import org.chromium.chrome.browser.MixPanelWorker;


public class InstallationSourceInformer {
  private static final String PREF_MIXPANEL_INSTALL_SOURCE_INFORMED = "mixpanel_installation_source_informed";

  public static void InformFromOther() {
    Inform("Other");
  }

  public static void InformFromPlayMarket() {
    Inform("Play Market");
  }

  public static void InformFromAdWords() {
    Inform("AdWords");
  }

  private static synchronized void Inform(String sourceName) {

    Log.i("TAG", "InstallationSourceInformer, sourceName="+sourceName);
    if (IsAlreadyInformed()) {
      Log.i("TAG", "InstallationSourceInformer, already informed");
      return;
    }

return;//not to spoil Miz Panel Data until all is tested
/*
    MixPanelWorker.SendEvent("App Installation Source Detected", "Installation Source", sourceName);
    SetAlreadyInformed();
*/  }

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
