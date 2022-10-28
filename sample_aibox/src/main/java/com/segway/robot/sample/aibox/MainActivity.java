package com.segway.robot.sample.aibox;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.RectF;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import com.segway.robot.sdk.vision.BindStateListener;
import com.segway.robot.sdk.vision.Vision;
import com.segway.robot.sdk.vision.calibration.RS2Intrinsic;
import com.segway.robot.sdk.vision.frame.Frame;
import com.segway.robot.sdk.vision.stream.PixelFormat;
import com.segway.robot.sdk.vision.stream.Resolution;
import com.segway.robot.sdk.vision.stream.VisionStreamType;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = MainActivity.class.getSimpleName();
    private static final String LOCAL_IMAGE_PATH = "sdcard/apple.jpeg";
    private static final int REQUEST_CODE = 1;
    private static String[] PERMISSIONS_STORAGE = {"android.permission.READ_EXTERNAL_STORAGE",
            "android.permission.WRITE_EXTERNAL_STORAGE"};
    private VisionImageView mImageView;
    private volatile boolean mIsBind;
    private volatile boolean mIsDetecting;
    private volatile boolean mIsImageStarted;
    private volatile boolean mIsCameraStarted;
    private Bitmap mBitmap;
    private Thread mWorkThread;
    private final Object mBitmapLock = new Object();
    private Button mBtnOpenImage;
    private Button mBtnCloseImage;
    private Button mBtnOpenCamera;
    private Button mBtnCloseCamera;
    private Button mBtnStart;
    private Button mBtnStop;
    private ByteBuffer mData;
    private DetectedResult[] mDetectedResults;
    private List<RectF> mRectList = new ArrayList<>();

    static {
        System.loadLibrary("vision_aibox");
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mImageView = findViewById(R.id.image);
        mBtnOpenImage = findViewById(R.id.btn_open_image);
        mBtnCloseImage = findViewById(R.id.btn_close_image);
        mBtnOpenCamera = findViewById(R.id.btn_open_camera);
        mBtnCloseCamera = findViewById(R.id.btn_close_camera);
        mBtnStart = findViewById(R.id.btn_start);
        mBtnStop = findViewById(R.id.btn_stop);
        checkPermission();
        resetUI();
    }

    private void resetUI() {
        mBtnOpenCamera.setEnabled(true);
        mBtnOpenImage.setEnabled(true);
        mBtnStart.setEnabled(false);
        mBtnStop.setEnabled(false);
        mBtnCloseCamera.setEnabled(false);
        mBtnCloseImage.setEnabled(false);
    }

    private void checkPermission() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission
                    .WRITE_EXTERNAL_STORAGE)) {
                Toast.makeText(this, "Please open the relevant permissions, otherwise you can not use this application normally!", Toast.LENGTH_SHORT).show();
            }
            ActivityCompat.requestPermissions(this, PERMISSIONS_STORAGE, REQUEST_CODE);
        }
    }

    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.btn_open_image:
                openImage();
                break;
            case R.id.btn_close_image:
                closeImage();
                break;
            case R.id.btn_open_camera:
                openCamera();
                break;
            case R.id.btn_close_camera:
                closeCamera();
                break;
            case R.id.btn_start:
                startDetect();
                break;
            case R.id.btn_stop:
                stopDetect();
                break;
            default:
                break;
        }
    }

    private synchronized void openImage() {

        if (mIsImageStarted) {
            return;
        }

        mBtnOpenCamera.setEnabled(false);

        mIsImageStarted = true;
        mWorkThread = new ImageWorkThread();
        mWorkThread.start();

        mBtnStart.setEnabled(true);
        mBtnCloseImage.setEnabled(true);
        mBtnOpenImage.setEnabled(false);

    }

    private synchronized void closeImage() {
        if (mIsDetecting) {
            stopDetect();
        }
        mIsImageStarted = false;
        if (mWorkThread != null) {
            try {
                mWorkThread.interrupt();
                mWorkThread.join();
            } catch (Exception e) {
                e.printStackTrace();
            }
            mWorkThread = null;
        }
        resetUI();
    }

    private synchronized void openCamera() {
        mIsCameraStarted = true;
        mBtnOpenImage.setEnabled(false);
        bindAndStartVision();
    }

    private synchronized void closeCamera() {
        mIsCameraStarted = false;
        if (mIsDetecting) {
            stopDetect();
        }
        unbindAndStopVision();
        if (mWorkThread != null) {
            try {
                mWorkThread.interrupt();
                mWorkThread.join();
            } catch (Exception e) {
                e.printStackTrace();
            }
            mWorkThread = null;
        }
        resetUI();
    }

    private synchronized void startDetect() {
        if (mIsDetecting) {
            return;
        }
        mIsDetecting = true;

        mBtnStop.setEnabled(true);
        mBtnStart.setEnabled(false);
    }


    private synchronized void stopDetect() {
        mIsDetecting = false;

        mBtnStop.setEnabled(false);
        mBtnStart.setEnabled(true);
    }

    private void bindAndStartVision() {
        //bind Vision Service
        boolean ret = Vision.getInstance().bindService(this, new BindStateListener() {
            @Override
            public void onBind() {
                Log.d(TAG, "onBind");
                mIsBind = true;
                try {
                    //Obtain internal calibration data
//                    RS2Intrinsic intrinsics = Vision.getInstance().getIntrinsics(VisionStreamType.FISH_EYE);
//                    Log.d(TAG, "intrinsics: " + intrinsics);
                    Vision.getInstance().startVision(VisionStreamType.FISH_EYE);

                    mWorkThread = new VisionWorkThread();
                    mWorkThread.start();
                    mBtnOpenCamera.setEnabled(false);
                    mBtnStart.setEnabled(true);
                    mBtnCloseCamera.setEnabled(true);

                } catch (Exception e) {
                    Log.d(TAG, "error:", e);
                }
            }

            @Override
            public void onUnbind(String reason) {
                Log.d(TAG, "onUnbind");
                mIsBind = false;
                mBtnOpenCamera.setEnabled(true);
            }
        });
        if (!ret) {
            Log.d(TAG, "Vision Service does not exist");
        }
    }

    private void unbindAndStopVision() {
        try {
            Vision.getInstance().stopVision(VisionStreamType.FISH_EYE);
        } catch (Exception e) {
            Log.d(TAG, "error:", e);
        }
        Vision.getInstance().unbindService();
    }

    private void showImage() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                synchronized (mBitmapLock) {
                    mRectList.clear();
                    if (mDetectedResults != null) {
                        for (DetectedResult result : mDetectedResults) {
                            mRectList.add(new RectF(result.x1/4, result.y1/4, result.x2/4, result.y2/4.));
                        }
                    }
                    mImageView.mark(mRectList);
                    mImageView.setImageBitmap(mBitmap);
                }
            }
        });
    }

    class ImageWorkThread extends Thread {
        @Override
        public void run() {
            while (mIsImageStarted) {
                synchronized (mBitmapLock) {
                    mBitmap = BitmapFactory.decodeFile(LOCAL_IMAGE_PATH);
                    if (mBitmap == null) {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                Toast.makeText(MainActivity.this, "The picture does not exist!", Toast.LENGTH_SHORT).show();
                            }
                        });
                        break;
                    }
                    if (mIsDetecting) {
                        int size = mBitmap.getByteCount();
                        if (mData == null || mData.capacity() != size) {
                            mData = ByteBuffer.allocateDirect(size);
                        }
                        mData.rewind();
                        Bitmap bitmap = mBitmap.copy(mBitmap.getConfig(), true);
                        mBitmap.copyPixelsFromBuffer(mData);
                        mBitmap = bitmap;
                        mDetectedResults = VisionNative.nativeDetect(mData, PixelFormat.RGBA8888, mBitmap.getWidth(), mBitmap.getHeight());
                    } else {
                        mDetectedResults = null;
                    }
                }
                showImage();
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            clearBitmap();
        }
    }

    private void clearBitmap() {
        synchronized (mBitmapLock) {
            mBitmap = null;
            mDetectedResults = null;
        }
        showImage();
    }

    class VisionWorkThread extends Thread {
        @Override
        public void run() {
            while (mIsCameraStarted && mIsBind) {
                long startTs = System.currentTimeMillis();
                try {
                    Frame frame = Vision.getInstance().getLatestFrame(VisionStreamType.FISH_EYE);
                    Log.d(TAG, "ts: " + frame.getInfo().getPlatformTimeStamp() + "  " + frame.getInfo().getIMUTimeStamp());
                    int resolution = frame.getInfo().getResolution();
                    int width = Resolution.getWidth(resolution);
                    int height = Resolution.getHeight(resolution);
                    synchronized (mBitmapLock) {
                        if (mBitmap == null) {
                            mBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
                        }
                    }
                    int pixelFormat = frame.getInfo().getPixelFormat();
                    if (pixelFormat == PixelFormat.YUV420 || pixelFormat == PixelFormat.YV12) {
                        int limit = frame.getByteBuffer().limit();

                        if (mIsDetecting) {
                            if (mData == null || mData.capacity() != limit) {
                                mData = ByteBuffer.allocateDirect(limit);
                            }
                            frame.getByteBuffer().position(0);
                            mData.rewind();
                            mData.put(frame.getByteBuffer());
                            synchronized (mBitmapLock) {
                                mDetectedResults = VisionNative.nativeDetect(mData, pixelFormat, width, height);
                            }
                        } else {
                            synchronized (mBitmapLock) {
                                mDetectedResults = null;
                            }
                        }
                        byte[] buff = new byte[limit];
                        frame.getByteBuffer().position(0);
                        frame.getByteBuffer().get(buff);
                        synchronized (mBitmapLock) {
                            yuv2RGBBitmap(buff, mBitmap, width, height);
                        }

                    } else {
                        Log.d(TAG, "An unsupported format");
                    }
                    if (mBitmap != null) {
                        showImage();
                    }
                    Vision.getInstance().returnFrame(frame);
                } catch (Exception e) {
                    e.printStackTrace();
                }
                long endTs = System.currentTimeMillis();
                long interval = 100 - (endTs - startTs);
                if (interval > 0) {
                    try {
                        Thread.sleep(interval);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }

            clearBitmap();
        }
    }

    private void yuv2RGBBitmap(byte[] data, Bitmap bitmap, int width, int height) {
        int frameSize = width * height;
        int[] rgba = new int[frameSize];
        for (int i = 0; i < height; i++) {
            for (int j = 0; j < width; j++) {
                int y = (0xff & ((int) data[i * width + j]));
                int v = (0xff & ((int) data[frameSize + (i >> 1) * width + (j & ~1) + 0]));

                int u = (0xff & ((int) data[frameSize + (i >> 1) * width + (j & ~1) + 1]));

                y = y < 16 ? 16 : y;
                int r = Math.round(1.164f * (y - 16) + 1.596f * (v - 128));
                int g = Math.round(1.164f * (y - 16) - 0.813f * (v - 128) - 0.391f * (u - 128));
                int b = Math.round(1.164f * (y - 16) + 2.018f * (u - 128));
                r = r < 0 ? 0 : (r > 255 ? 255 : r);
                g = g < 0 ? 0 : (g > 255 ? 255 : g);
                b = b < 0 ? 0 : (b > 255 ? 255 : b);
                rgba[i * width + j] = 0xff000000 + (b << 16) + (g << 8) + r;
            }
        }
        bitmap.setPixels(rgba, 0, width, 0, 0, width, height);
    }
}
