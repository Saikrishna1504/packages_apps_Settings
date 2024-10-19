package com.android.settings;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.SigningInfo;
import android.os.UserHandle;
import android.util.Log;

import com.android.settingslib.utils.ThreadUtils;

import java.io.File;
import java.io.IOException;
import java.util.HexFormat;
import java.util.List;

// Due to a bug in boot-time package verification extensions (PackageVerityExt), Vanadium Trichrome
// static shared library was removed in some cases in an incorrect way. Only the library APK was
// removed, all library package state was left behind: package setting, per-user package state,
// per-user permission state etc.
//
// PackageVerityExt bug is now fixed. This one-time cleanup task removes leftover package states.
public class VanadiumLibraryCleanup {
    private static final String TAG = VanadiumLibraryCleanup.class.getSimpleName();

    private static final String MARKER_FILE_NAME = "vanadium_trichrome_library_cleanup_done2";

    public static void maybeRun(Context ctx) {
        if (ctx.getUserId() != UserHandle.USER_SYSTEM) {
            return;
        }

        ThreadUtils.postOnBackgroundThread(() -> {
            try {
                run(ctx);
            } catch (Throwable e) {
                // don't crash, removing these leftovers is not that important
                Log.e(TAG, "", e);
            }
        });
    }

    private static void run(Context ctx) {
        File markerFile = new File(ctx.getFilesDir(), MARKER_FILE_NAME);
        if (markerFile.isFile()) {
            return;
        }

        // MATCH_ANY_USER is needed in case the user has manually uninstalled these entries in USER_SYSTEM
        int baseFlags = PackageManager.MATCH_ANY_USER | PackageManager.MATCH_UNINSTALLED_PACKAGES;

        PackageManager pm = ctx.getPackageManager();
        List<ApplicationInfo> installedPkgs =
                pm.getInstalledApplications(baseFlags);

        byte[] vanadiumCertDigest = HexFormat.of()
                .parseHex("c6adb8b83c6d4c17d292afde56fd488a51d316ff8f2c11c5410223bff8a7dbb3");

        var deleteObserver = new DeleteObserver();

        for (ApplicationInfo appInfo : installedPkgs) {
            String pkgName = appInfo.packageName;
            // Static shared libraries have a synthetic package name:
            // <package name> <underscore> <package version>
            if (!pkgName.startsWith("app.vanadium.trichromelibrary_")) {
                continue;
            }

            Log.d(TAG, "processing " + pkgName);
            PackageInfo pkgInfo;
            try {
                pkgInfo = pm.getPackageInfo(pkgName, baseFlags | PackageManager.GET_SIGNING_CERTIFICATES);
            } catch (PackageManager.NameNotFoundException e) {
                Log.e(TAG, "", e);
                continue;
            }
            SigningInfo signingInfo = pkgInfo.signingInfo;
            if (signingInfo == null) {
                Log.w(TAG, "signingInfo is null");
                continue;
            }
            if (!signingInfo.getSigningDetails().hasSha256Certificate(vanadiumCertDigest)) {
                Log.w(TAG, "unknown signing certificate");
                continue;
            }

            if (appInfo.sourceDir != null) {
                Log.w(TAG, "base APK is not null");
                continue;
            }
            // there's no need to check whether this ApplicationInfo represents an actual static
            // shared library, they are filtered out by default

            pm.deletePackage(pkgName, deleteObserver, PackageManager.DELETE_ALL_USERS);
        }

        try {
            if (!markerFile.createNewFile()) {
                Log.w(TAG, "markerFile.createNewFile() returned false");
            }
        } catch (IOException e) {
            Log.w(TAG, "unable to create markerFile", e);
        }
    }

    static class DeleteObserver extends android.content.pm.IPackageDeleteObserver.Stub {
        @Override
        public void packageDeleted(String packageName, int returnCode) {
            Log.d(TAG, "packageDeleted callback for " + packageName
                    + ", result " + PackageManager.deleteStatusToString(returnCode));
        }
    }
}
