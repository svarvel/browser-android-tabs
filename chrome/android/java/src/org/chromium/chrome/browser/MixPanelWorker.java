/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */
 
package org.chromium.chrome.browser;

import org.chromium.base.ContextUtils;

import org.json.JSONException;
import org.json.JSONObject;

public class MixPanelWorker {
    public static void SendEvent(String eventName, String propertyName, String propertyValue) {
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
}
