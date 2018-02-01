/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.chromium.chrome.browser;

import org.chromium.base.Log;
import org.chromium.chrome.browser.preferences.PrefServiceBridge;
import org.chromium.chrome.browser.tab.Tab;

import java.net.URL;
import java.net.MalformedURLException;

public class DisableVideoPauseOnBackground {
    private static String TAG = "PLAYBG";
    public static void Execute(Tab tab) {
        final boolean videoInBackgroundEnabled = PrefServiceBridge.getInstance().playVideoInBackgroundEnabled();
        if (videoInBackgroundEnabled && NeedToDisable(tab)) {
          tab.getWebContents().evaluateJavaScript(SCRIPT, null);
        }
    }

    private static boolean IsYTWatchUrl(String sUrl) {
        if (sUrl == null || sUrl.isEmpty()) {
          return false;
        }

        try {
          URL url = new URL(sUrl);
          if ("/watch".equalsIgnoreCase(url.getPath()) ) {
            String sHost = url.getHost();
            if ("www.youtube.com".equalsIgnoreCase(sHost) ||
                "youtube.com".equalsIgnoreCase(sHost) ||
                "m.youtube.com".equalsIgnoreCase(sHost)) {
                return true;
            }
          }
        } catch(MalformedURLException e) {
          Log.w(TAG, "MalformedURLException "+ e.getMessage());
        }

        return false;
    }

    private static boolean NeedToDisable(Tab tab) {
        boolean bNeedToDisablePause = (tab != null) && IsYTWatchUrl(tab.getUrl());
        return bNeedToDisablePause;
    }

    private static final String SCRIPT = ""
+"(function() {"

+"    var lastTimeHidden = null;"
+"    document.addEventListener('visibilitychange', function() {"
+"        if (document.hidden) {"
+"            lastTimeHidden = Date.now();"
+"        }"
+"    }, false);"

+"    var doReplacePause = function(video) {"
+"        if (video.originalPause === undefined) {"
+"            video.originalPause = video.pause;"
+"            video.pause = function() {"
+"                if (document.visibilityState == 'visible' || lastTimeHidden == null || (lastTimeHidden != null && Date.now() - lastTimeHidden > 200)) {"
+"                    video.originalPause();"
+"                } else {"
+"                    callUndefinedFn();"
+"                }"
+"            }"
+"        }"
+"    };"
+"    var setupObserverAttempts = 0;"
+"    var setupObserver = function() {"
+"        var observer = new MutationObserver(function(mutations) {"
+"            mutations.forEach(function(mutation) {"
+"                if (mutation.type === 'attributes' && mutation.attributeName === 'src' && mutation.target.nodeName === 'VIDEO') {"
+"                    doReplacePause(mutation.target);"
+"                }"
+"                if (mutation.type === 'childList') {"
+"                    for (var i = 0; i < mutation.addedNodes.length; i++) {"
+"                        if (mutation.addedNodes[i].nodeName === 'VIDEO') {"
+"                            doReplacePause(mutation.addedNodes[i]);"
+"                        }"
+"                    }"
+"                }"
+"            });"
+"        });"
+"        var target = document.querySelector('html');"
+"        var config = {"
+"            childList: true,"
+"            attributes: true,"
+"            subtree: true,"
+"            characterData: true"
+"        };"
+"        if (target) {"
+"            observer.observe(target, config);"
+"        } else if (setupObserverAttempts < 10) {"
+"            setTimeout(setupObserver, 100);"
+"            ++setupObserverAttempts;"
+"        }"
+"    };"
+"    setTimeout(setupObserver, 100);"
+"}());"
;
}
