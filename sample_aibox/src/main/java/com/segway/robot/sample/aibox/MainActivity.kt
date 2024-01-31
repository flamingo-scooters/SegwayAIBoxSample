package com.segway.robot.sample.aibox

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.RectF
import android.os.Bundle
import android.os.Environment
import android.os.PersistableBundle
import androidx.core.app.ActivityCompat
import androidx.appcompat.app.AppCompatActivity
import android.util.Log
import android.view.ViewGroup
import android.widget.Button
import android.widget.Toast
import com.segway.robot.sdk.vision.BindStateListener
import com.segway.robot.sdk.vision.Vision
import com.segway.robot.sdk.vision.stream.PixelFormat
import com.segway.robot.sdk.vision.stream.Resolution
import com.segway.robot.sdk.vision.stream.VisionStreamType
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream
import java.nio.ByteBuffer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.concurrent.Volatile
import kotlin.math.roundToInt

class MainActivity() : AppCompatActivity() {
    private var mImageView: VisionImageView? = null

    @Volatile
    private var mIsBind = false

    @Volatile
    private var mIsDetecting = false

    @Volatile
    private var mIsImageStarted = false

    private var shouldRecord = false

    @Volatile
    private var mIsCameraStarted = false
    private var mBitmap: Bitmap? = null
    private var mVisionWorkThread: Thread? = null
    private var mImageWorkThread: Thread? = null
    private val mBitmapLock = Any()
    private var mBtnOpenImage: Button? = null
    private var mBtnCloseImage: Button? = null
    private var mBtnOpenCamera: Button? = null
    private var mBtnCloseCamera: Button? = null
    private var mBtnStart: Button? = null
    private var mBtnStop: Button? = null
    private var mBtnRecording: Button? = null
    private var mData: ByteBuffer? = null
    private var mDetectedResults: Array<DetectedResult>? = null
    private val mRectList: MutableList<RectF> = ArrayList()
    private var mImageViewWidth = 0
    private var mImageViewHeight = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        shouldRecord = savedInstanceState?.getBoolean(SHOULD_RECORD, false) ?: false
        mImageView = findViewById(R.id.image)
        mBtnOpenImage = findViewById(R.id.btn_open_image)
        mBtnCloseImage = findViewById(R.id.btn_close_image)
        mBtnOpenCamera = findViewById(R.id.btn_open_camera)
        mBtnCloseCamera = findViewById(R.id.btn_close_camera)
        mBtnStart = findViewById(R.id.btn_start)
        mBtnStop = findViewById(R.id.btn_stop)
        mBtnRecording = findViewById(R.id.btn_record)
        checkPermission()
        resetUI()
        mBtnOpenImage?.setOnClickListener { openImage() }
        mBtnCloseImage?.setOnClickListener { closeImage() }
        mBtnOpenCamera?.setOnClickListener { openCamera() }
        mBtnCloseCamera?.setOnClickListener { closeCamera() }
        mBtnStart?.setOnClickListener { startDetect() }
        mBtnStop?.setOnClickListener { stopDetect() }
        mBtnRecording?.setOnClickListener {
            shouldRecord = !shouldRecord
            if (shouldRecord) {
                mBtnRecording?.setText(R.string.btn_stop_recording)
            } else {
                mBtnRecording?.setText(R.string.btn_start_recording)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        closeImage()
        closeCamera()
    }

    private fun resetUI() {
        mBtnOpenCamera?.isEnabled = true
        mBtnOpenImage?.isEnabled = true
        mBtnStart?.isEnabled = false
        mBtnStop?.isEnabled = false
        mBtnCloseCamera?.isEnabled = false
        mBtnCloseImage?.isEnabled = false
    }

    private fun checkPermission() {
        if ((ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                        != PackageManager.PERMISSION_GRANTED)) {
            if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                Toast.makeText(this, "Please accept the relevant permissions, otherwise you can not use this application normally!", Toast.LENGTH_SHORT).show()
            }
            ActivityCompat.requestPermissions(this, PERMISSIONS_STORAGE, REQUEST_CODE)
        }
    }

    @Synchronized
    private fun openImage() {
        if (mIsImageStarted) {
            return
        }
        mBtnOpenCamera?.isEnabled = false
        mIsImageStarted = true
        mImageWorkThread = ImageWorkThread()
        mImageWorkThread?.start()
        mBtnStart?.isEnabled = true
        mBtnCloseImage?.isEnabled = true
        mBtnOpenImage?.isEnabled = false
    }

    @Synchronized
    private fun closeImage() {
        if (mIsDetecting) {
            stopDetect()
        }
        mIsImageStarted = false
        if (mVisionWorkThread != null) {
            try {
                mImageWorkThread?.interrupt()
                mImageWorkThread?.join()
            } catch (e: Exception) {
                e.printStackTrace()
            }
            mImageWorkThread = null
        }
        resetUI()
    }

    @Synchronized
    private fun openCamera() {
        mIsCameraStarted = true
        mBtnOpenImage?.isEnabled = false
        bindAndStartVision()
    }

    @Synchronized
    private fun closeCamera() {
        mIsCameraStarted = false
        if (mIsDetecting) {
            stopDetect()
        }
        unbindAndStopVision()
        if (mVisionWorkThread != null) {
            try {
                mVisionWorkThread?.interrupt()
                mVisionWorkThread?.join()
            } catch (e: Exception) {
                e.printStackTrace()
            }
            mVisionWorkThread = null
        }
        resetUI()
    }

    @Synchronized
    private fun startDetect() {
        if (mIsDetecting) {
            return
        }
        mIsDetecting = true
        mBtnStop?.isEnabled = true
        mBtnStart?.isEnabled = false
    }

    @Synchronized
    private fun stopDetect() {
        mIsDetecting = false
        mBtnStop?.isEnabled = false
        mBtnStart?.isEnabled = true
    }

    private fun bindAndStartVision() {
        //bind Vision Service
        val ret = Vision.getInstance().bindService(this, object : BindStateListener {
            override fun onBind() {
                Log.d(TAG, "onBind")
                mIsBind = true
                try {
                    //Obtain internal calibration data
                    val intrinsics = Vision.getInstance().getIntrinsics(VisionStreamType.FISH_EYE)
                    Log.d(TAG, "intrinsics: $intrinsics")
                    Vision.getInstance().startVision(VisionStreamType.FISH_EYE)
                    mVisionWorkThread = VisionWorkThread()
                    mVisionWorkThread?.start()
                    mBtnOpenCamera?.isEnabled = false
                    mBtnStart?.isEnabled = true
                    mBtnCloseCamera?.isEnabled = true
                } catch (e: Exception) {
                    Log.d(TAG, "error:", e)
                }
            }

            override fun onUnbind(reason: String) {
                Log.d(TAG, "onUnbind")
                mIsBind = false
                mBtnOpenCamera?.isEnabled = true
            }
        })
        if (!ret) {
            Log.d(TAG, "Vision Service does not exist")
            Vision.getInstance().unbindService()
        }
    }

    private fun unbindAndStopVision() {
        try {
            Vision.getInstance().stopVision(VisionStreamType.FISH_EYE)
        } catch (e: Exception) {
            Log.d(TAG, "error:", e)
        }
        Vision.getInstance().unbindService()
    }

    private fun showImage() {
        runOnUiThread(Runnable {
            synchronized(mBitmapLock) {
                mRectList.clear()
                val results = mDetectedResults
                if (results != null) {
                    for (result: DetectedResult in results) {
                        mRectList.add(RectF(result.x1 / BITMAP_SCALE, result.y1 / BITMAP_SCALE,
                                result.x2 / BITMAP_SCALE, result.y2 / BITMAP_SCALE))
                    }
                }
                if (mBitmap != null) {
                    val width: Int? = mBitmap?.width?.div(BITMAP_SCALE)
                    val height: Int? = mBitmap?.height?.div(BITMAP_SCALE)
                    if (width != null && height != null && (width != mImageViewWidth || height != mImageViewHeight)) {
                        mImageViewWidth = width
                        mImageViewHeight = height
                        val layoutParams: ViewGroup.LayoutParams? = mImageView?.layoutParams
                        if (layoutParams != null) {
                            layoutParams.width = mImageViewWidth
                            layoutParams.height = mImageViewHeight
                            mImageView?.layoutParams = layoutParams
                        }
                    }
                }
                mImageView?.mark(mRectList)
                mImageView?.setImageBitmap(mBitmap)
            }
        })
    }

    internal inner class ImageWorkThread : Thread() {
        override fun run() {
            while (mIsImageStarted) {
                synchronized(mBitmapLock) {
                    mBitmap = BitmapFactory.decodeFile(LOCAL_IMAGE_PATH)
                    //                    mBitmap = BitmapFactory.decodeResource(getResources(), R.raw.fashion_sample);
                    val currentBitmap = mBitmap
                    if (currentBitmap == null) {
                        runOnUiThread { Toast.makeText(this@MainActivity, "The picture does not exist!", Toast.LENGTH_SHORT).show() }
                    } else if (mIsDetecting) {
                        val size: Int = currentBitmap.byteCount
                        if (mData == null || mData?.capacity() != size) {
                            mData = ByteBuffer.allocateDirect(size)
                        }
                        mData?.rewind()
                        val bitmap: Bitmap? = currentBitmap.copy(currentBitmap.config, true)
                        mBitmap?.copyPixelsToBuffer(mData!!)
                        mBitmap = bitmap
                        mDetectedResults = VisionNative.nativeDetect(mData, PixelFormat.RGBA8888, mBitmap!!.width, mBitmap!!.height)
                    } else {
                        mDetectedResults = null
                    }
                }
                showImage()
                try {
                    sleep(100)
                } catch (e: InterruptedException) {
                    e.printStackTrace()
                }
            }
            clearBitmap()
        }
    }

    private fun clearBitmap() {
        synchronized(mBitmapLock) {
            mBitmap = null
            mDetectedResults = null
        }
        showImage()
    }

    internal inner class VisionWorkThread : Thread() {
        override fun run() {
            while (mIsCameraStarted && mIsBind) {
                val startTs = System.currentTimeMillis()
                try {
                    val frame = Vision.getInstance().getLatestFrame(VisionStreamType.FISH_EYE)
                    Log.d(TAG, "ts: " + frame.info.platformTimeStamp + "  " + frame.info.imuTimeStamp)
                    val resolution = frame.info.resolution
                    val width = Resolution.getWidth(resolution)
                    val height = Resolution.getHeight(resolution)
                    synchronized(mBitmapLock) {
                        if (mBitmap == null) {
                            mBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                        }
                    }
                    val pixelFormat = frame.info.pixelFormat
                    if (pixelFormat == PixelFormat.YUV420 || pixelFormat == PixelFormat.YV12) {
                        val limit = frame.byteBuffer.limit()
                        if (mIsDetecting) {
                            if (mData == null || mData?.capacity() != limit) {
                                mData = ByteBuffer.allocateDirect(limit)
                            }
                            frame.byteBuffer.position(0)
                            mData?.rewind()
                            mData?.put(frame.byteBuffer)
                            synchronized(mBitmapLock) { mDetectedResults = VisionNative.nativeDetect(mData, pixelFormat, width, height) }
                        } else {
                            synchronized(mBitmapLock) { mDetectedResults = null }
                        }
                        val buff = ByteArray(limit)
                        frame.byteBuffer.position(0)
                        frame.byteBuffer[buff]
                        synchronized(mBitmapLock) { yuv2RGBBitmap(buff, mBitmap, width, height) }
                    } else {
                        Log.d(TAG, "An unsupported format")
                    }
                    if (mBitmap != null) {
                        showImage()
                    }
                    if (mBitmap != null && shouldRecord) {
                        saveImage(mBitmap)
                    }
                    Vision.getInstance().returnFrame(frame)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                val endTs = System.currentTimeMillis()
                val interval = 100 - (endTs - startTs)
                if (interval > 0) {
                    try {
                        sleep(interval)
                    } catch (e: InterruptedException) {
                        e.printStackTrace()
                    }
                }
            }
            clearBitmap()
        }
    }

    private fun saveImage(bitmap: Bitmap?) {
        if (bitmap == null) {
            return
        }
        synchronized(mBitmapLock) {
            if (Environment.getExternalStorageState() == Environment.MEDIA_MOUNTED) {
                val footageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES)
                if (footageDir?.exists() == false) {
                    footageDir.mkdirs()
                }
                if (footageDir != null) {
                    val msTimeStamp = SimpleDateFormat("yyyyMMdd_HHmmssSSS", Locale.US).format(Date())
                    val fileName = "footage_$msTimeStamp"
                    val newFile = File(footageDir, "$fileName.jpg")
                    if (!newFile.exists()) {
                        try {
                            newFile.createNewFile()
                        } catch (e: IOException) {
//                        firebaseCrashlytics.recordException(e)
                        }
                        try {
                            val newFileOut: OutputStream = FileOutputStream(newFile)
                            bitmap.compress(Bitmap.CompressFormat.JPEG, 80, newFileOut)
                            newFileOut.flush()
                            newFileOut.close()
                            runOnUiThread { Toast.makeText(this, "Saved image", Toast.LENGTH_SHORT).show() }
                            Log.d("bg-record", "Saved image")
                        } catch (t: Throwable) {
                            val logMessage = "Error resizing/saving bitmap"
                            Log.e("bg-record", logMessage)
//                        firebaseCrashlytics.log(logMessage)
//                        firebaseCrashlytics.recordException(t)
                        }

                    }
                }
            }
        }
    }

    private fun yuv2RGBBitmap(data: ByteArray, bitmap: Bitmap?, width: Int, height: Int) {
        val frameSize = width * height
        val rgba = IntArray(frameSize)
        for (i in 0 until height) {
            for (j in 0 until width) {
                var y = (0xff and (data[i * width + j].toInt()))
                val v = (0xff and (data[frameSize + ((i shr 1) * width) + (j and 1.inv()) + 0].toInt()))
                val u = (0xff and (data[frameSize + ((i shr 1) * width) + (j and 1.inv()) + 1].toInt()))
                y = if (y < 16) 16 else y
                var r: Int = (1.164f * (y - 16) + 1.596f * (v - 128)).roundToInt()
                var g: Int = ((1.164f * (y - 16)) - (0.813f * (v - 128)) - (0.391f * (u - 128))).roundToInt()
                var b: Int = (1.164f * (y - 16) + 2.018f * (u - 128)).roundToInt()
                r = if (r < 0) 0 else (if (r > 255) 255 else r)
                g = if (g < 0) 0 else (if (g > 255) 255 else g)
                b = if (b < 0) 0 else (if (b > 255) 255 else b)
                rgba[i * width + j] = -0x1000000 + (b shl 16) + (g shl 8) + r
            }
        }
        bitmap?.setPixels(rgba, 0, width, 0, 0, width, height)
    }

    override fun onSaveInstanceState(outState: Bundle, outPersistentState: PersistableBundle) {
        super.onSaveInstanceState(outState, outPersistentState)
        outState.putBoolean(SHOULD_RECORD, shouldRecord)
    }

    companion object {
        private val TAG = MainActivity::class.java.simpleName
        private const val LOCAL_IMAGE_PATH = "sdcard/apple.jpeg"
        private const val REQUEST_CODE = 1
        private val PERMISSIONS_STORAGE = arrayOf("android.permission.READ_EXTERNAL_STORAGE",
                "android.permission.WRITE_EXTERNAL_STORAGE")
        private const val BITMAP_SCALE = 4
        private const val SHOULD_RECORD = "should_record"

        init {
            System.loadLibrary("vision_aibox")
        }
    }
}
