package com.android.settings.security;

import android.content.Context;
import android.ext.settings.DenyNewUsbSetting;
import android.os.SystemProperties;

import androidx.preference.PreferenceScreen;

import com.android.settings.R;
import com.android.settings.ext.AbstractListPreferenceController;
import com.android.settings.ext.RadioButtonPickerFragment2;

import java.util.Arrays;
import java.util.List;

import static com.android.settings.ext.ExtSettingControllerHelper.getGlobalSettingAvailability;

public class DenyNewUsbPrefController extends AbstractListPreferenceController {

    private final List<String> list = Arrays.asList(
            DenyNewUsbSetting.DISABLED,
            DenyNewUsbSetting.DYNAMIC,
            DenyNewUsbSetting.ENABLED
    );

    public DenyNewUsbPrefController(Context ctx, String key) {
        super(ctx, key);
    }

    @Override
    public int getAvailabilityStatus() {
        return getGlobalSettingAvailability(mContext);
    }

    @Override
    protected void getEntries(Entries entries) {
        entries.add(R.string.deny_new_usb_val_enabled, list.indexOf(DenyNewUsbSetting.ENABLED));
        entries.add(R.string.deny_new_usb_val_dynamic, list.indexOf(DenyNewUsbSetting.DYNAMIC));
        entries.add(R.string.deny_new_usb_val_disabled, list.indexOf(DenyNewUsbSetting.DISABLED));
    }

    @Override
    public void addPrefsAfterList(RadioButtonPickerFragment2 fragment, PreferenceScreen screen) {
        addFooterPreference(screen, R.string.deny_new_usb_footer,
                "https://grapheneos.org/usage#usb-peripherals");
    }

    @Override
    protected int getCurrentValue() {
        return list.indexOf(DenyNewUsbSetting.SYS_PROP.get());
    }

    @Override
    protected boolean setValue(int val) {
        String strVal = list.get(val);
        boolean res = DenyNewUsbSetting.SYS_PROP.put(strVal);
        if (!res) {
            return false;
        }
        if (DenyNewUsbSetting.DYNAMIC.equals(strVal)) {
            // when "dynamic" is written to the sysprop, the following hook is triggered in
            // system/core/rootdir/init.rc :

            // on property:persist.security.deny_new_usb=dynamic
            // -    write /proc/sys/kernel/deny_new_usb 1

            // But the fact that the setting was set by the user implies that the device is unlocked,
            // and deny_new_usb should be disabled until the device gets locked
            SystemProperties.set(DenyNewUsbSetting.TRANSIENT_PROP,
                    DenyNewUsbSetting.TRANSIENT_DISABLE);
        }
        return true;
    }

    @Override
    protected boolean isCredentialConfirmationRequired() {
        return true;
    }
}
