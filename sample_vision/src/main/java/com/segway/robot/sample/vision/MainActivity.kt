package com.segway.robot.sample.vision;

import android.app.Activity;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;

import com.segway.robot.sdk.vision.BindStateListener;
import com.segway.robot.sdk.vision.Vision;
import com.segway.robot.sdk.vision.calibration.RS2Intrinsic;
import com.segway.robot.sdk.vision.frame.Frame;
import com.segway.robot.sdk.vision.stream.PixelFormat;
import com.segway.robot.sdk.vision.stream.Resolution;
import com.segway.robot.sdk.vision.stream.VisionStreamType;

import java.util.Timer;
import java.util.TimerTask;

/**
 * Vision SDK demo
 */
public class MainActivity extends Activity {

    private static final String TAG = "VisionSample";
    private Bitmap mBitmap;
    private ImageView mCameraView;
    private Timer mTimer;
    private ImageDisplay mImageDisplay;
    private Button mBtnBind;
    private Button mBtnUnbind;
    private Button mBtnStartVision1;
    private Button mBtnStartVision2;
    private Button mBtnStopVision;
    private volatile boolean mIsBind;
    private final Object mLock = new Object();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mCameraView = findViewById(R.id.iv_camera);
        mBtnBind = findViewById(R.id.bind);
        mBtnUnbind = findViewById(R.id.unbind);
        mBtnStartVision1 = findViewById(R.id.start_vision_1);
        mBtnStartVision2 = findViewById(R.id.start_vision_2);
        mBtnStopVision  = findViewById(R.id.stop_vision);

        mBtnBind.setOnClickListener(v -> {
            boolean ret = Vision.getInstance().bindService(this, new BindStateListener() {
                @Override
                public void onBind() {
                    Log.d(TAG, "onBind");
                    mIsBind = true;
                    //Obtain internal calibration data
                    RS2Intrinsic intrinsics = Vision.getInstance().getIntrinsics(VisionStreamType.FISH_EYE);
                    Log.d(TAG, "intrinsics: " + intrinsics);
                }

                @Override
                public void onUnbind(String reason) {
                    Log.d(TAG, "onUnbind");
                    mIsBind = false;
                }
            });
            if (!ret) {
                Log.d(TAG, "Vision Service does not exist");
            }
        });

        mBtnUnbind.setOnClickListener(v -> {
            if (mIsBind) {
                Vision.getInstance().stopVision(VisionStreamType.FISH_EYE);
                Vision.getInstance().unbindService();
            }
            if (mTimer != null) {
                mTimer.cancel();
                mTimer = null;
            }
            mBtnStartVision1.setEnabled(true);
            mBtnStartVision2.setEnabled(true);
            mIsBind = false;
        });

        mBtnStartVision1.setOnClickListener(v -> {
            if (!mIsBind) {
                Toast.makeText(this, "The vision service is not connected.", Toast.LENGTH_SHORT).show();
                return;
            }
            Vision.getInstance().startVision(VisionStreamType.FISH_EYE, new Vision.FrameListener() {
                @Override
                public void onNewFrame(int streamType, Frame frame) {
                    parseFrame(frame);
                }
            });
            mBtnStartVision2.setEnabled(false);
        });

        mBtnStartVision2.setOnClickListener(v -> {
            if (!mIsBind) {
                Toast.makeText(this, "The vision service is not connected.", Toast.LENGTH_SHORT).show();
                return;
            }
            Vision.getInstance().startVision(VisionStreamType.FISH_EYE, (streamType, frame) -> parseFrame(frame));
            mBtnStartVision2.setEnabled(false);
        });

        mBtnStopVision.setOnClickListener(v -> {
            if (!mIsBind) {
                Toast.makeText(this, "The vision service is not connected.", Toast.LENGTH_SHORT).show();
                return;
            }
            Vision.getInstance().stopVision(VisionStreamType.FISH_EYE);
            if (mTimer != null) {
                mTimer.cancel();
                mTimer = null;
            }
            mBtnStartVision1.setEnabled(true);
            mBtnStartVision2.setEnabled(true);
        });
    }

    private void parseFrame(Frame frame) {
        synchronized (mLock) {

            int resolution = frame.getInfo().getResolution();
            int width = Resolution.getWidth(resolution);
            int height = Resolution.getHeight(resolution);
            if (mBitmap == null) {
                mBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
                mImageDisplay = new ImageDisplay(width, height);
            }
            int pixelFormat = frame.getInfo().getPixelFormat();
            if (pixelFormat == PixelFormat.YUV420 || pixelFormat == PixelFormat.YV12) {
                int limit = frame.getByteBuffer().limit();
                byte[] buff = new byte[limit];
                frame.getByteBuffer().position(0);
                frame.getByteBuffer().get(buff);
                yuv2RGBBitmap(buff, mBitmap, width, height);
            } else {
                Log.d(TAG, "An unsupported format");
            }
        }
        runOnUiThread(mImageDisplay);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Vision.getInstance().stopVision(VisionStreamType.FISH_EYE);
        if (mTimer != null) {
            mTimer.cancel();
            mTimer = null;
        }
    }

    class ImageDisplayTimerTask extends TimerTask {

        @Override
        public void run() {
            synchronized (mLock) {
                Frame frame = null;
                try {
                    frame = Vision.getInstance().getLatestFrame(VisionStreamType.FISH_EYE);
                } catch (Exception e) {
                    Log.e(TAG, "IllegalArgumentException  " + e.getMessage());
                }
                if (frame != null) {
                    parseFrame(frame);
                    Vision.getInstance().returnFrame(frame);
                }
            }
        }
    }

    class ImageDisplay implements Runnable {

        int mWidth;
        int mHeight;
        boolean setParamsFlag;
        float zoom = 0.5f;

        public ImageDisplay(int width, int height) {
            mWidth = (int) (width * zoom);
            mHeight = (int) (height * zoom);
            setParamsFlag = false;
        }

        @Override
        public void run() {
            if (!setParamsFlag) {
                ViewGroup.LayoutParams params = mCameraView.getLayoutParams();
                params.width = mWidth;
                params.height = mHeight;
                mCameraView.setLayoutParams(params);
                setParamsFlag = true;
            }

            mCameraView.setImageBitmap(mBitmap);

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
