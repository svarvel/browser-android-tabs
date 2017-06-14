// Copyright 2010 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.components.sync;

import android.accounts.Account;
import android.content.ContentResolver;
import android.content.SyncStatusObserver;
import android.os.Bundle;

/**
 * A SyncContentResolverDelegate that simply forwards calls to ContentResolver.
 */
public class SystemSyncContentResolverDelegate implements SyncContentResolverDelegate {
    @Override
    public Object addStatusChangeListener(int mask, SyncStatusObserver callback) {
        return ContentResolver.addStatusChangeListener(mask, callback);
    }

    @Override
    public void removeStatusChangeListener(Object handle) {
        try {
            ContentResolver.removeStatusChangeListener(handle);
        } catch (SecurityException exc) {}
    }

    @Override
    public void setMasterSyncAutomatically(boolean sync) {
        try {
            ContentResolver.setMasterSyncAutomatically(sync);
        } catch (SecurityException exc) {}
    }

    @Override
    public boolean getMasterSyncAutomatically() {
        try {
            return ContentResolver.getMasterSyncAutomatically();
        } catch (SecurityException exc) {}

        return false;
    }

    @Override
    public boolean getSyncAutomatically(Account account, String authority) {
        try {
            return ContentResolver.getSyncAutomatically(account, authority);
        } catch (SecurityException exc) {}

        return false;
    }

    @Override
    public void setSyncAutomatically(Account account, String authority, boolean sync) {
        try {
            ContentResolver.setSyncAutomatically(account, authority, sync);
        } catch (SecurityException exc) {}
    }

    @Override
    public void setIsSyncable(Account account, String authority, int syncable) {
        try {
            ContentResolver.setIsSyncable(account, authority, syncable);
        } catch (SecurityException exc) {}
    }

    @Override
    public int getIsSyncable(Account account, String authority) {
        try {
            return ContentResolver.getIsSyncable(account, authority);
        } catch (SecurityException exc) {}

        return 0;
    }

    @Override
    public void removePeriodicSync(Account account, String authority, Bundle extras) {
        try {
            ContentResolver.removePeriodicSync(account, authority, extras);
        } catch (SecurityException exc) {}
    }
}
