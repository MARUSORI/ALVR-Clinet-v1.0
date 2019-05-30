package com.polygraphene.alvr;

import android.util.Log;

import java.util.LinkedList;

public class ThreadQueue {
    private LinkedList<Runnable> mQueue = new LinkedList<>();
    private boolean mStopped = false;

    synchronized public void post(Runnable runnable) {
        if(mStopped) {
            return;
        }
        mQueue.addLast(runnable);
        notifyAll();
    }

    // Post runnable and wait completion.
    public void send(Runnable runnable) {
        synchronized (this) {
            if(mStopped) {
                return;
            }
            mQueue.addLast(runnable);
            notifyAll();

            while(mQueue.contains(runnable)) {
                try {
                    wait();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public boolean waitIdle() {
        Runnable runnable;
        while ((runnable = next()) != null) {

            Log.d("jay", "waitIdle");

            if (mStopped) {
                Log.d("jay", "mStopped");
                return false;
            }
            runnable.run();
            synchronized (this) {
                // Notify queue change for threads waiting completion of "send" method.
                notifyAll();
                Log.d("jay", "Notify queue change for threads waiting completion of send method");
                mQueue.removeFirst();
            }
        }
        if(mStopped) {
            Log.d("jay", "waitIdle mStopped");
            return false;
        }
        Log.d("jay", "waitIdle return true");
        return true;
    }

    public void interrupt() {
        mStopped = true;
        post(null);
    }

    synchronized private Runnable next() {
        if (mQueue.size() == 0) {
            return null;
        }
        return mQueue.getFirst();
    }

    synchronized public void waitNext() {
        while(mQueue.size() == 0){
            Log.d("jay", "waitNext: mQueue.size() == 0");
            try {
                wait();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }
}
