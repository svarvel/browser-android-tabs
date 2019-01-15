
package org.chromium.chrome.browser.util;

import android.app.Activity;
import android.content.Context;
import android.support.annotation.NonNull;


import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.safetynet.SafetyNet;
import com.google.android.gms.safetynet.SafetyNetApi;
import com.google.android.gms.safetynet.SafetyNetClient;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Random;

import org.chromium.base.ApplicationStatus;
import org.chromium.base.annotations.CalledByNative;
import org.chromium.base.annotations.JNINamespace;
import org.chromium.base.ContextUtils;
import org.chromium.base.Log;
import org.chromium.chrome.browser.ConfigAPIs;

/**
 * Utility class for providing additional safety checks.
 */
@JNINamespace("safetynet_check")
public class SafetyNetCheck {
    private static final String TAG = "SafetyNetCheck";

    private long mNativeSafetyNetCheck;

    private SafetyNetCheck(long staticSafetyNetCheck) {
        mNativeSafetyNetCheck = staticSafetyNetCheck;
    }

    @CalledByNative
    private static SafetyNetCheck create(long staticSafetyNetCheck) {
        return new SafetyNetCheck(staticSafetyNetCheck);
    }

    @CalledByNative
    private void destroy() {
        assert mNativeSafetyNetCheck != 0;
        mNativeSafetyNetCheck = 0;
    }

    /**
    * Performs client attestation
    */
    @CalledByNative
    public boolean clientAttestation(String nonceData) {
        boolean res = false;
        try {
            Activity activity = ApplicationStatus.getLastTrackedFocusedActivity();
            if (activity == null) return false;
            if (GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(activity) == ConnectionResult.SUCCESS) {
                byte[] nonce = nonceData.isEmpty() ? getRequestNonce() : nonceData.getBytes();
                SafetyNetClient client = SafetyNet.getClient(activity);
                Task<SafetyNetApi.AttestationResponse> attestTask = client.attest(nonce, ConfigAPIs.GS_API_KEY);                
                attestTask.addOnSuccessListener(activity,
                    new OnSuccessListener<SafetyNetApi.AttestationResponse>() {
                        @Override
                        public void onSuccess(SafetyNetApi.AttestationResponse response) {
                            clientAttestationResult(true, response.getJwsResult());
                        }
                    }).addOnFailureListener(activity, new OnFailureListener() {
                        @Override
                        public void onFailure(@NonNull Exception e) {
                            Log.e(TAG, "Failed to perform SafetyNetCheck: " + e);
                            clientAttestationResult(false, e.toString());
                        }
                    });
                res = true;
            }
        } catch (Exception e) {
            Log.e(TAG, "SafetyNetCheck error: " + e);
        }
        return res;
    }

    /**
    * Generates a random 24-byte nonce.
    */
    private byte[] getRequestNonce() {
        byte[] bytes = new byte[24];
        Random random = new SecureRandom();
        random.nextBytes(bytes);
        return bytes;
    }

    /**
    * Returns client attestation final result
    */
    private void clientAttestationResult(boolean result, String resultString) {
        if (mNativeSafetyNetCheck == 0) return;
        nativeclientAttestationResult(mNativeSafetyNetCheck, result, resultString);
    }

    private native void nativeclientAttestationResult(long nativeSafetyNetCheck, boolean result, String resultString);
}