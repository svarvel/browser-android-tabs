/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.chromium.chrome.browser;

import org.chromium.base.ContextUtils;

import org.json.JSONException;
import org.json.JSONObject;

public class MixPanelWorker {
    // Send event with no options
    public static void SendEvent(String eventName) {
        ChromeApplication app = (ChromeApplication)ContextUtils.getApplicationContext();
        if (null != app && null != app.mMixpanelInstance) {
            app.mMixpanelInstance.track(eventName);
          }
    }

    // Send event with option
    public static void SendEvent(String eventName, String propertyName, Object propertyValue) {
        ChromeApplication app = (ChromeApplication)ContextUtils.getApplicationContext();
        if (null != app && null != app.mMixpanelInstance) {
            try {
                JSONObject obj = new JSONObject();
                obj.put(propertyName, propertyValue);
                app.mMixpanelInstance.track(eventName, obj);
            } catch (JSONException e) {
            }
        }
    }

    // Send event for Brave app start
    public static void SendBraveAppStartEvent(boolean httpsEverywhere, boolean trackingProtection, boolean adBlock, boolean regionalAdBlock, boolean fingerprintingProtection) {
      ChromeApplication app = (ChromeApplication)ContextUtils.getApplicationContext();
      if (null != app && null != app.mMixpanelInstance) {
          try {
              JSONObject obj = new JSONObject();
              obj.put("HTTPS Everywhere", httpsEverywhere);
              obj.put("Tracking Protection Mode", trackingProtection);
              obj.put("Ad Block", adBlock);
              obj.put("Regional Ad Block", regionalAdBlock);
              obj.put("Fingerprinting Protection", fingerprintingProtection);
              app.mMixpanelInstance.track("Brave App Start", obj);
          } catch (JSONException e) {
          }
      }
    }
}
