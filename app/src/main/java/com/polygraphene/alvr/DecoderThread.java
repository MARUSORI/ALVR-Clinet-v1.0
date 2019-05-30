package com.polygraphene.alvr;

import android.media.MediaCodec;
import android.media.MediaFormat;
import android.support.annotation.NonNull;
import android.support.v4.app.NavUtils;
import android.util.Log;
import android.view.Surface;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.LinkedList;
import java.util.List;

class DecoderThread extends ThreadBase {
    private static final String TAG = "DecoderThread";

    private static final int CODEC_H264 = 0;
    private static final int CODEC_H265 = 1;
    private int mCodec = CODEC_H265;

    private static final String VIDEO_FORMAT_H264 = "video/avc";
    private static final String VIDEO_FORMAT_H265 = "video/hevc";
    private String mFormat = VIDEO_FORMAT_H265;

    private NALParser mNalParser;
    //private MediaCodec mDecoder = null;
    public MediaCodec mDecoder =null;
    private Surface mSurface;

    private boolean mWaitNextIDR = false;

    @SuppressWarnings("unused")
    private MainActivity mMainActivity = null;

    private boolean mDebugIDRFrame = false;

    private int mBufferIndex = -1;

    private OutputFrameQueue mQueue; // 输入数据的队列

    private static final int NAL_TYPE_SPS = 7;
    private static final int NAL_TYPE_PPS = 8;
    private static final int NAL_TYPE_IDR = 5;
    private static final int NAL_TYPE_P = 1;

    private static final int H265_NAL_TYPE_TRAIL_R = 1;
    private static final int H265_NAL_TYPE_IDR_W_RADL = 19;
    private static final int H265_NAL_TYPE_VPS = 32;
    private static final int H265_NAL_TYPE_SPS = 33;
    private static final int H265_NAL_TYPE_PPS = 34;

    // Dummy SPS/PPS for some decoders which crashes on not set csd-0/csd-1. (e.g. Galaxy S6 Exynos decoder)
    byte[] DummySPS = new byte[]{(byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x01, (byte) 0x67, (byte) 0x64, (byte) 0x00, (byte) 0x20, (byte) 0xac, (byte) 0x2b, (byte) 0x40, (byte) 0x20,
            0x02, (byte) 0x0d, (byte) 0x80, (byte) 0x88, (byte) 0x00, (byte) 0x00, (byte) 0x1f, (byte) 0x40, (byte) 0x00, (byte) 0x0e, (byte) 0xa6, (byte) 0x04,
            0x7a, (byte) 0x55};
    byte[] DummyPPS = new byte[]{(byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x01, (byte) 0x68, (byte) 0xee, (byte) 0x3c, (byte) 0xb0};
    int DummyWidth = 1024;
    int DummyHeight = 512;

    byte[] DummyCSD_H265 = new byte[]{
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x01, (byte) 0x40, (byte) 0x01, (byte) 0x0c, (byte) 0x01, (byte) 0xff, (byte) 0xff, (byte) 0x21, (byte) 0x40,
            (byte) 0x00, (byte) 0x00, (byte) 0x03, (byte) 0x00, (byte) 0x00, (byte) 0x03, (byte) 0x00, (byte) 0x00, (byte) 0x03, (byte) 0x00, (byte) 0x00, (byte) 0x03,
            (byte) 0x00, (byte) 0x78, (byte) 0xac, (byte) 0x09, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x01, (byte) 0x42, (byte) 0x01, (byte) 0x01, (byte) 0x21,
            (byte) 0x40, (byte) 0x00, (byte) 0x00, (byte) 0x03, (byte) 0x00, (byte) 0x00, (byte) 0x03, (byte) 0x00, (byte) 0x00, (byte) 0x03, (byte) 0x00, (byte) 0x00,
            (byte) 0x03, (byte) 0x00, (byte) 0x78, (byte) 0xa0, (byte) 0x02, (byte) 0x00, (byte) 0x80, (byte) 0x20, (byte) 0x16, (byte) 0x5a, (byte) 0xd2, (byte) 0x90,
            (byte) 0x96, (byte) 0x4b, (byte) 0x8c, (byte) 0x04, (byte) 0x04, (byte) 0x00, (byte) 0x00, (byte) 0x03, (byte) 0x00, (byte) 0x04, (byte) 0x00, (byte) 0x00,
            (byte) 0x03, (byte) 0x00, (byte) 0xf0, (byte) 0x20, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x01, (byte) 0x44, (byte) 0x01, (byte) 0xc0, (byte) 0xf7,
            (byte) 0xc0, (byte) 0xcc, (byte) 0x90
    };

    private final List<Integer> mAvailableInputs = new LinkedList<>();


    // === jay fix
    public void satSurface(Surface surface) {
        mSurface = surface;
    }

    public void onSurfaceCreated(Surface surface) {
        this.mSurface=surface;
    }

    public void onSurfaceChanged(Surface surface) {

    }

    public void onSurfaceDestroyed() {
    }


    DecoderThread(NALParser nalParser,
                  Surface surface, MainActivity mainActivity) {
        mNalParser = nalParser;
        mMainActivity = mainActivity;

        Log.e("jay", "DecoderThread: "+ surface);
        //mSurface = null;
        mSurface = surface;



    }

    private void frameLog(String s) {
        Log.v(TAG, s);
    }

    public void start() {
        super.startBase();
    }

    public void interrupt() {
        super.interrupt();
        synchronized (mAvailableInputs) {
            mAvailableInputs.notifyAll();
        }
        mNalParser.notifyWaitingThread();
    }

    // jay fix
    public static int fcnt=0;

    protected void run() {
        try {
            Log.d("jay", "DecoderThread::run start decodeLoop"+mSurface);
            decodeLoop();
        } catch (IOException | InterruptedException | IllegalStateException e) {
            e.printStackTrace();
            Log.v(TAG, "DecoderThread stopped by Exception.");
        }
        finally {
            Log.v(TAG, "Stopping decoder.");
            if (mDecoder != null) {
                try {
                    mDecoder.stop();
                    mDecoder.release();
                } catch (IllegalStateException e) {
                    e.printStackTrace();
                }
            }
        }
        Log.v(TAG, "DecoderThread stopped.");
    }

    // jay fix
    byte[] ReadAnnexBformatBitStream(String path){
        byte[] bytes=null;
        FileInputStream fis = null;
        try {
            fis = new FileInputStream(path);
            // 1.从FileInputStream对象获取文件通道FileChannel
            FileChannel channel = fis.getChannel();
            int fileSize = (int) channel.size();

            // 2.从通道读取文件内容
            ByteBuffer byteBuffer = ByteBuffer.allocate(fileSize);

            // channel.read(ByteBuffer) 方法就类似于 inputstream.read(byte)
            // 每次read都将读取 allocate 个字节到ByteBuffer
            channel.read(byteBuffer);
            // 注意先调用flip方法反转Buffer,再从Buffer读取数据
            byteBuffer.flip();
            // 可以将当前Buffer包含的字节数组全部读取出来
            bytes = byteBuffer.array();
            byteBuffer.clear();
            // 关闭通道和文件流
            channel.close();
            fis.close();
        } catch (IOException e) {
            e.printStackTrace();
        }finally {

        }
        return bytes;
    }

    private void decodeLoop() throws InterruptedException, IOException {
        synchronized (mAvailableInputs) {
            mAvailableInputs.clear();
        }

        MediaFormat format = MediaFormat.createVideoFormat(mFormat, DummyWidth, DummyHeight);
        format.setString("KEY_MIME", mFormat);

        if (mCodec == CODEC_H264) {
            format.setByteBuffer("csd-0", ByteBuffer.wrap(DummySPS, 0, DummySPS.length));
            format.setByteBuffer("csd-1", ByteBuffer.wrap(DummyPPS, 0, DummyPPS.length));
        } else {
            format.setByteBuffer("csd-0", ByteBuffer.wrap(DummyCSD_H265, 0, DummyCSD_H265.length));
        }

        // ==== 配置解码器
        mDecoder = MediaCodec.createDecoderByType(mFormat);

        mQueue = new OutputFrameQueue(mDecoder);

        mDecoder.setVideoScalingMode(MediaCodec.VIDEO_SCALING_MODE_SCALE_TO_FIT);
        // 设置回调函数,最后获得解码完的数据
        mDecoder.setCallback(new Callback());
        // 设置Surface
        Log.d("jay", "decodeLoop configure Surface: "+mSurface);
        mDecoder.configure(format, mSurface, null, 0);
        // 启动解码器
        mDecoder.start();

        Log.v(TAG, "Codec created. Type=" + mFormat + " Name=" + mDecoder.getCodecInfo().getName());

        mWaitNextIDR = true;

        //Log.e("jay", "decodeLoop::mStopped: "+ isStopped());

        // === 下面就是获取网络数据,进行码流数据解析,送入解码器
        while (!isStopped()) {

            Log.d("jay", "decodeLoop before mNalParser.waitNal()");
            // 解析网络包
            NAL nal = mNalParser.waitNal();

            //NAL nal= new NAL();
            // jay fix
            // 在这里使用循环读入一帧H.265的码流进行解码.
            //Log.d("jay", "ReadAnnexBformatBitStream.....");
            //String hevcbin = null;
            //hevcbin = "/storage/emulated/0/Download/shark_singleframe.H265";
            //byte [] bytes = ReadAnnexBformatBitStream(hevcbin);
            //nal.buf = bytes;
            //nal.length=bytes.length;
            //nal.frameIndex=fcnt++;
            // end of jay fix

            if (nal == null) {
                Log.v("jay", "decodeLoop Stopped. nal==null.");
                break;
            }
            if (isStopped()) {
                Log.v("jay", "decodeLoop Stopped. mStopped==true.");
                mNalParser.recycleNal(nal);
                break;
            }

            Log.d("jay", "decodeLoop: nal != null");

            int NALType;

            if (mCodec == CODEC_H264) {
                NALType = nal.buf[4] & 0x1F;
            } else { // CODEC_H265
                NALType = (nal.buf[4] >> 1) & 0x3F;
            }

            Utils.frameLog(nal.frameIndex, "Got NAL Type=" + NALType + " Length=" + nal.length + " QueueSize=" + mNalParser.getNalListSize());

            // jay fix
            //mCodec = CODEC_H265;
            //NALType = H265_NAL_TYPE_IDR_W_RADL;
            // end jay fix

            long presentationTime = System.nanoTime() / 1000;

            if ((mCodec == CODEC_H264 && NALType == NAL_TYPE_SPS) ||
                    (mCodec == CODEC_H265 && NALType == H265_NAL_TYPE_VPS)) {
                // (VPS + )SPS + PPS
                Utils.frameLog(nal.frameIndex, "jay Feed codec config. Size=" + nal.length + " Codec=" + mCodec + " NALType=" + NALType);

                mWaitNextIDR = false;

                sendInputBuffer(nal, 0, MediaCodec.BUFFER_FLAG_CODEC_CONFIG);

                mNalParser.recycleNal(nal);
            }
            else if ((mCodec == CODEC_H264 && NALType == NAL_TYPE_IDR) ||
                    (mCodec == CODEC_H265 && NALType == H265_NAL_TYPE_IDR_W_RADL)) {
                // IDR-Frame
                Utils.frameLog(nal.frameIndex, "jay Feed IDR-Frame. Size=" + nal.length + " PresentationTime=" + presentationTime);

                LatencyCollector.DecoderInput(nal.frameIndex);

                // debug
//                for(int i=0;i<5;i++){
//                    sendInputBuffer(nal, presentationTime+i, 0);
//                }

                // 将数据送入解码器
                sendInputBuffer(nal, presentationTime, 0);
                mNalParser.recycleNal(nal);
            }
            else {
                // PFrame
                LatencyCollector.DecoderInput(nal.frameIndex);

                if (mWaitNextIDR) {
                    // Ignore P-Frame until next I-Frame
                    Utils.frameLog(nal.frameIndex, "jay Ignoring P-Frame");
                }
                else {
                    // P-Frame
                    Utils.frameLog(nal.frameIndex, "jay Feed P-Frame. Size=" + nal.length + " PresentationTime=" + presentationTime);

                    sendInputBuffer(nal, presentationTime, 0);
                }
                mNalParser.recycleNal(nal);
            }



        } // while(!stop);
    }

    // Output IDR frame in external media dir for debugging. (/sdcard/Android/media/...)
    private void debugIDRFrame(NAL buf, NAL spsBuffer, NAL ppsBuffer) {
        if (spsBuffer == null || ppsBuffer == null) {
            return;
        }
        if (mDebugIDRFrame) {
            try {
                String path = mMainActivity.getExternalMediaDirs()[0].getAbsolutePath() + "/" + buf.frameIndex + ".h264";
                FileOutputStream stream = new FileOutputStream(path);
                stream.write(spsBuffer.buf, 0, spsBuffer.length);
                stream.write(ppsBuffer.buf, 0, ppsBuffer.length);
                stream.write(buf.buf, 0, buf.length);
                stream.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private ByteBuffer getInputBuffer(NAL nal) throws InterruptedException {
        Utils.frameLog(nal.frameIndex, "Wait next input buffer.");
        while (true) {
            synchronized (mAvailableInputs) {
                if (isStopped()) {
                    throw new InterruptedException();
                }
                if (mAvailableInputs.size() > 0) {
                    mBufferIndex = mAvailableInputs.get(0);
                    mAvailableInputs.remove(0);
                    break;
                }
                mAvailableInputs.wait();
            }
        }

        ByteBuffer buffer = mDecoder.getInputBuffer(mBufferIndex);
        Utils.frameLog(nal.frameIndex, "Uses input index=" + mBufferIndex + " NAL QueueSize=" + mNalParser.getNalListSize()
                + " Buffer capacity=" + buffer.remaining());
        return buffer;
    }

    private void sendInputBuffer(NAL nal, long presentationTimeUs, int flags) throws InterruptedException {
        if (presentationTimeUs != 0) {
            Log.e("jay", "sendInputBuffer: "+ nal.frameIndex);
            mQueue.pushInputBuffer(presentationTimeUs, nal.frameIndex);
        }

        int remain = nal.length;
        while (remain > 0) {

            // // 就是获取空闲索引号对应的buffer
            ByteBuffer buffer = getInputBuffer(nal);
            int copyLength = Math.min(nal.length, buffer.remaining());
            // 将解析的数据放入buffer中
            buffer.put(nal.buf, 0, copyLength);
            // queueInputBuffer 送码流数据
            mDecoder.queueInputBuffer(mBufferIndex, 0, buffer.position(), presentationTimeUs, flags);
            remain -= copyLength;
            Log.e("jay", "mDecoder.queueInputBuffer length: "+copyLength);

            if (remain > 0) {
                String name = mDecoder.getCodecInfo().getName();
                Utils.frameLog(nal.frameIndex, "Splitting input buffer for codec. NAL Size="
                        + nal.length + " copyLength=" + copyLength + " codec=" + name);
            }
        }
        mBufferIndex = -1;
    }

    class Callback extends MediaCodec.Callback {
        @Override
        public void onInputBufferAvailable(@NonNull MediaCodec codec, int index) {
            //frameLog("onInputBufferAvailable " + index + " " + mBuf);
            Log.e("jay", "MediaCodec::onInputBufferAvailable: "+index );
            synchronized (mAvailableInputs) {
                mAvailableInputs.add(index);
                mAvailableInputs.notifyAll();
            }
        }

        @Override
        public void onOutputBufferAvailable(@NonNull MediaCodec codec, int index, @NonNull MediaCodec.BufferInfo info) {
            Log.e("jay", "MediaCodec::onOutputBufferAvailable: "+index );
            mQueue.pushOutputBuffer(index, info);
        }

        @Override
        public void onError(@NonNull MediaCodec codec, @NonNull MediaCodec.CodecException e) {
            Log.e("DecodeActivity", "Codec Error: " + e.getMessage() + "\n" + e.getDiagnosticInfo());
        }

        @Override
        public void onOutputFormatChanged(@NonNull MediaCodec codec, @NonNull MediaFormat format) {
            Log.d("DecodeActivity", "New format " + mDecoder.getOutputFormat());
        }
    }

    public boolean isFrameAvailable() {
        if(mQueue!=null){
            Log.d("jay", "isFrameAvailable: "+mQueue.isFrameAvailable());
        }
        return mQueue != null && mQueue.isFrameAvailable();
    }

    public long render() {

        Log.e("jay", "DecoderThread.render(): " +mSurface);
        if (mQueue == null) {
            Log.e("jay", "DecoderThread.render(): mQueue == null");
            return -1;
        }
        return mQueue.render();
    }

    public void onConnect(int codec, int frameQueueSize) {
        if (mQueue != null) {
            mQueue.reset();
            setFrameQueueSize(frameQueueSize);
        }
        notifyCodecChange(codec);
    }

    public void onDisconnect() {
        mQueue.stop();
    }

    private void notifyCodecChange(int codec) {
        if (codec != mCodec) {
            stopAndWait();
            mCodec = codec;
            if (mCodec == CODEC_H264) {
                mFormat = VIDEO_FORMAT_H264;
            } else {
                mFormat = VIDEO_FORMAT_H265;
            }
            mNalParser.clearStopped();
            start();
        } else {
            mWaitNextIDR = true;
        }
    }

    public void setFrameQueueSize(int frameQueueSize) {
        if (mQueue != null) {
            mQueue.setQueueSize(frameQueueSize);
        }
    }
}
