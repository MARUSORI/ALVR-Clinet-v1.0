package com.polygraphene.alvr;

import android.opengl.EGLContext;
import android.util.Log;
import android.view.View;

import java.util.concurrent.TimeUnit;

// jay fix
// Sensor 传感器
import com.polygraphene.alvr.SensorClass;

class TrackingThread extends ThreadBase {
    private static final String TAG = "jay TrackingThread";
    private int mRefreshRate;

    interface TrackingCallback {
        void onTracking(float[] position, float[] orientation);
    }

    private TrackingCallback mCallback;
    private ArThread mArThread;

    // jay Fix
    private boolean isSensorOk = false;
    private SensorClass mSensor;
    private float[] mPosition = new float[4];
    private float[] mOrientation = new float[4];

   public TrackingThread(int refreshRate) {
        mRefreshRate = refreshRate;
    }

    public void setCallback(TrackingCallback callback) {
        mCallback = callback;
    }

    public void start(EGLContext mEGLContext, MainActivity mainActivity, int cameraTexture) {

        Log.d(TAG, "start: ready to startup ArThread");

        mArThread = new ArThread(mEGLContext);
        // 初始化
        mArThread.initialize(mainActivity);
        // 设置cameraTexure
        mArThread.setCameraTexture(cameraTexture);
        super.startBase();
        // 启动AR
        mArThread.start();

        mSensor = new SensorClass(mainActivity,new View(mainActivity));
        Log.d(TAG, "start: new SensorClass is done!");
        mSensor.registerSensors();
        isSensorOk =true;

    }

    public void onConnect() {
        mArThread.onConnect();
    }

    public void onDisconnect() {
        mArThread.onDisconnect();
    }

    @Override
    public void stopAndWait() {
        mArThread.stopAndWait();
        super.stopAndWait();
    }

    @Override
    public void run() {
        long previousFetchTime = System.nanoTime();


        while (!isStopped()) {

            //float [] ori = mArThread.getOrientation();
            //Log.d(TAG, "run: getOrientation: "+ori[0]+" "+ori[1]+" "+ori[2]+" "+ori[3]);

            //mCallback.onTracking(mArThread.getPosition(), mArThread.getOrientation());

            if(isSensorOk){
                mSensor.onTracking(mPosition,mOrientation);
                mCallback.onTracking(mPosition,mOrientation);
            }


            try {
                previousFetchTime += 1000 * 1000 * 1000 / mRefreshRate;
                long next = previousFetchTime - System.nanoTime();
                if (next < 0) {
                    // Exceed time!
                    previousFetchTime = System.nanoTime();
                } else {
                    TimeUnit.NANOSECONDS.sleep(next);
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        Log.v(TAG, "TrackingThread has stopped.");
    }

    public boolean onRequestPermissionsResult(MainActivity activity) {
        return mArThread.onRequestPermissionsResult(activity);
    }

    public String getErrorMessage() {
        return mArThread.getErrorMessage();
    }
}
