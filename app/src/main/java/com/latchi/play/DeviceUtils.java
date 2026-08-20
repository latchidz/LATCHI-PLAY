package com.latchi.play;

import android.app.UiModeManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.Configuration;

public final class DeviceUtils {
    private DeviceUtils() {}

    public static boolean isTelevision(Context context) {
        UiModeManager manager = (UiModeManager) context.getSystemService(Context.UI_MODE_SERVICE);
        boolean tvMode = manager != null && manager.getCurrentModeType() == Configuration.UI_MODE_TYPE_TELEVISION;
        PackageManager pm = context.getPackageManager();
        return tvMode || pm.hasSystemFeature(PackageManager.FEATURE_LEANBACK) ||
                pm.hasSystemFeature("android.hardware.type.television");
    }
}
