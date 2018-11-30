/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.chromium.chrome.browser;

import android.app.Activity;

import org.chromium.base.ApplicationStatus;
import org.chromium.base.Log;
import org.chromium.base.SysUtils;
import org.chromium.chrome.browser.ChromeTabbedActivity;

import java.lang.ref.WeakReference;

public class BraveRewardsHelper {
  static public ChromeTabbedActivity GetChromeTabbedActivity() {
    for (WeakReference<Activity> ref : ApplicationStatus.getRunningActivities()) {
        if (!(ref.get() instanceof ChromeTabbedActivity)) continue;

        return (ChromeTabbedActivity)ref.get();
    }

    return null;
  }
}