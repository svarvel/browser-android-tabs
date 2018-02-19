/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.chromium.chrome.browser.preferences;

import android.Manifest;
import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Dialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.Configuration;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.hardware.Camera;
import android.hardware.SensorManager;
import org.chromium.base.task.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.PreferenceFragment;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AlertDialog;
import android.text.format.DateUtils;
import android.text.Html;
import android.util.DisplayMetrics;
import android.view.ContextThemeWrapper;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.WindowManager;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.TableLayout;
import android.widget.LinearLayout;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.WriterException;

import org.chromium.base.Log;
import org.chromium.chrome.R;
import org.chromium.ui.KeyboardVisibilityDelegate;

import org.chromium.base.ContextUtils;
import org.chromium.chrome.browser.ChromeApplication;
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
import java.util.ArrayList;
import java.util.List;

/**
 * Settings fragment that allows to control Sync functionality.
 */
public class BraveSyncScreensPreference extends PreferenceFragment
      implements View.OnClickListener, CompoundButton.OnCheckedChangeListener, BarcodeTracker.BarcodeGraphicTrackerCallback{

  private static final String PREF_NAME = "SyncPreferences";
  private static final String PREF_SYNC_SWITCH = "sync_switch";
  private static final String PREF_SYNC_BOOKMARKS = "brave_sync_bookmarks";
  private static final String PREF_SYNC_TABS = "brave_sync_tabs";
  private static final String PREF_SYNC_HISTORY = "brave_sync_history";
  private static final String PREF_SYNC_AUTOFILL_PASSWORDS = "brave_sync_autofill_passwords";
  private static final String PREF_SYNC_PAYMENT_SETTINGS = "brave_sync_payment_settings";
  private static final String PREF_SEED = "Seed";
  private static final String PREF_SYNC_DEVICE_NAME = "SyncDeviceName";
  private static final String TAG = "SYNC";
  // Permission request codes need to be < 256
  private static final int RC_HANDLE_CAMERA_PERM = 2;
  // Intent request code to handle updating play services if needed.
  private static final int RC_HANDLE_GMS = 9001;
  // For QR code generation
  private static final int WHITE = 0xFFFFFFFF;
  private static final int BLACK = 0xFF000000;
  private static final int WIDTH = 256;

  private ChromeSwitchPreference mPrefSwitch;
  private ChromeSwitchPreference mPrefSwitchBookmarks;
  private ChromeSwitchPreference mPrefSwitchTabs;
  private ChromeSwitchPreference mPrefSwitchHistory;
  private ChromeSwitchPreference mPrefSwitchAutofillPasswords;
  private ChromeSwitchPreference mPrefSwitchPaymentSettings;
  private Switch mSyncSwitchBookmarks;
  private Switch mSyncSwitchTabs;
  private Switch mSyncSwitchHistory;
  private Switch mSyncSwitchAutofillPasswords;
  private Switch mSyncSwitchPaymentSettings;
  // The have a sync code button displayed in the Sync view.
  private Button mScanChainCodeButton;
  private Button mStartNewChainButton;
  private Button mEnterCodeWordsButton;
  private Button mDoneButton;
  private Button mDoneLaptopButton;
  private Button mDisplayCodeWordsButton;
  private ImageButton mMobileButton;
  private ImageButton mLaptopButton;
  private Button mAddDeviceButton;
  private Button mRemoveDeviceButton;
  // Brave Sync messaeg text view
  private TextView mBraveSyncTextViewInitial;
  private TextView mBraveSyncTextViewSyncChainCode;
  private TextView mBraveSyncTextViewAddMobileDevice;
  private TextView mBraveSyncTextViewAddLaptop;
  private TextView mBraveSyncTextDevicesTitle;
  private CameraSource mCameraSource;
  private CameraSourcePreview mCameraSourcePreview;
  private BraveSyncScreensObserver mSyncScreensObserver;
  private String mDeviceName = "";
  private ListView mDevicesListView;
  private ArrayAdapter<String> mDevicesAdapter;
  private List<String> mDevicesList;
  private ScrollView mScrollViewSyncInitial;
  private ScrollView mScrollViewSyncChainCode;
  private ScrollView mScrollViewSyncStartChain;
  private ScrollView mScrollViewAddMobileDevice;
  private ScrollView mScrollViewAddLaptop;
  private ScrollView mScrollViewEnterCodeWords;
  private ScrollView mScrollViewSyncDone;
  private LayoutInflater mInflater;
  private ImageView mQRCodeImage;

  @Override
  public void onConfigurationChanged(Configuration newConfig) {
      super.onConfigurationChanged(newConfig);

      // Checks the orientation of the screen
      if (newConfig.orientation != Configuration.ORIENTATION_UNDEFINED
            && null != mCameraSourcePreview) {
          mCameraSourcePreview.stop();
          try {
              startCameraSource();
          } catch (SecurityException exc) {
          }
      }
  }

  @Override
  public View onCreateView(
          LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
      if (ensureCameraPermission()) {
          createCameraSource(true, false);
      }
      mInflater = inflater;
      // Read which category we should be showing.
      return mInflater.inflate(R.layout.brave_sync_layout, container, false);
  }

  private boolean ensureCameraPermission() {
      if (ActivityCompat.checkSelfPermission(getActivity().getApplicationContext(), Manifest.permission.CAMERA)
              == PackageManager.PERMISSION_GRANTED){
          return true;
      }
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
          requestPermissions(
                  new String[]{Manifest.permission.CAMERA}, RC_HANDLE_CAMERA_PERM);
      }

      return false;
  }

  @Override
   public void onRequestPermissionsResult(int requestCode,
                                          String[] permissions,
                                          int[] grantResults) {
       if (requestCode != RC_HANDLE_CAMERA_PERM) {
           super.onRequestPermissionsResult(requestCode, permissions, grantResults);

           return;
       }

       if (grantResults.length != 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
           // we have permission, so create the camerasource
           createCameraSource(true, false);

           return;
       }

       Log.e(TAG, "Permission not granted: results len = " + grantResults.length +
               " Result code = " + (grantResults.length > 0 ? grantResults[0] : "(empty)"));
       // We still allow to enter words
       //getActivity().onBackPressed();
   }

  @Override
  public void onActivityCreated(Bundle savedInstanceState) {
      addPreferencesFromResource(R.xml.brave_sync_preferences);
      getActivity().setTitle(R.string.sign_in_sync);

      mPrefSwitch = (ChromeSwitchPreference) findPreference(PREF_SYNC_SWITCH);

      mPrefSwitchBookmarks = (ChromeSwitchPreference) findPreference(PREF_SYNC_BOOKMARKS);
      mPrefSwitchTabs = (ChromeSwitchPreference) findPreference(PREF_SYNC_TABS);
      mPrefSwitchHistory = (ChromeSwitchPreference) findPreference(PREF_SYNC_HISTORY);
      mPrefSwitchAutofillPasswords = (ChromeSwitchPreference) findPreference(PREF_SYNC_AUTOFILL_PASSWORDS);
      mPrefSwitchPaymentSettings = (ChromeSwitchPreference) findPreference(PREF_SYNC_PAYMENT_SETTINGS);

      mSyncSwitchBookmarks = (Switch) getView().findViewById(R.id.sync_bookmarks_switch);
      if (null != mSyncSwitchBookmarks) {
          mSyncSwitchBookmarks.setOnCheckedChangeListener(this);
      }
      //mSyncSwitchTabs = (Switch) getView().findViewById(R.id.brave_sync_tabs_switch);
      if (null != mSyncSwitchTabs) {
          mSyncSwitchTabs.setOnCheckedChangeListener(this);
      }
      //mSyncSwitchHistory = (Switch) getView().findViewById(R.id.brave_sync_history_switch);
      if (null != mSyncSwitchHistory) {
          mSyncSwitchHistory.setOnCheckedChangeListener(this);
      }
      //mSyncSwitchAutofillPasswords = (Switch) getView().findViewById(R.id.brave_sync_autofill_passwords_switch);
      if (null != mSyncSwitchAutofillPasswords) {
          mSyncSwitchAutofillPasswords.setOnCheckedChangeListener(this);
      }
      //mSyncSwitchPaymentSettings = (Switch) getView().findViewById(R.id.brave_sync_payment_settings_switch);
      if (null != mSyncSwitchPaymentSettings) {
          mSyncSwitchPaymentSettings.setOnCheckedChangeListener(this);
      }

      mScrollViewSyncInitial = (ScrollView) getView().findViewById(R.id.view_sync_initial);
      mScrollViewSyncChainCode = (ScrollView) getView().findViewById(R.id.view_sync_chain_code);
      mScrollViewSyncStartChain = (ScrollView) getView().findViewById(R.id.view_sync_start_chain);
      mScrollViewAddMobileDevice = (ScrollView) getView().findViewById(R.id.view_add_mobile_device);
      mScrollViewAddLaptop = (ScrollView) getView().findViewById(R.id.view_add_laptop);
      mScrollViewEnterCodeWords = (ScrollView) getView().findViewById(R.id.view_enter_code_words);
      mScrollViewSyncDone = (ScrollView) getView().findViewById(R.id.view_sync_done);

      mScanChainCodeButton = (Button) getView().findViewById(R.id.brave_sync_btn_scan_chain_code);
      if (mScanChainCodeButton != null) {
          mScanChainCodeButton.setOnClickListener(this);
      }

      mStartNewChainButton = (Button) getView().findViewById(R.id.brave_sync_btn_start_new_chain);
      if (mStartNewChainButton != null) {
          mStartNewChainButton.setOnClickListener(this);
      }

      mEnterCodeWordsButton = (Button) getView().findViewById(R.id.brave_sync_btn_enter_code_words);
      if (mEnterCodeWordsButton != null) {
          mEnterCodeWordsButton.setOnClickListener(this);
      }

      mQRCodeImage = (ImageView) getView().findViewById(R.id.brave_sync_qr_code_image);

      mDoneButton = (Button) getView().findViewById(R.id.brave_sync_btn_done);
      if (mDoneButton != null) {
          mDoneButton.setOnClickListener(this);
      }

      mDoneLaptopButton = (Button) getView().findViewById(R.id.brave_sync_btn_add_laptop_done);
      if (mDoneLaptopButton != null) {
          mDoneLaptopButton.setOnClickListener(this);
      }

      mDisplayCodeWordsButton = (Button) getView().findViewById(R.id.brave_sync_btn_display_code_words);
      if (mDisplayCodeWordsButton != null) {
          mDisplayCodeWordsButton.setOnClickListener(this);
      }

      mMobileButton = (ImageButton) getView().findViewById(R.id.brave_sync_btn_mobile);
      if (mMobileButton != null) {
          mMobileButton.setOnClickListener(this);
      }

      mLaptopButton = (ImageButton) getView().findViewById(R.id.brave_sync_btn_laptop);
      if (mLaptopButton != null) {
          mLaptopButton.setOnClickListener(this);
      }

      mBraveSyncTextViewInitial = (TextView) getView().findViewById(R.id.brave_sync_text_initial);
      mBraveSyncTextViewSyncChainCode = (TextView) getView().findViewById(R.id.brave_sync_text_sync_chain_code);
      mBraveSyncTextViewAddMobileDevice = (TextView) getView().findViewById(R.id.brave_sync_text_add_mobile_device);
      mBraveSyncTextViewAddLaptop = (TextView) getView().findViewById(R.id.brave_sync_text_add_laptop);
      mBraveSyncTextDevicesTitle = (TextView) getView().findViewById(R.id.brave_sync_devices_title);
      setMainSyncText();
      mCameraSourcePreview = (CameraSourcePreview) getView().findViewById(R.id.preview);

      mAddDeviceButton = (Button) getView().findViewById(R.id.brave_sync_btn_add_device);
      if (null != mAddDeviceButton) {
          mAddDeviceButton.setOnClickListener(this);
      }

      mRemoveDeviceButton = (Button) getView().findViewById(R.id.brave_sync_btn_remove_device);
      if (null != mRemoveDeviceButton) {
          mRemoveDeviceButton.setOnClickListener(this);
      }

      setAppropriateView();
      getActivity().getWindow().setSoftInputMode(
                WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN);

      super.onActivityCreated(savedInstanceState);
      // Initialize mSyncScreensObserver
      ChromeApplication application = (ChromeApplication)ContextUtils.getApplicationContext();
      if (null != application && null != application.mBraveSyncWorker) {
          if (null == mSyncScreensObserver) {
              mSyncScreensObserver = new BraveSyncScreensObserver() {
                  public void onSyncError() {
                      showEndDialog(getResources().getString(R.string.sync_device_failure));
                  }

                  public void onSeedReceived(String seed, boolean fromCodeWords, boolean afterInitialization) {
                      if (fromCodeWords) {
                          assert !afterInitialization;
                          if (!isBarCodeValid(seed, false)) {
                              showEndDialog(getResources().getString(R.string.sync_device_failure));
                          }
                          Log.i(TAG, "!!!received seed == " + seed);
                          // Save seed and deviceId in preferences
                          SharedPreferences sharedPref = getActivity().getApplicationContext().getSharedPreferences(PREF_NAME, 0);
                          SharedPreferences.Editor editor = sharedPref.edit();
                          editor.putString(PREF_SEED, seed);
                          editor.apply();
                          getActivity().runOnUiThread(new Runnable() {
                              @Override
                              public void run() {
                                  if (null != mPrefSwitch) {
                                      mPrefSwitch.setChecked(true);
                                  }
                                  ChromeApplication application = (ChromeApplication)ContextUtils.getApplicationContext();
                                  if (null != application && null != application.mBraveSyncWorker) {
                                      showEndDialog(getResources().getString(R.string.sync_device_success));
                                      application.mBraveSyncWorker.InitSync(true, false);
                                  }
                                  setAppropriateView();
                              }
                          });
                      } else if (afterInitialization) {
                          assert !fromCodeWords;
                          Log.i(TAG, "!!!init received seed == " + seed);
                          if (null != seed && !seed.isEmpty()) {
                              if (View.VISIBLE == mScrollViewAddMobileDevice.getVisibility()) {
                                  Log.i(TAG, "Generate QR with seed: " + seed);
                                  new Thread(new Runnable() {
                                      @Override
                                      public void run() {
                                          // Generate QR code
                                          BitMatrix result;
                                          try {
                                              result = new MultiFormatWriter().encode(seed, BarcodeFormat.QR_CODE, WIDTH, WIDTH, null);
                                          } catch (WriterException e) {
                                              Log.e(TAG, "QR code unsupported format: " + e);
                                              return;
                                          }
                                          int w = result.getWidth();
                                          int h = result.getHeight();
                                          int[] pixels = new int[w * h];
                                          for (int y = 0; y < h; y++) {
                                              int offset = y * w;
                                              for (int x = 0; x < w; x++) {
                                                  pixels[offset + x] = result.get(x, y) ? BLACK : WHITE;
                                              }
                                          }
                                          Bitmap bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
                                          bitmap.setPixels(pixels, 0, WIDTH, 0, 0, w, h);
                                          getActivity().runOnUiThread(new Runnable() {
                                              @Override
                                              public void run() {
                                                  mQRCodeImage.setImageBitmap(bitmap);
                                                  mQRCodeImage.invalidate();
                                              }
                                          });
                                      }
                                  }).start();
                              } else if (View.VISIBLE == mScrollViewAddLaptop.getVisibility()) {
                                  getActivity().runOnUiThread(new Runnable() {
                                      @Override
                                      public void run() {
                                          application.mBraveSyncWorker.GetCodeWords();
                                      }
                                  });
                              }
                          }
                      } else {
                          Log.e(TAG, "Unknown flag on receiving seed");
                          assert false;
                      }
                  }

                  public void onCodeWordsReceived(String[] codeWords) {
                      getActivity().runOnUiThread(new Runnable() {
                          @Override
                          public void run() {
                              Log.i(TAG, "onCodeWordsReceived:");
                              for (int i = 0; i < codeWords.length; i++) {
                                  Log.i(TAG, codeWords[i].trim());
                                  EditText wordControl = getWordControl(i + 1, false);
                                  if (null != wordControl) {
                                      wordControl.setText(codeWords[i].trim());
                                      wordControl.invalidate();
                                  } else {
                                      Log.e(TAG, "wordControl is null");
                                  }
                              }
                          }
                      });
                  }

                  public void onDevicesAvailable() {
                      synchronized (this) {
                          // Load other devices in chain
                          ChromeApplication application = (ChromeApplication)ContextUtils.getApplicationContext();
                          if (null != application && null != application.mBraveSyncWorker) {
                              ViewGroup insertPoint = (ViewGroup) getView().findViewById(R.id.brave_sync_devices);
                              insertPoint.removeAllViews();
                              new Thread(new Runnable() {
                                  @Override
                                  public void run() {
                                      ArrayList<String> devices = application.mBraveSyncWorker.GetAllDevices();
                                      getActivity().runOnUiThread(new Runnable() {
                                          @Override
                                          public void run() {
                                              int index = 0;
                                              for (String device : devices) {
                                                  View separator = (View) mInflater.inflate(R.layout.menu_separator, null);
                                                  View listItemView = (View) mInflater.inflate(R.layout.brave_sync_device, null);
                                                  if (null != listItemView && null != separator && null != insertPoint) {
                                                      TextView textView = (TextView) listItemView.findViewById(R.id.brave_sync_device_text);
                                                      if (null != textView) {
                                                          textView.setText(device);
                                                      }
                                                      insertPoint.addView(separator, index++);
                                                      insertPoint.addView(listItemView, index++);
                                                  }
                                              }
                                              if (index > 0) {
                                                  View separator = (View) mInflater.inflate(R.layout.menu_separator, null);
                                                  if (null != insertPoint && null != separator) {
                                                      insertPoint.addView(separator, index++);
                                                  }
                                              } else {
                                                  mBraveSyncTextDevicesTitle.setVisibility(View.GONE);
                                              }
                                          }
                                      });
                                  }
                              }).start();
                          }
                      }
                  }
              };
          }
          application.mBraveSyncWorker.InitJSWebView(mSyncScreensObserver);
      }
  }

  private void setAppropriateView() {
      getActivity().setTitle(R.string.prefs_sync);
      SharedPreferences sharedPref = getActivity().getApplicationContext().getSharedPreferences(PREF_NAME, 0);
      String seed = sharedPref.getString(PREF_SEED, null);
      Log.i(TAG, "setAppropriateView: seed == " + seed);
      if (null == seed || seed.isEmpty()) {
          if (null != mCameraSourcePreview) {
              mCameraSourcePreview.stop();
          }
          if (null != mScrollViewSyncInitial) {
              mScrollViewSyncInitial.setVisibility(View.VISIBLE);
          }
          if (null != mScrollViewSyncChainCode) {
              mScrollViewSyncChainCode.setVisibility(View.GONE);
          }
          if (null != mScrollViewEnterCodeWords) {
              mScrollViewEnterCodeWords.setVisibility(View.GONE);
          }
          if (null != mScrollViewAddMobileDevice) {
              mScrollViewAddMobileDevice.setVisibility(View.GONE);
          }
          if (null != mScrollViewAddLaptop) {
              mScrollViewAddLaptop.setVisibility(View.GONE);
          }
          if (null != mScrollViewSyncStartChain) {
              mScrollViewSyncStartChain.setVisibility(View.GONE);
          }
          if (null != mScrollViewSyncDone) {
              mScrollViewSyncDone.setVisibility(View.GONE);
          }
          return;
      }
      setSyncDoneLayout();
  }

  private void setMainSyncText() {
      setSyncText(getResources().getString(R.string.brave_sync), getResources().getString(R.string.brave_sync_description_page_1_part_1) + "\n" +
                    getResources().getString(R.string.brave_sync_description_page_1_part_2), mBraveSyncTextViewInitial);
  }

  private void setQRCodeText() {
      setSyncText("", getResources().getString(R.string.brave_sync_qrcode_message_v2), mBraveSyncTextViewSyncChainCode);
  }

  private void setSyncText(String title, String message, TextView textView) {
      String htmlMessage = "";
      if (title.length() > 0) {
         htmlMessage = "<b>" + title + "</b><br/><br/>";
      }
      htmlMessage += message.replace("\n", "<br/>");
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
          setTextViewStyle(htmlMessage, textView);
      } else {
          setTextViewStyleOld(htmlMessage, textView);
      }
  }

  @TargetApi(Build.VERSION_CODES.N)
  private void setTextViewStyle(String text, TextView textView) {
      if (null != textView) {
          textView.setText(Html.fromHtml(text, Html.FROM_HTML_MODE_LEGACY));
      }
  }

  private void setTextViewStyleOld(String text, TextView textView) {
      if (null != textView) {
          textView.setText(Html.fromHtml(text));
      }
  }

  /** OnClickListener for the clear button. We show an alert dialog to confirm the action */
  @Override
  public void onClick(View v) {
      if (getActivity() == null || v != mScanChainCodeButton && v != mStartNewChainButton
          && v != mEnterCodeWordsButton && v != mDoneButton && v != mDoneLaptopButton && v != mDisplayCodeWordsButton
          && v != mMobileButton && v != mLaptopButton && v != mRemoveDeviceButton && v != mAddDeviceButton) return;

      if (mScanChainCodeButton == v) {
          showAddDeviceNameDialog();
      } else if (mStartNewChainButton == v) {
          setNewChainLayout();
      } else if (mMobileButton == v) {
          setAddMobileDeviceLayout();
      } else if (mLaptopButton == v) {
          setAddLaptopLayout();
      } else if (mDoneButton == v) {
          setSyncDoneLayout();
      } else if (mDoneLaptopButton == v) {
          setSyncDoneLayout();
      } else if (mDisplayCodeWordsButton == v) {
          setAddLaptopLayout();
      } else if (mEnterCodeWordsButton == v) {
          if (null != mScrollViewSyncInitial) {
              mScrollViewSyncInitial.setVisibility(View.GONE);
          }
          if (null != mScrollViewAddMobileDevice) {
              mScrollViewAddMobileDevice.setVisibility(View.GONE);
          }
          if (null != mScrollViewAddLaptop) {
              mScrollViewAddLaptop.setVisibility(View.GONE);
          }
          if (null != mScrollViewSyncStartChain) {
              mScrollViewSyncStartChain.setVisibility(View.GONE);
          }
          if (null != mCameraSourcePreview) {
              mCameraSourcePreview.stop();
          }
          if (null != mScrollViewSyncChainCode) {
              mScrollViewSyncChainCode.setVisibility(View.GONE);
          }
          if (null != mScrollViewEnterCodeWords) {
              mScrollViewEnterCodeWords.setVisibility(View.VISIBLE);
          }
          getActivity().setTitle(R.string.brave_sync_code_words_title);
          EditText wordControl = getWordControl(1, true);
          if (null != wordControl) {
              wordControl.requestFocus();
              InputMethodManager imm = (InputMethodManager) getActivity().getSystemService(Context.INPUT_METHOD_SERVICE);
              imm.showSoftInput(wordControl, InputMethodManager.SHOW_FORCED);
          }
          EditText wordLastControl = getWordControl(16, true);
          if (null != wordLastControl) {
              wordLastControl.setOnEditorActionListener(new EditText.OnEditorActionListener() {
                  @Override
                  public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                      if ((event != null && (event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) || (actionId == EditorInfo.IME_ACTION_DONE)) {
                          ChromeApplication application = (ChromeApplication)ContextUtils.getApplicationContext();
                          String[] words = new String[16];
                          if (null != application && null != application.mBraveSyncWorker && null != words) {
                              for (int i = 1; i < 17; i++) {
                                  EditText wordControl = getWordControl(i, true);
                                  if (null == wordControl) {
                                      break;
                                  }
                                  words[i - 1] = wordControl.getText().toString();
                              }
                              application.mBraveSyncWorker.GetNumber(words);
                          }
                      }
                      return false;
                  }
              });
          }
      } else if (mRemoveDeviceButton == v) {
          Log.i(TAG, "mRemoveDeviceButton clicked");
          //ResetSyncDialog();
      } else if (mAddDeviceButton == v) {
          setNewChainLayout();
      }
  }

  @Override
  public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
      if (getActivity() == null || buttonView != mSyncSwitchBookmarks && buttonView != mSyncSwitchTabs &&
            buttonView != mSyncSwitchHistory && buttonView != mSyncSwitchAutofillPasswords && buttonView != mSyncSwitchPaymentSettings) {
          Log.w(TAG, "Unknown button");
          return;
      }
      if (buttonView == mSyncSwitchBookmarks && null != mPrefSwitchBookmarks) {
          mPrefSwitchBookmarks.setChecked(isChecked);
      } else if (buttonView == mSyncSwitchTabs && null != mPrefSwitchTabs) {
          mPrefSwitchTabs.setChecked(isChecked);
      } else if (buttonView == mSyncSwitchHistory && null != mPrefSwitchHistory) {
          mPrefSwitchHistory.setChecked(isChecked);
      } else if (buttonView == mSyncSwitchAutofillPasswords && null != mPrefSwitchAutofillPasswords) {
          mPrefSwitchAutofillPasswords.setChecked(isChecked);
      } else if (buttonView == mSyncSwitchPaymentSettings && null != mPrefSwitchPaymentSettings) {
          mPrefSwitchPaymentSettings.setChecked(isChecked);
      }
  }

  private void showMainSyncScrypt() {
      if (null != mScrollViewSyncInitial) {
          mScrollViewSyncInitial.setVisibility(View.VISIBLE);
      }
      if (null != mScrollViewAddMobileDevice) {
          mScrollViewAddMobileDevice.setVisibility(View.GONE);
      }
      if (null != mScrollViewAddLaptop) {
          mScrollViewAddLaptop.setVisibility(View.GONE);
      }
      if (null != mScrollViewSyncStartChain) {
          mScrollViewSyncStartChain.setVisibility(View.GONE);
      }
      if (null != mScrollViewSyncChainCode) {
          mScrollViewSyncChainCode.setVisibility(View.GONE);
      }
      if (null != mScrollViewEnterCodeWords) {
          mScrollViewEnterCodeWords.setVisibility(View.GONE);
      }
      setMainSyncText();
  }

  // Handles the requesting of the camera permission.
  private void requestCameraPermission() {
      Log.w(TAG, "Camera permission is not granted. Requesting permission");

      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
          final String[] permissions = new String[]{Manifest.permission.CAMERA};

          requestPermissions(permissions, RC_HANDLE_CAMERA_PERM);
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
          // download has not yet completed, then the above call will not detect any barcodes.
          //
          // isOperational() can be used to check if the required native libraries are currently
          // available.  The detectors will automatically become operational once the library
          // downloads complete on device.
          Log.i(TAG, "Detector dependencies are not yet available.");
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
      builder = builder.setFocusMode(
              autoFocus ? Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE : null);

      mCameraSource = builder
              .setFlashMode(useFlash ? Camera.Parameters.FLASH_MODE_TORCH : null)
              .build();
  }

  private void startCameraSource() throws SecurityException {
      if (mCameraSource != null && mCameraSourcePreview.mCameraExist) {
          // check that the device has play services available.
          try {
              int code = GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(
                      getActivity().getApplicationContext());
              if (code != ConnectionResult.SUCCESS) {
                  Dialog dlg =
                          GoogleApiAvailability.getInstance().getErrorDialog(getActivity(), code, RC_HANDLE_GMS);
                  if (null != dlg) {
                      dlg.show();
                  }
              }
          } catch (ActivityNotFoundException e) {
              Log.e(TAG, "Unable to start camera source.", e);
              mCameraSource.release();
              mCameraSource = null;

              return;
          }
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
      try {
          if (null != mCameraSourcePreview && View.GONE != mScrollViewSyncChainCode.getVisibility()) {
              startCameraSource();
          }
      } catch (SecurityException se) {
          Log.e(TAG,"Do not have permission to start the camera", se);
      } catch (RuntimeException e) {
          Log.e(TAG, "Could not start camera source.", e);
      }
  }

  @Override
  public void onPause() {
      super.onPause();
      if (mCameraSourcePreview != null) {
          mCameraSourcePreview.stop();
      }
      InputMethodManager imm = (InputMethodManager) getActivity().getSystemService(Context.INPUT_METHOD_SERVICE);
      imm.hideSoftInputFromWindow(getView().getWindowToken(), 0);
  }

  @Override
  public void onDestroy() {
      super.onDestroy();
      if (mCameraSourcePreview != null) {
          mCameraSourcePreview.release();
      }
  }

  private boolean isBarCodeValid(String barcode, boolean hexValue) {
      if (hexValue && barcode.length() != 64) {
          return false;
      } else if (!hexValue) {
          String[] split = barcode.split(", ");
          if (split.length != 32) {
              return false;
          }
      }

      return true;
  }

  @Override
  public void onDetectedQrCode(Barcode barcode) {
      if (barcode != null) {
          //Log.i(TAG, "!!!code == " + barcode.displayValue);
          final String barcodeValue = barcode.displayValue;
          if (!isBarCodeValid(barcodeValue, true)) {
              showEndDialog(getResources().getString(R.string.sync_device_failure));
              showMainSyncScrypt();

              return;
          }
          String[] barcodeString = barcodeValue.replaceAll("..(?!$)", "$0 ").split(" ");
          String seed = "";
          for (int i = 0; i < barcodeString.length; i++) {
              if (0 != seed.length()) {
                  seed += ", ";
              }
              seed += String.valueOf(Integer.parseInt(barcodeString[i], 16));
          }
          Log.i(TAG, "!!!seed == " + seed);
          // Save seed and deviceId in preferences
          SharedPreferences sharedPref = getActivity().getApplicationContext().getSharedPreferences(PREF_NAME, 0);
          SharedPreferences.Editor editor = sharedPref.edit();
          editor.putString(PREF_SEED, seed);
          editor.apply();
          getActivity().runOnUiThread(new Runnable() {
              @Override
              public void run() {
                  if (null != mPrefSwitch) {
                      mPrefSwitch.setChecked(true);
                  }
                  ChromeApplication application = (ChromeApplication)ContextUtils.getApplicationContext();
                  if (null != application && null != application.mBraveSyncWorker) {
                      application.mBraveSyncWorker.InitSync(true, false);
                  }
                  setAppropriateView();
              }
          });
      }
  }

  private EditText getWordControl(int number, boolean editMode) {
      EditText control = null;
      switch (number) {
        case 1:
          control = editMode ? (EditText)getView().findViewById(R.id.editTextWord1) : (EditText)getView().findViewById(R.id.textWord1);
          break;
        case 2:
          control = editMode ? (EditText)getView().findViewById(R.id.editTextWord2) : (EditText)getView().findViewById(R.id.textWord2);
          break;
        case 3:
          control = editMode ? (EditText)getView().findViewById(R.id.editTextWord3) : (EditText)getView().findViewById(R.id.textWord3);
          break;
        case 4:
          control = editMode ? (EditText)getView().findViewById(R.id.editTextWord4) : (EditText)getView().findViewById(R.id.textWord4);
          break;
        case 5:
          control = editMode ? (EditText)getView().findViewById(R.id.editTextWord5) : (EditText)getView().findViewById(R.id.textWord5);
          break;
        case 6:
          control = editMode ? (EditText)getView().findViewById(R.id.editTextWord6) : (EditText)getView().findViewById(R.id.textWord6);
          break;
        case 7:
          control = editMode ? (EditText)getView().findViewById(R.id.editTextWord7) : (EditText)getView().findViewById(R.id.textWord7);
          break;
        case 8:
          control = editMode ? (EditText)getView().findViewById(R.id.editTextWord8) : (EditText)getView().findViewById(R.id.textWord8);
          break;
        case 9:
          control = editMode ? (EditText)getView().findViewById(R.id.editTextWord9) : (EditText)getView().findViewById(R.id.textWord9);
          break;
        case 10:
          control = editMode ? (EditText)getView().findViewById(R.id.editTextWord10) : (EditText)getView().findViewById(R.id.textWord10);
          break;
        case 11:
          control = editMode ? (EditText)getView().findViewById(R.id.editTextWord11) : (EditText)getView().findViewById(R.id.textWord11);
          break;
        case 12:
          control = editMode ? (EditText)getView().findViewById(R.id.editTextWord12) : (EditText)getView().findViewById(R.id.textWord12);
          break;
        case 13:
          control = editMode ? (EditText)getView().findViewById(R.id.editTextWord13) : (EditText)getView().findViewById(R.id.textWord13);
          break;
        case 14:
          control = editMode ? (EditText)getView().findViewById(R.id.editTextWord14) : (EditText)getView().findViewById(R.id.textWord14);
          break;
        case 15:
          control = editMode ? (EditText)getView().findViewById(R.id.editTextWord15) : (EditText)getView().findViewById(R.id.textWord15);
          break;
        case 16:
          control = editMode ? (EditText)getView().findViewById(R.id.editTextWord16) : (EditText)getView().findViewById(R.id.textWord16);
          break;
        default:
          Log.e(TAG, "Code words out of scope");
          control = null;
      }

      return control;
  }

  private void showEndDialog(String message) {
      AlertDialog.Builder alert = new AlertDialog.Builder(getActivity(), R.style.AlertDialogTheme);
      if (null == alert) {
          return;
      }
      DialogInterface.OnClickListener onClickListener = new DialogInterface.OnClickListener() {
          @Override
          public void onClick(DialogInterface dialog, int button) {
          }
      };
      AlertDialog alertDialog = alert
              .setTitle(getResources().getString(R.string.sync_device))
              .setMessage(message)
              .setPositiveButton(R.string.ok, onClickListener)
              .create();
      alertDialog.getDelegate().setHandleNativeActionModesEnabled(false);
      alertDialog.show();
  }

  private void showAddDeviceNameDialog() {
      LayoutInflater inflater = (LayoutInflater) getActivity().getSystemService(
              Context.LAYOUT_INFLATER_SERVICE);
      View view = inflater.inflate(R.layout.add_sync_device_name_dialog, null);
      final EditText input = (EditText) view.findViewById(R.id.device_name);

      DialogInterface.OnClickListener onClickListener = new DialogInterface.OnClickListener() {
          @Override
          public void onClick(DialogInterface dialog, int button) {
              if (button == AlertDialog.BUTTON_POSITIVE) {
                  mDeviceName = input.getText().toString();
                  if (mDeviceName.isEmpty()) {
                      mDeviceName = input.getHint().toString();
                  }
                  SharedPreferences sharedPref = getActivity().getApplicationContext().getSharedPreferences(PREF_NAME, 0);
                  SharedPreferences.Editor editor = sharedPref.edit();
                  editor.putString(PREF_SYNC_DEVICE_NAME, mDeviceName);
                  editor.apply();
                  if (null != mScrollViewSyncInitial) {
                      mScrollViewSyncInitial.setVisibility(View.GONE);
                  }
                  if (null != mScrollViewEnterCodeWords) {
                      mScrollViewEnterCodeWords.setVisibility(View.GONE);
                  }
                  if (null != mScrollViewAddMobileDevice) {
                      mScrollViewAddMobileDevice.setVisibility(View.GONE);
                  }
                  if (null != mScrollViewAddLaptop) {
                      mScrollViewAddLaptop.setVisibility(View.GONE);
                  }
                  if (null != mScrollViewSyncStartChain) {
                      mScrollViewSyncStartChain.setVisibility(View.GONE);
                  }
                  setQRCodeText();
                  getActivity().setTitle(R.string.brave_sync_scan_chain_code);
                  if (null != mScrollViewSyncChainCode) {
                      mScrollViewSyncChainCode.setVisibility(View.VISIBLE);
                  }
                  if (null != mCameraSourcePreview) {
                      int rc = ActivityCompat.checkSelfPermission(getActivity().getApplicationContext(), Manifest.permission.CAMERA);
                      if (rc == PackageManager.PERMISSION_GRANTED) {
                          try {
                            startCameraSource();
                          } catch (SecurityException exc) {
                          }
                      }
                  }
              }
          }
      };

      AlertDialog.Builder alert = new AlertDialog.Builder(getActivity(), R.style.AlertDialogTheme);
      if (null == alert) {
          return;
      }
      AlertDialog alertDialog = alert
              .setTitle(R.string.sync_settings_add_device_name_title)
              .setMessage(getResources().getString(R.string.sync_settings_add_device_name_label))
              .setView(view)
              .setPositiveButton(R.string.ok, onClickListener)
              .setNegativeButton(R.string.cancel, onClickListener)
              .create();
      alertDialog.getDelegate().setHandleNativeActionModesEnabled(false);
      alertDialog.setOnShowListener(new DialogInterface.OnShowListener() {
          @Override
          public void onShow(DialogInterface dialog) {
            KeyboardVisibilityDelegate.getInstance().showKeyboard(input);
          }
      });
      alertDialog.show();
      Button cancelButton = alertDialog.getButton(AlertDialog.BUTTON_NEGATIVE);
      cancelButton.setVisibility(View.GONE);
  }

  private void ResetSyncDialog() {
      AlertDialog.Builder alert = new AlertDialog.Builder(getActivity(), R.style.AlertDialogTheme);
      if (null == alert) {
          return;
      }
      DialogInterface.OnClickListener onClickListener = new DialogInterface.OnClickListener() {
          @Override
          public void onClick(DialogInterface dialog, int button) {
              if (button == AlertDialog.BUTTON_POSITIVE) {
                  if (null != mPrefSwitch) {
                      mPrefSwitch.setChecked(false);
                  }
                  ChromeApplication application = (ChromeApplication)ContextUtils.getApplicationContext();
                  if (null != application && null != application.mBraveSyncWorker) {
                      application.mBraveSyncWorker.ResetSync();
                  }
                  setAppropriateView();
              }
          }
      };
      AlertDialog alertDialog = alert
              .setTitle(getResources().getString(R.string.brave_sync_remove_device_text))
              .setMessage(getResources().getString(R.string.resetting_sync))
              .setPositiveButton(R.string.ok, onClickListener)
              .setNegativeButton(R.string.cancel, onClickListener)
              .create();
      alertDialog.getDelegate().setHandleNativeActionModesEnabled(false);
      alertDialog.show();
  }

  private void setNewChainLayout() {
      getActivity().setTitle(R.string.brave_sync_start_new_chain);
      if (null != mScrollViewSyncInitial) {
          mScrollViewSyncInitial.setVisibility(View.GONE);
      }
      if (null != mScrollViewEnterCodeWords) {
          mScrollViewEnterCodeWords.setVisibility(View.GONE);
      }
      if (null != mScrollViewAddMobileDevice) {
          mScrollViewAddMobileDevice.setVisibility(View.GONE);
      }
      if (null != mScrollViewAddLaptop) {
          mScrollViewAddLaptop.setVisibility(View.GONE);
      }
      if (null != mScrollViewSyncStartChain) {
          mScrollViewSyncStartChain.setVisibility(View.VISIBLE);
      }
  }

  private void setAddMobileDeviceLayout() {
      getActivity().setTitle(R.string.brave_sync_btn_mobile);
      if (null != mBraveSyncTextViewAddMobileDevice) {
          setSyncText(getResources().getString(R.string.brave_sync_scan_sync_code),
                        getResources().getString(R.string.brave_sync_add_mobile_device_text_part_1) + "\n\n" +
                        getResources().getString(R.string.brave_sync_add_mobile_device_text_part_2), mBraveSyncTextViewAddMobileDevice);
      }
      if (null != mScrollViewSyncInitial) {
          mScrollViewSyncInitial.setVisibility(View.GONE);
      }
      if (null != mScrollViewEnterCodeWords) {
          mScrollViewEnterCodeWords.setVisibility(View.GONE);
      }
      if (null != mScrollViewAddMobileDevice) {
          mScrollViewAddMobileDevice.setVisibility(View.VISIBLE);
      }
      if (null != mScrollViewAddLaptop) {
          mScrollViewAddLaptop.setVisibility(View.GONE);
      }
      if (null != mScrollViewSyncStartChain) {
          mScrollViewSyncStartChain.setVisibility(View.GONE);
      }
      getActivity().runOnUiThread(new Runnable() {
          @Override
          public void run() {
              ChromeApplication application = (ChromeApplication)ContextUtils.getApplicationContext();
              if (null != application && null != application.mBraveSyncWorker) {
                  SharedPreferences sharedPref = getActivity().getApplicationContext().getSharedPreferences(PREF_NAME, 0);
                  String seed = sharedPref.getString(PREF_SEED, null);
                  if (null == seed || seed.isEmpty()) {
                      // Init to receive new seed
                      application.mBraveSyncWorker.InitSync(true, true);
                  } else {
                      mSyncScreensObserver.onSeedReceived(seed, false, true);
                  }
              }
          }
      });
  }

  private void setAddLaptopLayout() {
      getActivity().setTitle(R.string.brave_sync_btn_laptop);
      if (null != mBraveSyncTextViewAddLaptop) {
          setSyncText(getResources().getString(R.string.brave_sync_add_laptop_text_title),
                        getResources().getString(R.string.brave_sync_add_laptop_text_part_1) + "\n\n" +
                        getResources().getString(R.string.brave_sync_add_laptop_text_part_2), mBraveSyncTextViewAddLaptop);
      }
      if (null != mScrollViewSyncInitial) {
          mScrollViewSyncInitial.setVisibility(View.GONE);
      }
      if (null != mScrollViewEnterCodeWords) {
          mScrollViewEnterCodeWords.setVisibility(View.GONE);
      }
      if (null != mScrollViewAddMobileDevice) {
          mScrollViewAddMobileDevice.setVisibility(View.GONE);
      }
      if (null != mScrollViewAddLaptop) {
          mScrollViewAddLaptop.setVisibility(View.VISIBLE);
      }
      if (null != mScrollViewSyncStartChain) {
          mScrollViewSyncStartChain.setVisibility(View.GONE);
      }
      getActivity().runOnUiThread(new Runnable() {
          @Override
          public void run() {
              ChromeApplication application = (ChromeApplication)ContextUtils.getApplicationContext();
              if (null != application && null != application.mBraveSyncWorker) {
                  SharedPreferences sharedPref = getActivity().getApplicationContext().getSharedPreferences(PREF_NAME, 0);
                  String seed = sharedPref.getString(PREF_SEED, null);
                  if (null == seed || seed.isEmpty()) {
                      // Init to receive new seed
                      application.mBraveSyncWorker.InitSync(true, true);
                  } else {
                      mSyncScreensObserver.onSeedReceived(seed, false, true);
                  }
              }
          }
      });
  }

  private void setSyncDoneLayout() {
      getActivity().setTitle(R.string.prefs_sync);
      if (null != mCameraSourcePreview) {
          mCameraSourcePreview.stop();
      }
      if (null != mScrollViewSyncInitial) {
          mScrollViewSyncInitial.setVisibility(View.GONE);
      }
      if (null != mScrollViewSyncChainCode) {
          mScrollViewSyncChainCode.setVisibility(View.GONE);
      }
      if (null != mScrollViewEnterCodeWords) {
          mScrollViewEnterCodeWords.setVisibility(View.GONE);
      }
      if (null != mScrollViewAddMobileDevice) {
          mScrollViewAddMobileDevice.setVisibility(View.GONE);
      }
      if (null != mScrollViewAddLaptop) {
          mScrollViewAddLaptop.setVisibility(View.GONE);
      }
      if (null != mScrollViewSyncStartChain) {
          mScrollViewSyncStartChain.setVisibility(View.GONE);
      }
      if (null != mScrollViewSyncDone) {
          mScrollViewSyncDone.setVisibility(View.VISIBLE);
      }
      if (null != mSyncSwitchBookmarks) {
          if (null != mPrefSwitchBookmarks) {
              mSyncSwitchBookmarks.setChecked(mPrefSwitchBookmarks.isChecked());
          }
      }
      if (null != mSyncSwitchTabs) {
          if (null != mPrefSwitchTabs) {
              mSyncSwitchTabs.setChecked(mPrefSwitchTabs.isChecked());
          }
      }
      if (null != mSyncSwitchHistory) {
          if (null != mPrefSwitchHistory) {
              mSyncSwitchHistory.setChecked(mPrefSwitchHistory.isChecked());
          }
      }
      if (null != mSyncSwitchAutofillPasswords) {
          if (null != mPrefSwitchAutofillPasswords) {
              mSyncSwitchAutofillPasswords.setChecked(mPrefSwitchAutofillPasswords.isChecked());
          }
      }
      if (null != mSyncSwitchPaymentSettings) {
          if (null != mPrefSwitchPaymentSettings) {
              mSyncSwitchPaymentSettings.setChecked(mPrefSwitchPaymentSettings.isChecked());
          }
      }
      ChromeApplication application = (ChromeApplication)ContextUtils.getApplicationContext();
      if (null != mSyncScreensObserver) {
          mSyncScreensObserver.onDevicesAvailable();
      }
  }
}
