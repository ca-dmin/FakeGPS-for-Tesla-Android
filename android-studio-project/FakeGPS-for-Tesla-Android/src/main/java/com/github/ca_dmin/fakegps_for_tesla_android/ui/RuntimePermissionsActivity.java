package com.github.ca_dmin.fakegps_for_tesla_android.ui;

import android.app.Activity;

public abstract class RuntimePermissionsActivity extends Activity {
    public abstract void onPermissionsGranted();
    public abstract void onPermissionsDenied(String[] permissions);
}
