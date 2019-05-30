package com.polygraphene.alvr;

import android.opengl.EGLContext;
import android.util.Log;

import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;


class UdpReceiverThread extends ThreadBase implements NALParser, TrackingThread.TrackingCallback {
    //private static final String TAG = "UdpReceiverThread";
    private static final String TAG = "jay UdpReceiverThread";

    static {
        System.loadLibrary("native-lib");
    }

    private static final String BROADCAST_ADDRESS = "255.255.255.255";

    private TrackingThread mTrackingThread;
    private VrContext mVrContext;
    private int mPort;
    private boolean mIs72Hz = false;
    private boolean mInitialized = false;
    private boolean mInitializeFailed = false;

    private String mPreviousServerAddress;
    private int mPreviousServerPort;

    interface Callback {
        void onConnected(int width, int height, int codec, int frameQueueSize);

        void onChangeSettings(int enableTestMode, int suspend, int frameQueueSize);

        void onShutdown(String serverAddr, int serverPort);

        void onDisconnect();
    }

    private Callback mCallback;

    UdpReceiverThread(Callback callback, VrContext vrContext) {
        mCallback = callback;
        mVrContext = vrContext;
    }

    public void setPort(int port) {
        mPort = port;
    }

    private String getDeviceName() {
        String manufacturer = android.os.Build.MANUFACTURER;
        String model = android.os.Build.MODEL;
        if (model.toLowerCase().startsWith(manufacturer.toLowerCase())) {
            return model;
        } else {
            return manufacturer + " " + model;
        }
    }

    public void recoverConnectionState(String serverAddress, int serverPort) {
        mPreviousServerAddress = serverAddress;
        mPreviousServerPort = serverPort;
    }

    public boolean start(final boolean is72Hz, EGLContext mEGLContext, MainActivity mainActivity) {
        // 创建跟踪器
        mTrackingThread = new TrackingThread(is72Hz ? 72 : 60);
        mTrackingThread.setCallback(this);
        mIs72Hz = is72Hz;

        super.startBase();

        synchronized (this) {
            while (!mInitialized && !mInitializeFailed) {
                try {
                    wait();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }

        if(!mInitializeFailed) {
            Log.d("jay", "start: mTrackingThread.start!!!!!!");
            //mTrackingThread.start(mEGLContext, mainActivity, mVrContext.getCameraTexture());

            //GLuint textures[textureCount];
            //glGenTextures(textureCount, textures);

            int[] textures = new int[1];
            GLES20.glGenTextures(1, textures, 0);
            int textureId = textures[0];
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId);

            GLES20.glTexParameterf(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER,
                    GLES20.GL_NEAREST);
            GLES20.glTexParameterf(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER,
                    GLES20.GL_LINEAR);

            mTrackingThread.start(mEGLContext, mainActivity, textureId);
        }
        return !mInitializeFailed;
    }

    @Override
    public void stopAndWait() {
        // TODO: need to fix when this function should be called by VrThread::onPause()
        if(mInitialized){
            Log.d(TAG, "stopAndWait! jay fix");
            mTrackingThread.stopAndWait();
            interruptNative();
            super.stopAndWait();
        }
    }

    @Override
    public void run() {
        try {
            String[] broadcastList = getBroadcastAddressList();

            int ret = initializeSocket(mPort, getDeviceName(), broadcastList, mIs72Hz);
            if (ret != 0) {
                Log.e(TAG, "Error on initializing socket. Code=" + ret + ".");
                synchronized (this) {
                    mInitializeFailed = true;
                    notifyAll();
                }
                return;
            }
            synchronized (this) {
                mInitialized = true;
                notifyAll();
            }
            Log.v(TAG, "UdpReceiverThread initialized.");

            runLoop(mPreviousServerAddress, mPreviousServerPort);
        } finally {
            mCallback.onShutdown(getServerAddress(), getServerPort());
            closeSocket();
        }

        Log.v(TAG, "UdpReceiverThread stopped.");
    }

    // List broadcast address from all interfaces except for mobile network.
    // We should send all broadcast address to use USB tethering or VPN.
    private String[] getBroadcastAddressList() {
        List<String> ret = new ArrayList<>();
        try {
            Enumeration<NetworkInterface> networkInterfaces = NetworkInterface.getNetworkInterfaces();

            while (networkInterfaces.hasMoreElements()) {
                NetworkInterface networkInterface = networkInterfaces.nextElement();

                if (networkInterface.getName().startsWith("rmnet")) {
                    // Ignore mobile network interfaces.
                    Log.v(TAG, "Ignore interface. Name=" + networkInterface.getName());
                    continue;
                }

                List<InterfaceAddress> interfaceAddresses = networkInterface.getInterfaceAddresses();

                String address = "";
                for (InterfaceAddress interfaceAddress : interfaceAddresses) {
                    address += interfaceAddress.toString() + ", ";
                    // getBroadcast() return non-null only when ipv4.
                    if (interfaceAddress.getBroadcast() != null) {
                        ret.add(interfaceAddress.getBroadcast().getHostAddress());
                    }
                }
                Log.v(TAG, "Interface: Name=" + networkInterface.getName() + " Address=" + address + " 2=" + address);
            }
            Log.v(TAG, ret.size() + " broadcast addresses were found.");
            for (String address : ret) {
                Log.v(TAG, address);
            }
        } catch (SocketException e) {
            e.printStackTrace();
        }
        if (ret.size() == 0) {
            ret.add(BROADCAST_ADDRESS);
        }
        return ret.toArray(new String[]{});
    }

    @Override
    public void onTracking(float[] position, float[] orientation) {

        Log.d(TAG, "onTracking: fetchTrackingInfo!");

        if (isTracking()) {
            Log.d(TAG, "onTracking: ready to send TrackInfo!");
            //mVrContext.fetchTrackingInfo(getPointer(), position, orientation); // 发送位置信息

            //jay fix
            Log.d(TAG, "onTracking: fetchTrackingInfo!!!"+orientation[0]+" "+ orientation[1]+" "+orientation[2]+" "+orientation[3]);

            long time=System.currentTimeMillis();

            // (数据类型)(最小值+Math.random()*(最大值-最小值+1))
//            float x = (float) Math.random();
//            float y = (float) Math.random();
//            float z = (float) Math.random();
//            float w = (float) Math.random();
//            orientation[0]=x;
//            orientation[1]=y;
//            orientation[2]=z;
//            orientation[3]=w;
//            Log.d(TAG, "jay Position onTracking: "+x+","+y+","+","+z+","+w);

            // 在这里调用udpManager来发送数据!!!
            // getPointer : 获得udpManager的地址 即调用底层的 g_udpManager.get();
            SendTrackingInfo(getPointer(),position,orientation);
        }

    }

    public boolean isTracking() {
        return isConnected();
    }

    public String getErrorMessage() {
        //return mTrackingThread.getErrorMessage();
        return null;
    }

    private void SendTrackingInfo(long udpManager, float[] position, float[] orientation){
        SendTrackingInfoNative(udpManager,position,orientation);
    }

    // called from native
    @SuppressWarnings("unused")
    public void onConnected(int width, int height, int codec, int frameQueueSize) {
        Log.v(TAG, "jay onConnected is called.");
        mCallback.onConnected(width, height, codec, frameQueueSize);
        // 设置追踪器状态为连接
        mTrackingThread.onConnect();
    }

    @SuppressWarnings("unused")
    public void onDisconnected() {
        Log.v(TAG, "jay onDisconnected is called.");
        mCallback.onDisconnect();
        mTrackingThread.onDisconnect();
    }

    @SuppressWarnings("unused")
    public void onChangeSettings(int EnableTestMode, int suspend, int frameQueueSize) {
        mCallback.onChangeSettings(EnableTestMode, suspend, frameQueueSize);
    }

    private native int initializeSocket(int port, String deviceName, String[] broadcastAddrList, boolean is72Hz);
    private native void closeSocket();
    private native void runLoop(String serverAddress, int serverPort);
    private native void interruptNative();

    public native boolean isConnected();

    private native long getPointer();
    private native String getServerAddress();
    private native int getServerPort();

    // jay fix

    // send Rotation and Position to Sever!
    private native void SendTrackingInfoNative(long udpManager, float[] position, float[] orientation);

    // endof jay fix

    //
    // NALParser interface
    //

    @Override
    public native int getNalListSize();

    @Override
    public native NAL waitNal();

    @Override
    public native NAL getNal();

    @Override
    public native void recycleNal(NAL nal);

    @Override
    public native void flushNALList();

    @Override
    public native void notifyWaitingThread();

    @Override
    public native void clearStopped();
}