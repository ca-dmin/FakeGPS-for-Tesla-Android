package com.github.ca_dmin.fakegps_for_tesla_android.service.looper;

// copied from:
//   https://github.com/xiangtailiang/FakeGPS/blob/V1.1/app/src/main/java/com/github/fakegps/LocationThread.java

import com.github.ca_dmin.fakegps_for_tesla_android.data_model.LocPoint;

import android.content.Context;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Process;
import android.provider.Settings;

public class LocationThread extends HandlerThread {
    private Context mContext;
    private LocationThreadManager mLocationThreadManager;
    private int mTimeInterval;
    private Handler mHandler;

    public LocationThread(Context context, LocationThreadManager locationThreadManager, int timeInterval) {
        super("LocationThread", Process.THREAD_PRIORITY_MORE_FAVORABLE);

        mContext = context;
        mLocationThreadManager = locationThreadManager;
        mTimeInterval = timeInterval;
    }

    @Override
    public synchronized void start() {
        super.start();

        mHandler = new Handler(getLooper());
        mHandler.post(mUpdateLocation);
    }

    public void startThread() {
        MockLocationProviderManager.startMockingLocation(mContext);

        start();
    }

    public void stopThread() {
        MockLocationProviderManager.stopMockingLocation();

        mHandler.removeCallbacksAndMessages(null);
        try {
            quit();
            interrupt();
        }
        catch (Exception e) {}
        mLocationThreadManager = null;
    }

    public void updateTimeInterval(int timeInterval) {
        mTimeInterval = timeInterval;
    }

    Runnable mUpdateLocation = new Runnable() {
        @Override
        public void run() {
            LocPoint locPoint = mLocationThreadManager.getUpdateLocPoint();
            if (locPoint != null) {
                MockLocationProviderManager.exec(
                        locPoint.getLatitude(),
                        locPoint.getLongitude(),
                        locPoint.getBearing(),
                        locPoint.getSpeed()
                        );
            }
            try {
                if (mLocationThreadManager.shouldContinue()) {
                    mHandler.postDelayed(mUpdateLocation, mTimeInterval);
                }
            } catch (Exception e) {}

        }
    };

    public Handler getHandler() {
        return mHandler;
    }
}
