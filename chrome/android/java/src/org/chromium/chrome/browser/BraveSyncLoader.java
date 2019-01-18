/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.chromium.chrome.browser;

import org.chromium.base.Log;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.os.Build;
import android.os.Looper;
import android.util.Base64;
import android.util.JsonReader;
import android.util.JsonToken;
import android.webkit.JavascriptInterface;

import org.chromium.base.annotations.JNINamespace;
import org.chromium.base.task.AsyncTask;
import org.chromium.base.ContextUtils;
import org.chromium.base.Log;
import org.chromium.components.bookmarks.BookmarkId;
import org.chromium.components.bookmarks.BookmarkType;
import org.chromium.components.url_formatter.UrlFormatter;
import org.chromium.chrome.browser.bookmarks.BookmarkBridge;
import org.chromium.chrome.browser.bookmarks.BookmarkModel;
import org.chromium.chrome.browser.bookmarks.BookmarkUtils;
import org.chromium.chrome.browser.bookmarks.BookmarkBridge.BookmarkItem;
import org.chromium.chrome.browser.bookmarks.BookmarkBridge.BookmarkModelObserver;
import org.chromium.chrome.browser.partnerbookmarks.PartnerBookmarksShim;
import org.chromium.chrome.browser.preferences.BraveSyncScreensObserver;
import org.chromium.chrome.browser.WebContentsFactory;
import org.chromium.content_public.browser.JavascriptInjector;
import org.chromium.content_public.browser.WebContents;
import org.chromium.content_public.browser.LoadUrlParams;
import org.chromium.components.embedder_support.view.ContentView;
import org.chromium.content_public.browser.ViewEventSink;
import org.chromium.content.browser.ViewEventSinkImpl;
import org.chromium.chrome.browser.ChromeVersionInfo;
import org.chromium.ui.base.ActivityWindowAndroid;
import org.chromium.ui.base.ViewAndroidDelegate;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.lang.IllegalArgumentException;
import java.lang.Runnable;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.HashSet;
import java.util.HashMap;
import java.util.Random;
import java.util.Scanner;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.UnsupportedEncodingException;


public class BraveSyncLoader {

    private BookmarkModel mModel = null;

    public BraveSyncWorker mBraveSyncWorker = null;
    private Context mContext = null;

    public BraveSyncLoader(Context context) {
Log.i("TAG_BookmDb", "[BookmDb] BraveSyncLoader CTOR tid=" + Thread.currentThread().getId());
         mContext = context;
         mModel = new BookmarkModel();
         mModel.addObserver(mBookmarkModelObserver);

         mBraveSyncWorker = new BraveSyncWorker(mContext, false);

//         new Thread() {
//             @Override
//             public void run() {
// Log.i("TAG_BookmDb", "[BookmDb] BraveSyncLoader.run before sleep tid=" + Thread.currentThread().getId());
// //             try
// //             {
// //                 Thread.sleep(10*1000); // crash
// //             }
// //             catch(InterruptedException e)
// //             {
// //                  // this part is executed when an exception (in this example InterruptedException) occurs
// // Log.i("TAG_BookmDb", "[BookmDb] BraveSyncLoader.run InterruptedException tid=" + Thread.currentThread().getId());
// //             }
// Log.i("TAG_BookmDb", "[BookmDb] BraveSyncLoader.run after sleep tid=" + Thread.currentThread().getId());
//               mModel = new BookmarkModel();
//               mModel.addObserver(mBookmarkModelObserver);
//             }
//           }.start();
    }

    public final BookmarkModelObserver mBookmarkModelObserver = new BookmarkModelObserver() {

        @Override
        public void bookmarkModelChanged() {
        }

        @Override
        public void bookmarkModelLoaded() {
Log.i("TAG_BookmDb", "[BookmDb] BraveSyncLoader...bookmarkModelLoaded tid=" + Thread.currentThread().getId());
        }

        @Override
        public void bookmarkIdsReassigned(boolean idsReassigned) {

Log.i("TAG_BookmDb", "[BookmDb] BraveSyncLoader...bookmarkIdsReassigned idsReassigned="+idsReassigned
+" tid=" + Thread.currentThread().getId());

              mModel.removeObserver(mBookmarkModelObserver);
//               mModel.destroy();
//               mModel = null;
// Log.i("TAG_BookmDb", "[BookmDb] BraveSyncLoader...bookmarkIdsReassigned, destroyed model ");

              //List<BookmarkId> childIds;
              HashMap<String, Long> objectIdToLocalId = new HashMap<>();

              List<BookmarkId> topFolders = mModel.getTopLevelFolderIDs(true, true);
              for (BookmarkId topFolder : topFolders) {
                   List<BookmarkId> childIds = mModel.getChildIDs(topFolder, true, true);
                   for (BookmarkId childId : childIds) {
                      String objectId = mModel.GetNodeMetaInfo(childId, "object_id");
                      if(objectId != null && !objectId.isEmpty()) {
                          objectIdToLocalId.put(objectId, childId.getId());
Log.i("TAG_BookmDb", "[BookmDb] map "+objectId+" => "+objectIdToLocalId.get(objectId));
                      }
                   }
              }

//              mBraveSyncWorker = new BraveSyncWorker(mContext, idsReassigned);
        }
    };
}
