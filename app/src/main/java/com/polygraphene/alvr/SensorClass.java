package com.polygraphene.alvr;

import android.app.Activity;
import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.util.Log;
import android.view.Surface;
import android.view.View;

/**
 * Created by Wang on 2018/3/9.
 */

public class SensorClass implements SensorEventListener {

    private static String TAG = "jay SensorClass";
    public SensorManager mSensorManager;
    public View mView;
    private int deviceRotation;

    private float[] originViewDirection = new float[4];
    private float[] currentViewDirection = new float[4];

    private Sensor mRotate;

    private float[] rotate = new float[16];
    private boolean isRotateSensorAvailable;
    private boolean isSensorsAvailable;
    private long lastRotateTime = 0;
    private int rotateNum = 0;

    //private native void setRotationMatrix(float [] rotate);

    // jay fix
    private float[] mOri = new float[4];

    public void onTracking(float[] position, float[] orientation){
        orientation[0]= mOri[0];
        orientation[1]= mOri[1];
        orientation[2]= mOri[2];
        orientation[3]= mOri[3];
        //Log.d(TAG, "getOrientation: "+orientation[0]+" "+orientation[1]+" "+orientation[2]+" "+orientation[3]);
    }

//    static {
//        System.loadLibrary("native-Sensor");
//    }

    public boolean isSensorsAvailable() {
        return isSensorsAvailable;
    }

    public SensorClass(Activity mainActivity, View view) {
        mSensorManager = (SensorManager) mainActivity.getSystemService(Context.SENSOR_SERVICE);


        mRotate = mSensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);

        isSensorsAvailable = (mRotate != null);
        mView = view;

        originViewDirection[0] = 0.0f;
        originViewDirection[1] = 0.0f;
        originViewDirection[2] = 1.0f;
        originViewDirection[3] = 0.0f;
    }

    public boolean registerSensors() {
        if (isSensorsAvailable == false) {
            return false;
        }
        isRotateSensorAvailable = mSensorManager.registerListener(this,mRotate,SensorManager.SENSOR_DELAY_NORMAL);

        isSensorsAvailable = isRotateSensorAvailable;
        return isSensorsAvailable;
    }

    public void unregisterSensors() {
        mSensorManager.unregisterListener(this);
    }

    @Override
    public void onSensorChanged(SensorEvent event) {

        Log.d(TAG, "onSensorChanged");

//        if (mView.getDisplay() == null) {
//            Log.d(TAG, "onSensorChanged: mView.getDisplay() == null");
//            return;
//        }

        //deviceRotation = mView.getDisplay().getRotation();

        deviceRotation = Surface.ROTATION_180;
        int axisX = SensorManager.AXIS_X;
        int axisY = SensorManager.AXIS_Y;

        switch (event.sensor.getType()) {
            case Sensor.TYPE_ROTATION_VECTOR: {
                switch (deviceRotation) {
                    case Surface.ROTATION_0:
                        axisX = SensorManager.AXIS_X;
                        axisY = SensorManager.AXIS_Y;
                        SensorManager.getRotationMatrixFromVector(rotate, event.values);
                        SensorManager.getQuaternionFromVector(mOri,event.values);
                        break;

                    case Surface.ROTATION_90:
                        axisX = SensorManager.AXIS_Y;
                        axisY = SensorManager.AXIS_MINUS_X;
                        float[] tmp1 = new float[16];
                        SensorManager.getRotationMatrixFromVector(tmp1, event.values);
                        SensorManager.getQuaternionFromVector(mOri,event.values);
                        SensorManager.remapCoordinateSystem(tmp1, axisX, axisY, rotate);
                        break;

                    case Surface.ROTATION_180:
                        axisX = SensorManager.AXIS_MINUS_X;
                        axisY = SensorManager.AXIS_MINUS_Y;
                        float[] tmp2 = new float[16];
                        SensorManager.getRotationMatrixFromVector(tmp2, event.values);
                        SensorManager.getQuaternionFromVector(mOri,event.values);
                        SensorManager.remapCoordinateSystem(tmp2, axisX, axisY, rotate);
                        break;

                    case Surface.ROTATION_270:
                        axisX = SensorManager.AXIS_MINUS_Y;
                        axisY = SensorManager.AXIS_X;
                        float[] tmp3 = new float[16];
                        SensorManager.getRotationMatrixFromVector(tmp3, event.values);
                        SensorManager.getQuaternionFromVector(mOri,event.values);
                        SensorManager.remapCoordinateSystem(tmp3, axisX, axisY, rotate);
                        break;

                    default:
                        break;
                }

//                double [] fixRotation = new double[4];
//                fixRotation[0] = Math.sqrt(2)/2.0;
//                fixRotation[1] = Math.sqrt(2)/2.0;
//                fixRotation[2] = Math.sqrt(2)/2.0;
//                fixRotation[3] = Math.sqrt(2)/2.0;

            }
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int state) {

    }
}
