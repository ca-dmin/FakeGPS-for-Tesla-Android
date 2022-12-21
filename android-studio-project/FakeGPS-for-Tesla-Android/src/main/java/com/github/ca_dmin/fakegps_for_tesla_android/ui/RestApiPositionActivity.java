package com.github.ca_dmin.fakegps_for_tesla_android.ui;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.github.ca_dmin.fakegps_for_tesla_android.R;
import com.github.ca_dmin.fakegps_for_tesla_android.data_model.LocPoint;
import com.github.ca_dmin.fakegps_for_tesla_android.data_model.SharedPrefs;
import com.github.ca_dmin.fakegps_for_tesla_android.security_model.RuntimePermissions;
import com.github.ca_dmin.fakegps_for_tesla_android.service.LocationService;

public class RestApiPositionActivity extends RuntimePermissionsActivity {
    private LocPoint originalLoc;

    private TextView text_rest_api_location;
    private Button   button_toggle_state;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_rest_api_position);

        originalLoc = SharedPrefs.getTripOrigin(RestApiPositionActivity.this);


        text_rest_api_location = (TextView) findViewById(R.id.text_rest_api_location);
        button_toggle_state  = (Button)   findViewById(R.id.button_toggle_state);

        reset();


        button_toggle_state.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    if (LocationService.isStarted()) {
                        LocationService.doStop(RestApiPositionActivity.this, true);
                        button_toggle_state.setText(R.string.label_button_start);
                    }
                    else {
                        doStart(true);
                    }
                }
                catch(Exception e) {}
            }
        });

    }

    private void reset() {

        text_rest_api_location.setText(originalLoc.toString());
        String restApiLastResponse = " to be implemented ...";
        text_rest_api_location.setText(restApiLastResponse);
        if (LocationService.isStarted())
            button_toggle_state.setText(R.string.label_button_stop);
    }

    private void doStart(boolean check_permissions) {
        if (check_permissions) {
            RuntimePermissions.onPermissionsGranted(RestApiPositionActivity.this);
        }
        else {
            LocPoint modifiedLoc  = new LocPoint(originalLoc);

            LocationService.doStart(RestApiPositionActivity.this, true, modifiedLoc, null, 0, true);

            SharedPrefs.putTripOrigin(RestApiPositionActivity.this, modifiedLoc);

            button_toggle_state.setText(R.string.label_button_stop);
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Runtime Permissions:
    // ---------------------------------------------------------------------------------------------

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        RuntimePermissions.onRequestPermissionsResult(RestApiPositionActivity.this, requestCode, permissions, grantResults);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        RuntimePermissions.onActivityResult(RestApiPositionActivity.this, requestCode, resultCode, data);
    }

    @Override
    public void onPermissionsGranted() {
        doStart(false);
    }

    @Override
    public void onPermissionsDenied(String[] permissions) {
        String text = "The following list contains required permissions that are not yet granted:\n  " + TextUtils.join("\n  ", permissions);
        Toast.makeText(RestApiPositionActivity.this, text, Toast.LENGTH_LONG).show();
    }
}
