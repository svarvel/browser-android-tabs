/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.chromium.chrome.browser;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Build;
import android.util.JsonReader;
import android.util.JsonToken;
import android.webkit.WebView;
import android.webkit.JavascriptInterface;

import org.chromium.base.annotations.JNINamespace;
import org.chromium.base.ContextUtils;
import org.chromium.base.Log;
import org.chromium.components.bookmarks.BookmarkId;
import org.chromium.components.bookmarks.BookmarkType;
import org.chromium.components.url_formatter.UrlFormatter;
import org.chromium.chrome.browser.bookmarks.BookmarkModel;
import org.chromium.chrome.browser.bookmarks.BookmarkUtils;
import org.chromium.chrome.browser.bookmarks.BookmarkBridge.BookmarkItem;
import org.chromium.chrome.browser.preferences.BraveSyncScreensObserver;

import java.lang.IllegalArgumentException;
import java.lang.Runnable;
import java.util.Calendar;
import java.util.List;
import java.util.Random;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.IOException;
import java.io.UnsupportedEncodingException;

@JNINamespace("brave_sync_storage")
public class BraveSyncWorker {

    private static final String PREF_NAME = "SyncPreferences";
    private static final String PREF_LAST_FETCH_NAME = "TimeLastFetch";
    // TODO should by encrypted properly
    private static final String PREF_DEVICE_ID = "DeviceId";
    private static final String PREF_SEED = "Seed";
    //
    private static final int INTERVAL_TO_FETCH_RECORDS = 1000 * 60;    // Milliseconds
    private static final int INTERVAL_TO_REFETCH_RECORDS = 5000 * 60;    // Milliseconds
    private static final int LAST_RECORDS_COUNT = 980;
    private static final int SEND_RECORDS_COUNT_LIMIT = 1000;
    private static final String PREF_SYNC_SWITCH = "sync_switch";
    private static final String PREF_BOOKMARKS_CHECK_BOX = "sync_bookmarks_check_box";
    private static final String CREATE_RECORD = "0";
    private static final String UPDATE_RECORD = "1";
    private static final String DELETE_RECORD = "2";

    private final SharedPreferences mSharedPreferences;

    // WebView to sync data
    private WebView mSyncWebView;
    // WebView to call js functions
    private WebView mJSWebView;
    // Sync data
    private static final String SYNC_HTML_SCRIPT = "<script src='android_sync.js' type='text/javascript'></script><script src='bundle.js' type='text/javascript'></script>";
    private static final String SYNC_WORDS_HTML_SCRIPT = "<script src='niceware.js' type='text/javascript'></script><script src='android_sync_words.js' type='text/javascript'></script>";
    private SyncThread mSyncThread = null;
    //

    private Context mContext;
    private boolean mStopThread = false;
    private SyncIsReady mSyncIsReady;

    private String mSeed = null;
    private String mDeviceId = null;
    private String mApiVersion = "0";
    //private String mServerUrl = "https://sync-staging.brave.com";
    private String mServerUrl = "https://sync.brave.com";
    private String mDebug = "true";
    private long mTimeLastFetch = 0;   // In milliseconds
    private boolean mShouldResetSync = false;
    private String mLatestRecordTimeStampt = "";
    private boolean mFetchInProgress = false;

    private BraveSyncScreensObserver mSyncScreensObserver;

    public static class SyncRecordType {
        public static final String BOOKMARKS = "BOOKMARKS";
        public static final String HISTORY = "HISTORY_SITES";
        public static final String PREFERENCES = "PREFERENCES";

        public static String GetJSArray() {
            return "['" + BOOKMARKS + "']";//"', '" + HISTORY + "', '" + PREFERENCES + "']";
        }
    }

    class SyncIsReady {

        public boolean mFetchRecordsReady = false;
        public boolean mResolveRecordsReady = false;
        public boolean mSendRecordsReady = false;
        public boolean mDeleteUserReady = false;
        public boolean mDeleteCategoryReady = false;
        public boolean mDeleteSiteSettingsReady = false;
        public boolean mReady = false;

        public boolean IsReady() {
            return mReady && mFetchRecordsReady && mResolveRecordsReady
                && mSendRecordsReady && mDeleteUserReady && mDeleteCategoryReady
                && mDeleteSiteSettingsReady;
        }
    }

    class BookMarkInternal {
        public String mUrl = "";
        public String mTitle = "";
        public String mParentFolderObjectId = "";
        public boolean mIsFolder = false;
    }



    public BraveSyncWorker(Context context) {
        mContext = context;
        mSharedPreferences = ContextUtils.getAppSharedPreferences();
        mSyncIsReady = new SyncIsReady();
        mSyncThread = new SyncThread();
        if (null != mSyncThread) {
            mSyncThread.start();
        }
    }

    public void Stop() {
        mStopThread = true;
        if (mSyncThread != null) {
            mSyncThread.interrupt();
            mSyncThread = null;
        }
        nativeClear();
    }

    public void CreateUpdateDeleteBookmarks(String action, BookmarkItem[] bookmarks) {
        assert null != bookmarks;
        if (null == bookmarks || 0 == bookmarks.length) {
            return;
        }

        final String actionFinal = action;
        final BookmarkItem[] bookmarksFinal = bookmarks;

        new AsyncTask<String, Void, Void>() {
            @Override
            protected Void doInBackground(String... params) {
                if (!mSyncIsReady.IsReady()) {
                    return null;
                }

                String bookmarkRequest = "[";
                for (int i = 0; i < bookmarksFinal.length; i++) {
                    if (bookmarkRequest.length() > 1) {
                        bookmarkRequest += ", ";
                    }
                    String objectId = GenerateObjectId(bookmarksFinal[i].getId().getId());
                    bookmarkRequest += CreateRecord(objectId, "bookmark", actionFinal, mDeviceId) +
                      CreateBookmarkRecord(bookmarksFinal[i].getUrl(),
                        bookmarksFinal[i].getTitle(), bookmarksFinal[i].isFolder(),
                        bookmarksFinal[i].getParentId().getId()) + "}";
                    SaveObjectId(bookmarksFinal[i].getId().getId(), objectId);
                }

                if (bookmarkRequest.length() > 1) {
                    bookmarkRequest += "]";
                } else {
                    // Nothing to send
                    return null;
                }
                Log.i("TAG", "!!!bookmarkRequest == " + bookmarkRequest);
                SendSyncRecords(SyncRecordType.BOOKMARKS, bookmarkRequest);

                return null;
            }
        }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    public void DeleteBookmarks(BookmarkItem[] bookmarks) {
        CreateUpdateDeleteBookmarks(DELETE_RECORD, bookmarks);
    }

    public void CreateUpdateBookmark(boolean bCreate, BookmarkItem bookmarkItem) {
        BookmarkItem[] bookmarks = new BookmarkItem[1];
        bookmarks[0] = bookmarkItem;
        CreateUpdateDeleteBookmarks((bCreate ? CREATE_RECORD : UPDATE_RECORD), bookmarks);
    }

    private String CreateRecord(String objectId, String objectData, String action, String deviceId) {
        String record = "{ action: " + action + ", ";
        record += "deviceId: [" + deviceId + "], ";
        record += "objectId: [" + objectId + "], ";
        record += "objectData: '" + objectData + "', ";

        return record;
    }

    private String CreateBookmarkRecord(String url, String title, boolean isFolder, long parentFolderId) {
        String bookmarkRequest = "bookmark:";
        bookmarkRequest += "{ site:";
        bookmarkRequest += "{ location: \"" + url + "\", ";
        bookmarkRequest += "title: \"" + title.replace("\\", "\\\\").replace("\"", "\\\"") + "\", ";
        bookmarkRequest += "customTitle: '', ";
        bookmarkRequest += "lastAccessedTime: 0, ";
        bookmarkRequest += "creationlderObjectId: " + parentFolderId + " }}";

        return bookmarkRequest;
    }

    private String GetObjectId(long localId) {
        String objectId = nativeGetObjectIdByLocalId(String.valueOf(localId));
        if (0 == objectId.length()) {
            return objectId;
        }
        JsonReader reader = null;
        String res = "";
        try {
            reader = new JsonReader(new InputStreamReader(new ByteArrayInputStream(objectId.getBytes()), "UTF-8"));
            reader.beginArray();
            while (reader.hasNext()) {
                reader.beginObject();
                while (reader.hasNext()) {
                    String name = reader.nextName();
                    if (name.equals("objectId")) {
                        res = reader.nextString();
                    } else {
                        reader.skipValue();
                    }
               }
               reader.endObject();
           }
           reader.endArray();
        } catch (UnsupportedEncodingException e) {
            Log.i("TAG", "GetObjectId UnsupportedEncodingException error " + e);
        } catch (IOException e) {
            Log.i("TAG", "GetObjectId IOException error " + e);
        } catch (IllegalStateException e) {
              Log.i("TAG", "GetObjectId IllegalStateException error " + e);
        } finally {
            if (null != reader) {
                try {
                    reader.close();
                } catch (IOException e) {
                }
            }
        }

        return res;
    }

    private void SaveObjectId(long localId, String objectId) {
        String objectIdJSON = "[{\"objectId\": \"" + objectId + "\", \"apiVersion\": \"" + mApiVersion + "\"}]";
        nativeSaveObjectId(String.valueOf(localId), objectIdJSON, objectId);
    }

    private String GenerateObjectId(long localId) {
        String res = GetObjectId(localId);
        if (0 != res.length()) {
            return res;
        }
        // Generates 16 random 8 bits numbers
        Random random = new Random();
        for (int i = 0; i < 16; i++) {
            if (i != 0) {
                res += ", ";
            }
            try {
                res += String.valueOf(random.nextInt(256));
            } catch (IllegalArgumentException exc) {
                res = "";
                Log.i("TAG", "ObjectId generation exception " + exc);
            }
        }

        return res;
    }

    private void TrySync() {
        try {
            if (mShouldResetSync) {
                mSyncWebView = null;
                mShouldResetSync = false;
            }
            if (null == mSyncWebView) {
                mSyncWebView = new WebView(mContext);
                mSyncWebView.getSettings().setJavaScriptEnabled(true);
                mSyncWebView.addJavascriptInterface(new JsObject(), "injectedObject");
                // TODO debug
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                    WebView.setWebContentsDebuggingEnabled(true);
                }
                //
                mSyncWebView.loadDataWithBaseURL("file:///android_asset/", SYNC_HTML_SCRIPT, "text/html", "UTF-8",null);
            }
        } catch (Exception exc) {
            // Ignoring sync exception, we will try it on a next loop execution
            Log.i("TAG", "TrySync exception: " + exc);
        }
    }

    private void CallScript(String strCall) {
        ((Activity)mContext).runOnUiThread(new EjectedRunnable(strCall));
    }

    private void GotInitData() {
        String deviceId = (null == mDeviceId ? null : "[" + mDeviceId + "]");
        String seed = (null == mSeed ? null : "[" + mSeed + "]");
        CallScript(String.format("javascript:callbackList['got-init-data'](null, %1$s, %2$s, {apiVersion: '%3$s', serverUrl: '%4$s', debug: %5$s})", seed, deviceId, mApiVersion, mServerUrl, mDebug));
    }

    private void SaveInitData(String arg1, String arg2) {
        if (null == arg1 || null == arg2) {
            Log.i("TAG", "Sync SaveInitData args expected");
        }
        mSeed = arg1;
        mDeviceId = arg2;
        Log.i("TAG", "!!!deviceId == " + mDeviceId);
        Log.i("TAG", "!!!seed == " + mSeed);
        // Save seed and deviceId in preferences
        SharedPreferences sharedPref = mContext.getSharedPreferences(PREF_NAME, 0);
        SharedPreferences.Editor editor = sharedPref.edit();
        editor.putString(PREF_DEVICE_ID, mDeviceId);
        if (null == mSeed || mSeed.isEmpty()) {
            editor.putString(PREF_SEED, mSeed);
        }
        editor.apply();
    }

    public void SendSyncRecords(String recordType, String recordsJSON) {
        if (!mSyncIsReady.IsReady()) {
            return;
        }
        CallScript(String.format("javascript:callbackList['send-sync-records'](null, '%1$s', %2$s)", recordType, recordsJSON));
    }

    public void FetchSyncRecords(String lastRecordFetchTime) {
        synchronized (this) {
            if (!mSyncIsReady.IsReady()) {
                return;
            }
            if (0 == mTimeLastFetch) {
                // Grab current existing bookmarksIds to sync them
                List<BookmarkItem> localBookmarks = GetBookmarkItems();
                if (null != localBookmarks) {
                    Log.i("TAG", "!!!localBookmarks.size() == " + localBookmarks.size());
                    int listSize = localBookmarks.size();
                    for (int i = 0; i < listSize; i += SEND_RECORDS_COUNT_LIMIT) {
                        List<BookmarkItem> subList = localBookmarks.subList(i, Math.min(listSize, i + SEND_RECORDS_COUNT_LIMIT));
                        CreateUpdateDeleteBookmarks(CREATE_RECORD, subList.toArray(new BookmarkItem[subList.size()]));
                    }
                }
            }
            Calendar currentTime = Calendar.getInstance();
            if (currentTime.getTimeInMillis() - mTimeLastFetch <= INTERVAL_TO_FETCH_RECORDS
                  && lastRecordFetchTime.isEmpty()) {
                return;
            }
            String fetchToRequest = (lastRecordFetchTime.isEmpty() ? String.valueOf(mTimeLastFetch) : lastRecordFetchTime);
            CallScript(String.format("javascript:callbackList['fetch-sync-records'](null, %1$s, %2$s, true)", SyncRecordType.GetJSArray(), fetchToRequest/*String.valueOf(mTimeLastFetch / 1000)*/));
            if (lastRecordFetchTime.isEmpty()) {
                mTimeLastFetch = currentTime.getTimeInMillis();
                // Save last fetch time in preferences
                SharedPreferences sharedPref = mContext.getSharedPreferences(PREF_NAME, 0);
                SharedPreferences.Editor editor = sharedPref.edit();
                editor.putLong(PREF_LAST_FETCH_NAME, mTimeLastFetch);
                editor.apply();
            }
        }
    }

    public String GetExistingObjects(String categoryName, String recordsJSON, String latestRecordTimeStampt) {
        if (null == categoryName || null == recordsJSON) {
            return "";
        }
        if (!SyncRecordType.BOOKMARKS.equals(categoryName)) {
            // TODO sync for other categories
            return "";
        }
        mFetchInProgress = true;
        mLatestRecordTimeStampt = latestRecordTimeStampt;
        String res = "";

        //to do debug
        /*int iPos = recordsJSON.indexOf("parentFolderObjectId");
        if (-1 != iPos) {
            Log.i("TAG", "!!!record == " + recordsJSON.substring(iPos, iPos + 2000));
        }*/
        //

        int count = 0;
        JsonReader reader = null;
        try {
            reader = new JsonReader(new InputStreamReader(new ByteArrayInputStream(recordsJSON.getBytes()), "UTF-8"));
            reader.beginArray();
            while (reader.hasNext()) {
                String action = CREATE_RECORD;
                String objectId = "";
                String deviceId = "";
                String objectData = "";
                BookMarkInternal bookmarkInternal = null;
                reader.beginObject();
                while (reader.hasNext()) {
                    String name = reader.nextName();
                    if (name.equals("action")) {
                        action = GetAction(reader);
                    } else if (name.equals("deviceId")) {
                        deviceId = GetDeviceId(reader);
                    } else if (name.equals("objectId")) {
                        objectId = GetObjectIdJSON(reader);
                    } else if (name.equals("bookmark")) {
                        bookmarkInternal = GetBookmarkRecord(reader);
                    } else if (name.equals("objectData")) {
                        objectData = GetObjectDataJSON(reader);
                    } else {
                        reader.skipValue();
                    }
               }
               reader.endObject();
               // TODO send parentFolderObjectId
               if (null == bookmarkInternal) {
                    continue;
               }
               String serverRecord = "[" + CreateRecord(objectId, "bookmark",
                 action, deviceId) + CreateBookmarkRecord(bookmarkInternal.mUrl,
                 bookmarkInternal.mTitle, bookmarkInternal.mIsFolder, 0) + " }";
               String localRecord = "";
               BookmarkItem bookmartItem = GetBookmarkItemByLocalId(nativeGetLocalIdByObjectId(objectId));
               if (null != bookmartItem) {
                   // TODO pass always CREATE_RECORD, it means action is create
                   localRecord = CreateRecord(objectId, "bookmark", CREATE_RECORD, mDeviceId) +
                       CreateBookmarkRecord(bookmartItem.getUrl(), bookmartItem.getTitle(),
                       bookmartItem.isFolder(), bookmartItem.getParentId().getId()) + " }]";
               }
               if (0 == res.length()) {
                    res += "[";
               } else {
                    res += ", ";
               }
               res += serverRecord + ", " + (0 != localRecord.length() ? localRecord : "null]");
               count++;
           }
           reader.endArray();
        } catch (UnsupportedEncodingException e) {
            Log.i("TAG", "GetExistingObjects UnsupportedEncodingException error " + e);
        } catch (IOException e) {
            Log.i("TAG", "GetExistingObjects IOException error " + e);
        } catch (IllegalStateException e) {
            Log.i("TAG", "GetExistingObjects IllegalStateException error " + e);
        } catch (IllegalArgumentException exc) {
            Log.i("TAG", "GetExistingObjects generation exception " + exc);
        } finally {
            if (null != reader) {
                try {
                    reader.close();
                } catch (IOException e) {
                }
            }
        }
        if (0 != res.length()) {
            res += "]";
        }
        if (count <= LAST_RECORDS_COUNT) {
            // We finished fetch in chunks;
            Log.i("TAG", "!!!finished fetch in chunks");
            mLatestRecordTimeStampt = "";
        }
        //to do debug
        //Log.i("TAG", "!!!GetExistingObjects res == " + res);
        //

        return res;
    }

    private BookmarkItem GetBookmarkItemByLocalId(String localId) {
        if (0 == localId.length()) {
            return null;
        }
        try {
           long llocalId = Long.parseLong(localId);
           GetBookmarkIdRunnable bookmarkRunnable = new GetBookmarkIdRunnable(llocalId);
           if (null == bookmarkRunnable) {
              return null;
           }
           synchronized (bookmarkRunnable)
           {
               // Execute code on UI thread
               ((Activity)mContext).runOnUiThread(bookmarkRunnable);

               // Wait until runnable finished
               try {
                   bookmarkRunnable.wait();
               } catch (InterruptedException e) {
               }
           }

           return bookmarkRunnable.mBookmarkItem;
        } catch (NumberFormatException e) {
        }

        return null;
    }

    public void SendResolveSyncRecords(String categoryName, String existingRecords) {
        if (!mSyncIsReady.IsReady()
              || null == categoryName || null == existingRecords
              || 0 == categoryName.length() || 0 == existingRecords.length()) {
            return;
        }
        CallScript(String.format("javascript:callbackList['resolve-sync-records'](null, '%1$s', %2$s)", categoryName, existingRecords));
    }

    private void EditBookmarkByLocalId(String localId, String url, String title) {
        if (0 == localId.length()) {
            return;
        }
        try {
           long llocalId = Long.parseLong(localId);
           EditBookmarkRunnable bookmarkRunnable = new EditBookmarkRunnable(llocalId, url, title);
           if (null == bookmarkRunnable) {
              return;
           }
           synchronized (bookmarkRunnable)
           {
               // Execute code on UI thread
               ((Activity)mContext).runOnUiThread(bookmarkRunnable);

               // Wait until runnable finished
               try {
                   bookmarkRunnable.wait();
               } catch (InterruptedException e) {
               }
           }
        } catch (NumberFormatException e) {
        }

        return;
    }

    private void AddBookmark(String url, String title, String objectId) {
        try {
           AddBookmarkRunnable bookmarkRunnable = new AddBookmarkRunnable(url, title);
           if (null == bookmarkRunnable) {
              return;
           }
           synchronized (bookmarkRunnable)
           {
               // Execute code on UI thread
               ((Activity)mContext).runOnUiThread(bookmarkRunnable);

               // Wait until runnable finished
               try {
                   bookmarkRunnable.wait();
               } catch (InterruptedException e) {
               }
           }
           if (null != bookmarkRunnable.mBookmarkId) {
               SaveObjectId(bookmarkRunnable.mBookmarkId.getId(), objectId);
           }
        } catch (NumberFormatException e) {
        }

        return;
    }

    private List<BookmarkItem> GetBookmarkItems() {
        try {
           GetBookmarkItemsRunnable bookmarkRunnable = new GetBookmarkItemsRunnable();
           if (null == bookmarkRunnable) {
              return null;
           }
           synchronized (bookmarkRunnable)
           {
               // Execute code on UI thread
               ((Activity)mContext).runOnUiThread(bookmarkRunnable);

               // Wait until runnable finished
               try {
                   bookmarkRunnable.wait();
               } catch (InterruptedException e) {
               }
           }

           return bookmarkRunnable.mBookmarksItems;
        } catch (NumberFormatException e) {
        }

        return null;
    }

    class GetBookmarkItemsRunnable implements Runnable {
        private List<BookmarkItem> mBookmarksItems = null;

        public GetBookmarkItemsRunnable() {
        }

        @Override
        public void run() {
            BookmarkModel newBookmarkModel = new BookmarkModel();
            if (null != newBookmarkModel) {
                BookmarkId mobileFolderId = newBookmarkModel.getMobileFolderId();
                if (null != mobileFolderId) {
                    mBookmarksItems = newBookmarkModel.getBookmarksForFolder(mobileFolderId);
                }
                newBookmarkModel.destroy();
            }

            synchronized (this)
            {
                this.notify();
            }
        }
    }

    public void ResolvedSyncRecords(String categoryName, String recordsJSON) {
        if (null == categoryName || null == recordsJSON) {
            return;
        }
        if (!SyncRecordType.BOOKMARKS.equals(categoryName)) {
            // TODO sync for other categories
            return;
        }

        //to do debug
        /*String[] records = recordsJSON.split("action");
        for (int i = 0; i < records.length; i++) {
            Log.i("TAG", "!!!record[" + i + "]" + records[i]);
        }*/
        //
        JsonReader reader = null;
        try {
            reader = new JsonReader(new InputStreamReader(new ByteArrayInputStream(recordsJSON.getBytes()), "UTF-8"));
            reader.beginArray();
            while (reader.hasNext()) {
                String objectId = "";
                BookMarkInternal bookmarkInternal = null;
                reader.beginObject();
                while (reader.hasNext()) {
                    String name = reader.nextName();
                    if (name.equals("objectId")) {
                        objectId = GetObjectIdJSON(reader);
                    } else if (name.equals("bookmark")) {
                        bookmarkInternal = GetBookmarkRecord(reader);
                    } else {
                        reader.skipValue();
                    }
               }
               reader.endObject();
               if (null == bookmarkInternal) {
                    continue;
               }
               if (!bookmarkInternal.mUrl.isEmpty() && !bookmarkInternal.mTitle.isEmpty()) {
                   String localId = nativeGetLocalIdByObjectId(objectId);
                   if (0 != localId.length()) {
                      EditBookmarkByLocalId(localId, bookmarkInternal.mUrl, bookmarkInternal.mTitle);
                   } else {
                      AddBookmark(bookmarkInternal.mUrl, bookmarkInternal.mTitle, objectId);
                   }
               }
           }
           reader.endArray();
        } catch (UnsupportedEncodingException e) {
            Log.i("TAG", "ResolvedSyncRecords UnsupportedEncodingException error " + e);
        } catch (IOException e) {
            Log.i("TAG", "ResolvedSyncRecords IOException error " + e);
        } catch (IllegalStateException e) {
            Log.i("TAG", "ResolvedSyncRecords IllegalStateException error " + e);
        } catch (IllegalArgumentException exc) {
            Log.i("TAG", "ResolvedSyncRecords generation exception " + exc);
        } finally {
            if (null != reader) {
                try {
                    reader.close();
                } catch (IOException e) {
                }
            }
        }
        if (mLatestRecordTimeStampt.isEmpty()) {
            mFetchInProgress = false;
        } else {
            FetchSyncRecords(mLatestRecordTimeStampt);
        }
    }

    public void DeleteSyncUser() {
        // TODO
    }

    public void DeleteSyncCategory() {
        // TODO
    }

    public void DeleteSyncSiteSettings() {
        // TODO
    }

    private String GetAction(JsonReader reader) throws IOException {
        if (null == reader) {
            return CREATE_RECORD;
        }
        int action = reader.nextInt();
        if (1 == action) {
            return UPDATE_RECORD;
        } else if (2 == action) {
            return DELETE_RECORD;
        }

        return CREATE_RECORD;
    }

    private String GetDeviceId(JsonReader reader) throws IOException {
        if (null == reader) {
            return "";
        }

        String deviceId = "";
        if (JsonToken.BEGIN_OBJECT == reader.peek()) {
            reader.beginObject();
            while (reader.hasNext()) {
                reader.nextName();
                if (0 != deviceId.length()) {
                    deviceId += ", ";
                }
                deviceId += reader.nextString();
            }
            reader.endObject();
        } else {
            reader.beginArray();
            while (reader.hasNext()) {
                if (0 != deviceId.length()) {
                    deviceId += ", ";
                }
                deviceId += String.valueOf(reader.nextInt());
            }
            reader.endArray();
        }

        return deviceId;
    }

    private String GetObjectIdJSON(JsonReader reader) throws IOException {
        if (null == reader) {
            return "";
        }

        String objectId = "";
        JsonToken objectType = reader.peek();
        if (JsonToken.BEGIN_OBJECT == reader.peek()) {
            reader.beginObject();
            while (reader.hasNext()) {
                reader.nextName();
                if (0 != objectId.length()) {
                    objectId += ", ";
                }
                objectId += String.valueOf(reader.nextInt());
            }
            reader.endObject();
        } else if (JsonToken.BEGIN_ARRAY == reader.peek()) {
            reader.beginArray();
            while (reader.hasNext()) {
                if (0 != objectId.length()) {
                    objectId += ", ";
                }
                objectId += String.valueOf(reader.nextInt());
            }
            reader.endArray();
        } else {
            assert false;
            objectId = String.valueOf(reader.nextInt());
        }

        return objectId;
    }

    private String GetObjectDataJSON(JsonReader reader) throws IOException {
        if (null == reader) {
            return "";
        }

        return reader.nextString();
    }

    private BookMarkInternal GetBookmarkRecord(JsonReader reader) throws IOException {
        BookMarkInternal bookmarkInternal = new BookMarkInternal();
        if (null == reader || null == bookmarkInternal) {
            return null;
        }

        reader.beginObject();
        while (reader.hasNext()) {
            String name = reader.nextName();
            if (name.equals("site")) {
                reader.beginObject();
                while (reader.hasNext()) {
                    name = reader.nextName();
                    if (name.equals("location")) {
                        bookmarkInternal.mUrl = reader.nextString();
                    } else if (name.equals("title")) {
                        bookmarkInternal.mTitle = reader.nextString();
                    } else {
                        reader.skipValue();
                    }
                }
                reader.endObject();
            } else if (name.equals("isFolder")) {
                bookmarkInternal.mIsFolder = reader.nextBoolean();
            } else if (name.equals("parentFolderObjectId")) {
                if (JsonToken.BEGIN_OBJECT == reader.peek()) {
                    reader.beginObject();
                    while (reader.hasNext()) {
                        reader.nextName();
                        bookmarkInternal.mParentFolderObjectId += reader.nextString();
                    }
                    reader.endObject();
                } else {
                    bookmarkInternal.mParentFolderObjectId = GetObjectIdJSON(reader);
                }
            } else {
                reader.skipValue();
            }
        }
        reader.endObject();

        return bookmarkInternal;
    }

    class EjectedRunnable implements Runnable {
        private String mJsToExecute;

        public EjectedRunnable(String jsToExecute) {
            mJsToExecute = jsToExecute;
        }

        @Override
        public void run() {
            if (null == mSyncWebView || null == mJsToExecute) {
                return;
            }
            mSyncWebView.loadUrl(mJsToExecute);
        }
    }

    class GetBookmarkIdRunnable implements Runnable {
        public BookmarkItem mBookmarkItem = null;
        private long mBookmarkId;

        public GetBookmarkIdRunnable(long bookmarkId) {
            mBookmarkId = bookmarkId;
            mBookmarkItem = null;
        }

        @Override
        public void run() {
            BookmarkModel newBookmarkModel = new BookmarkModel();
            BookmarkId bookmarkId = new BookmarkId(mBookmarkId, BookmarkType.NORMAL);
            if (null != newBookmarkModel && null != bookmarkId) {
                if (newBookmarkModel.doesBookmarkExist(bookmarkId)) {
                    mBookmarkItem = newBookmarkModel.getBookmarkById(bookmarkId);
                }
                newBookmarkModel.destroy();
            }

            synchronized (this)
            {
                this.notify();
            }
        }
    }

    class EditBookmarkRunnable implements Runnable {
        private long mBookmarkId;
        private String mUrl;
        private String mTitle;

        public EditBookmarkRunnable(long bookmarkId, String url, String title) {
            mBookmarkId = bookmarkId;
            mUrl = url;
            mTitle = title;
        }

        @Override
        public void run() {
            BookmarkModel newBookmarkModel = new BookmarkModel();
            BookmarkId bookmarkId = new BookmarkId(mBookmarkId, BookmarkType.NORMAL);
            if (null != newBookmarkModel && null != bookmarkId) {
                if (newBookmarkModel.doesBookmarkExist(bookmarkId)) {
                    BookmarkItem bookmarkItem = newBookmarkModel.getBookmarkById(bookmarkId);
                    if (null != bookmarkItem) {
                        if (!bookmarkItem.getTitle().equals(mTitle)) {
                            newBookmarkModel.setBookmarkTitle(bookmarkId, mTitle);
                        }
                        if (!mUrl.isEmpty() && bookmarkItem.isUrlEditable()) {
                            String fixedUrl = UrlFormatter.fixupUrl(mUrl);
                            if (null != fixedUrl && !fixedUrl.equals(bookmarkItem.getTitle())) {
                                newBookmarkModel.setBookmarkUrl(bookmarkId, fixedUrl);
                            }
                        }
                    }
                }
                newBookmarkModel.destroy();
            }

            synchronized (this)
            {
                this.notify();
            }
        }
    }

    class AddBookmarkRunnable implements Runnable {
        private BookmarkId mBookmarkId = null;
        private String mUrl;
        private String mTitle;

        public AddBookmarkRunnable(String url, String title) {
            mUrl = url;
            mTitle = title;
        }

        @Override
        public void run() {
            BookmarkModel newBookmarkModel = new BookmarkModel();
            if (null != newBookmarkModel) {
                mBookmarkId = BookmarkUtils.addBookmarkSilently(mContext, newBookmarkModel, mTitle, mUrl);
                newBookmarkModel.destroy();
            }

            synchronized (this)
            {
                this.notify();
            }
        }
    }

    class SyncThread extends Thread {
        public void run() {
          SharedPreferences sharedPref = mContext.getSharedPreferences(PREF_NAME, 0);
          mTimeLastFetch = sharedPref.getLong(PREF_LAST_FETCH_NAME, 0);
          mDeviceId = sharedPref.getString(PREF_DEVICE_ID, null);

          for (;;) {
              try {
                  boolean prefSyncDefault = false; // TODO replace it on false
                  boolean prefSync = mSharedPreferences.getBoolean(
                          PREF_SYNC_SWITCH, prefSyncDefault);
                  if (prefSync) {
                      InitSync(false);
                      Calendar currentTime = Calendar.getInstance();
                      long timeLastFetch = currentTime.getTimeInMillis();
                      if (!mFetchInProgress || timeLastFetch - mTimeLastFetch > INTERVAL_TO_REFETCH_RECORDS) {
                          mFetchInProgress = false;
                          FetchSyncRecords("");
                      }
                  }
                  Thread.sleep(BraveSyncWorker.INTERVAL_TO_FETCH_RECORDS);
              }
              catch(Exception exc) {
                  // Just ignore it if we cannot sync
                  Log.i("TAG", "Sync loop exception: " + exc);
              }
              if (mStopThread) {
                  break;
              }
          }
        }
    }

    public void ResetSync() {
        mShouldResetSync = true;
        SharedPreferences sharedPref = mContext.getSharedPreferences(PREF_NAME, 0);
        SharedPreferences.Editor editor = sharedPref.edit();
        editor.remove(PREF_LAST_FETCH_NAME);
        editor.remove(PREF_DEVICE_ID);
        editor.remove(PREF_SEED);
        editor.apply();
        mSeed = null;
        mDeviceId = null;
        mTimeLastFetch = 0;
        Log.i("TAG", "!!!ResetSync");
    }

    public void InitSync(boolean calledFromUIThread) {
          SharedPreferences sharedPref = mContext.getSharedPreferences(PREF_NAME, 0);
          if (null == mSeed || mSeed.isEmpty()) {
              mSeed = sharedPref.getString(PREF_SEED, null);
              Log.i("TAG", "!!!got seed == " + mSeed);
          }
          if (null == mSeed || mSeed.isEmpty()) {
              return;
          }
          // Init sync WebView
          if (!calledFromUIThread) {
              ((Activity)mContext).runOnUiThread(new Runnable() {
                  @Override
                  public void run() {
                     TrySync();
                  }
              });
          } else {
              TrySync();
          }
    }

    class JsObject {
        @JavascriptInterface
        public void handleMessage(String message, String arg1, String arg2, String arg3) {
            Log.i("TAG", "!!!here message1 == " + message);
            if (null != arg3 && !arg3.isEmpty()) {
                Log.i("TAG", "!!!arg3 == " + arg3);
            }
            switch (message) {
              case "get-init-data":
                break;
              case "got-init-data":
                GotInitData();
                break;
              case "save-init-data":
                SaveInitData(arg1, arg2);
                break;
              case "sync-debug":
                if (null != arg1) {
                    Log.i("TAG", "!!!sync-debug: " + arg1);
                }
                break;
              case "fetch-sync-records":
                mSyncIsReady.mFetchRecordsReady = true;
                break;
              case "resolve-sync-records":
                mSyncIsReady.mResolveRecordsReady = true;
                break;
              case "resolved-sync-records":
                ResolvedSyncRecords(arg1, arg2);
                break;
              case "send-sync-records":
                mSyncIsReady.mSendRecordsReady = true;
                break;
              case "delete-sync-user":
                mSyncIsReady.mDeleteUserReady = true;
                break;
              case "delete-sync-category":
                mSyncIsReady.mDeleteCategoryReady = true;
                break;
              case "delete-sync-site-settings":
                mSyncIsReady.mDeleteSiteSettingsReady = true;
                break;
              case "sync-ready":
                mSyncIsReady.mReady = true;
                FetchSyncRecords("");
                break;
              case "get-existing-objects":
                SendResolveSyncRecords(arg1, GetExistingObjects(arg1, arg2, arg3));
                break;
              default:
                Log.i("TAG", "!!!message == " + message + ", !!!arg1 == " + arg1 + ", arg2 == " + arg2);
                break;
            }
        }
    }

    class JsObjectWordsToBytes {
        @JavascriptInterface
        public void nicewareOutput(String result) {
            if (null == result || 0 == result.length()) {
                if (null != mSyncScreensObserver) {
                    mSyncScreensObserver.onWordsCodeWrong();
                }
                return;
            }

            JsonReader reader = null;
            String seed = "";
            try {
                reader = new JsonReader(new InputStreamReader(new ByteArrayInputStream(result.getBytes()), "UTF-8"));
                reader.beginObject();
                while (reader.hasNext()) {
                    String name = reader.nextName();
                    if (name.equals("data")) {
                        reader.beginArray();
                        while (reader.hasNext()) {
                            if (0 != seed.length()) {
                                seed += ", ";
                            }
                            seed += String.valueOf(reader.nextInt());
                        }
                        reader.endArray();
                    } else {
                        reader.skipValue();
                    }
               }
               reader.endObject();
            } catch (UnsupportedEncodingException e) {
                Log.i("TAG", "nicewareOutput UnsupportedEncodingException error " + e);
                if (null != mSyncScreensObserver) {
                    mSyncScreensObserver.onWordsCodeWrong();
                }
            } catch (IOException e) {
                Log.i("TAG", "nicewareOutput IOException error " + e);
                if (null != mSyncScreensObserver) {
                    mSyncScreensObserver.onWordsCodeWrong();
                }
            } catch (IllegalStateException e) {
                Log.i("TAG", "nicewareOutput IllegalStateException error " + e);
                if (null != mSyncScreensObserver) {
                    mSyncScreensObserver.onWordsCodeWrong();
                }
            } catch (IllegalArgumentException exc) {
                Log.i("TAG", "nicewareOutput generation exception " + exc);
                if (null != mSyncScreensObserver) {
                    mSyncScreensObserver.onWordsCodeWrong();
                }
            } finally {
                if (null != reader) {
                    try {
                        reader.close();
                    } catch (IOException e) {
                    }
                }
            }
            Log.i("TAG", "!!!seed == " + seed);

            if (null != mSyncScreensObserver) {
                mSyncScreensObserver.onSeedReceived(seed);
            }
        }
    }

    public void InitJSWebView(BraveSyncScreensObserver syncScreensObserver) {
        try {
            if (null == mJSWebView) {
                mJSWebView = new WebView(mContext);
                mJSWebView.getSettings().setJavaScriptEnabled(true);
                mJSWebView.addJavascriptInterface(new JsObjectWordsToBytes(), "injectedObject");
                // TODO debug
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                    WebView.setWebContentsDebuggingEnabled(true);
                }
                //
                mJSWebView.loadDataWithBaseURL("file:///android_asset/", SYNC_WORDS_HTML_SCRIPT, "text/html", "UTF-8",null);
            }
        } catch (Exception exc) {
            // Ignoring sync exception, we will try it on next loop execution
            Log.i("TAG", "InitJSWebView exception: " + exc);
        }
        if (null == mSyncScreensObserver) {
            mSyncScreensObserver = syncScreensObserver;
        }
    }

    public void GetNumber(String[] words) {
        if (null == mJSWebView) {
            return;
        }
        String wordsJSArray = "";
        for (int i = 0; i < words.length; i++) {
            if (0 == i) {
                wordsJSArray = "[";
            } else {
                wordsJSArray += ", ";
            }
            wordsJSArray += "'" + words[i] + "'";
            if (words.length - 1 == i) {
                wordsJSArray += "]";
            }
        }
        Log.i("TAG", "!!!words == " + wordsJSArray);
        mJSWebView.loadUrl(String.format("javascript:getBytesFromWords(%1$s)", wordsJSArray));
    }

    private native String nativeGetObjectIdByLocalId(String localId);
    private native String nativeGetLocalIdByObjectId(String objectId);
    private native void nativeSaveObjectId(String localId, String objectIdJSON, String objectId);
    private native void nativeClear();
}
