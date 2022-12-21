package com.github.ca_dmin.fakegps_for_tesla_android.service.looper;

// copied from:
//   https://github.com/xiangtailiang/FakeGPS/blob/V1.1/app/src/main/java/com/github/fakegps/JoyStickManager.java

import com.github.ca_dmin.fakegps_for_tesla_android.data_model.LocPoint;
import com.github.ca_dmin.fakegps_for_tesla_android.data_model.SharedPrefsState;
import com.github.ca_dmin.fakegps_for_tesla_android.event_hooks.ISharedPrefsListener;
import com.github.ca_dmin.fakegps_for_tesla_android.service.LocationService;

import android.content.Context;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class LocationThreadManager implements ISharedPrefsListener {
    private static LocationThreadManager INSTANCE = new LocationThreadManager();

    private Context mContext;
    private LocationThread mLocationThread;
    private LocPoint mCurrentLocPoint;
    private LocPoint mOriginLocPoint;
    private LocPoint mTargetLocPoint;
    private int mFlyTime;
    private int mFlyTimeIndex;
    private int mTimeInterval;
    private int mFixedCount;
    private int mFixedCountRemaining;
    private boolean mTripHoldDestination;

    private boolean mIsStarted = false;
    private boolean mIsFlyMode = false;
    private boolean mIsRestApi = false;

    private String mApiUrl = "";

    private LocationThreadManager() {
        mContext = null;
    }

    public void init(Context context) {
        mContext = context;
        importSharedPrefs();
    }

    public static LocationThreadManager get() {
        return INSTANCE;
    }

    public void start(LocPoint locPoint) {
        if (mContext == null) return;
        if (locPoint == null) return;

        mCurrentLocPoint = new LocPoint(locPoint);
        if ((mLocationThread == null) || !mLocationThread.isAlive()) {
            mLocationThread = new LocationThread(mContext, this, mTimeInterval);
            mLocationThread.startThread();
        }

        mFixedCountRemaining = mFixedCount;
        mIsStarted = true;
    }

    public void stop() {
        if (mLocationThread != null) {
            mLocationThread.stopThread();
            mLocationThread = null;
        }

        mIsStarted = false;
        stopService();
    }

    private void stopService() {
        LocationService.doStop(mContext, true);
    }

    public boolean isStarted() {
        return mIsStarted;
    }

    public LocPoint getCurrentLocPoint() {
        return new LocPoint(mCurrentLocPoint);
    }

    public static String getJsonStringFromUrl(HttpURLConnection url) {
        try (InputStream input = url.getInputStream()) {
            InputStreamReader isr = new InputStreamReader(input);
            BufferedReader reader = new BufferedReader(isr);
            StringBuilder json = new StringBuilder();
            int c;
            while ((c = reader.read()) != -1) {
                json.append((char) c);
            }
            System.out.println(json.toString());
            return json.toString();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public LocPoint getUpdateLocPoint() {
        double lat = mCurrentLocPoint.getLatitude();
        double lon = mCurrentLocPoint.getLongitude();
        float bearing = 0;
        float speed = 0;

        if (mIsRestApi) {

            try {

                URL url = null;
                try {
                    url = new URL(mApiUrl);
                    System.out.println("getUpdateLocPoint: " + url.toString());
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
                HttpURLConnection urlConnection = null;
                try {
                    urlConnection = (HttpURLConnection) url.openConnection();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }

                try {
                    try {
                    /*
                    http://<server with teslamate-api>/api/v1/cars/1/status
                    {
                      "data": {
                        ...
                        "status": {
                          ...
                          "car_geodata": {
                            ...
                            "latitude": 43.114014,
                            "longitude": 12.11118
                          },
                          ...
                          "driving_details": {
                            ...
                            "speed": 0,
                            "heading": 195,
                            "elevation": 482
                          },

                     */
                        JSONObject obj = new JSONObject(getJsonStringFromUrl(urlConnection));
                        lat = obj.getJSONObject("data")
                                .getJSONObject("status")
                                .getJSONObject("car_geodata")
                                .getDouble("latitude");
                        lon = obj.getJSONObject("data")
                                .getJSONObject("status")
                                .getJSONObject("car_geodata")
                                .getDouble("longitude");

                        lon = obj.getJSONObject("data")
                                .getJSONObject("status")
                                .getJSONObject("car_geodata")
                                .getDouble("longitude");

                        bearing = obj.getJSONObject("data")
                                .getJSONObject("status")
                                .getJSONObject("driving_details")
                                .getInt("heading");

                        // km/h --> to m/s
                        speed = (float) (obj.getJSONObject("data")
                                .getJSONObject("status")
                                .getJSONObject("driving_details")
                                .getDouble("speed") / 3.6);

                        mCurrentLocPoint.setLatitude(lat);
                        mCurrentLocPoint.setLongitude(lon);
                        mCurrentLocPoint.setBearing(bearing);
                        mCurrentLocPoint.setSpeed(speed);

                        System.out.println("getUpdateLocPoint GOT this from JSON: " + mCurrentLocPoint.toString());

                    } catch (JSONException e) {
                        throw new RuntimeException(e);
                    }


                } finally {
                    urlConnection.disconnect();
                }

            } catch (Exception exception) {

                System.out.println("getUpdateLocPoint exception old on: " + mCurrentLocPoint.toString());
            }

            return new LocPoint(mCurrentLocPoint);
        }

        if (!mIsFlyMode) {
            System.out.println("getUpdateLocPoint fixed pos: " + mCurrentLocPoint.toString());
            return new LocPoint(mCurrentLocPoint);
        }

        // flymode
        System.out.println("getUpdateLocPoint fly mode: " + mCurrentLocPoint.toString());

        if (mFlyTimeIndex >= mFlyTime) {
            jumpToLocation(mTargetLocPoint);
            mFixedCountRemaining = (mTripHoldDestination) ? mFixedCount : -1;
            return new LocPoint(mCurrentLocPoint);
        }
        else {
            float factor = (float) mFlyTimeIndex / (float) mFlyTime;
            lat = mOriginLocPoint.getLatitude()  + (factor * (mTargetLocPoint.getLatitude()  - mOriginLocPoint.getLatitude()));
            lon = mOriginLocPoint.getLongitude() + (factor * (mTargetLocPoint.getLongitude() - mOriginLocPoint.getLongitude()));
            mFlyTimeIndex++;
            mCurrentLocPoint.setLatitude(lat);
            mCurrentLocPoint.setLongitude(lon);
            return new LocPoint(mCurrentLocPoint);
        }
    }

    public boolean shouldContinue() {
        boolean is_done = (
                (!mIsFlyMode && (mFixedCountRemaining < 0))
            ||  ( mIsFlyMode && (mFlyTimeIndex > mFlyTime))
        );

        if (is_done) {
            stop();
        }

        return !is_done;
    }

    public void jumpToLocation(LocPoint location) {
        mIsFlyMode = false;
        mCurrentLocPoint = new LocPoint(location);
    }

    public void flyToLocation(LocPoint location, int trip_duration_seconds) {
        mOriginLocPoint = new LocPoint(mCurrentLocPoint);
        mTargetLocPoint = new LocPoint(location);
        mIsFlyMode      = true;
        mFlyTimeIndex   = 0;
        mFlyTime        = convertFlyTime_secondsToLoopIterations(trip_duration_seconds, mTimeInterval);
    }

    public boolean isFlyMode() {
        return mIsFlyMode;
    }

    public void stopFlyMode() {
        mIsFlyMode = false;
    }

    public boolean isRestApiMode() {
        return mIsRestApi;
    }

    public void startRestApiMode() {
        System.out.println("startRestApiMode");
        mIsRestApi = true;
    }

    public void stopRestApiMode() {
        System.out.println("stopRestApiMode");
        mIsRestApi = false;
    }

    // =================================
    // integrate with Shared Preferences
    // =================================

    @Override
    public void onSharedPrefsChange(short diff_fields) {
        importSharedPrefs();
    }

    private void importSharedPrefs() {
        if (mContext == null) return;

        SharedPrefsState prefsState = new SharedPrefsState(mContext, true);

        if (mFixedCount != prefsState.fixed_count) {
            mFixedCount = prefsState.fixed_count;

            if (mIsStarted && !mIsFlyMode) {
                mFixedCountRemaining = mFixedCount;
            }
        }

        mApiUrl =  prefsState.api_url;

        if (mTimeInterval != prefsState.time_interval) {
            updateFlyTime(prefsState.time_interval);

            mTimeInterval = prefsState.time_interval;

            if ((mLocationThread != null) && mLocationThread.isAlive()) {
                mLocationThread.updateTimeInterval(mTimeInterval);
            }
        }

        mTripHoldDestination    = prefsState.trip_hold_destination;

        /*
        mCurrentLocPoint = new LocPoint(prefsState.trip_origin_lat,      prefsState.trip_origin_lon);
        mOriginLocPoint  = new LocPoint(prefsState.trip_origin_lat,      prefsState.trip_origin_lon);
        mTargetLocPoint  = new LocPoint(prefsState.trip_destination_lat, prefsState.trip_destination_lon);
        mFlyTime         = prefsState.trip_duration;
        */
    }

    // =================================
    // mFlyTime counts the number of loop iterations that occur @ mTimeInterval
    // - when mTimeInterval changes, mFlyTime needs to be recalculated
    // - call this method BEFORE mTimeInterval is changed
    // =================================
    private void updateFlyTime(int new_time_interval) {
        if (!mIsStarted || !mIsFlyMode || (mFlyTimeIndex >= mFlyTime))
            return;

        int remaining_trip_duration_seconds    = convertFlyTime_loopIterationsToSeconds(mFlyTime - mFlyTimeIndex, mTimeInterval);
        int remaining_trip_duration_iterations = convertFlyTime_secondsToLoopIterations(remaining_trip_duration_seconds, new_time_interval);

        mOriginLocPoint = new LocPoint(mCurrentLocPoint);
        mFlyTimeIndex   = 0;
        mFlyTime        = remaining_trip_duration_iterations;
    }

    // =================================
    // static helpers
    // =================================

    private static int convertFlyTime_secondsToLoopIterations(int trip_duration_seconds, int time_interval) {
        // (1 loop iteration / time_interval ms)(1000 ms / 1 sec)(trip_duration_seconds secs)
        return (int) Math.ceil((1000f / time_interval) * trip_duration_seconds);
    }

    private static int convertFlyTime_loopIterationsToSeconds(int trip_duration_iterations, int time_interval) {
        // (time_interval ms / 1 loop iteration)(1 sec / 1000 ms)(trip_duration_iterations loop iterations)
        return (int) Math.ceil((time_interval / 1000f) * trip_duration_iterations);
    }

}
