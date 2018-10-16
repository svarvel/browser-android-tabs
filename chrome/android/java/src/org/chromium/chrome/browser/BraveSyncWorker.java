/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.chromium.chrome.browser;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import org.chromium.base.task.AsyncTask;
import android.os.Build;
import android.os.Looper;
import android.util.Base64;
import android.util.JsonReader;
import android.util.JsonToken;
import android.webkit.JavascriptInterface;

import org.chromium.base.annotations.JNINamespace;
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


@JNINamespace("brave_sync_storage")
public class BraveSyncWorker {
    private static final String TAG = "SYNC";
    public static final String PREF_NAME = "SyncPreferences";
    private static final String PREF_LAST_FETCH_NAME = "TimeLastFetch";
    public static final String PREF_DEVICE_ID = "DeviceId";
    public static final String PREF_BASE_ORDER = "BaseOrder";
    public static final String PREF_LAST_ORDER = "LastOrder";
    public static final String PREF_SEED = "Seed";
    public static final String PREF_SYNC_DEVICE_NAME = "SyncDeviceName";
    private static final int SYNC_SLEEP_ATTEMPTS_COUNT = 20;
    private static final int INTERVAL_TO_FETCH_RECORDS = 1000 * 60;    // Milliseconds
    private static final int INTERVAL_TO_REFETCH_RECORDS = 10000 * 60;    // Milliseconds
    private static final int LAST_RECORDS_COUNT = 980;
    private static final int SEND_RECORDS_COUNT_LIMIT = 1000;
    private static final int FETCH_RECORDS_CHUNK_SIZE = 300;
    private static final String PREF_SYNC_SWITCH = "sync_switch";
    private static final String PREF_SYNC_BOOKMARKS = "brave_sync_bookmarks";
    public static final String PREF_SYNC_TABS = "brave_sync_tabs";
    public static final String PREF_SYNC_HISTORY = "brave_sync_history";
    public static final String PREF_SYNC_AUTOFILL_PASSWORDS = "brave_sync_autofill_passwords";
    public static final String PREF_SYNC_PAYMENT_SETTINGS = "brave_sync_payment_settings";
    public static final String CREATE_RECORD = "0";
    public static final String UPDATE_RECORD = "1";
    public static final String DELETE_RECORD = "2";
    private static final int ATTEMPTS_BEFORE_SENDING_NOT_SYNCED_RECORDS = 10;

    private final SharedPreferences mSharedPreferences;

    private static final String ANDROID_SYNC_JS = "android_sync.js";
    private static final String BUNDLE_JS = "bundle.js";
    private static final String CRYPTO_JS = "crypto.js";
    private static final String ANDROID_SYNC_WORDS_JS = "android_sync_words.js";

    private static final String ORIGINAL_SEED_KEY = "originalSeed";
    private static final String DEVICES_NAMES = "devicesNames";
    public static final int NICEWARE_WORD_COUNT = 16;
    public static final int BIP39_WORD_COUNT = 24;

    private SyncThread mSyncThread = null;

    private Context mContext;
    private boolean mStopThread = false;
    private SyncIsReady mSyncIsReady;

    private String mSeed = null;
    private String mDeviceId = null;
    private String mDeviceName = null;
    private String mApiVersion = "0";
    private String mBaseOrder = null;
    private String mLastOrder = null;
    private String mServerUrl = "https://sync-staging.brave.com";
    //private String mServerUrl = "https://sync.brave.com";
    private String mDebug = "true";
    private long mTimeLastFetch = 0;   // In milliseconds
    private long mTimeLastFetchExecuted = 0;   // In milliseconds
    private String mLatestRecordTimeStampt = "";
    private boolean mFetchInProgress = false;
    private BookmarkId mDefaultFolder = null;
    private BookmarkModel mNewBookmarkModel = null;
    private boolean mInterruptSyncSleep = false;

    private BraveSyncScreensObserver mSyncScreensObserver;

    private List<ResolvedRecordsToApply> mResolvedRecordsToApply = new ArrayList<ResolvedRecordsToApply>();

    private WebContents mWebContents = null;
    private JavascriptInjector mWebContentsInjector = null;
    private ViewEventSinkImpl mViewEventSink = null;
    private WebContents mJSWebContents = null;
    private JavascriptInjector mJSWebContentsInjector = null;
    private ViewEventSinkImpl mJSViewEventSink = null;
    private boolean mReorderBookmarks;
    private int mAttepmtsBeforeSendingNotSyncedRecords = ATTEMPTS_BEFORE_SENDING_NOT_SYNCED_RECORDS;

    enum NotSyncedRecordsOperation {
        GetItems, AddItems, DeleteItems
    }

    public static class SyncRecordType {
        public static final String BOOKMARKS = "BOOKMARKS";
        public static final String HISTORY = "HISTORY_SITES";
        public static final String PREFERENCES = "PREFERENCES";

        public static String GetJSArray() {
            return "['" + BOOKMARKS + "', '" + PREFERENCES + "']";//"', '" + HISTORY + "', '" + PREFERENCES + "']";
        }
    }

    public static class SyncObjectData {
        public static final String BOOKMARK = "bookmark";
        public static final String HISTORY_SITE = "historySite";
        public static final String SITE_SETTING = "siteSetting";
        public static final String DEVICE = "device";
    }

    class SyncIsReady {

        public boolean mFetchRecordsReady = false;
        public boolean mResolveRecordsReady = false;
        public boolean mSendRecordsReady = false;
        public boolean mDeleteUserReady = false;
        public boolean mDeleteCategoryReady = false;
        public boolean mDeleteSiteSettingsReady = false;
        public boolean mReady = false;
        public boolean mShouldResetSync = false;

        public boolean IsReady() {
            return mReady && mFetchRecordsReady && mResolveRecordsReady
                && mSendRecordsReady && mDeleteUserReady && mDeleteCategoryReady
                && mDeleteSiteSettingsReady && !mShouldResetSync;
        }

        public void Reset() {
            mFetchRecordsReady = false;
            mResolveRecordsReady = false;
            mSendRecordsReady = false;
            mDeleteUserReady = false;
            mDeleteCategoryReady = false;
            mDeleteSiteSettingsReady = false;
            mReady = false;
        }
    }

    class BookMarkInternal {
        public String mUrl = "";
        public String mTitle = "";
        public String mCustomTitle = "";
        public String mParentFolderObjectId = "";
        public boolean mIsFolder = false;
        public long mLastAccessedTime = 0;
        public long mCreationTime = 0;
        public String mFavIcon = "";
        public String mOrder = "";
    }

    public class OrderedBookmark implements Comparable<OrderedBookmark> {
        private BookmarkItem bookmark;
        private String order;

        public OrderedBookmark(BookmarkItem bookmark, String order){
            this.bookmark = bookmark;
            this.order = order;
        }

        public BookmarkItem Bookmark() {
            return bookmark;
        }

        @Override
        public int compareTo(OrderedBookmark compare) {
            if (order.isEmpty() || compare.order.isEmpty()) {
                Log.e(TAG, "Incorrect bookmark order");
                // This should not happen
                assert false;
                return 0;
            }
            String[] thisNumbers = order.split("\\.");
            String[] compareNumbers = compare.order.split("\\.");
            int maxSize = thisNumbers.length > compareNumbers.length ? compareNumbers.length : thisNumbers.length;
            for (int i = 0; i < maxSize; i++) {
                if (Integer.parseInt(thisNumbers[i]) > Integer.parseInt(compareNumbers[i])) {
                    return 1;
                } else if (Integer.parseInt(thisNumbers[i]) < Integer.parseInt(compareNumbers[i])){
                    return -1;
                }
            }
            // It means we have equal start parts(ex. 2.2.1.1 vs 2.2.1)
            if (thisNumbers.length > compareNumbers.length) {
                return -1;
            } else if (compareNumbers.length > thisNumbers.length) {
                return 1;
            }
            Log.e(TAG, "Bookmark compare improper state");
            Log.e(TAG, "order == " + order);
            Log.e(TAG, "compare.order == " + compare.order);
            // This should not happen
            assert false;
            return 0;
        }
    }

    public class ResolvedRecordsToApply {
        public ResolvedRecordsToApply(String objectId, String action, BookMarkInternal bookMarkInternal, String deviceName, String deviceId) {
            mObjectId = objectId;
            mAction = action;
            mBookmarkInternal = bookMarkInternal;
            mDeviceName = deviceName;
            mDeviceId = deviceId;
        }

        public String mObjectId;
        public String mAction;
        public BookMarkInternal mBookmarkInternal;
        public String mDeviceName;
        public String mDeviceId;
    }

    public final BookmarkModelObserver mBookmarkModelObserver = new BookmarkModelObserver() {

        private BookmarkBridge mBridge = null;

        @Override
        public void bookmarkModelChanged() {
        }

        @Override
        public void braveExtensiveBookmarkChangesBeginning() {
            if (null != mBridge && !mBridge.isBookmarkModelLoaded()) {
                mBridge = null;
            }
            if (null == mBridge) {
                return;
            }
            mBridge.extensiveBookmarkChangesBeginning();
        }

        @Override
        public void braveExtensiveBookmarkChangesEnded() {
            if (null != mBridge && !mBridge.isBookmarkModelLoaded()) {
                mBridge = null;
            }
            if (null == mBridge) {
                return;
            }
            mBridge.extensiveBookmarkChangesEnded();
        }

        @Override
        public void braveBookmarkModelLoaded(BookmarkBridge bridge) {
            mBridge = bridge;
        }
    };



    public BraveSyncWorker(Context context) {
        mReorderBookmarks = false;
        mContext = context;
        mTimeLastFetchExecuted = 0;
        mSharedPreferences = ContextUtils.getAppSharedPreferences();
        mSyncIsReady = new SyncIsReady();
        mSyncThread = new SyncThread();
        if (null != mSyncThread) {
            mSyncThread.start();
        }
        if (null == mDefaultFolder) {
            GetDefaultFolderId();
        }
    }

    private String convertStreamToString(InputStream is) {
        Scanner s = new Scanner(is).useDelimiter("\\A");
        return s.hasNext() ? s.next() : "";
    }

    public void Stop() {
        mStopThread = true;
        if (null != mNewBookmarkModel) {
            mNewBookmarkModel.destroy();
            mNewBookmarkModel = null;
        }
        if (mSyncThread != null) {
            mSyncThread.interrupt();
            mSyncThread = null;
        }
        nativeClear();
    }

    public void CreateUpdateDeleteBookmarks(String action, BookmarkItem[] bookmarks, final boolean addIdsToNotSynced,
              final boolean isInitialSync) {
        assert null != bookmarks;
        if (null == bookmarks || 0 == bookmarks.length || !mSyncIsReady.IsReady() || !IsSyncBookmarksEnabled()) {
            return;
        }
        Log.i(TAG, "CreateUpdateDeleteBookmarks: " + bookmarks.length);
        final String actionFinal = action;
        final HashSet<Long> processedFolderIds = new HashSet<Long>();
        final long defaultFolderId = (null != mDefaultFolder ? mDefaultFolder.getId() : 0);
        final List<BookmarkItem> bookmarksParentFolders = new ArrayList<BookmarkItem>();
        boolean uiThread = (Looper.myLooper() == Looper.getMainLooper());
        if (!actionFinal.equals(DELETE_RECORD)) {
            // Fill parent folders recursively, if it's not delete operation
            for (int i = 0; i < bookmarks.length; i++) {
                long processedId = bookmarks[i].getParentId().getId();
                if (defaultFolderId == processedId) {
                    continue;
                }
                int currentSize = bookmarksParentFolders.size();
                if (!processedFolderIds.contains(processedId)) {
                    BookmarkItem item = !uiThread ? GetBookmarkItemByLocalId(String.valueOf(processedId)) : BookmarkItemByBookmarkId(processedId);
                    while (item != null && !item.getTitle().isEmpty()) {
                        processedFolderIds.add(processedId);
                        bookmarksParentFolders.add(currentSize, item);
                        processedId = item.getParentId().getId();
                        if (processedFolderIds.contains(processedId)) {
                            break;
                        }
                        if (defaultFolderId == processedId) {
                            break;
                        }
                        item = !uiThread ? GetBookmarkItemByLocalId(String.valueOf(processedId)) : BookmarkItemByBookmarkId(processedId);
                    }
                }
            }
        }
        final BookmarkItem[] bookmarksFinal = bookmarks;

        new AsyncTask<Void>() {
            @Override
            protected Void doInBackground() {
                if (!mSyncIsReady.IsReady()) {
                    return null;
                }

                ArrayList<String> ids = new ArrayList<String>();
                StringBuilder bookmarkRequest = new StringBuilder("[");
                boolean comesFromPreviousSeed = false;
                if (isInitialSync) {
                    String originalSeed = GetObjectId(ORIGINAL_SEED_KEY);
                    if (originalSeed.equals(mSeed)) {
                        comesFromPreviousSeed = true;
                    }
                }
                if (actionFinal.equals(DELETE_RECORD)) {
                    // On delete we just process delete items
                    formRequestForBookmarks(bookmarksFinal, processedFolderIds, comesFromPreviousSeed, actionFinal, true, bookmarkRequest, ids);
                } else {
                    // On other cases we process parent folders first
                    formRequestForParrentFolders(bookmarksParentFolders, isInitialSync, comesFromPreviousSeed, bookmarkRequest, ids);
                    formRequestForBookmarks(bookmarksFinal, processedFolderIds, comesFromPreviousSeed, actionFinal, false, bookmarkRequest, ids);
                }
                if (bookmarkRequest.length() > 1) {
                    bookmarkRequest.append("]");
                } else {
                    // Nothing to send
                    return null;
                }
                //Log.i(TAG, "!!!bookmarkRequest == " + bookmarkRequest);
                SendSyncRecords(SyncRecordType.BOOKMARKS, bookmarkRequest, actionFinal, ids);

                return null;
            }

            private void formRequestForParrentFolders(List<BookmarkItem> bookmarksParentFolders, boolean isInitialSync, boolean comesFromPreviousSeed,
                                                        StringBuilder bookmarkRequest, ArrayList<String> ids) {
                for (BookmarkItem bookmarkFolder : bookmarksParentFolders) {
                    String localId = String.valueOf(bookmarkFolder.getId().getId());
                    String objectId = GetObjectId(localId);
                    if (!isInitialSync && !objectId.isEmpty()
                          || isInitialSync && comesFromPreviousSeed) {
                        continue;
                    }

                    bookmarkRequest.append(formRequestByBookmarkItem(bookmarkFolder, bookmarkRequest.length() <= 1, CREATE_RECORD, defaultFolderId, comesFromPreviousSeed));
                    if (addIdsToNotSynced) {
                        ids.add(localId);
                    }
                }
            }

            private void formRequestForBookmarks(BookmarkItem[] bookmarksFinal, HashSet<Long> processedFolderIds, boolean comesFromPreviousSeed, String actionFinal, boolean deleteOperation,
                                                  StringBuilder bookmarkRequest, ArrayList<String> ids) {
                if (deleteOperation) {
                    // Delete operation we perform in reverse order
                    for (int i = bookmarksFinal.length - 1; i >=0; i--) {
                        Log.i(TAG, "Deleting " + bookmarksFinal[i].getTitle());
                        bookmarkRequest.append(formRequestByBookmarkItem(bookmarksFinal[i], bookmarkRequest.length() <= 1, actionFinal, defaultFolderId, comesFromPreviousSeed));
                        if (addIdsToNotSynced) {
                            ids.add(String.valueOf(bookmarksFinal[i].getId().getId()));
                        }
                    }
                } else {
                    for (int i = 0; i < bookmarksFinal.length; i++) {
                        if (bookmarksFinal[i].isFolder() && processedFolderIds.contains(bookmarksFinal[i].getId().getId())) {
                            continue;
                        }
                        bookmarkRequest.append(formRequestByBookmarkItem(bookmarksFinal[i], bookmarkRequest.length() <= 1, actionFinal, defaultFolderId, comesFromPreviousSeed));
                        if (addIdsToNotSynced) {
                            ids.add(String.valueOf(bookmarksFinal[i].getId().getId()));
                        }
                    }
                }
            }

            private StringBuilder formRequestByBookmarkItem(BookmarkItem bookmarkItem, boolean firstRecord, String action,
                    long defaultFolderId, boolean comesFromPreviousSeed) {
                StringBuilder bookmarkRequest = new StringBuilder("");
                String localId = String.valueOf(bookmarkItem.getId().getId());
                String objectId = GetObjectId(localId);
                boolean objectExist = !objectId.isEmpty();
                if (!objectExist && action.equals(DELETE_RECORD)) {
                    // Do not create an object on delete
                    return bookmarkRequest;
                }
                if (objectExist && isInitialSync && comesFromPreviousSeed) {
                    return new StringBuilder("");
                }
                if (!objectExist) {
                    objectId = GenerateObjectId(localId);
                }
                if (!firstRecord) {
                    bookmarkRequest.append(", ");
                }
                String order = GetBookmarkOrder(localId, !objectExist);
                if (order.isEmpty()) {
                    Log.e(TAG, "formRequestByBookmarkItem empty order");
                    assert false;
                }
                long parentId = bookmarkItem.getParentId().getId();
                bookmarkRequest.append(CreateRecord(new StringBuilder(objectId), SyncObjectData.BOOKMARK, new StringBuilder(action), new StringBuilder(mDeviceId)));
                bookmarkRequest.append(CreateBookmarkRecord(bookmarkItem.getUrl(),
                    bookmarkItem.getTitle(), bookmarkItem.isFolder(),
                    parentId, "", "", 0, 0, "", order));
                bookmarkRequest.append("}");
                if (!objectExist) {
                    Log.i(TAG, "Saving object [" + bookmarkItem.getId().getId() + ", " + bookmarkItem.isFolder() + ", " + order + "]: " + objectId);
                    SaveObjectId(String.valueOf(bookmarkItem.getId().getId()), objectId, order, true);
                    mReorderBookmarks = true;
                    ReorderBookmarks();
                }
                // We will delete the objectId when we ensure that records were transferred
                /*else if (action.equals(DELETE_RECORD)) {
                    nativeDeleteByLocalId(localId);
                }*/

                return bookmarkRequest;
            }
        }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    public void DeleteBookmarks(BookmarkItem[] bookmarks) {
        if (!IsSyncEnabled()) {
            if (0 == mTimeLastFetch && 0 == mTimeLastFetchExecuted) {
                return;
            }
        }
        CreateUpdateDeleteBookmarks(DELETE_RECORD, bookmarks, true, false);
    }

    public void CreateUpdateBookmark(boolean bCreate, BookmarkItem bookmarkItem) {
        if (!IsSyncEnabled()) {
            if (0 == mTimeLastFetch && 0 == mTimeLastFetchExecuted) {
                return;
            }
        }
        BookmarkItem[] bookmarks = new BookmarkItem[1];
        bookmarks[0] = bookmarkItem;
        CreateUpdateDeleteBookmarks((bCreate ? CREATE_RECORD : UPDATE_RECORD), bookmarks, true, false);
    }

    private StringBuilder CreateRecord(StringBuilder objectId, String objectData, StringBuilder action, StringBuilder deviceId) {
        StringBuilder record = new StringBuilder("{ action: ");
        record.append(action).append(", ");
        record.append("deviceId: [").append(deviceId).append("], ");
        record.append("objectId: [").append(objectId).append("], ");
        record.append("objectData: '").append(objectData).append("', ");

        return record;
    }

    private StringBuilder CreateDeviceCreationRecord(String deviceName, String objectId, String action, String deviceId) {
        //Log.i(TAG, "CreateDeviceCreationRecord: " + deviceName);
        assert !deviceName.isEmpty();
        if (deviceName.isEmpty()) {
            return new StringBuilder(deviceName);
        }
        StringBuilder record = new StringBuilder("{ action: ").append(action).append(", ");
        record.append("deviceId: [").append(deviceId).append("], ");
        record.append("objectId: [").append(objectId).append("], ");
        record.append(SyncObjectData.DEVICE).append(": { name: \"").append(replaceUnsupportedCharacters(deviceName)).append("\"}}");

        //Log.i(TAG, "!!!device record == " + record);
        return record;
    }

    private String replaceUnsupportedCharacters(String in) {
      return in.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private StringBuilder CreateBookmarkRecord(String url, String title, boolean isFolder, long parentFolderId,
              String parentFolderObjectId, String customTitle, long lastAccessedTime, long creationTime, String favIcon,
              String order) {
        StringBuilder bookmarkRequest = new StringBuilder("bookmark:");
        bookmarkRequest.append("{ site:");
        bookmarkRequest.append("{ location: \"").append(url).append("\", ");
        if (!isFolder) {
            bookmarkRequest.append("title: \"").append(replaceUnsupportedCharacters(title)).append("\", ");
            bookmarkRequest.append("customTitle: \"").append(replaceUnsupportedCharacters(customTitle)).append("\", ");
        } else {
            bookmarkRequest.append("title: \"\", ");
            if (!customTitle.isEmpty()) {
                bookmarkRequest.append("customTitle: \"").append(replaceUnsupportedCharacters(customTitle)).append("\", ");
            } else {
                bookmarkRequest.append("customTitle: \"").append(replaceUnsupportedCharacters(title)).append("\", ");
            }
        }
        bookmarkRequest.append("favicon: \"").append(favIcon).append("\", ");
        bookmarkRequest.append("lastAccessedTime: ").append(lastAccessedTime).append(", ");
        bookmarkRequest.append("creationTime: ").append(creationTime).append("}, ");
        bookmarkRequest.append("isFolder: ").append(isFolder).append(", ");
        bookmarkRequest.append("order: \"").append(order).append("\", ");
        long defaultFolderId = (null != mDefaultFolder ? mDefaultFolder.getId() : 0);
        String parentObjectId = parentFolderObjectId;
        if (defaultFolderId != parentFolderId) {
            parentObjectId = "[" + GetObjectId(String.valueOf(parentFolderId)) + "]";
            assert !parentObjectId.isEmpty();
        }
        if (parentObjectId.isEmpty() || parentObjectId.length() <= 2) {
            parentObjectId = "null";
        }
        bookmarkRequest.append("parentFolderObjectId: ").append(parentObjectId).append("}");

        return bookmarkRequest;
    }

    private StringBuilder CreateDeviceRecord(String deviceName) {
      StringBuilder deviceRequest = new StringBuilder("device:");
      deviceRequest.append("{ name:\"").append(replaceUnsupportedCharacters(deviceName)).append("\"}");

      return deviceRequest;
    }

    private String GetDeviceNameByObjectId(String objectId) {
        String object = nativeGetObjectIdByLocalId(DEVICES_NAMES);
        if (object.isEmpty()) {
            return "";
        }

        String res = "";
        try {
            //Log.i(TAG, "GetDeviceNameByObjectId: trying to read JSON: " + object);
            JSONObject result = new JSONObject(object);
            JSONArray devices = result.getJSONArray("devices");
            for (int i = 0; i < devices.length(); i++) {
                JSONObject device = devices.getJSONObject(i);
                String currentObject = device.getString("objectId");
                if (currentObject.equals(objectId)) {
                    res = device.getString("name");
                    break;
                }
            }
        } catch (JSONException e) {
            Log.e(TAG, "GetDeviceNameByObjectId JSONException error " + e);
        } catch (IllegalStateException e) {
              Log.e(TAG, "GetDeviceNameByObjectId IllegalStateException error " + e);
        }

        //Log.i(TAG, "!!!GetDeviceNameByObjectId res == " + res);

        return res;
    }

    public ArrayList<ResolvedRecordsToApply> GetAllDevices() {
        ArrayList<ResolvedRecordsToApply> result_devices = new ArrayList<ResolvedRecordsToApply>();
        if (!mSyncIsReady.IsReady()) {
            return result_devices;
        }
        String object = nativeGetObjectIdByLocalId(DEVICES_NAMES);
        if (object.isEmpty()) {
            return result_devices;
        }

        JsonReader reader = null;
        try {
            //Log.i(TAG, "GetAllDevices: trying to read JSON: " + object);
            JSONObject result = new JSONObject(object);
            JSONArray devices = result.getJSONArray("devices");
            for (int i = 0; i < devices.length(); i++) {
                JSONObject device = devices.getJSONObject(i);
                String deviceName = device.getString("name");
                String currentObject = device.getString("objectId");
                String deviceId = device.getString("deviceId");
                result_devices.add(new ResolvedRecordsToApply(currentObject, "0", null, deviceName, deviceId));
            }
        } catch (JSONException e) {
            Log.e(TAG, "GetAllDevices JSONException error " + e);
        } catch (IllegalStateException e) {
              Log.e(TAG, "GetAllDevices IllegalStateException error " + e);
        }
        return result_devices;
    }

    private String GetBookmarkOrder(String localId) {
        try {
            String objectId = nativeGetObjectIdByLocalId(localId);
            if (objectId.isEmpty()) {
                return "";
            }
            JSONArray bookmarkArray = new JSONArray(objectId);
            JSONObject bookmark = bookmarkArray.getJSONObject(0);
            if (!bookmark.has("order")) {
                Log.e(TAG, "Could not find order for bookmark: " + objectId);
                return "";
            }
            String order = bookmark.getString("order");
            return order;
        } catch (JSONException e) {
            Log.e(TAG, "Could not get order for bookmark: " + e);
            return "";
        }
    }

    private String GetObjectId(String localId) {
        String objectId = nativeGetObjectIdByLocalId(localId);
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
            Log.e(TAG, "GetObjectId UnsupportedEncodingException error " + e);
        } catch (IOException e) {
            Log.e(TAG, "GetObjectId IOException error " + e);
        } catch (IllegalStateException e) {
              Log.e(TAG, "GetObjectId IllegalStateException error " + e);
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

    private void SaveObjectId(String localId, String objectId, String order, boolean saveObjectId) {
        String objectIdJSON = "[{\"objectId\": \"" + objectId + "\", \"order\": \"" + order + "\", \"apiVersion\": \"" + mApiVersion + "\"}]";
        if (!saveObjectId) {
            nativeSaveObjectId(localId, objectIdJSON, "");
        } else {
            nativeSaveObjectId(localId, objectIdJSON, objectId);
        }
    }

    private String GenerateObjectId(String localId) {
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
                Log.e(TAG, "ObjectId generation exception " + exc);
            }
        }

        return res;
    }

    private void TrySync() {
        try {
            if (mSyncIsReady.mShouldResetSync) {
                if (null != mWebContentsInjector) {
                    mWebContentsInjector.removeInterface("injectedObject");
                }
                if (null != mWebContents) {
                    mWebContents.destroy();
                }
                mWebContents = null;
                mViewEventSink = null;
                mWebContentsInjector = null;
                mSyncIsReady.mShouldResetSync = false;
            }
            if (null == mWebContents) {
                mWebContents = WebContentsFactory.createWebContents(false, true);
                if (null != mWebContents) {
                    mViewEventSink = ViewEventSinkImpl.from(mWebContents);
                    if (null != mViewEventSink) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
                            initContenViewCore();
                        } else {
                            getWebContentsInjector().addPossiblyUnsafeInterface(new JsObject(), "injectedObject", null);
                        }

                        String toLoad = "<script type='text/javascript'>";
                        try {
                            String script = convertStreamToString(mContext.getAssets().open(ANDROID_SYNC_JS));
                            toLoad += script.replace("%", "%25").replace("\n", "%0A") + "</script><script type='text/javascript'>";
                            script = convertStreamToString(mContext.getAssets().open(BUNDLE_JS));
                            toLoad += script.replace("%", "%25").replace("\n", "%0A") + "</script>";
                        } catch (IOException exc) {
                            Log.e(TAG, "Load script exception: " + exc);
                        }
                        LoadUrlParams loadUrlParams = LoadUrlParams.createLoadDataParamsWithBaseUrl(toLoad, "text/html", false, "file:///android_asset/", null);
                        loadUrlParams.setCanLoadLocalResources(true);
                        mWebContents.getNavigationController().loadUrl(loadUrlParams);
                    }
                }
            }
        } catch (Exception exc) {
            // Ignoring sync exception, we will try it on a next loop execution
            Log.e(TAG, "TrySync exception: " + exc);
        }
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
    private void initContenViewCore() {
        getWebContentsInjector().addPossiblyUnsafeInterface(new JsObject(), "injectedObject", JavascriptInterface.class);
    }

    private void CallScript(StringBuilder strCall) {
        ((Activity)mContext).runOnUiThread(new EjectedRunnable(strCall));
    }

    private void GotInitData() {
        String deviceId = (null == mDeviceId ? null : "[" + mDeviceId + "]");
        String seed = (null == mSeed ? null : "[" + mSeed + "]");
        if (0 == (mContext.getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE)) {
            mDebug = "false";
        }
        CallScript(new StringBuilder(String.format("javascript:callbackList['got-init-data'](null, %1$s, %2$s, {apiVersion: '%3$s', serverUrl: '%4$s', debug: %5$s})", seed, deviceId, mApiVersion, mServerUrl, mDebug)));
    }

    private void SaveInitData(String arg1, String arg2) {
        if (null == arg1 || null == arg2) {
            if (null != mSyncScreensObserver) {
                mSyncScreensObserver.onSyncError("Incorrect args for SaveInitData");
            }
        }
        if (null != arg1 && !arg1.isEmpty()) {
            mSeed = arg1;
        }
        mDeviceId = arg2;
        //Log.i(TAG, "!!!deviceId == " + mDeviceId);
        //Log.i(TAG, "!!!seed == " + mSeed);
        // Save seed and deviceId in preferences
        SharedPreferences sharedPref = mContext.getSharedPreferences(PREF_NAME, 0);
        SharedPreferences.Editor editor = sharedPref.edit();
        editor.putString(PREF_DEVICE_ID, mDeviceId);
        if (null != mSeed && !mSeed.isEmpty()) {
            if (null != mSyncScreensObserver) {
                mSyncScreensObserver.onSeedReceived(mSeed, false, true);
            }
            editor.putString(PREF_SEED, mSeed);
        }
        editor.apply();
    }

    public void SendSyncRecords(String recordType, StringBuilder recordsJSON, String action, ArrayList<String> ids) {
        if (!mSyncIsReady.IsReady()) {
            return;
        }
        SaveGetDeleteNotSyncedRecords(recordType, action, ids, NotSyncedRecordsOperation.AddItems);
        StringBuilder script = new StringBuilder("javascript:callbackList['send-sync-records'](null, '");
        script.append(recordType).append("'");
        script.append(", ").append(recordsJSON).append(")");
        Log.i(TAG, "SendSyncRecords: " + recordsJSON);
        CallScript(script);
    }

    @SuppressWarnings("unchecked")
    private ArrayList<String> GetNotSyncedRecords(String recordId) {
        ArrayList<String> existingList = new ArrayList<String>();
        try {
            String currentArray = GetObjectId(recordId);
            if (!currentArray.isEmpty()) {
                byte[] data = Base64.decode(currentArray, Base64.DEFAULT);
                ObjectInputStream ois = new ObjectInputStream(
                                                new ByteArrayInputStream(data));
                existingList = (ArrayList<String>)ois.readObject();
                ois.close();
            }
        } catch (IOException ioe) {
        } catch (ClassNotFoundException e) {
        }

        return existingList;
    }

    private void SaveNotSyncedRecords(String recordId, ArrayList<String> existingList) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ObjectOutputStream oos = new ObjectOutputStream(baos);
            oos.writeObject(existingList);
            oos.close();
            SaveObjectId(recordId, Base64.encodeToString(baos.toByteArray(), Base64.DEFAULT), "", false);
        } catch (IOException ioe) {
        }
    }

    private synchronized ArrayList<String> SaveGetDeleteNotSyncedRecords(String recordType, String action, ArrayList<String> ids, NotSyncedRecordsOperation operation) {
        if (NotSyncedRecordsOperation.GetItems != operation && 0 == ids.size()) {
            return null;
        }
        String recordId = recordType + action;
        ArrayList<String> existingList = GetNotSyncedRecords(recordId);
        if (NotSyncedRecordsOperation.GetItems == operation) {
            return existingList;
        } else if (NotSyncedRecordsOperation.AddItems == operation) {
            for (String id: ids) {
                if (!existingList.contains(id)) {
                    existingList.add(id);
                }
            }
        } else if (NotSyncedRecordsOperation.DeleteItems == operation) {
            boolean listChanged = false;
            boolean clearLocalDb = action.equals(DELETE_RECORD);
            for (String id: ids) {
                boolean itemRemoved = existingList.remove(id);
                if (!listChanged) {
                    listChanged = itemRemoved;
                }
                // Delete corresponding objectIds
                if (clearLocalDb) {
                    nativeDeleteByLocalId(id);
                }
            }
            if (!listChanged) {
                return null;
            }
        }

        SaveNotSyncedRecords(recordId, existingList);

        return null;
    }

    public void SetUpdateDeleteDeviceName(String action, String deviceName, String deviceId, String objectId) {
        if (action.equals(CREATE_RECORD)) {
            objectId = GenerateObjectId("deviceName");
        }
        assert !objectId.isEmpty();
        if (objectId.isEmpty()) {
            return;
        }
        StringBuilder request = new StringBuilder("[");
        request.append(CreateDeviceCreationRecord(deviceName, objectId, action, deviceId)).append("]");
        ArrayList<String> ids = new ArrayList<String>();
        //ids.add(id);
        //Log.i(TAG, "!!!device operation request: " + request.toString());
        SendSyncRecords(SyncRecordType.PREFERENCES, request, action, ids);
    }

    private void SendAllLocalBookmarks() {
      if (null == mNewBookmarkModel) {
          return;
      }
      synchronized (mNewBookmarkModel)
      {
          // Grab current existing bookmarksIds to sync them
          List<BookmarkItem> localBookmarks = GetBookmarkItems();
          if (null != localBookmarks) {
              //Log.i(TAG, "!!!localBookmarks.size() == " + localBookmarks.size());
              int listSize = localBookmarks.size();
              for (int i = 0; i < listSize; i += SEND_RECORDS_COUNT_LIMIT) {
                  List<BookmarkItem> subList = localBookmarks.subList(i, Math.min(listSize, i + SEND_RECORDS_COUNT_LIMIT));
                  CreateUpdateDeleteBookmarks(CREATE_RECORD, subList.toArray(new BookmarkItem[subList.size()]), true, true);
              }
          }
      }
    }

    private synchronized void FetchSyncRecords(String lastRecordFetchTime) {
        if (!mSyncIsReady.IsReady()) {
            //Log.i(TAG, "!!!Sync is not ready");
            return;
        }
        //Log.i(TAG, "!!!in FetchSyncRecords lastRecordFetchTime == " + lastRecordFetchTime);
        if (0 == mTimeLastFetch && 0 == mTimeLastFetchExecuted) {
            // It is the very first time of the sync start
            // Set device name
            if (null == mDeviceName || mDeviceName.isEmpty()) {
                SharedPreferences sharedPref = mContext.getSharedPreferences(PREF_NAME, 0);
                mDeviceName = sharedPref.getString(PREF_SYNC_DEVICE_NAME, "");
            }
            SetUpdateDeleteDeviceName(CREATE_RECORD, mDeviceName, mDeviceId, "");
            SendAllLocalBookmarks();
        }
        Calendar currentTime = Calendar.getInstance();
        long timeDiff = currentTime.getTimeInMillis() - mTimeLastFetch;
        if (currentTime.getTimeInMillis() - mTimeLastFetch <= INTERVAL_TO_FETCH_RECORDS && lastRecordFetchTime.isEmpty()) {
            return;
        }
        mInterruptSyncSleep = false;
        String fetchToRequest = (lastRecordFetchTime.isEmpty() ? String.valueOf(mTimeLastFetch) : lastRecordFetchTime);
        CallScript(new StringBuilder(String.format("javascript:callbackList['fetch-sync-records'](null, %1$s, %2$s, %3$s)", SyncRecordType.GetJSArray(), fetchToRequest, FETCH_RECORDS_CHUNK_SIZE)));
        mTimeLastFetchExecuted = currentTime.getTimeInMillis();
        if (!lastRecordFetchTime.isEmpty()) {
            try {
                mTimeLastFetch = Long.parseLong(lastRecordFetchTime);
                // Save last fetch time in preferences
                SharedPreferences sharedPref = mContext.getSharedPreferences(PREF_NAME, 0);
                SharedPreferences.Editor editor = sharedPref.edit();
                editor.putLong(PREF_LAST_FETCH_NAME, mTimeLastFetch);
                editor.apply();
            } catch (NumberFormatException e) {
            }
        }
    }

    public StringBuilder GetExistingObjects(String categoryName, String recordsJSON, String latestRecordTimeStampt, boolean isTruncated) {
        if (null == categoryName || null == recordsJSON) {
            return new StringBuilder("");
        }
        if (!SyncRecordType.BOOKMARKS.equals(categoryName) && !SyncRecordType.PREFERENCES.equals(categoryName)) {
            // TODO sync for other categories
            return new StringBuilder("");
        }
        mFetchInProgress = true;
        mLatestRecordTimeStampt = latestRecordTimeStampt;

        StringBuilder res = new StringBuilder("");
        if (recordsJSON.length() > 2 && SyncRecordType.BOOKMARKS.equals(categoryName)) {
            Log.i(TAG, "!!!in GetExistingObjects: " + latestRecordTimeStampt + ": " + isTruncated + ": " + recordsJSON);
        }

        // Debug
        /*int iPos = recordsJSON.indexOf("NewFolder3");
        if (-1 != iPos) {
            if (iPos + 2000 > recordsJSON.length()) {
                Log.i(TAG, "!!!GetExistingObjects == " + recordsJSON.substring(iPos));
            } else {
                Log.i(TAG, "!!!GetExistingObjects == " + recordsJSON.substring(iPos, iPos + 2000));
            }
            if (iPos > 500) {
                if (iPos + 1500 > recordsJSON.length()) {
                    Log.i(TAG, "!!!GetExistingObjects == " + recordsJSON.substring(iPos - 500));
                } else {
                    Log.i(TAG, "!!!GetExistingObjects == " + recordsJSON.substring(iPos - 500, iPos + 1500));
                }
            }
        }*/
        /*iPos = recordsJSON.indexOf("\"objectId\":{\"0\":26,\"1\":251", iPos + 1);
        if (-1 != iPos) {
            if (iPos + 2000 > recordsJSON.length()) {
                Log.i(TAG, "!!!GetExistingObjects1 == " + recordsJSON.substring(iPos));
            } else {
                Log.i(TAG, "!!!GetExistingObjects1 == " + recordsJSON.substring(iPos, iPos + 2000));
            }
            if (iPos > 500) {
                if (iPos + 1500 > recordsJSON.length()) {
                    Log.i(TAG, "!!!GetExistingObjects1 == " + recordsJSON.substring(iPos - 500));
                } else {
                    Log.i(TAG, "!!!GetExistingObjects1 == " + recordsJSON.substring(iPos - 500, iPos + 1500));
                }
            }
        }*/
        //Log.i(TAG, "!!!recordsJSON == " + recordsJSON);
        /*int iPos = recordsJSON.indexOf("Bobrina");
        if (-1 != iPos) {
            Log.i(TAG, "!!!record == " + recordsJSON.substring(iPos, iPos + 2000));
            if (iPos > 500) {
                Log.i(TAG, "!!!record1 == " + recordsJSON.substring(iPos - 500, iPos + 1500));
            }
        }*/
        /*int step = 2000;
        int count = 0;
        for (;;) {
            int endIndex = count * step + step;
            if (endIndex > recordsJSON.length() - 1) {
                endIndex = recordsJSON.length() - 1;
            }
            String substr = recordsJSON.substring(count * step, endIndex);
            Log.i(TAG, "!!!substr == " + substr);
            if (endIndex != count * step + step) {
                break;
            }
            count++;
        }*/
        //

        HashMap<String, ArrayList<String>> syncedRecordsMap = new HashMap<String, ArrayList<String>>();
        long defaultFolderId = (null != mDefaultFolder ? mDefaultFolder.getId() : 0);
        JsonReader reader = null;
        try {
            reader = new JsonReader(new InputStreamReader(new ByteArrayInputStream(recordsJSON.getBytes()), "UTF-8"));
            reader.beginArray();
            while (reader.hasNext()) {
                StringBuilder action = new StringBuilder(CREATE_RECORD);
                StringBuilder objectId = new StringBuilder("");
                StringBuilder deviceId = new StringBuilder("");
                StringBuilder objectData = new StringBuilder("");
                BookMarkInternal bookmarkInternal = null;
                StringBuilder deviceName = new StringBuilder("");
                reader.beginObject();
                while (reader.hasNext()) {
                    String name = reader.nextName();
                    if (name.equals("action")) {
                        action = GetAction(reader);
                    } else if (name.equals("deviceId")) {
                        deviceId = GetDeviceId(reader);
                    } else if (name.equals("objectId")) {
                        objectId = GetObjectIdJSON(reader);
                    } else if (name.equals(SyncObjectData.BOOKMARK)) {
                        bookmarkInternal = GetBookmarkRecord(reader);
                    } else if (name.equals(SyncObjectData.DEVICE)) {
                        deviceName = GetDeviceName(reader);
                    } else if (name.equals("objectData")) {
                        objectData = GetObjectDataJSON(reader);
                    } else {
                        reader.skipValue();
                    }
               }
               reader.endObject();
               if (null == bookmarkInternal && 0 == deviceName.length()) {
                    continue;
               }
               StringBuilder serverRecord = new StringBuilder("[");
               StringBuilder localRecord = new StringBuilder("");
               if (null != bookmarkInternal) {
                 String localId = nativeGetLocalIdByObjectId(objectId.toString());
                 serverRecord.append(CreateRecord(objectId, SyncObjectData.BOOKMARK,
                   action, deviceId)).append(CreateBookmarkRecord(bookmarkInternal.mUrl,
                   bookmarkInternal.mTitle, bookmarkInternal.mIsFolder, defaultFolderId, "[" + bookmarkInternal.mParentFolderObjectId + "]",
                   bookmarkInternal.mCustomTitle, bookmarkInternal.mLastAccessedTime, bookmarkInternal.mCreationTime,
                   bookmarkInternal.mFavIcon, bookmarkInternal.mOrder)).append(" }");
                 BookmarkItem bookmarkItem = GetBookmarkItemByLocalId(localId);
                 if (null != bookmarkItem) {
                     String order = GetBookmarkOrder(localId, false);
                     // TODO pass always CREATE_RECORD, it means action is create
                     long parentId = bookmarkItem.getParentId().getId();
                     localRecord.append(CreateRecord(objectId, SyncObjectData.BOOKMARK, new StringBuilder(CREATE_RECORD), new StringBuilder(mDeviceId)))
                          .append(CreateBookmarkRecord(bookmarkItem.getUrl(), bookmarkItem.getTitle(),
                          bookmarkItem.isFolder(), parentId, "", "", 0, 0, "", order)).append(" }]");
                 }
                 Log.i(TAG, "Confirmed: " + bookmarkInternal.mTitle + ": " + action);
                 // Mark the record as sucessfully sent
                 ArrayList<String> value = syncedRecordsMap.get(action.toString());
                 if (null == value) {
                    value = new ArrayList<String>();
                 }
                 value.add(localId);
                 syncedRecordsMap.put(action.toString(), value);
               } else {
                 serverRecord.append(CreateRecord(objectId, SyncObjectData.DEVICE,
                   action, deviceId)).append(CreateDeviceRecord(deviceName.toString())).append(" }");
                 String localDeviceName = GetDeviceNameByObjectId(objectId.toString());
                 if (!localDeviceName.isEmpty()) {
                   localRecord.append(CreateRecord(objectId, SyncObjectData.DEVICE, new StringBuilder(CREATE_RECORD),
                      new StringBuilder(mDeviceId))).append(CreateDeviceRecord(localDeviceName)).append(" }]");
                 }
               }
               if (0 == res.length()) {
                    res.append("[");
               } else {
                    res.append(", ");
               }
               res.append(serverRecord).append(", ").append(0 != localRecord.length() ? localRecord : "null]");
           }
           reader.endArray();
        } catch (UnsupportedEncodingException e) {
            Log.e(TAG, "GetExistingObjects UnsupportedEncodingException error " + e);
        } catch (IOException e) {
            Log.e(TAG, "GetExistingObjects IOException error " + e);
        } catch (IllegalStateException e) {
            Log.e(TAG, "GetExistingObjects IllegalStateException error " + e);
        } catch (IllegalArgumentException exc) {
            Log.e(TAG, "GetExistingObjects generation exception " + exc);
        } finally {
            if (null != reader) {
                try {
                    reader.close();
                } catch (IOException e) {
                }
            }
        }

        if (0 != res.length()) {
            res.append("]");
        }
        if (!isTruncated) {
            // We finished fetch in chunks;
            //Log.i(TAG, "!!!finished fetch in chunks");
            mLatestRecordTimeStampt = "";
        }
        for (Map.Entry<String, ArrayList<String>> entry : syncedRecordsMap.entrySet()) {
            SaveGetDeleteNotSyncedRecords(categoryName, entry.getKey(), entry.getValue(), NotSyncedRecordsOperation.DeleteItems);
        }
        //
        //Log.i(TAG, "!!!GetExistingObjects res == " + res);
        /*int step = 2000;
        int count = 0;
        for (;;) {
            int endIndex = count * step + step;
            if (endIndex > res.length() - 1) {
                endIndex = res.length() - 1;
            }
            String substr = res.substring(count * step, endIndex);
            Log.i(TAG, "!!!substr == " + substr);
            if (endIndex != count * step + step) {
                break;
            }
            count++;
        }*/
        //

        //Log.i(TAG, "!!!res == " + res.toString());
        return res;
    }

    private ArrayList<BookmarkItem> GetBookmarkItemsByLocalIds(ArrayList<String> localIds, String action) {
        if (0 == localIds.size()) {
            return new ArrayList<BookmarkItem>();
        }
        try {
           ArrayList<Long> localIdsLong = new ArrayList<Long>();
           for (String id: localIds) {
              localIdsLong.add(Long.parseLong(id));
           }
           GetBookmarkItemsByLocalIdsRunnable bookmarkRunnable = new GetBookmarkItemsByLocalIdsRunnable(localIdsLong, action);
           if (null == bookmarkRunnable) {
              return new ArrayList<BookmarkItem>();
           }
           synchronized (bookmarkRunnable)
           {
               // Execute code on UI thread
               ((Activity)mContext).runOnUiThread(bookmarkRunnable);

               // Wait until runnable is finished
               try {
                   bookmarkRunnable.wait();
               } catch (InterruptedException e) {
               }
           }

           return bookmarkRunnable.mBookmarkItems;
        } catch (NumberFormatException e) {
            Log.e(TAG, "NumberFormatException: " + e);
        }

        return null;
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

               // Wait until runnable is finished
               try {
                   bookmarkRunnable.wait();
               } catch (InterruptedException e) {
                   Log.e(TAG, "GetBookmarkItemByLocalId error: " + e);
               }
           }

           return bookmarkRunnable.mBookmarkItem;
        } catch (NumberFormatException e) {
           Log.e(TAG, "GetBookmarkItemByLocalId error: " + e);
        }

        return null;
    }

    public void SendResolveSyncRecords(String categoryName, StringBuilder existingRecords) {
        if (!mSyncIsReady.IsReady()
              || null == categoryName || null == existingRecords
              || 0 == categoryName.length()) {
            return;
        }
        if (0 == existingRecords.length()) {
            existingRecords.append("[]");
        }
        StringBuilder script = new StringBuilder("javascript:callbackList['resolve-sync-records'](null, '");
        script.append(categoryName).append("'");
        script.append(", ").append(existingRecords).append(")");

        CallScript(script);
    }

    private void DeleteBookmarkByLocalId(String localId) {
        if (0 == localId.length()) {
            return;
        }
        try {
           long llocalId = Long.parseLong(localId);
           DeleteBookmarkRunnable bookmarkRunnable = new DeleteBookmarkRunnable(llocalId);
           if (null == bookmarkRunnable) {
              return;
           }
           synchronized (bookmarkRunnable)
           {
               // Execute code on UI thread
               ((Activity)mContext).runOnUiThread(bookmarkRunnable);

               // Wait until runnable is finished
               try {
                   bookmarkRunnable.wait();
               } catch (InterruptedException e) {
               }
           }
           nativeDeleteByLocalId(localId);
           if (null != bookmarkRunnable.mBookmarksItems) {
               for (BookmarkItem item: bookmarkRunnable.mBookmarksItems) {
                  String itemLocalId = String.valueOf(item.getId().getId());
                  nativeDeleteByLocalId(itemLocalId);
               }
           }
        } catch (NumberFormatException e) {
        }

        return;
    }

    class DeleteBookmarkRunnable implements Runnable {
        private long mBookmarkId;
        public List<BookmarkItem> mBookmarksItems = null;

        public DeleteBookmarkRunnable(long bookmarkId) {
            mBookmarkId = bookmarkId;
        }

        @Override
        public void run() {
            BookmarkId bookmarkId = new BookmarkId(mBookmarkId, BookmarkType.NORMAL);
            if (bookmarkId == null) {
                Log.e(TAG, "bookmarkId == null");
            }
            if (null != mNewBookmarkModel && null != bookmarkId) {
                synchronized (mNewBookmarkModel) {
                    // Get children to clean local leveldb, all children are deleted recursively
                    BookmarkItem bookmarkRoot = mNewBookmarkModel.getBookmarkById(bookmarkId);
                    mBookmarksItems = getBookmarksForFolder(mNewBookmarkModel, bookmarkRoot);
                    // Delete children bookmarks
                    for (BookmarkItem bookmark : mBookmarksItems) {
                        if (!bookmark.isFolder()) {
                            Log.i(TAG, "deleteBookmarkSilently: " + bookmark.getTitle() + " (" + bookmark.getId().getId() + ")");
                            mNewBookmarkModel.deleteBookmarkSilently(bookmark.getId());
                        }
                    }
                    // Delete children bookmark folders
                    for (BookmarkItem bookmark : mBookmarksItems) {
                        if (bookmark.isFolder()) {
                            Log.i(TAG, "deleteBookmarkSilently: " + bookmark.getTitle() + " (" + bookmark.getId().getId() + ")");
                            mNewBookmarkModel.deleteBookmarkSilently(bookmark.getId());
                        }
                    }
                    // Delete root bookmark
                    if (bookmarkRoot == null) {
                        Log.e(TAG, "Failed to find root bookmark: " + bookmarkId);
                    } else {
                        Log.i(TAG, "Delete root bookmark: " + bookmarkRoot.getTitle() + "(" + bookmarkId + ")");
                    }
                    mNewBookmarkModel.deleteBookmarkSilently(bookmarkId);
                }
            }

            synchronized (this)
            {
                this.notify();
            }
        }
    }

    private void EditBookmarkByLocalId(String localId, String url, String title, String parentLocalId, String objectId, String order) {
        if (0 == localId.length()) {
            return;
        }
        try {
           long llocalId = Long.parseLong(localId);
           long defaultFolderId = (null != mDefaultFolder ? mDefaultFolder.getId() : 0);
           long lparentLocalId = defaultFolderId;
           if (!parentLocalId.isEmpty()) {
                lparentLocalId = Long.parseLong(parentLocalId);
           }
           EditBookmarkRunnable bookmarkRunnable = new EditBookmarkRunnable(llocalId, url, title, lparentLocalId);
           if (null == bookmarkRunnable) {
              return;
           }
           synchronized (bookmarkRunnable)
           {
               // Execute code on UI thread
               ((Activity)mContext).runOnUiThread(bookmarkRunnable);

               // Wait until runnable is finished
               try {
                   bookmarkRunnable.wait();
               } catch (InterruptedException e) {
                  Log.e(TAG, "EditBookmarkByLocalId error: " + e);
               }
           }
        } catch (NumberFormatException e) {
            Log.e(TAG, "EditBookmarkByLocalId error: " + e);
        }
        Log.i(TAG, "EditBookmarkByLocalId: " + localId + ": " + objectId + ": " + order);
        SaveObjectId(localId, objectId, order, true);
        mReorderBookmarks = true;
        return;
    }

    private void AddBookmark(String url, String title, boolean isFolder, String objectId, String parentLocalId, String order) {
        try {
           long defaultFolderId = (null != mDefaultFolder ? mDefaultFolder.getId() : 0);
           long lparentLocalId = defaultFolderId;
           if (!parentLocalId.isEmpty()) {
                lparentLocalId = Long.parseLong(parentLocalId);
           }
           AddBookmarkRunnable bookmarkRunnable = new AddBookmarkRunnable(url, title, isFolder, lparentLocalId);
           if (null == bookmarkRunnable) {
                return;
           }
           synchronized (bookmarkRunnable)
           {
               // Execute code on UI thread
               ((Activity)mContext).runOnUiThread(bookmarkRunnable);

               // Wait until runnable is finished
               try {
                   bookmarkRunnable.wait();
               } catch (InterruptedException e) {
                   Log.e(TAG, "AddBookmark error: " + e);
               }
           }
           if (null != bookmarkRunnable.mBookmarkId) {
               Log.i(TAG, "AddBookmark: " + String.valueOf(bookmarkRunnable.mBookmarkId.getId()) + ": " + objectId + ": " + order);
               SaveObjectId(String.valueOf(bookmarkRunnable.mBookmarkId.getId()), objectId, order, true);
               mReorderBookmarks = true;
           }
        } catch (NumberFormatException e) {
            Log.e(TAG, "AddBookmark error: " + e);
        }
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

               // Wait until runnable is finished
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
            if (null != mNewBookmarkModel && null != mDefaultFolder) {
                mBookmarksItems = getBookmarksForFolder(mNewBookmarkModel, mNewBookmarkModel.getBookmarkById(mDefaultFolder));
            }

            synchronized (this)
            {
                this.notify();
            }
        }
    }

    private List<BookmarkItem> getBookmarksForFolder(BookmarkModel newBookmarkModel, BookmarkItem parent) {
        List<BookmarkItem> res = new ArrayList<BookmarkItem>();
        if (null == parent || !parent.isFolder()) {
            return res;
        }
        res = newBookmarkModel.getBookmarksForFolder(parent.getId());
        List<BookmarkItem> newList = new ArrayList<BookmarkItem>();
        for (BookmarkItem item : res) {
            if (!item.isFolder()) {
                continue;
            }
            newList.addAll(getBookmarksForFolder(newBookmarkModel, item));
        }
        res.addAll(newList);

        return res;
    }

    public void ResolvedSyncRecords(String categoryName, String recordsJSON) {
        //Log.i(TAG, "!!!in ResolvedSyncRecords");
        if (null == categoryName || null == recordsJSON) {
            assert false;
            return;
        }
        if (!SyncRecordType.BOOKMARKS.equals(categoryName) && !SyncRecordType.PREFERENCES.equals(categoryName)) {
            // TODO sync for other categories
            assert false;
            return;
        }

        // Debug
        /*int iPos = recordsJSON.indexOf("Vim Commands");
        if (-1 != iPos) {
            if (iPos + 2000 > recordsJSON.length()) {
                Log.i(TAG, "!!!Resolvedrecord == " + recordsJSON.substring(iPos));
            } else {
                Log.i(TAG, "!!!Resolvedrecord == " + recordsJSON.substring(iPos, iPos + 2000));
            }
            if (iPos > 500) {
                if (iPos + 1500 > recordsJSON.length()) {
                    Log.i(TAG, "!!!Resolvedrecord == " + recordsJSON.substring(iPos - 500));
                } else {
                    Log.i(TAG, "!!!Resolvedrecord == " + recordsJSON.substring(iPos - 500, iPos + 1500));
                }
            }
        }*/
        //
        /*if (recordsJSON.length() > 3) {
            Log.i(TAG, "ResolvedSyncRecords!!!recordsJSON = " + recordsJSON);
        }*/
        /*String[] records = recordsJSON.split("action");
        for (int i = 0; i < records.length; i++) {
            Log.i(TAG, "!!!record[" + i + "]" + records[i]);
        }*/
        //
        if (SyncRecordType.BOOKMARKS.equals(categoryName)) {
          SetExtensiveBookmarkOperation(true);
        }
        List<ResolvedRecordsToApply> devicesRecords = new ArrayList<ResolvedRecordsToApply>();
        JsonReader reader = null;
        try {
            reader = new JsonReader(new InputStreamReader(new ByteArrayInputStream(recordsJSON.getBytes()), "UTF-8"));
            reader.beginArray();
            while (reader.hasNext()) {
                String objectId = "";
                String action = CREATE_RECORD;
                BookMarkInternal bookmarkInternal = null;
                String deviceName = "";
                String deviceId = "";
                reader.beginObject();
                while (reader.hasNext()) {
                    String name = reader.nextName();
                    if (name.equals("action")) {
                        action = GetAction(reader).toString();
                    } else if (name.equals("deviceId")) {
                        deviceId = GetDeviceId(reader).toString();
                    } else if (name.equals("objectId")) {
                        objectId = GetObjectIdJSON(reader).toString();
                    } else if (name.equals(SyncObjectData.BOOKMARK)) {
                        bookmarkInternal = GetBookmarkRecord(reader);
                    } else if (name.equals(SyncObjectData.DEVICE)) {
                        deviceName = GetDeviceName(reader).toString();
                    } else {
                        reader.skipValue();
                    }
               }
               reader.endObject();
               if (null == bookmarkInternal && deviceName.isEmpty()) {
                  continue;
               }
               if (null != bookmarkInternal && (action.equals(DELETE_RECORD) || !bookmarkInternal.mTitle.isEmpty() || !bookmarkInternal.mCustomTitle.isEmpty())) {
                   if (BookmarkResolver(new ResolvedRecordsToApply(objectId, action, bookmarkInternal, "", ""), mResolvedRecordsToApply, false)) {
                      continue;
                   }
               } else if (!deviceName.isEmpty()) {
                   devicesRecords.add(new ResolvedRecordsToApply(objectId, action, null, deviceName, deviceId));
               } else {
                  Log.e(TAG, "Unknown state");
                  assert false;
               }
           }
           reader.endArray();
        } catch (UnsupportedEncodingException e) {
            Log.e(TAG, "ResolvedSyncRecords UnsupportedEncodingException error " + e);
        } catch (IOException e) {
            Log.e(TAG, "ResolvedSyncRecords IOException error " + e);
        } catch (IllegalStateException e) {
            Log.e(TAG, "ResolvedSyncRecords IllegalStateException error " + e);
        } catch (IllegalArgumentException exc) {
            Log.e(TAG, "ResolvedSyncRecords generation exception " + exc);
        } finally {
            if (null != reader) {
                try {
                    reader.close();
                } catch (IOException e) {
                }
            }
        }
        if (mResolvedRecordsToApply.size() != 0) {
            int oldSize = 0;
            int newSize = 0;
            boolean applyToDefaultFolder = false;
            List<ResolvedRecordsToApply> resolvedRecordsToApply = new ArrayList<ResolvedRecordsToApply>();
            do {
                oldSize = mResolvedRecordsToApply.size();
                for (ResolvedRecordsToApply resolvedRecord: mResolvedRecordsToApply) {
                    if (BookmarkResolver(resolvedRecord, resolvedRecordsToApply, applyToDefaultFolder)) {
                        continue;
                    }
                }
                mResolvedRecordsToApply = new ArrayList<ResolvedRecordsToApply>(resolvedRecordsToApply);
                resolvedRecordsToApply.clear();
                newSize = mResolvedRecordsToApply.size();
                //Log.i(TAG, "!!!oldSize == " + oldSize + ", newSize == " + newSize);
                if (oldSize == newSize) {
                    if (mLatestRecordTimeStampt.isEmpty()) {
                        // Applying all records to default folder
                        applyToDefaultFolder = true;
                    } else {
                        break;
                    }
                }
            } while (0 != newSize);
        }
        DeviceResolver(devicesRecords);
        if (SyncRecordType.BOOKMARKS.equals(categoryName)) {
          SetExtensiveBookmarkOperation(false);
        }
        if (mLatestRecordTimeStampt.isEmpty()) {
            mTimeLastFetch = mTimeLastFetchExecuted;
            SharedPreferences sharedPref = mContext.getSharedPreferences(PREF_NAME, 0);
            SharedPreferences.Editor editor = sharedPref.edit();
            editor.putLong(PREF_LAST_FETCH_NAME, mTimeLastFetch);
            editor.apply();
            mFetchInProgress = false;
            SendNotSyncedRecords();
            if (SyncRecordType.BOOKMARKS.equals(categoryName)) {
                ReorderBookmarks();
            }
        } else {
            FetchSyncRecords(mLatestRecordTimeStampt);
        }
    }

    private void ReorderBookmarks() {
        if (null == mNewBookmarkModel) {
            return;
        }
        synchronized (mNewBookmarkModel)
        {
            if (!mReorderBookmarks) {
                return;
            }
            List<BookmarkItem> localBookmarks = GetBookmarkItems();
            List<OrderedBookmark> orderedBookmarks = new ArrayList<OrderedBookmark>();
            for (BookmarkItem bookmark : localBookmarks) {
                String order = GetBookmarkOrder(String.valueOf(bookmark.getId().getId()));
                if (order.isEmpty()) {
                    Log.e(TAG, "ReorderBookmarks empty order for " + bookmark.getId().getId());
                    assert false;
                }
                OrderedBookmark orderedBookmark = new OrderedBookmark(bookmark, order);
                orderedBookmarks.add(orderedBookmark);
            }
            Collections.sort(orderedBookmarks);
            ((Activity)mContext).runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    for (OrderedBookmark orderedBookmark : orderedBookmarks) {
                        mNewBookmarkModel.moveBookmark(orderedBookmark.Bookmark().getId(), orderedBookmark.Bookmark().getParentId());
                    }
                }
            });
            mReorderBookmarks = false;
        }
    }

    private void SendNotSyncedRecords() {
        if (mAttepmtsBeforeSendingNotSyncedRecords-- == 0) {
            mAttepmtsBeforeSendingNotSyncedRecords = ATTEMPTS_BEFORE_SENDING_NOT_SYNCED_RECORDS;
            // TODO for other categories, not only for bookmarks
            ProcessNotSyncedRecords(SyncRecordType.BOOKMARKS, CREATE_RECORD);
            ProcessNotSyncedRecords(SyncRecordType.BOOKMARKS, UPDATE_RECORD);
            ProcessNotSyncedRecords(SyncRecordType.BOOKMARKS, DELETE_RECORD);
        }
    }

    private void ProcessNotSyncedRecords(String categoryName, String action) {
        ArrayList<String> ids = SaveGetDeleteNotSyncedRecords(categoryName, action, new ArrayList<String>(), NotSyncedRecordsOperation.GetItems);
        ArrayList<BookmarkItem> items = GetBookmarkItemsByLocalIds(ids, action);
        BookmarkItem[] itemsArray = new BookmarkItem[items.size()];
        itemsArray = items.toArray(itemsArray);
        CreateUpdateDeleteBookmarks(action, itemsArray, false, false);
    }

    private void DeviceResolver(List<ResolvedRecordsToApply> resolvedRecords) {
        //Log.i(TAG, "DeviceResolver: resolvedRecords.size(): " + resolvedRecords.size());
        assert null != resolvedRecords;
        if (0 == resolvedRecords.size()) {
            return;
        }
        String object = nativeGetObjectIdByLocalId(DEVICES_NAMES);

        List<ResolvedRecordsToApply> existingRecords = new ArrayList<ResolvedRecordsToApply>();
        if (!object.isEmpty()) {
          try {
              JSONObject result = new JSONObject(object);
              JSONArray devices = result.getJSONArray("devices");
              for (int i = 0; i < devices.length(); i++) {
                  JSONObject device = devices.getJSONObject(i);
                  String deviceName = device.getString("name");
                  String currentObject = device.getString("objectId");
                  String deviceId = device.getString("deviceId");
                  existingRecords.add(new ResolvedRecordsToApply(currentObject, "0", null, deviceName, deviceId));
              }
          } catch (JSONException e) {
              Log.e(TAG, "DeviceResolver JSONException error " + e);
          } catch (IllegalStateException e) {
              Log.e(TAG, "DeviceResolver IllegalStateException error " + e);
          }
        }

        for (ResolvedRecordsToApply resolvedRecord: resolvedRecords) {
            assert !resolvedRecord.mDeviceName.isEmpty();
            boolean exist = false;
            ResolvedRecordsToApply existingRecordToRemove = null;
            for (ResolvedRecordsToApply existingRecord: existingRecords) {
                if (existingRecord.mObjectId.equals(resolvedRecord.mObjectId)) {
                    if (resolvedRecord.mAction.equals(DELETE_RECORD)) {
                        existingRecordToRemove = existingRecord;
                    } else if (resolvedRecord.mAction.equals(UPDATE_RECORD)) {
                        existingRecord.mDeviceName = resolvedRecord.mDeviceName;
                    }
                    exist = true;
                    break;
                }
            }
            if (null != existingRecordToRemove) {
                if (existingRecordToRemove.mDeviceId.equals(mDeviceId)) {
                    // We deleted current device, so need to reset sync
                    //Log.i(TAG, "DeviceResolver reset sync for " + resolvedRecord.mDeviceName);
                    ResetSync();
                }
                //Log.i(TAG, "DeviceResolver remove from list device " + resolvedRecord.mDeviceName);
                existingRecords.remove(existingRecordToRemove);
            }
            if (!exist && !resolvedRecord.mAction.equals(DELETE_RECORD)) {
                // TODO add to the list
                existingRecords.add(resolvedRecord);
            }
        }
        // TODO add or remove devices in devices list
        JSONObject result = new JSONObject();
        try {
            JSONArray devices = new JSONArray();
            for (ResolvedRecordsToApply existingRecord: existingRecords) {
                JSONObject device = new JSONObject();
                device.put("name", replaceUnsupportedCharacters(existingRecord.mDeviceName));
                device.put("objectId", existingRecord.mObjectId);
                device.put("deviceId", existingRecord.mDeviceId);
                devices.put(device);
            }
            result.put("devices", devices);
        } catch (JSONException e) {
            Log.e(TAG, "DeviceResolver JSONException error " + e);
        }
        nativeSaveObjectId(DEVICES_NAMES, result.toString(), "");
        if (null != mSyncScreensObserver && !mSyncIsReady.mShouldResetSync) {
            mSyncScreensObserver.onDevicesAvailable();
        }
    }

    private boolean BookmarkResolver(ResolvedRecordsToApply resolvedRecord, List<ResolvedRecordsToApply> resolvedRecordsToApply, boolean applyToDefaultFolder) {
        // Return true if we need to skip that folder
        assert null != resolvedRecord && null != resolvedRecordsToApply;
        if (null == resolvedRecord || null == resolvedRecordsToApply) {
            return false;
        }
        String localId = nativeGetLocalIdByObjectId(resolvedRecord.mObjectId);
        if (localId.isEmpty() && resolvedRecord.mAction.equals(DELETE_RECORD)) {
            // Just skip that item as it is not locally and was deleted
            return true;
        }
        String parentLocalId = nativeGetLocalIdByObjectId(resolvedRecord.mBookmarkInternal.mParentFolderObjectId);
        if (!applyToDefaultFolder && parentLocalId.isEmpty() && !resolvedRecord.mBookmarkInternal.mParentFolderObjectId.isEmpty()) {
            resolvedRecordsToApply.add(resolvedRecord);
            return true;
        }
        if (0 != localId.length()) {
            if (resolvedRecord.mAction.equals(UPDATE_RECORD)) {
                EditBookmarkByLocalId(localId, resolvedRecord.mBookmarkInternal.mUrl,
                    (resolvedRecord.mBookmarkInternal.mCustomTitle.isEmpty() ? resolvedRecord.mBookmarkInternal.mTitle : resolvedRecord.mBookmarkInternal.mCustomTitle),
                    parentLocalId, resolvedRecord.mObjectId, resolvedRecord.mBookmarkInternal.mOrder);
            } else if (resolvedRecord.mAction.equals(DELETE_RECORD)) {
                DeleteBookmarkByLocalId(localId);
            } else {
                //assert false;
                // Ignore of adding an existing object
            }
        } else {
            AddBookmark(resolvedRecord.mBookmarkInternal.mUrl,
                (resolvedRecord.mBookmarkInternal.mCustomTitle.isEmpty() ? resolvedRecord.mBookmarkInternal.mTitle : resolvedRecord.mBookmarkInternal.mCustomTitle),
                resolvedRecord.mBookmarkInternal.mIsFolder, resolvedRecord.mObjectId, parentLocalId, resolvedRecord.mBookmarkInternal.mOrder);
        }

        return false;
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

    private StringBuilder GetAction(JsonReader reader) throws IOException {
        if (null == reader) {
            return new StringBuilder(CREATE_RECORD);
        }
        int action = reader.nextInt();
        if (1 == action) {
            return new StringBuilder(UPDATE_RECORD);
        } else if (2 == action) {
            return new StringBuilder(DELETE_RECORD);
        }

        return new StringBuilder(CREATE_RECORD);
    }

    private StringBuilder GetDeviceId(JsonReader reader) throws IOException {
        StringBuilder deviceId = new StringBuilder("");
        if (null == reader) {
            return deviceId;
        }

        if (JsonToken.BEGIN_OBJECT == reader.peek()) {
            reader.beginObject();
            while (reader.hasNext()) {
                reader.nextName();
                if (0 != deviceId.length()) {
                    deviceId.append(", ");
                }
                deviceId.append(reader.nextString());
            }
            reader.endObject();
        } else {
            reader.beginArray();
            while (reader.hasNext()) {
                if (0 != deviceId.length()) {
                    deviceId.append(", ");
                }
                deviceId.append(reader.nextInt());
            }
            reader.endArray();
        }

        return deviceId;
    }

    private StringBuilder GetObjectIdJSON(JsonReader reader) throws IOException {
        StringBuilder objectId = new StringBuilder("");
        if (null == reader) {
            return objectId;
        }

        JsonToken objectType = reader.peek();
        if (JsonToken.BEGIN_OBJECT == reader.peek()) {
            reader.beginObject();
            while (reader.hasNext()) {
                reader.nextName();
                if (0 != objectId.length()) {
                    objectId.append(", ");
                }
                objectId.append(reader.nextInt());
            }
            reader.endObject();
        } else if (JsonToken.BEGIN_ARRAY == reader.peek()) {
            reader.beginArray();
            while (reader.hasNext()) {
                if (0 != objectId.length()) {
                    objectId.append(", ");
                }
                objectId.append(reader.nextInt());
            }
            reader.endArray();
        } else if (JsonToken.NULL == reader.peek()) {
            reader.nextNull();
        } else {
            assert false;
            //objectId = String.valueOf(reader.nextInt());
        }

        return objectId;
    }

    private StringBuilder GetObjectDataJSON(JsonReader reader) throws IOException {
        if (null == reader) {
            return new StringBuilder("");
        }

        return new StringBuilder(reader.nextString());
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
                    } else if (name.equals("customTitle")) {
                        bookmarkInternal.mCustomTitle = reader.nextString();
                    } else if (name.equals("lastAccessedTime")) {
                        bookmarkInternal.mLastAccessedTime = reader.nextLong();
                    } else if (name.equals("creationTime")) {
                        bookmarkInternal.mCreationTime = reader.nextLong();
                    } else if (name.equals("favicon")) {
                        bookmarkInternal.mFavIcon = reader.nextString();
                    } else {
                        assert false;
                        reader.skipValue();
                    }
                }
                reader.endObject();
            } else if (name.equals("isFolder")) {
                if (JsonToken.BOOLEAN == reader.peek()) {
                    bookmarkInternal.mIsFolder = reader.nextBoolean();
                } else {
                    bookmarkInternal.mIsFolder = (reader.nextInt() != 0);
                }
            } else if (name.equals("order")) {
                bookmarkInternal.mOrder = reader.nextString();
            } else if (name.equals("parentFolderObjectId")) {
                bookmarkInternal.mParentFolderObjectId = GetObjectIdJSON(reader).toString();
            } else {
                reader.skipValue();
            }
        }
        reader.endObject();

        return bookmarkInternal;
    }

    private StringBuilder GetDeviceName(JsonReader reader) throws IOException {
        StringBuilder res = new StringBuilder("");

        if (null == reader) {
            return res;
        }

        reader.beginObject();
        while (reader.hasNext()) {
            String name = reader.nextName();
            if (name.equals("name")) {
                res.append(reader.nextString());
            } else {
                reader.skipValue();
            }
        }
        reader.endObject();

        return res;
    }

    class EjectedRunnable implements Runnable {
        private StringBuilder mJsToExecute;

        public EjectedRunnable(StringBuilder jsToExecute) {
            mJsToExecute = jsToExecute;
            mJsToExecute.insert(0, "javascript:(function() { ");
            mJsToExecute.append(" })()");
        }

        @Override
        public void run() {
            if (null == mWebContents || null == mJsToExecute) {
                return;
            }
            mWebContents.getNavigationController().loadUrl(new LoadUrlParams(mJsToExecute.toString()));
        }
    }

    class GetBookmarkItemsByLocalIdsRunnable implements Runnable {
        public ArrayList<BookmarkItem> mBookmarkItems = null;
        private ArrayList<Long> mBookmarkIds;
        private String mAction;

        public GetBookmarkItemsByLocalIdsRunnable(ArrayList<Long> bookmarkIds, String action) {
            mBookmarkIds = bookmarkIds;
            mAction = action;
            mBookmarkItems = new ArrayList<BookmarkItem>();
        }

        @Override
        public void run() {
            if (null != mBookmarkIds) {
                for (Long id: mBookmarkIds) {
                    BookmarkItem bookmarkItem = BookmarkItemByBookmarkId(id);
                    if (null != bookmarkItem) {
                        mBookmarkItems.add(bookmarkItem);
                    } else if (mAction.equals(DELETE_RECORD)) {
                        long defaultFolderId = (null != mDefaultFolder ? mDefaultFolder.getId() : 0);
                        mBookmarkItems.add(BookmarkBridge.createBookmarkItem(id, BookmarkType.NORMAL, "", "", false,
                            defaultFolderId, BookmarkType.NORMAL, true, true));
                    }
                }
            }

            synchronized (this)
            {
                this.notify();
            }
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
            mBookmarkItem = BookmarkItemByBookmarkId(mBookmarkId);

            synchronized (this)
            {
                this.notify();
            }
        }
    }

    private BookmarkItem BookmarkItemByBookmarkId(long lBookmarkId) {
        BookmarkItem bookmarkItem = null;
        BookmarkId bookmarkId = new BookmarkId(lBookmarkId, BookmarkType.NORMAL);
        if (null != mNewBookmarkModel && null != bookmarkId) {
            synchronized (mNewBookmarkModel) {
                if (mNewBookmarkModel.doesBookmarkExist(bookmarkId)) {
                    bookmarkItem = mNewBookmarkModel.getBookmarkById(bookmarkId);
                }
            }
        }

        return bookmarkItem;
    }

    class SetExtensiveBookmarkOperationRunnable implements Runnable {
        private boolean mExtensiveOperation;

        public SetExtensiveBookmarkOperationRunnable(boolean extensiveOperation) {
             mExtensiveOperation = extensiveOperation;
        }

        @Override
        public void run() {
            if (null != mBookmarkModelObserver) {
                if (!mExtensiveOperation) {
                    mBookmarkModelObserver.braveExtensiveBookmarkChangesEnded();
                } else {
                    mBookmarkModelObserver.braveExtensiveBookmarkChangesBeginning();
                }
            }

            synchronized (this)
            {
                this.notify();
            }
        }
    }

    private void SetExtensiveBookmarkOperation(boolean extensiveOperation) {
        SetExtensiveBookmarkOperationRunnable extensiveOperationRunnable = new SetExtensiveBookmarkOperationRunnable(extensiveOperation);
        if (null == extensiveOperationRunnable) {
           return;
        }
        synchronized (extensiveOperationRunnable)
        {
            // Execute code on UI thread
            ((Activity)mContext).runOnUiThread(extensiveOperationRunnable);

            // Wait until runnable is finished
            try {
                extensiveOperationRunnable.wait();
            } catch (InterruptedException e) {
            }
        }
    }

    class GetDefaultFolderIdRunnable implements Runnable {
        public GetDefaultFolderIdRunnable() {
        }

        @Override
        public void run() {
            GetDefaultFolderIdInUIThread();

            synchronized (this)
            {
                this.notify();
            }
        }
    }

    private void GetDefaultFolderId() {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            GetDefaultFolderIdInUIThread();

            return;
        }

        GetDefaultFolderIdRunnable folderIdRunnable = new GetDefaultFolderIdRunnable();
        if (null == folderIdRunnable) {
           return;
        }
        synchronized (folderIdRunnable)
        {
            // Execute code on UI thread
            ((Activity)mContext).runOnUiThread(folderIdRunnable);

            // Wait until runnable is finished
            try {
                folderIdRunnable.wait();
            } catch (InterruptedException e) {
            }
        }
    }

    private void GetDefaultFolderIdInUIThread() {
        if (null == mNewBookmarkModel) {
            mNewBookmarkModel = new BookmarkModel();
        }
        if (null != mNewBookmarkModel) {
            // Partner bookmarks need to be loaded explicitly so that BookmarkModel can be loaded.
            PartnerBookmarksShim.kickOffReading(mContext);
            mNewBookmarkModel.finishLoadingBookmarkModel(new Runnable() {
                @Override
                public void run() {
                  BookmarkId bookmarkId = mNewBookmarkModel.getMobileFolderId();

                  mDefaultFolder = bookmarkId;
                }
            });
        }
    }

    class EditBookmarkRunnable implements Runnable {
        private long mBookmarkId;
        private String mUrl;
        private String mTitle;
        private long mParentLocalId;

        public EditBookmarkRunnable(long bookmarkId, String url, String title, long parentLocalId) {
            mBookmarkId = bookmarkId;
            mUrl = url;
            mTitle = title;
            mParentLocalId = parentLocalId;
        }

        @Override
        public void run() {
            BookmarkId parentBookmarkId = null;
            long defaultFolderId = (null != mDefaultFolder ? mDefaultFolder.getId() : 0);
            if (defaultFolderId != mParentLocalId) {
                parentBookmarkId = new BookmarkId(mParentLocalId, BookmarkType.NORMAL);
            } else {
                parentBookmarkId = mDefaultFolder;
                assert mDefaultFolder != null;
            }
            BookmarkId bookmarkId = new BookmarkId(mBookmarkId, BookmarkType.NORMAL);
            if (null != mNewBookmarkModel && null != bookmarkId) {
                synchronized (mNewBookmarkModel) {
                    if (mNewBookmarkModel.doesBookmarkExist(bookmarkId)) {
                        BookmarkItem bookmarkItem = mNewBookmarkModel.getBookmarkById(bookmarkId);
                        if (null != bookmarkItem) {
                            if (!bookmarkItem.getTitle().equals(mTitle)) {
                                mNewBookmarkModel.setBookmarkTitle(bookmarkId, mTitle);
                            }
                            if (!mUrl.isEmpty() && bookmarkItem.isUrlEditable()) {
                                String fixedUrl = UrlFormatter.fixupUrl(mUrl);
                                if (null != fixedUrl && !fixedUrl.equals(bookmarkItem.getTitle())) {
                                    mNewBookmarkModel.setBookmarkUrl(bookmarkId, fixedUrl);
                                }
                            }
                            if (bookmarkItem.getParentId().getId() != mParentLocalId) {
                                mNewBookmarkModel.moveBookmark(bookmarkId, parentBookmarkId);
                            }
                        }
                    }
                }
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
        private boolean misFolder;
        private long mParentLocalId;

        public AddBookmarkRunnable(String url, String title, boolean isFolder, long parentLocalId) {
            mUrl = url;
            mTitle = title;
            misFolder = isFolder;
            mParentLocalId = parentLocalId;
        }

        @Override
        public void run() {
            BookmarkId parentBookmarkId = null;
            long defaultFolderId = (null != mDefaultFolder ? mDefaultFolder.getId() : 0);
            if (defaultFolderId != mParentLocalId) {
                parentBookmarkId = new BookmarkId(mParentLocalId, BookmarkType.NORMAL);
            } else {
                parentBookmarkId = mDefaultFolder;
            }
            if (null != mNewBookmarkModel) {
                synchronized (mNewBookmarkModel) {
                    if (!misFolder) {
                        mBookmarkId = BookmarkUtils.addBookmarkSilently(mContext, mNewBookmarkModel, mTitle, mUrl, parentBookmarkId);
                    } else {
                        mBookmarkId = mNewBookmarkModel.addFolder(parentBookmarkId, 0, mTitle);
                    }
                }
            }

            synchronized (this)
            {
                this.notify();
            }
        }
    }

    public boolean IsSyncEnabled() {
        boolean prefSyncDefault = false;
        boolean prefSync = mSharedPreferences.getBoolean(
                PREF_SYNC_SWITCH, prefSyncDefault);
        return prefSync;
    }

    public void SetSyncEnabled(boolean syncEnabled) {
        mSharedPreferences.edit().putBoolean(PREF_SYNC_SWITCH, syncEnabled).apply();
    }

    public boolean IsSyncBookmarksEnabled() {
        boolean prefSyncBookmarksDefault = true;
        boolean prefSyncBookmarks = mSharedPreferences.getBoolean(
                PREF_SYNC_BOOKMARKS, prefSyncBookmarksDefault);
        return prefSyncBookmarks;
    }

    public void SetSyncBookmarksEnabled(boolean syncBookmarksEnabled) {
        mSharedPreferences.edit().putBoolean(PREF_SYNC_BOOKMARKS, syncBookmarksEnabled).apply();
    }

    class SyncThread extends Thread {
        @Override
        public void run() {
          SharedPreferences sharedPref = mContext.getSharedPreferences(PREF_NAME, 0);
          mTimeLastFetch = sharedPref.getLong(PREF_LAST_FETCH_NAME, 0);
          mDeviceId = sharedPref.getString(PREF_DEVICE_ID, null);
          mDeviceName = sharedPref.getString(PREF_SYNC_DEVICE_NAME, null);
          mBaseOrder = sharedPref.getString(PREF_BASE_ORDER, null);
          mLastOrder = sharedPref.getString(PREF_LAST_ORDER, null);

          for (;;) {
              try {
                  if (IsSyncEnabled()) {
                      InitSync(false, false);
                      Calendar currentTime = Calendar.getInstance();
                      long timeLastFetch = currentTime.getTimeInMillis();
                      if (!mFetchInProgress || timeLastFetch - mTimeLastFetchExecuted > INTERVAL_TO_REFETCH_RECORDS) {
                          mResolvedRecordsToApply.clear();
                          mFetchInProgress = false;
                          FetchSyncRecords("");
                      }
                  }
                  for (int i = 0; i < BraveSyncWorker.SYNC_SLEEP_ATTEMPTS_COUNT; i++) {
                      if (i == BraveSyncWorker.SYNC_SLEEP_ATTEMPTS_COUNT / 2) {
                          // SZ: preventing from page been frozen, we do that on the middle of the loop
                          ((Activity)mContext).runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    if (null != mWebContents) {
                                        mWebContents.onShow();
                                    }
                                }
                            });
                      }
                      Thread.sleep(BraveSyncWorker.INTERVAL_TO_FETCH_RECORDS / BraveSyncWorker.SYNC_SLEEP_ATTEMPTS_COUNT);
                      if (mInterruptSyncSleep) {
                          break;
                      }
                  }
              }
              catch(Exception exc) {
                  // Just ignore it if we cannot sync
                  Log.e(TAG, "Sync loop exception: " + exc);
              }
              if (mStopThread) {
                  break;
              }
          }
        }
    }

    public void ResetSync() {
        Log.i(TAG, "ResetSync");
        SetSyncEnabled(false);
        mSyncIsReady.Reset();
        mSyncIsReady.mShouldResetSync = true;
        SharedPreferences sharedPref = mContext.getSharedPreferences(PREF_NAME, 0);
        SharedPreferences.Editor editor = sharedPref.edit();
        editor.remove(PREF_LAST_FETCH_NAME);
        editor.remove(PREF_DEVICE_ID);
        editor.remove(PREF_BASE_ORDER);
        editor.remove(PREF_LAST_ORDER);
        editor.remove(PREF_SEED);
        editor.remove(PREF_SYNC_DEVICE_NAME);
        editor.apply();
        final String seed = mSeed;
        mSeed = null;
        mDeviceId = null;
        mDeviceName = null;
        mTimeLastFetch = 0;
        mTimeLastFetchExecuted = 0;
        if (null != mSyncScreensObserver) {
            mSyncScreensObserver.onResetSync();
        }
        new Thread() {
            @Override
            public void run() {
              nativeResetSync(ORIGINAL_SEED_KEY);
              nativeResetSync(SyncRecordType.BOOKMARKS + CREATE_RECORD);
              nativeResetSync(SyncRecordType.BOOKMARKS + UPDATE_RECORD);
              nativeResetSync(SyncRecordType.BOOKMARKS + DELETE_RECORD);
              nativeResetSync(DEVICES_NAMES);
              SaveObjectId(ORIGINAL_SEED_KEY, seed, "", true);
              // TODO for other categories type
            }
        }.start();
    }

    public void InitSync(boolean calledFromUIThread, boolean startNewChain) {
          if (!startNewChain) {
              // Here we already supposed to get existing seed
              SharedPreferences sharedPref = mContext.getSharedPreferences(PREF_NAME, 0);
              if (null == mSeed || mSeed.isEmpty()) {
                  mSeed = sharedPref.getString(PREF_SEED, null);
              }
              if (null == mSeed || mSeed.isEmpty()) {
                  return;
              }
          } else {
              Log.i(TAG, "Start new chain. Current seed: " + mSeed);
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
        public void handleMessage(String message, String arg1, String arg2, String arg3, boolean arg4) {
            /*if (!message.equals("sync-debug")) {
                Log.i(TAG, "!!!message == " + message);
            }*/
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
                /*if (null != arg1) {
                    Log.i(TAG, "!!!sync-debug: " + arg1);
                }*/
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
              case "deleted-sync-user":
                break;
              case "delete-sync-category":
                mSyncIsReady.mDeleteCategoryReady = true;
                break;
              case "delete-sync-site-settings":
                mSyncIsReady.mDeleteSiteSettingsReady = true;
                break;
              case "sync-ready":
                if (mBaseOrder == null || mBaseOrder.isEmpty()) {
                    // Get sync order prefix
                    CallScript(new StringBuilder(String.format("javascript:callbackList['get-bookmarks-base-order'](null, %1$s, 'android')", mDeviceId)));
                } else {
                    mSyncIsReady.mReady = true;
                    FetchSyncRecords("");
                }
                break;
              case "get-existing-objects":
                SendResolveSyncRecords(arg1, GetExistingObjects(arg1, arg2, arg3, arg4));
                break;
              case "get-bookmarks-base-order":
                break;
              case "save-bookmarks-base-order":
                assert arg1 != null;
                // Save base order before sending local bookmarks
                mBaseOrder = arg1;
                SharedPreferences sharedPref = mContext.getSharedPreferences(PREF_NAME, 0);
                SharedPreferences.Editor editor = sharedPref.edit();
                editor.putString(PREF_BASE_ORDER, mBaseOrder);
                editor.apply();
                mSyncIsReady.mReady = true;
                FetchSyncRecords("");
                break;
              case "get-bookmark-order":
                break;
              case "save-bookmark-order":
                Log.i(TAG, "!!!save-bookmark-order1 arg1 == " + arg1 + ", arg2 == " + arg2 + ", arg3 == " + arg3);
                break;
              case "sync-setup-error":
                Log.e(TAG, "sync-setup-error , !!!arg1 == " + arg1 + ", arg2 == " + arg2);
                if (null != mSyncScreensObserver) {
                    mSyncScreensObserver.onSyncError(arg1);
                }
                break;
              default:
                Log.i(TAG, "!!!message == " + message + ", !!!arg1 == " + arg1 + ", arg2 == " + arg2);
                break;
            }
        }
    }

    class JsObjectWordsToBytes {
        @JavascriptInterface
        public void cryptoOutput(String result) {
            if (null == result || 0 == result.length()) {
                if (null != mSyncScreensObserver) {
                    mSyncScreensObserver.onSyncError("Incorrect crypto output");
                }
                return;
            }

            JsonReader reader = null;
            String seed = "";
            try {
                JSONObject data = new JSONObject(result);
                Iterator<?> keys = data.keys();
                while(keys.hasNext()) {
                    String key = (String)keys.next();
                    String value = data.getString(key);
                    if (0 != seed.length()) {
                        seed += ",";
                    }
                    seed += value;
                }
            } catch (JSONException e) {
                Log.e(TAG, "cryptoOutput JSONException error " + e);
                if (null != mSyncScreensObserver) {
                    mSyncScreensObserver.onSyncError("cryptoOutput JSONException error " + e);
                }
            }
            //Log.i(TAG, "!!!seed == " + seed);

            if (null != mSyncScreensObserver) {
                mSyncScreensObserver.onSeedReceived(seed, true, false);
            }
        }

        @JavascriptInterface
        public void cryptoOutputCodeWords(String result) {
            if (null == result || 0 == result.length()) {
                if (null != mSyncScreensObserver) {
                    mSyncScreensObserver.onSyncError("Incorrect crypto output for code words");
                }
                return;
            }

            String[] codeWords = result.replace('\"', ' ').trim().split(" ");

            if (NICEWARE_WORD_COUNT != codeWords.length && BIP39_WORD_COUNT != codeWords.length) {
                Log.e(TAG, "Incorrect number of code words");
                if (null != mSyncScreensObserver) {
                    mSyncScreensObserver.onSyncError("Incorrect number of code words");
                }
                return;
            }

            if (null != mSyncScreensObserver) {
                mSyncScreensObserver.onCodeWordsReceived(codeWords);
            }
        }

        @JavascriptInterface
        public void cryptoOutputError(String error) {
            if (null != mSyncScreensObserver) {
                mSyncScreensObserver.onSyncError(error);
            }
        }
    }

    public void InitJSWebView(BraveSyncScreensObserver syncScreensObserver) {
        try {
            if (null == mJSWebContents) {
                mJSWebContents = WebContentsFactory.createWebContents(false, true);
                if (null != mJSWebContents) {
                    mJSViewEventSink = ViewEventSinkImpl.from(mJSWebContents);
                    if (null != mJSViewEventSink) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
                            initJSContenViewCore();
                        } else {
                            getJSWebContentsInjector().addPossiblyUnsafeInterface(new JsObjectWordsToBytes(), "injectedObject", null);
                        }

                        String toLoad = "<script type='text/javascript'>";
                        try {
                            String script = convertStreamToString(mContext.getAssets().open(CRYPTO_JS));
                            toLoad += script.replace("%", "%25").replace("\n", "%0A") + "</script><script type='text/javascript'>";
                            script = convertStreamToString(mContext.getAssets().open(ANDROID_SYNC_WORDS_JS));
                            toLoad += script.replace("%", "%25").replace("\n", "%0A") + "</script>";
                        } catch (IOException exc) {}
                        LoadUrlParams loadUrlParams = LoadUrlParams.createLoadDataParamsWithBaseUrl(toLoad, "text/html", false, "file:///android_asset/", null);
                        loadUrlParams.setCanLoadLocalResources(true);
                        mJSWebContents.getNavigationController().loadUrl(loadUrlParams);
                    }
                }
            }
        } catch (Exception exc) {
            // Ignoring sync exception, we will try it on next loop execution
            Log.e(TAG, "InitJSWebView exception: " + exc);
        }
        // Always overwrite observer since it's focused on specific activity
        mSyncScreensObserver = syncScreensObserver;
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
    private void initJSContenViewCore() {
        getJSWebContentsInjector().addPossiblyUnsafeInterface(new JsObjectWordsToBytes(), "injectedObject", JavascriptInterface.class);
    }

    public void GetNumber(String[] words) {
        if (null == mJSWebContents) {
            return;
        }
        String wordsJSArray = "";
        for (int i = 0; i < words.length; i++) {
            if (0 == i) {
                wordsJSArray = "'";
            } else {
                wordsJSArray += " ";
            }
            wordsJSArray += words[i];
            if (words.length - 1 == i) {
                wordsJSArray += "'";
            }
        }
        //Log.i(TAG, "!!!words == " + wordsJSArray);
        mJSWebContents.getNavigationController().loadUrl(
                new LoadUrlParams("javascript:(function() { " + String.format("javascript:getBytesFromWords(%1$s)", wordsJSArray) + " })()"));
    }

    private JavascriptInjector getWebContentsInjector() {
        if (mWebContentsInjector == null) {
            mWebContentsInjector = JavascriptInjector.fromWebContents(mWebContents);
        }
        return mWebContentsInjector;
    }

    private JavascriptInjector getJSWebContentsInjector() {
        if (mJSWebContentsInjector == null) {
            mJSWebContentsInjector = JavascriptInjector.fromWebContents(mJSWebContents);
        }
        return mJSWebContentsInjector;
    }

    public void GetCodeWords() {
        if (null == mJSWebContents) {
            Log.e(TAG, "Error on receiving code words. JSWebContents is null.");
            return;
        }
        if (null == mSeed || mSeed.isEmpty()) {
            Log.e(TAG, "Error on receiving code words. Seed is empty.");
            return;
        }

        mJSWebContents.getNavigationController().loadUrl(
                new LoadUrlParams("javascript:(function() { " + String.format("javascript:getCodeWordsFromSeed([%1$s])", mSeed) + " })()"));
    }

    public void InterruptSyncSleep() {
        mInterruptSyncSleep = true;
    }

    private String GetBookmarkOrder(String localId, boolean generateIfEmpty) {
        String currentOrder = GetBookmarkOrder(localId);
        if (!currentOrder.isEmpty() || !generateIfEmpty) {
            return currentOrder;
        }
        if (mLastOrder == null || mLastOrder.isEmpty()) {
            assert mBaseOrder != null;
            // It is the very first element
            mLastOrder = mBaseOrder + "1";
        } else {
            assert !mLastOrder.isEmpty();
            String[] numbers = mLastOrder.split("\\.");
            assert numbers.length > 0;
            int newLastNumber = Integer.parseInt(numbers[numbers.length - 1]) + 1;
            mLastOrder = numbers[0] + ".";
            for (int i = 1; i < numbers.length - 1; i++) {
                mLastOrder += numbers[i] + ".";
            }
            mLastOrder += newLastNumber;
        }
        SharedPreferences sharedPref = mContext.getSharedPreferences(PREF_NAME, 0);
        SharedPreferences.Editor editor = sharedPref.edit();
        editor.putString(PREF_LAST_ORDER, mLastOrder);
        editor.apply();
        return mLastOrder;
    }

    private native String nativeGetObjectIdByLocalId(String localId);
    private native String nativeGetLocalIdByObjectId(String objectId);
    private native void nativeSaveObjectId(String localId, String objectIdJSON, String objectId);
    private native void nativeDeleteByLocalId(String localId);
    private native void nativeClear();
    private native void nativeResetSync(String key);
  }
