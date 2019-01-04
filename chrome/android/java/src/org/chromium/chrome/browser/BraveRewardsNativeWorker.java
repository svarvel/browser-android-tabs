/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.chromium.chrome.browser;

import org.chromium.base.Log;
import org.chromium.base.annotations.CalledByNative;
import org.chromium.base.annotations.JNINamespace;
import org.chromium.chrome.browser.BraveRewardsObserver;

import java.util.ArrayList;
import java.util.List;

@JNINamespace("chrome::android")
public class BraveRewardsNativeWorker {
    private List<BraveRewardsObserver> observers_;
    private long mNativeBraveRewardsNativeWorker;

    public BraveRewardsNativeWorker() {
        observers_ = new ArrayList<BraveRewardsObserver>();
    }

    public void Init() {
      if (mNativeBraveRewardsNativeWorker == 0) {
          nativeInit();
      }
    }

    public void Destroy() {
        if (mNativeBraveRewardsNativeWorker != 0) {
            nativeDestroy(mNativeBraveRewardsNativeWorker);
            mNativeBraveRewardsNativeWorker = 0;
        }
    }

    public void AddObserver(BraveRewardsObserver observer) {
        // TODO add synchronization if ever use it from diff threads
        observers_.add(observer);
    }

    public void RemoveObserver(BraveRewardsObserver observer) {
        // TODO add synchronization if ever use it from diff threads
        observers_.remove(observer);
    }

    public void CreateWallet() {
        nativeCreateWallet(mNativeBraveRewardsNativeWorker);
    }

    public boolean WalletExist() {
        return nativeWalletExist(mNativeBraveRewardsNativeWorker);
    }

    public void GetWalletProperties() {
        nativeGetWalletProperties(mNativeBraveRewardsNativeWorker);
    }

    public double GetWalletBalance() {
        return nativeGetWalletBalance(mNativeBraveRewardsNativeWorker);
    }

    public double GetWalletRate(String rate) {
        return nativeGetWalletRate(mNativeBraveRewardsNativeWorker, rate);
    }

    public void GetPublisherInfo(int tabId, String host) {
        nativeGetPublisherInfo(mNativeBraveRewardsNativeWorker, tabId, host);
    }

    public String GetPublisherURL(int tabId) {
        return nativeGetPublisherURL(mNativeBraveRewardsNativeWorker, tabId);
    }

    public String GetPublisherFavIconURL(int tabId) {
        return nativeGetPublisherFavIconURL(mNativeBraveRewardsNativeWorker, tabId);
    }

    public String GetPublisherName(int tabId) {
        return nativeGetPublisherName(mNativeBraveRewardsNativeWorker, tabId);
    }

    public String GetPublisherId(int tabId) {
        return nativeGetPublisherId(mNativeBraveRewardsNativeWorker, tabId);
    }

    public int GetPublisherPercent(int tabId) {
        return nativeGetPublisherPercent(mNativeBraveRewardsNativeWorker, tabId); 
    }

    public boolean GetPublisherExcluded(int tabId) {
        return nativeGetPublisherExcluded(mNativeBraveRewardsNativeWorker, tabId); 
    }

    public boolean GetPublisherVerified(int tabId) {
        return nativeGetPublisherVerified(mNativeBraveRewardsNativeWorker, tabId); 
    }

    public void IncludeInAutoContribution(int tabId, boolean exclude) {
      nativeIncludeInAutoContribution(mNativeBraveRewardsNativeWorker, tabId, exclude);
    }

    public void RemovePublisherFromMap(int tabId) {
        nativeRemovePublisherFromMap(mNativeBraveRewardsNativeWorker, tabId);
    }

    public void GetCurrentBalanceReport() {
        nativeGetCurrentBalanceReport(mNativeBraveRewardsNativeWorker);
    }

    @CalledByNative
    public void OnGetCurrentBalanceReport(String[] report) {
        for(BraveRewardsObserver observer : observers_) {
            observer.OnGetCurrentBalanceReport(report);
        }
    }

    @CalledByNative
    private void setNativePtr(long nativePtr) {
        assert mNativeBraveRewardsNativeWorker == 0;
        mNativeBraveRewardsNativeWorker = nativePtr;
    }

    @CalledByNative
    public void OnWalletInitialized(int error_code) {
        for(BraveRewardsObserver observer : observers_) {
            observer.OnWalletInitialized(error_code);
        }
    }

    @CalledByNative
    public void OnPublisherInfo(int tabId) {
        for(BraveRewardsObserver observer : observers_) {
            observer.OnPublisherInfo(tabId);
        }
    }

    @CalledByNative
    public void OnWalletProperties(int error_code) {
        for(BraveRewardsObserver observer : observers_) {
            observer.OnWalletProperties(error_code);
        }
    }

    private native void nativeInit();
    private native void nativeDestroy(long nativeBraveRewardsNativeWorker);
    private native void nativeCreateWallet(long nativeBraveRewardsNativeWorker);
    private native boolean nativeWalletExist(long nativeBraveRewardsNativeWorker);
    private native void nativeGetWalletProperties(long nativeBraveRewardsNativeWorker);
    private native double nativeGetWalletBalance(long nativeBraveRewardsNativeWorker);
    private native double nativeGetWalletRate(long nativeBraveRewardsNativeWorker, String rate);
    private native void nativeGetPublisherInfo(long nativeBraveRewardsNativeWorker, int tabId, String host);
    private native String nativeGetPublisherURL(long nativeBraveRewardsNativeWorker, int tabId);
    private native String nativeGetPublisherFavIconURL(long nativeBraveRewardsNativeWorker, int tabId);
    private native String nativeGetPublisherName(long nativeBraveRewardsNativeWorker, int tabId);
    private native String nativeGetPublisherId(long nativeBraveRewardsNativeWorker, int tabId);
    private native int nativeGetPublisherPercent(long nativeBraveRewardsNativeWorker, int tabId);
    private native boolean nativeGetPublisherExcluded(long nativeBraveRewardsNativeWorker, int tabId);
    private native boolean nativeGetPublisherVerified(long nativeBraveRewardsNativeWorker, int tabId);
    private native void nativeIncludeInAutoContribution(long nativeBraveRewardsNativeWorker, int tabId,
      boolean exclude);
    private native void nativeRemovePublisherFromMap(long nativeBraveRewardsNativeWorker, int tabId);
    private native void nativeGetCurrentBalanceReport(long nativeBraveRewardsNativeWorker);
}
