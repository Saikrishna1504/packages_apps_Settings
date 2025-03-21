/*
 * Copyright (C) 2018 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */
package com.android.settings.accounts;

import static android.provider.Settings.Secure.CROSS_PROFILE_CALENDAR_ENABLED;

import android.app.admin.DevicePolicyManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.UserHandle;
import android.os.UserManager;
import android.provider.Settings;
import android.util.Log;

import androidx.annotation.VisibleForTesting;

import com.android.settings.R;
import com.android.settings.Utils;
import com.android.settings.core.TogglePreferenceController;

import java.util.Set;

public class CrossProfileCalendarPreferenceController extends TogglePreferenceController {

    private static final String TAG = "CrossProfileCalendarPreferenceController";

    private UserHandle mManagedProfile;

    public CrossProfileCalendarPreferenceController(Context context, String key) {
        super(context, key);
        // Set default managed profile for the current user, otherwise isAvailable will be false and
        // the setting won't be searchable.
        UserManager userManager = context.getSystemService(UserManager.class);
        mManagedProfile = Utils.getManagedProfile(userManager);
    }

    public void setManagedProfile(UserHandle managedProfile) {
        mManagedProfile = managedProfile;
    }

    @Override
    public int getAvailabilityStatus() {
        if (mManagedProfile != null
                && !isCrossProfileCalendarDisallowedByAdmin(
                        mContext, mManagedProfile.getIdentifier())) {
            return AVAILABLE;
        }

        return DISABLED_FOR_USER;
    }

    @Override
    public boolean isChecked() {
        if (mManagedProfile == null) {
            return false;
        }
        return Settings.Secure.getIntForUser(mContext.getContentResolver(),
                CROSS_PROFILE_CALENDAR_ENABLED, /* default= */ 0,
                mManagedProfile.getIdentifier()) == 1;
    }

    @Override
    public boolean setChecked(boolean isChecked) {
        if (mManagedProfile == null) {
            return false;
        }
        final int value = isChecked ? 1 : 0;
        return Settings.Secure.putIntForUser(mContext.getContentResolver(),
                CROSS_PROFILE_CALENDAR_ENABLED, value, mManagedProfile.getIdentifier());
    }

    @Override
    public int getSliceHighlightMenuRes() {
        return R.string.menu_key_accounts;
    }

    static boolean isCrossProfileCalendarDisallowedByAdmin(Context context, int userId) {
        final Context managedProfileContext = createPackageContextAsUser(context, userId);
        final DevicePolicyManager dpm = managedProfileContext.getSystemService(
                DevicePolicyManager.class);
        if (dpm == null) {
            return true;
        }
        final Set<String> packages = dpm.getCrossProfileCalendarPackages();
        return packages != null && packages.isEmpty();
    }

    private static Context createPackageContextAsUser(Context context, int userId) {
        try {
            return context.createPackageContextAsUser(
                    context.getPackageName(), 0 /* flags */, UserHandle.of(userId));
        } catch (PackageManager.NameNotFoundException e) {
            Log.e(TAG, "Failed to create user context", e);
        }
        return null;
    }
}