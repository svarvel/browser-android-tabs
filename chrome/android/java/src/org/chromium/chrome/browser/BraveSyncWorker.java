
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

import java.lang.IllegalArgumentException;
import java.lang.Runnable;
import java.util.Calendar;
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
    private static final String PREF_SYNC_SWITCH = "sync_switch";
    private static final String PREF_BOOKMARKS_CHECK_BOX = "sync_bookmarks_check_box";
    private final SharedPreferences mSharedPreferences;

    // WebView to sync data
    private WebView mSyncWebView;
    // Sync data
    private static final String SYNC_HTML_SCRIPT = "<script src='android_sync.js' type='text/javascript'></script><script src='bundle.js' type='text/javascript'></script>";
    private SyncThread mSyncThread = null;
    //

    private Context mContext;
    private boolean mStopThread = false;
    private SyncIsReady mSyncIsReady;

    private String mSeed = null;
    private String mDeviceId = null;
    private String mApiVersion = "0";
    private String mServerUrl = "https://sync-staging.brave.com";
    private String mDebug = "true";
    private long mTimeLastFetch = 0;   // In milliseconds

    public static class SyncRecordType {
        public static final String BOOKMARKS = "BOOKMARKS";
        public static final String HISTORY = "HISTORY_SITES";
        public static final String PREFERENCES = "PREFERENCES";

        public static String GetJSArray() {
            return "['" + BOOKMARKS + "', '" + HISTORY + "', '" + PREFERENCES + "']";
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

    public void CreateUpdateBookmark(boolean bCreate, BookmarkItem bookmarkItem) {

        final boolean bCreateFinal = bCreate;
        final BookmarkItem bookmarkItemFinal = bookmarkItem;
        new AsyncTask<String, Void, Void>() {
            @Override
            protected Void doInBackground(String... params) {
                if (!mSyncIsReady.IsReady() || null == bookmarkItemFinal) {
                    return null;
                }

                String objectId = GenerateObjectId(bookmarkItemFinal.getId().getId());
                String bookmarkRequest = CreateRecord(objectId, "bookmark",
                  (bCreateFinal ? "0" : "1"), mDeviceId) +
                  CreateBookmarkRecord(bookmarkItemFinal.getUrl(),
                    bookmarkItemFinal.getTitle(), bookmarkItemFinal.isFolder(),
                    bookmarkItemFinal.getParentId().getId()) +
                  "}]";

                SendSyncRecords(SyncRecordType.BOOKMARKS, bookmarkRequest);
                SaveObjectId(bookmarkItemFinal.getId().getId(), objectId);

                return null;
            }
        }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    private String CreateRecord(String objectId, String objectData, String action, String deviceId) {
        String record = "[{ action: " + action + ", ";
        record += "deviceId: [" + deviceId + "], ";
        record += "objectId: [" + objectId + "], ";
        record += "objectData: '" + objectData + "', ";

        return record;
    }

    private String CreateBookmarkRecord(String url, String title, boolean isFolder, long parentFolderId) {
        String bookmarkRequest = "bookmark:";
        bookmarkRequest += "{ site:";
        bookmarkRequest += "{ location: '" + url + "', ";
        bookmarkRequest += "title: '" + title + "', ";
        bookmarkRequest += "customTitle: '', ";
        bookmarkRequest += "lastAccessedTime: 0, ";
        bookmarkRequest += "creationTime: 0 }, ";
        bookmarkRequest += "isFolder: " + (isFolder ? "true" : "false") + ", ";
        bookmarkRequest += "parentFolderObjectId: " + parentFolderId + " }";

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
            // Ignoring sync exception, we will try it on next loop execution
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
        // Save seed and deviceId in preferences
        SharedPreferences sharedPref = mContext.getSharedPreferences(PREF_NAME, 0);
        SharedPreferences.Editor editor = sharedPref.edit();
        editor.putString(PREF_DEVICE_ID, mDeviceId);
        editor.putString(PREF_SEED, mSeed);
        editor.apply();
    }

    public void SendSyncRecords(String recordType, String recordsJSON) {
        if (!mSyncIsReady.IsReady()) {
            return;
        }
        CallScript(String.format("javascript:callbackList['send-sync-records'](null, '%1$s', %2$s)", recordType, recordsJSON));
    }

    public void FetchSyncRecords() {
        if (!mSyncIsReady.IsReady()) {
            return;
        }
        CallScript(String.format("javascript:callbackList['fetch-sync-records'](null, %1$s, %2$s)", SyncRecordType.GetJSArray(), String.valueOf(mTimeLastFetch / 1000)));
        Calendar currentTime = Calendar.getInstance();
        mTimeLastFetch = currentTime.getTimeInMillis();
        // Save last fetch time in preferences
        SharedPreferences sharedPref = mContext.getSharedPreferences(PREF_NAME, 0);
        SharedPreferences.Editor editor = sharedPref.edit();
        editor.putLong(PREF_LAST_FETCH_NAME, mTimeLastFetch);
        editor.apply();
    }

    public String GetExistingObjects(String categoryName, String recordsJSON) {
        if (null == categoryName || null == recordsJSON) {
            return "";
        }
        if (!SyncRecordType.BOOKMARKS.equals(categoryName)) {
            // TODO sync for other categories
            return "";
        }
        String res = "";

        JsonReader reader = null;
        try {
            reader = new JsonReader(new InputStreamReader(new ByteArrayInputStream(recordsJSON.getBytes()), "UTF-8"));
            reader.beginArray();
            while (reader.hasNext()) {
                boolean bCreate = false;
                String objectId = "";
                String deviceId = "";
                String objectData = "";
                BookMarkInternal bookmarkInternal = null;
                reader.beginObject();
                while (reader.hasNext()) {
                    String name = reader.nextName();
                    if (name.equals("action")) {
                        bCreate = GetAction(reader);
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
               String serverRecord = CreateRecord(objectId, "bookmark",
                 (bCreate ? "0" : "1"), deviceId) + CreateBookmarkRecord(bookmarkInternal.mUrl,
                 bookmarkInternal.mTitle, bookmarkInternal.mIsFolder, 0) + " }]";
               String localRecord = "";
               BookmarkItem bookmartItem = GetBookmarkItemByLocalId(nativeGetLocalIdByObjectId(objectId));
               if (null != bookmartItem) {
                   // TODO pass always "0", it means action is create
                   localRecord = CreateRecord(objectId, "bookmark", "0", mDeviceId) +
                       CreateBookmarkRecord(bookmartItem.getUrl(), bookmartItem.getTitle(),
                       bookmartItem.isFolder(), bookmartItem.getParentId().getId()) + " }]";
               }
               if (0 == res.length()) {
                    res += "[";
               } else {
                    res += ", [";
               }
               res += serverRecord + ", " + (0 != localRecord.length() ? localRecord : "null");
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

    private void AddBookmark(String url, String title) {
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
               String objectId = GenerateObjectId(bookmarkRunnable.mBookmarkId.getId());
               SaveObjectId(bookmarkRunnable.mBookmarkId.getId(), objectId);
           }
        } catch (NumberFormatException e) {
        }

        return;
    }

    public void ResolvedSyncRecords(String categoryName, String recordsJSON) {
        if (null == categoryName || null == recordsJSON) {
            return;
        }
        if (!SyncRecordType.BOOKMARKS.equals(categoryName)) {
            // TODO sync for other categories
            return;
        }

        Log.i("TAG", "!!!Resolved == " + recordsJSON);

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
               String localId = nativeGetLocalIdByObjectId(objectId);
               if (0 != localId.length()) {
                  EditBookmarkByLocalId(nativeGetLocalIdByObjectId(objectId), bookmarkInternal.mUrl, bookmarkInternal.mTitle);
               } else {
                  AddBookmark(bookmarkInternal.mUrl, bookmarkInternal.mTitle);
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

    private boolean GetAction(JsonReader reader) throws IOException {
        if (null == reader) {
            return false;
        }

        return (reader.nextInt() == 0) ? true : false;
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
        } else {
            reader.beginArray();
            while (reader.hasNext()) {
                if (0 != objectId.length()) {
                    objectId += ", ";
                }
                objectId += String.valueOf(reader.nextInt());
            }
            reader.endArray();
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
                    bookmarkInternal.mParentFolderObjectId = String.valueOf(reader.nextInt());
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
          //mSeed = sharedPref.getString(PREF_SEED, null);

          for (;;) {
              try {
                  boolean prefSyncDefault = false; // TODO replace it on false
                  boolean prefSync = mSharedPreferences.getBoolean(
                          PREF_SYNC_SWITCH, prefSyncDefault);
                  if (prefSync) {
                      ((Activity)mContext).runOnUiThread(new Runnable() {
                          @Override
                          public void run() {
                             TrySync();
                          }
                      });
                      FetchSyncRecords();
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

    class JsObject {
        @JavascriptInterface
        public void handleMessage(String message, String arg1, String arg2) {
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
                break;
              case "get-existing-objects":
                SendResolveSyncRecords(arg1, GetExistingObjects(arg1, arg2));
                break;
              default:
                Log.i("TAG", "!!!message == " + message + ", !!!arg1 == " + arg1 + ", arg2 == " + arg2);
                break;
            }
        }
    }

    private native String nativeGetObjectIdByLocalId(String localId);
    private native String nativeGetLocalIdByObjectId(String objectId);
    private native void nativeSaveObjectId(String localId, String objectIdJSON, String objectId);
    private native void nativeClear();
}
