/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.chromium.chrome.browser.init;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager.NameNotFoundException;

import java.util.Calendar;
import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.util.Calendar;
import java.util.concurrent.Semaphore;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLException;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;

import org.chromium.base.Log;
import org.chromium.chrome.browser.ConfigAPIs;
import org.chromium.chrome.browser.ChromeVersionInfo;
import org.chromium.chrome.browser.util.DateUtils;
import org.chromium.chrome.browser.util.PackageUtils;

import org.json.JSONObject;

public class StatsUpdater {
    private static final String TAG = "STAT";

    //private static final String URP_CERT = "urp_staging.crt";
    private static final String URP_CERT = "urp.crt";
    private static final long MILLISECONDS_IN_A_DAY = 86400 * 1000;
    private static final long MILLISECONDS_IN_A_WEEK = 7 * MILLISECONDS_IN_A_DAY;
    private static final long MILLISECONDS_IN_A_MONTH = 30 * MILLISECONDS_IN_A_DAY;

    private static final String PREF_NAME = "StatsPreferences";
    private static final String MILLISECONDS_NAME = "Milliseconds";
    private static final String MILLISECONDS_FOR_WEEKLY_STATS_NAME = "MillisecondsForWeeklyStats";
    private static final String MONTH_NAME = "Month";
    private static final String YEAR_NAME = "Year";
    private static final String WEEK_OF_INSTALLATION_NAME = "WeekOfInstallation";
    private static final String PROMO_NAME = "Promo";
    private static final String URPC_NAME = "UserReferalProgramCode";
    private static final String DOWNLOAD_ID_NAME = "DownloadId";

    private static final String SERVER_REQUEST = "https://laptop-updates.brave.com/1/usage/android?daily=%1$s&weekly=%2$s&monthly=%3$s&platform=android&version=%4$s&first=%5$s&channel=stable&woi=%6$s&ref=%7$s";
    private static final String SERVER_REQUEST_URPC_INITIALIZE = "https://laptop-updates.brave.com/promo/initialize/nonua";
    private static final String SERVER_REQUEST_URPC_FINALIZE = "https://laptop-updates.brave.com/promo/activity";
    // Staging values
    //private static final String SERVER_REQUEST_URPC_INITIALIZE = "https://laptop-updates-staging.herokuapp.com/promo/initialize/nonua";
    //private static final String SERVER_REQUEST_URPC_FINALIZE = "https://laptop-updates-staging.herokuapp.com/promo/activity";
    private static final String URPC_PLATFORM = "android";

    private static Semaphore mAvailable = new Semaphore(1);

    public static void UpdateStats(Context context) {
        try {
            mAvailable.acquire();
            try {
                Calendar currentTime = Calendar.getInstance();
                long milliSeconds = currentTime.getTimeInMillis();

                StatsUpdater.UpdateUrpc(context, milliSeconds);

                StatsObject previousObject = StatsUpdater.GetPreferences(context);
                boolean firstRun = (0 == previousObject.mMilliSeconds);
                boolean daily = false;
                boolean weekly = false;
                boolean monthly = false;

                long milliSecondsOfTheCurrentDay = currentTime.get(Calendar.HOUR_OF_DAY) * 60 * 60 * 1000
                      + currentTime.get(Calendar.MINUTE) * 60 * 1000 + currentTime.get(Calendar.SECOND) * 1000
                      + currentTime.get(Calendar.MILLISECOND);
                if (milliSeconds - previousObject.mMilliSeconds >= MILLISECONDS_IN_A_DAY
                      || milliSecondsOfTheCurrentDay < milliSeconds - previousObject.mMilliSeconds) {
                    daily = true;
                }
                if (milliSeconds - previousObject.mMilliSecondsForWeeklyStat >= MILLISECONDS_IN_A_WEEK) {
                    weekly = true;
                }
                if (currentTime.get(Calendar.MONTH) != previousObject.mMonth || currentTime.get(Calendar.YEAR) != previousObject.mYear) {
                    monthly = true;
                }

                if (!firstRun && !daily && !weekly && !monthly) {
                    // We have nothing to update
                    return;
                }

                boolean updated = StatsUpdater.UpdateServer(context, firstRun, daily, weekly, monthly);
                if (updated) {
                    StatsObject currentObject = new StatsObject();
                    if (daily) {
                        currentObject.mMilliSeconds = milliSeconds;
                    } else {
                        currentObject.mMilliSeconds = previousObject.mMilliSeconds;
                    }
                    if (weekly) {
                        currentObject.mMilliSecondsForWeeklyStat = milliSeconds;
                    } else {
                        currentObject.mMilliSecondsForWeeklyStat = previousObject.mMilliSecondsForWeeklyStat;
                    }
                    if (monthly) {
                        currentObject.mMonth = currentTime.get(Calendar.MONTH);
                        currentObject.mYear = currentTime.get(Calendar.YEAR);
                    } else {
                        currentObject.mMonth = previousObject.mMonth;
                        currentObject.mYear = previousObject.mYear;
                    }
                    StatsUpdater.SetPreferences(context, currentObject);
                }
            } finally {
                mAvailable.release();
            }
        } catch (InterruptedException exc) {
        }
    }

    public static boolean UpdateServer(Context context, boolean firstRun, boolean daily, boolean weekly, boolean monthly) {
        String versionNumber = "0";
        PackageInfo info = null;
        try {
            info = context.getPackageManager().getPackageInfo(
                    context.getPackageName(), 0);
        } catch (NameNotFoundException e) {
        }

        if (null != info) {
            versionNumber = info.versionName;
        }
        if (versionNumber.equals("Developer Build")) {
            return false;
        }
        versionNumber = versionNumber.replace(" ", "%20");

        String woi = GetWeekOfInstallation(context);
        String ref = GetRef(context);

        String strQuery = String.format(SERVER_REQUEST, daily, weekly, monthly,
            versionNumber, firstRun, woi, ref);

        try {
            URL url = new URL(strQuery);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            try {
                connection.setRequestMethod("GET");
                connection.connect();
                if (HttpURLConnection.HTTP_OK != connection.getResponseCode()) {
                    Log.e(TAG, "stat update error == " + connection.getResponseCode());

                    return false;
                }

                return true;
            } finally {
                connection.disconnect();
            }
        }
        catch (MalformedURLException e) {
        }
        catch (IOException e) {
        }
        catch (Exception e) {
        }

        return false;
    }

    public static void UpdateUrpc(Context context, long currentTimeInMillis) {
        String urpc = GetUrpc(context);
        if (urpc.isEmpty()) {
            return;
        }
        try {
            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            InputStream caInput = new BufferedInputStream(context.getAssets().open(URP_CERT));
            Certificate ca;
            try {
                ca = cf.generateCertificate(caInput);
            } finally {
                caInput.close();
            }

            // Create a KeyStore containing our trusted CAs
            String keyStoreType = KeyStore.getDefaultType();
            KeyStore keyStore = KeyStore.getInstance(keyStoreType);
            keyStore.load(null, null);
            keyStore.setCertificateEntry("ca", ca);

            // Create a TrustManager that trusts the CAs in our KeyStore
            String tmfAlgorithm = TrustManagerFactory.getDefaultAlgorithm();
            TrustManagerFactory tmf = TrustManagerFactory.getInstance(tmfAlgorithm);
            tmf.init(keyStore);

            // Create an SSLContext that uses our TrustManager
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, tmf.getTrustManagers(), null);

            String downloadId = GetDownloadId(context);
            if (downloadId.isEmpty()){
                // Send request to get download id
                try {
                    URL url = new URL(SERVER_REQUEST_URPC_INITIALIZE);
                    HttpsURLConnection connection = (HttpsURLConnection) url.openConnection();
                    connection.setSSLSocketFactory(sslContext.getSocketFactory());
                    try {
                        // Create and send request JSON
                        JSONObject jsonOut = new JSONObject();
                        jsonOut.put("api_key", ConfigAPIs.URPC_API_KEY);
                        jsonOut.put("referral_code", urpc);
                        jsonOut.put("platform", URPC_PLATFORM);
                        connection.setDoOutput(true);
                        connection.setDoInput(true);
                        connection.setRequestProperty("Content-Type", "application/json");
                        connection.setRequestProperty("Accept", "application/json");
                        connection.setRequestMethod("PUT");
                        OutputStream os = connection.getOutputStream();
                        os.write(jsonOut.toString().getBytes("UTF-8"));
                        os.close();
                        // Read response JSON
                        BufferedReader br = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                        StringBuilder sb = new StringBuilder();
                        String line;
                        while ((line = br.readLine()) != null) {
                            sb.append(line + "\n");
                        }
                        br.close();
                        JSONObject jsonRes = new JSONObject(sb.toString());

                        downloadId = jsonRes.getString("download_id");
                        SetDownloadId(context, downloadId);
                    } finally {
                        connection.disconnect();
                    }
                } catch (MalformedURLException e) {
                    Log.e(TAG, "UpdateUrpc error 1: " + e);
                } catch (IOException e) {
                    Log.e(TAG, "UpdateUrpc error 2: " + e);
                } catch (Exception e) {
                    Log.e(TAG, "UpdateUrpc error 3: " + e);
                }
            }
            if (downloadId.isEmpty()){
                // It should not be empty at this point
                Log.e(TAG, "UpdateUrpc error: download id is empty");
                return;
            }
            PackageInfo info = null;
            try {
                info = context.getPackageManager().getPackageInfo(
                        context.getPackageName(), 0);
            } catch (NameNotFoundException e) {
                // Can't go further since we need first time install
                Log.e(TAG, "UpdateUrpc error on get package info: " + e);
                return;
            }
            if (null == info) {
                // Can't go further since we need first time install
                Log.e(TAG, "UpdateUrpc error: could not get package info");
                return;
            }
            if ((currentTimeInMillis - info.firstInstallTime) > MILLISECONDS_IN_A_MONTH) {
                // We need to inform server about that condition
                try {
                    URL url = new URL(SERVER_REQUEST_URPC_FINALIZE);
                    HttpsURLConnection connection = (HttpsURLConnection) url.openConnection();
                    connection.setSSLSocketFactory(sslContext.getSocketFactory());
                    try {
                        // Create and send request JSON
                        JSONObject jsonOut = new JSONObject();
                        jsonOut.put("download_id", downloadId);
                        jsonOut.put("api_key", ConfigAPIs.URPC_API_KEY);
                        connection.setDoOutput(true);
                        connection.setDoInput(true);
                        connection.setRequestProperty("Content-Type", "application/json");
                        connection.setRequestProperty("Accept", "application/json");
                        connection.setRequestMethod("PUT");
                        OutputStream os = connection.getOutputStream();
                        os.write(jsonOut.toString().getBytes("UTF-8"));
                        os.close();
                        // Read response JSON
                        BufferedReader br = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                        StringBuilder sb = new StringBuilder();
                        String line;
                        while ((line = br.readLine()) != null) {
                            sb.append(line + "\n");
                        }
                        br.close();
                        JSONObject jsonRes = new JSONObject(sb.toString());
                        String isFinalized = jsonRes.getString("finalized");
                        if (isFinalized.equals("true")) {
                            // Clean up values to skip further checking
                            SetUrpc(context, "");
                            SetDownloadId(context, "");
                        } else if (isFinalized.equals("false")) {
                            // We will repeat attempt next time
                            Log.w(TAG, "UpdateUrpc error: could not finalize");
                        } else {
                            // We should not be here
                            Log.e(TAG, "UpdateUrpc error: unknown response on finalize " + isFinalized);
                        }
                    } finally {
                        connection.disconnect();
                    }
                } catch (MalformedURLException e) {
                    Log.e(TAG, "UpdateUrpc error 1: " + e);
                } catch (IOException e) {
                    Log.e(TAG, "UpdateUrpc error 2: " + e);
                } catch (Exception e) {
                    Log.e(TAG, "UpdateUrpc error 3: " + e);
                }
            }
        } catch (CertificateException e) {
            Log.e(TAG, "UpdateUrpc cert validation failed: " + e);
        } catch (IOException e) {
            Log.e(TAG, "UpdateUrpc cert validation failed: " + e);
        } catch (KeyStoreException e) {
            Log.e(TAG, "UpdateUrpc cert validation failed: " + e);
        } catch (NoSuchAlgorithmException e) {
            Log.e(TAG, "UpdateUrpc cert validation failed: " + e);
        } catch (KeyManagementException e) {
            Log.e(TAG, "UpdateUrpc cert validation failed: " + e);
        }
    }

    public static StatsObject GetPreferences(Context context) {
        SharedPreferences sharedPref = context.getSharedPreferences(PREF_NAME, 0);

        StatsObject statsObject = new StatsObject();

        statsObject.mMilliSeconds = sharedPref.getLong(MILLISECONDS_NAME, 0);
        statsObject.mMilliSecondsForWeeklyStat = sharedPref.getLong(MILLISECONDS_FOR_WEEKLY_STATS_NAME, 0);
        statsObject.mMonth = sharedPref.getInt(MONTH_NAME, 0);
        statsObject.mYear = sharedPref.getInt(YEAR_NAME, 0);

        return statsObject;
    }

    public static void SetPreferences(Context context, StatsObject statsObject) {
        SharedPreferences sharedPref = context.getSharedPreferences(PREF_NAME, 0);

        SharedPreferences.Editor editor = sharedPref.edit();

        editor.putLong(MILLISECONDS_NAME, statsObject.mMilliSeconds);
        editor.putLong(MILLISECONDS_FOR_WEEKLY_STATS_NAME, statsObject.mMilliSecondsForWeeklyStat);
        editor.putInt(MONTH_NAME, statsObject.mMonth);
        editor.putInt(YEAR_NAME, statsObject.mYear);

        editor.apply();
    }

    private static String GetWeekOfInstallation(Context context) {
        SharedPreferences sharedPref = context.getSharedPreferences(PREF_NAME, 0);

        String weekOfInstallation = sharedPref.getString(WEEK_OF_INSTALLATION_NAME, null);
        if (weekOfInstallation == null || weekOfInstallation.isEmpty()) {
            SharedPreferences.Editor editor = sharedPref.edit();
            // If this is an update installation, consider week of installation
            // is the first Monday of 2016
            //DateUtils.testGetPreviousMondayDate();
            weekOfInstallation = PackageUtils.isFirstInstall(context) ?
              DateUtils.getPreviousMondayDate(Calendar.getInstance()) : "2016-01-04";
            editor.putString(WEEK_OF_INSTALLATION_NAME, weekOfInstallation);
            editor.apply();
        }

        return weekOfInstallation;
    }

    private static String GetRef(Context context) {
        SharedPreferences sharedPref = context.getSharedPreferences(PREF_NAME, 0);
        String ref = sharedPref.getString(PROMO_NAME, null);
        if (ref == null || ref.isEmpty()) {
            ref = "none";
        }
        return ref;
    }

    private static String GetUrpc(Context context) {
        SharedPreferences sharedPref = context.getSharedPreferences(PREF_NAME, 0);
        String urpc = sharedPref.getString(URPC_NAME, null);
        if (urpc == null) {
            urpc = "";
        }
        return urpc;
    }

    private static void SetUrpc(Context context, String urpc) {
        SharedPreferences sharedPref = context.getSharedPreferences(PREF_NAME, 0);
        SharedPreferences.Editor editor = sharedPref.edit();

        editor.putString(URPC_NAME, urpc);
        editor.apply();
    }

    private static String GetDownloadId(Context context) {
        SharedPreferences sharedPref = context.getSharedPreferences(PREF_NAME, 0);
        String downloadId = sharedPref.getString(DOWNLOAD_ID_NAME, null);
        if (downloadId == null) {
            downloadId = "";
        }
        return downloadId;
    }

    private static void SetDownloadId(Context context, String downloadId) {
        SharedPreferences sharedPref = context.getSharedPreferences(PREF_NAME, 0);
        SharedPreferences.Editor editor = sharedPref.edit();

        editor.putString(DOWNLOAD_ID_NAME, downloadId);
        editor.apply();
    }
}
