// Copyright 2014 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.preferences;

import android.Manifest;
import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.Configuration;
import android.hardware.Camera;
import android.hardware.SensorManager;
import android.os.Build;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.PreferenceFragment;
import android.support.v4.app.ActivityCompat;
import android.text.format.DateUtils;
import android.text.Html;
import android.util.DisplayMetrics;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import org.chromium.base.Log;
import org.chromium.chrome.R;

import org.chromium.chrome.browser.qrreader.BarcodeTracker;
import org.chromium.chrome.browser.qrreader.BarcodeTrackerFactory;
import org.chromium.chrome.browser.qrreader.CameraSource;
import org.chromium.chrome.browser.qrreader.CameraSourcePreview;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.vision.MultiProcessor;
import com.google.android.gms.vision.barcode.Barcode;
import com.google.android.gms.vision.barcode.BarcodeDetector;

import java.io.IOException;
import java.lang.Runnable;

/**
 * Settings fragment that allows to control Sync functionality.
 */
public class BraveSyncScreensPreference extends PreferenceFragment
      implements View.OnClickListener, BarcodeTracker.BarcodeGraphicTrackerCallback{

  private static final String TAG = "SYNC_PREFERENCES";
  private TextView mEmptyView;
  // The new to sync button displayed in the Sync view.
  private Button mNewToSyncButton;
  // The have a sync code button displayed in the Sync view.
  private Button mHaveASyncCodeButton;
  private Button mEnterCodeWordsButton;
  // Brave Sync messaeg text view
  private TextView mBraveSyncTextView;
  private CameraSource mCameraSource;
  private CameraSourcePreview mCameraSourcePreview;
  private ImageView mImageView;

  @Override
  public void onConfigurationChanged(Configuration newConfig) {
      super.onConfigurationChanged(newConfig);

      // Checks the orientation of the screen
      if (newConfig.orientation != Configuration.ORIENTATION_UNDEFINED) {
          if (null != mCameraSourcePreview) {
              mCameraSourcePreview.stop();
          }
          try {
              startCameraSource();
          } catch (SecurityException exc) {
            // TODO
          }
      }
  }

  @Override
  public View onCreateView(
          LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
      int rc = ActivityCompat.checkSelfPermission(getActivity().getApplicationContext(), Manifest.permission.CAMERA);
      if (rc == PackageManager.PERMISSION_GRANTED) {
          createCameraSource(true, false);
      }
      // Read which category we should be showing.
      return inflater.inflate(R.layout.brave_sync_layout, container, false);
  }

  @Override
  public void onActivityCreated(Bundle savedInstanceState) {
      addPreferencesFromResource(R.xml.brave_sync_preferences);
      getActivity().setTitle(R.string.prefs_sync);
      ListView listView = (ListView) getView().findViewById(android.R.id.list);
      mEmptyView = (TextView) getView().findViewById(android.R.id.empty);
      listView.setEmptyView(mEmptyView);
      listView.setDivider(null);

      mNewToSyncButton = (Button) getView().findViewById(R.id.new_to_sync);
      if (mNewToSyncButton != null) {
          mNewToSyncButton.setOnClickListener(this);
      }

      mHaveASyncCodeButton = (Button) getView().findViewById(R.id.have_existing_sync_code);
      if (mHaveASyncCodeButton != null) {
          mHaveASyncCodeButton.setOnClickListener(this);
      }

      mEnterCodeWordsButton = (Button) getView().findViewById(R.id.enter_code_words);
      if (mEnterCodeWordsButton != null) {
          mEnterCodeWordsButton.setOnClickListener(this);
      }
      mBraveSyncTextView = (TextView)getView().findViewById(R.id.brave_sync_text);
      setMainSyncText();
      mImageView = (ImageView)getView().findViewById(R.id.brave_sync_image);
      mCameraSourcePreview = (CameraSourcePreview)getView().findViewById(R.id.preview);

      super.onActivityCreated(savedInstanceState);
  }

  private void setMainSyncText() {
      setSyncText(getResources().getString(R.string.brave_sync), getResources().getString(R.string.brave_sync_message));
  }

  private void setQRCodeText() {
      setSyncText(getResources().getString(R.string.brave_sync_qrcode), getResources().getString(R.string.brave_sync_qrcode_message));
  }

  private void setSyncText(String title, String message) {
      String htmlMessage = "<b>" + title + "</b><br/>" + message.replace("\n", "<br/>");
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
          setTextViewStyle(htmlMessage);
      } else {
          setTextViewStyleOld(htmlMessage);
      }
  }

  @TargetApi(Build.VERSION_CODES.N)
  private void setTextViewStyle(String text) {
      if (null != mBraveSyncTextView) {
          mBraveSyncTextView.setText(Html.fromHtml(text, Html.FROM_HTML_MODE_LEGACY));
      }
  }

  private void setTextViewStyleOld(String text) {
      if (null != mBraveSyncTextView) {
          mBraveSyncTextView.setText(Html.fromHtml(text));
      }
  }

  /** OnClickListener for the clear button. We show an alert dialog to confirm the action */
  @Override
  public void onClick(View v) {
      if (getActivity() == null || v != mNewToSyncButton && v != mHaveASyncCodeButton) return;

      if (mHaveASyncCodeButton == v) {
          if (null != mImageView) {
              mImageView.setVisibility(View.GONE);
          }
          if (null != mNewToSyncButton) {
              mNewToSyncButton.setVisibility(View.GONE);
          }
          if (null != mHaveASyncCodeButton) {
              mHaveASyncCodeButton.setVisibility(View.GONE);
          }
          if (null != mEnterCodeWordsButton) {
              mEnterCodeWordsButton.setVisibility(View.VISIBLE);
          }
          setQRCodeText();
          if (null != mCameraSourcePreview) {
              mCameraSourcePreview.setVisibility(View.VISIBLE);
              int rc = ActivityCompat.checkSelfPermission(getActivity().getApplicationContext(), Manifest.permission.CAMERA);
              if (rc == PackageManager.PERMISSION_GRANTED) {
                  try {
                    startCameraSource();
                  } catch (SecurityException exc) {
                    // TODO
                  }
              } /*else {
                  requestCameraPermission();
              }*/
          }
      } else if (mNewToSyncButton == v) {
          // TODO
      }
  }

  @SuppressLint("InlinedApi")
  private void createCameraSource(boolean autoFocus, boolean useFlash) {
      Context context = getActivity().getApplicationContext();

      // A barcode detector is created to track barcodes.  An associated multi-processor instance
      // is set to receive the barcode detection results, track the barcodes, and maintain
      // graphics for each barcode on screen.  The factory is used by the multi-processor to
      // create a separate tracker instance for each barcode.
      BarcodeDetector barcodeDetector = new BarcodeDetector.Builder(context)
              .setBarcodeFormats(Barcode.ALL_FORMATS)
              .build();
      BarcodeTrackerFactory barcodeFactory = new BarcodeTrackerFactory(this);
      barcodeDetector.setProcessor(new MultiProcessor.Builder<>(barcodeFactory).build());

      if (!barcodeDetector.isOperational()) {
          // Note: The first time that an app using the barcode or face API is installed on a
          // device, GMS will download a native libraries to the device in order to do detection.
          // Usually this completes before the app is run for the first time.  But if that
          // download has not yet completed, then the above call will not detect any barcodes
          // and/or faces.
          //
          // isOperational() can be used to check if the required native libraries are currently
          // available.  The detectors will automatically become operational once the library
          // downloads complete on device.
          Log.w(TAG, "Detector dependencies are not yet available.");

          // Check for low storage.  If there is low storage, the native library will not be
          // downloaded, so detection will not become operational.
          // TODO
          /*IntentFilter lowstorageFilter = new IntentFilter(Intent.ACTION_DEVICE_STORAGE_LOW);
          boolean hasLowStorage = registerReceiver(null, lowstorageFilter) != null;

          if (hasLowStorage) {
              Toast.makeText(this, R.string.low_storage_error,
                      Toast.LENGTH_LONG).show();
              Log.w(TAG, getString(R.string.low_storage_error));
          }*/
      }

      // Creates and starts the camera.  Note that this uses a higher resolution in comparison
      // to other detection examples to enable the barcode detector to detect small barcodes
      // at long distances.
      DisplayMetrics metrics = new DisplayMetrics();
      getActivity().getWindowManager().getDefaultDisplay().getMetrics(metrics);

      CameraSource.Builder builder = new CameraSource.Builder(getActivity().getApplicationContext(), barcodeDetector)
              .setFacing(CameraSource.CAMERA_FACING_BACK)
              .setRequestedPreviewSize(metrics.widthPixels, metrics.heightPixels)
              .setRequestedFps(24.0f);

      // make sure that auto focus is an available option
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
          builder = builder.setFocusMode(
                  autoFocus ? Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE : null);
      }

      mCameraSource = builder
              .setFlashMode(useFlash ? Camera.Parameters.FLASH_MODE_TORCH : null)
              .build();
  }

  private void startCameraSource() throws SecurityException {
      // check that the device has play services available.
      int code = GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(
              getActivity().getApplicationContext());
      if (code != ConnectionResult.SUCCESS) {
          //TODO
          /*Dialog dlg =
                  GoogleApiAvailability.getInstance().getErrorDialog(this, code, RC_HANDLE_GMS);
          dlg.show();*/
      }

      if (mCameraSource != null) {
          try {
              mCameraSourcePreview.start(mCameraSource);
          } catch (IOException e) {
              Log.e(TAG, "Unable to start camera source.", e);
              mCameraSource.release();
              mCameraSource = null;
          }
      }
  }

  @Override
  public void onResume() {
      super.onResume();
      if (null != mCameraSourcePreview && View.GONE != mCameraSourcePreview.getVisibility()) {
          startCameraSource();
      }
  }

  @Override
  public void onPause() {
      super.onPause();
      if (mCameraSourcePreview != null) {
          mCameraSourcePreview.stop();
      }
  }

  @Override
  public void onDestroy() {
      super.onDestroy();
      if (mCameraSourcePreview != null) {
          mCameraSourcePreview.release();
      }
  }

  @Override
  public void onDetectedQrCode(Barcode barcode) {
      if (barcode != null) {
          Log.i(TAG, "!!!code == " + barcode.displayValue);
          getActivity().runOnUiThread(new Runnable() {
              @Override
              public void run() {
                  mCameraSourcePreview.stop();
                  //TODO to switch on a correct screen
                  if (null != mImageView) {
                      mImageView.setVisibility(View.VISIBLE);
                  }
                  if (null != mNewToSyncButton) {
                      mNewToSyncButton.setVisibility(View.VISIBLE);
                  }
                  if (null != mHaveASyncCodeButton) {
                      mHaveASyncCodeButton.setVisibility(View.VISIBLE);
                  }
                  if (null != mEnterCodeWordsButton) {
                      mEnterCodeWordsButton.setVisibility(View.GONE);
                  }
                  if (null != mCameraSourcePreview) {
                      mCameraSourcePreview.setVisibility(View.GONE);
                  }
                  setMainSyncText();
                  //
              }
          });
      }
  }

}
