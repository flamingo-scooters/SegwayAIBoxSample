package com.segway.robot.sample.aibox;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.RectF;
import android.os.Bundle;
import android.os.Environment;
import android.os.PersistableBundle;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.Toast;

import com.segway.robot.sdk.vision.BindStateListener;
import com.segway.robot.sdk.vision.Vision;
import com.segway.robot.sdk.vision.calibration.RS2Intrinsic;
import com.segway.robot.sdk.vision.frame.Frame;
import com.segway.robot.sdk.vision.stream.PixelFormat;
import com.segway.robot.sdk.vision.stream.Resolution;
import com.segway.robot.sdk.vision.stream.VisionStreamType;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = MainActivity.class.getSimpleName();
    private static final String LOCAL_IMAGE_PATH = "sdcard/apple.jpeg";
    private static final int REQUEST_CODE = 1;
    private static String[] PERMISSIONS_STORAGE = {"android.permission.READ_EXTERNAL_STORAGE",
            "android.permission.WRITE_EXTERNAL_STORAGE"};
    private static final int BITMAP_SCALE = 4;
    private VisionImageView mImageView;
    private volatile boolean mIsBind;
    private volatile boolean mIsDetecting;
    private volatile boolean mIsImageStarted;
    private volatile boolean mIsCameraStarted;
    private volatile boolean shouldRecord = false;
    private Bitmap mBitmap;
    private Thread mVisionWorkThread;
    private Thread mImageWorkThread;
    private final Object mBitmapLock = new Object();
    private Button mBtnOpenImage;
    private Button mBtnCloseImage;
    private Button mBtnOpenCamera;
    private Button mBtnCloseCamera;
    private Button mBtnStart;
    private Button mBtnStop;
    private Button mBtnRecording;
    private ByteBuffer mData;
    private DetectedResult[] mDetectedResults;
    private List<RectF> mRectList = new ArrayList<>();
    private int mImageViewWidth;
    private int mImageViewHeight;

    static {
        System.loadLibrary("vision_aibox");
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        if (savedInstanceState != null) {
            shouldRecord = savedInstanceState.getBoolean("should_record", false);
        }
        mImageView = findViewById(R.id.image);
        mBtnOpenImage = findViewById(R.id.btn_open_image);
        mBtnCloseImage = findViewById(R.id.btn_close_image);
        mBtnOpenCamera = findViewById(R.id.btn_open_camera);
        mBtnCloseCamera = findViewById(R.id.btn_close_camera);
        mBtnRecording = findViewById(R.id.btn_record);
        mBtnStart = findViewById(R.id.btn_start);
        mBtnStop = findViewById(R.id.btn_stop);
        checkPermission();
        resetUI();

        mBtnOpenImage.setOnClickListener(v -> {
            openImage();
        });
        mBtnCloseImage.setOnClickListener(v -> {
            closeImage();
        });
        mBtnOpenCamera.setOnClickListener(v -> {
            openCamera();
        });
        mBtnCloseCamera.setOnClickListener(v -> {
            closeCamera();
        });
        mBtnStart.setOnClickListener(v -> {
            startDetect();
        });
        mBtnStop.setOnClickListener(v -> {
            stopDetect();
        });

        mBtnRecording.setOnClickListener(v -> {
            shouldRecord = !shouldRecord;
            if (shouldRecord) {
                mBtnRecording.setText(R.string.btn_stop_recording);
            } else {
                mBtnRecording.setText(R.string.btn_start_recording);
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        closeImage();
        closeCamera();
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

    private synchronized void openImage() {

        if (mIsImageStarted) {
            return;
        }

        mBtnOpenCamera.setEnabled(false);

        mIsImageStarted = true;
        mImageWorkThread = new ImageWorkThread();
        mImageWorkThread.start();

        mBtnStart.setEnabled(true);
        mBtnCloseImage.setEnabled(true);
        mBtnOpenImage.setEnabled(false);

    }

    private synchronized void closeImage() {
        if (mIsDetecting) {
            stopDetect();
        }
        mIsImageStarted = false;
        if (mVisionWorkThread != null) {
            try {
                mImageWorkThread.interrupt();
                mImageWorkThread.join();
            } catch (Exception e) {
                e.printStackTrace();
            }
            mImageWorkThread = null;
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
        if (mVisionWorkThread != null) {
            try {
                mVisionWorkThread.interrupt();
                mVisionWorkThread.join();
            } catch (Exception e) {
                e.printStackTrace();
            }
            mVisionWorkThread = null;
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
                    RS2Intrinsic intrinsics = Vision.getInstance().getIntrinsics(VisionStreamType.FISH_EYE);
                    Log.d(TAG, "intrinsics: " + intrinsics);
                    Vision.getInstance().startVision(VisionStreamType.FISH_EYE);

                    mVisionWorkThread = new VisionWorkThread();
                    mVisionWorkThread.start();
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
                            mRectList.add(new RectF(result.x1 / BITMAP_SCALE, result.y1 / BITMAP_SCALE,
                                    result.x2 / BITMAP_SCALE, result.y2 / BITMAP_SCALE));
                        }
                    }
                    if (mBitmap != null) {
                        int width = mBitmap.getWidth() / BITMAP_SCALE;
                        int height = mBitmap.getHeight() / BITMAP_SCALE;
                        if (width != mImageViewWidth || height != mImageViewHeight) {
                            mImageViewWidth = width;
                            mImageViewHeight = height;
                            ViewGroup.LayoutParams layoutParams = mImageView.getLayoutParams();
                            layoutParams.width = mImageViewWidth;
                            layoutParams.height = mImageViewHeight;
                            mImageView.setLayoutParams(layoutParams);

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
                        mBitmap.copyPixelsToBuffer(mData);
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
                    if (mBitmap != null && shouldRecord) {
                        saveImage(mBitmap);
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

    private void saveImage(Bitmap bitmap) {
        if (bitmap == null) {
            return;
        }
        synchronized (mBitmapLock) {
            if (Objects.equals(Environment.getExternalStorageState(), Environment.MEDIA_MOUNTED)) {
                File footageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
                if (footageDir != null) {
                    if (!footageDir.exists()) {
                        footageDir.mkdirs();
                    }
                    String msTimeStamp = new SimpleDateFormat("yyyyMMdd_HHmmssSSS", Locale.US).format(new Date());
                    String fileName = "footage_" + msTimeStamp + ".jpg";
                    File newFile = new File(footageDir, fileName);
                    if (!newFile.exists()) {
                        try {
                            boolean created = newFile.createNewFile();
                        } catch (IOException e) {
//                        firebaseCrashlytics.recordException(e)
                        }
                        try {
                            OutputStream newFileOut = new FileOutputStream(newFile);
                            bitmap.compress(Bitmap.CompressFormat.JPEG, 80, newFileOut);
                            newFileOut.flush();
                            newFileOut.close();
                            runOnUiThread(() -> Toast.makeText(MainActivity.this, "Saved image", Toast.LENGTH_SHORT).show());
                            Log.d("bg-record", "Saved image");
                        } catch (Throwable t) {
                            String logMessage = "Error resizing/saving bitmap";
                            Log.e("bg-record", logMessage);
//                        firebaseCrashlytics.log(logMessage)
//                        firebaseCrashlytics.recordException(t)
                        }
                    }
                }
            }
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

    @Override
    public void onSaveInstanceState(Bundle outState, PersistableBundle outPersistentState) {
        super.onSaveInstanceState(outState, outPersistentState);
        outState.putBoolean("should_record", shouldRecord);
    }
}
