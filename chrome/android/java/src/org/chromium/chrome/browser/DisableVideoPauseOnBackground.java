/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.chromium.chrome.browser;

import org.chromium.base.Log;
import org.chromium.chrome.browser.preferences.PrefServiceBridge;
import org.chromium.chrome.browser.tab.Tab;

public class DisableVideoPauseOnBackground {
    public static void Execute(Tab tab) {
        final boolean videoInBackgroundEnabled = PrefServiceBridge.getInstance().playVideoInBackgroundEnabled();
        if (videoInBackgroundEnabled && NeedToDisable(tab)) {
          tab.getWebContents().evaluateJavaScript(SCRIPT, null);
        }
    }

    private static boolean NeedToDisable(Tab tab) {
        boolean bRet = tab != null && tab.getUrl().contains("https://m.youtube.com/watch?");
        return bRet;
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
