package com.polygraphene.alvr;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.SurfaceTexture;
import android.media.MediaCodec;
import android.opengl.EGL14;
import android.opengl.EGLContext;
import android.support.v4.app.NavUtils;
import android.util.Log;
import android.view.Surface;

class VrThread extends Thread {
    //private static final String TAG = "VrThread";
    private static final String TAG = "jay VrThread";

    private static final String KEY_SERVER_ADDRESS = "serverAddress";
    private static final String KEY_SERVER_PORT = "serverPort";

    private static final int PORT = 9944;

    private MainActivity mMainActivity; // 上下文

    //private VrContext mVrContext = new VrContext(); // vr设备上下文
    private VrContext mVrContext = null;
    private ThreadQueue mQueue = null;
    private boolean mResumed = false;

    private SurfaceTexture mSurfaceTexture;
    private Surface mSurface=null;

    private final Object mWaiter = new Object();
    private boolean mFrameAvailable = false;

    private LoadingTexture mLoadingTexture = new LoadingTexture();

    // Worker threads
    private DecoderThread mDecoderThread;
    private UdpReceiverThread mReceiverThread;

    private EGLContext mEGLContext;

    public VrThread(MainActivity mainActivity) {
        Log.d("jay", "VrThread::VrThread init mainActivity without surface");
        this.mMainActivity = mainActivity; // 保存上下文
    }

    public void onSurfaceCreated(final Surface surface) {
        Log.v("jay", "VrThread.onSurfaceCreated "+surface);
        // 存储创建的Surface
        mSurface=surface;

        send(new Runnable() {
            @Override
            public void run() {
                startDecoder();
            }
        });
    }
    public void onSurfaceChanged(final Surface surface) {
        Log.v("jay", "VrThread.onSurfaceChanged."+surface);
    }

    public void onSurfaceDestroyed() {
        Log.v("jay", "VrThread.onSurfaceDestroyed!");
    }

    // jay fix
    public void startDecoder(){

        mDecoderThread = new DecoderThread(mReceiverThread, mSurface, mMainActivity);
        try {
            mDecoderThread.start();

            if (!mReceiverThread.start(false, mEGLContext, mMainActivity)) {
                Log.e(TAG, "FATAL: Initialization of ReceiverThread failed.");
                return;
            }
        } catch (IllegalArgumentException | IllegalStateException | SecurityException e) {
            e.printStackTrace();
        }
    }

    public void onResume() {
        Log.e("jay", "VrThread::onResume");
        synchronized (mWaiter) {
            mResumed = true;
            mWaiter.notifyAll();
        }
        send(new Runnable() {
            @Override
            public void run() {
                    Log.v(TAG, "VrThread.onResume: Starting worker threads.");
                    // 启动传输线程
                    mReceiverThread = new UdpReceiverThread(mUdpReceiverCallback, mVrContext);
                    mReceiverThread.setPort(PORT);
                    loadConnectionState();
            }
        });
        Log.v(TAG, "VrThread.onResume: Worker threads has started.");
    }

    public void onPause() {
        Log.v(TAG, "VrThread.onPause: Stopping worker threads.");
        synchronized (mWaiter) {
            mResumed = false;
            mWaiter.notifyAll();
        }
        // DecoderThread must be stopped before ReceiverThread
        if (mDecoderThread != null) {
            Log.v(TAG, "VrThread.onPause: Stopping DecoderThread.");
            mDecoderThread.stopAndWait();
        }
        if (mReceiverThread != null) {
            Log.v(TAG, "VrThread.onPause: Stopping ReceiverThread.");
            mReceiverThread.stopAndWait();
        }

        Log.v(TAG, "VrThread.onPause: mVrContext.onPause().");
        send(new Runnable() {
            @Override
            public void run() {
                //mVrContext.onPause();
            }
        });
        Log.v(TAG, "VrThread.onPause: All worker threads has stopped.");
    }

    public void onKeyEvent(final int keyCode, final int action) {
//        post(new Runnable() {
//            @Override
//            public void run() {
//                mVrContext.onKeyEvent(keyCode, action);
//            }
//        });
    }

    // Called from onDestroy
    @Override
    public void interrupt() {
        post(new Runnable() {
            @Override
            public void run() {
                mLoadingTexture.destroyTexture();
                mQueue.interrupt();
            }
        });
    }

    private void post(Runnable runnable) {
        waitLooperPrepared();
        mQueue.post(runnable);
    }

    private void send(Runnable runnable) {
        waitLooperPrepared();
        mQueue.send(runnable);
    }

    // 检测线程队列是否为空.
    private void waitLooperPrepared() {
        synchronized (this) {
            while (mQueue == null) {
                try {
                    wait();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    @Override
    public void run() {

        Log.e("jay", "VrThread::run");

        setName("VrThread");
        synchronized (this) {
            // 创建一个线程队列
            mQueue = new ThreadQueue();
            notifyAll();
        }

//        mVrContext.initialize(mMainActivity, mMainActivity.getAssets(), Constants.IS_ARCORE_BUILD);
//        mSurfaceTexture = new SurfaceTexture(mVrContext.getSurfaceTextureID());
//        mSurfaceTexture.setOnFrameAvailableListener(new SurfaceTexture.OnFrameAvailableListener() {
//            @Override
//            public void onFrameAvailable(SurfaceTexture surfaceTexture) {
//                synchronized (mWaiter) {
//                    mFrameAvailable = true;
//                    mWaiter.notifyAll();
//                }
//            }
//        });
//
//        mSurface = new Surface(mSurfaceTexture);
//        mLoadingTexture.initializeMessageCanvas(mVrContext.getLoadingTexture());
//        mLoadingTexture.drawMessage(mMainActivity.getVersionName() + "\nLoading...");


        mEGLContext = EGL14.eglGetCurrentContext();

        Log.v(TAG, "Start loop of VrThread.");
        while (mQueue.waitIdle()) {
            // 等待 onResume 执行完成
            //mFrameAvailable = true;
            //mWaiter.notifyAll();
            if (!mResumed) {
                mQueue.waitNext();
                continue;
            }
            render();
        }

    }

    private void render() {
        Log.d("jay", "enter the VrThread Render");
        //Log.d("jay", "render: mReceiverThread.isConnected(): "+mReceiverThread.isConnected());
        //Log.d(TAG, "render: mReceiverThread.getErrorMessage(): "+mReceiverThread.getErrorMessage());
        //Log.d("jay", "render: mDecoderThread.isFrameAvailable(): "+mDecoderThread.isFrameAvailable());

        if (mReceiverThread.isConnected() && mDecoderThread.isFrameAvailable() && mReceiverThread.getErrorMessage() == null) {
            // 渲染
            Log.d("jay", "VrThread Render go to waitFrame");
            long renderedFrameIndex = waitFrame();
            if (renderedFrameIndex != -1) {
                //mVrContext.render(renderedFrameIndex);
                Log.d("jay", "should be mVrContext.render(renderedFrameIndex)");
            }
        }
        else {
            if (mReceiverThread.getErrorMessage() != null) {
                Log.e("jay", "render: mReceiverThread.getErrorMessage() != null");
                //mLoadingTexture.drawMessage("jay " + mMainActivity.getVersionName() + "\n \n!!! Error on ARCore initialization !!!\n" + mReceiverThread.getErrorMessage());
            } else {
                if (mReceiverThread.isConnected()) {
                    Log.e("jay", "render: mReceiverThread.isConnected() Connected! Streaming will begin soon!");
                    //mLoadingTexture.drawMessage("jay " + mMainActivity.getVersionName() + "\n \nConnected!\nStreaming will begin soon!");
                } else {
                    Log.e("jay", "render non ALVR server Press CONNECT button non ALVR server.");
                    //mLoadingTexture.drawMessage("jay " + mMainActivity.getVersionName() + "\n \nPress CONNECT button\non ALVR server.");
                }
            }
            //mVrContext.renderLoading();
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    private long waitFrame() {
        Log.e("jay", "VrThread::waitFrame !");
        synchronized (mWaiter) {
            mFrameAvailable = false;
            // 进行渲染
            long frameIndex = mDecoderThread.render();
            if (frameIndex == -1) {
                return -1;
            }
//            while (!mFrameAvailable) {
//                try {
//                    Log.e("jay", "wait for mFrameAvailable is true!!!");
//                    mWaiter.wait();
//                } catch (InterruptedException e) {
//                    e.printStackTrace();
//                }
//            }
//            mSurfaceTexture.updateTexImage();
            return frameIndex;
        }
    }

    private UdpReceiverThread.Callback mUdpReceiverCallback = new UdpReceiverThread.Callback() {
        @Override
        public void onConnected(final int width, final int height, final int codec, final int frameQueueSize) {
            // We must wait completion of notifyGeometryChange
            // to ensure the first video frame arrives after notifyGeometryChange.
            send(new Runnable() {
                @Override
                public void run() {
                    //mVrContext.setFrameGeometry(width, height);
                    mDecoderThread.onConnect(codec, frameQueueSize);
                }
            });
        }

        @Override
        public void onChangeSettings(int enableTestMode, int suspend, int frameQueueSize) {
            //mVrContext.onChangeSettings(enableTestMode, suspend);
            mDecoderThread.setFrameQueueSize(frameQueueSize);
        }

        @Override
        public void onShutdown(String serverAddr, int serverPort) {
            saveConnectionState(serverAddr, serverPort);
        }

        @Override
        public void onDisconnect() {
            mDecoderThread.onDisconnect();
        }
    };

    private void saveConnectionState(String serverAddress, int serverPort) {
        Log.v(TAG, "save connection state: " + serverAddress + " " + serverPort);
        SharedPreferences pref = mMainActivity.getSharedPreferences("pref", Context.MODE_PRIVATE);
        SharedPreferences.Editor edit = pref.edit();
        // If server address is NULL, it means no preserved connection.
        edit.putString(KEY_SERVER_ADDRESS, serverAddress);
        edit.putInt(KEY_SERVER_PORT, serverPort);
        edit.apply();
    }

    private void loadConnectionState() {
        SharedPreferences pref = mMainActivity.getSharedPreferences("pref", Context.MODE_PRIVATE);
        String serverAddress = pref.getString(KEY_SERVER_ADDRESS, null);
        int serverPort = pref.getInt(KEY_SERVER_PORT, 0);

        saveConnectionState(null, 0);

        Log.v(TAG, "load connection state: " + serverAddress + " " + serverPort);
        mReceiverThread.recoverConnectionState(serverAddress, serverPort);
    }

    public boolean isTracking() {
//        return mVrContext != null && mReceiverThread != null
//                && mVrContext.isVrMode() && mReceiverThread.isConnected();
        return mReceiverThread!=null&&mReceiverThread.isConnected();
    }
}
