package com.segway.robot.sample.aibox

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.RectF
import android.os.Bundle
import android.os.Environment
import android.os.Environment.getExternalStoragePublicDirectory
import android.util.Log
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Observer
import com.segway.robot.sample.aibox.tool.Event
import com.segway.robot.sample.aibox.vision.OwnVision
import com.segway.robot.sdk.vision.BindStateListener
import com.segway.robot.sdk.vision.frame.Frame
import com.segway.robot.sdk.vision.stream.PixelFormat
import com.segway.robot.sdk.vision.stream.Resolution
import com.segway.robot.sdk.vision.stream.VisionStreamType
import java.io.File
import java.nio.ByteBuffer
import kotlin.concurrent.Volatile
import kotlin.math.roundToInt

class AIBoxActivity : AppCompatActivity() {
    private var mImageView: VisionImageView? = null

    @Volatile
    private var mIsBind = false

    @Volatile
    private var mIsDetecting = false

    @Volatile
    private var mIsImageStarted = false

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
    private var mBtnCamXPreview: Button? = null
    private var camXPreview: PreviewView? = null
    private var mTvLogs: TextView? = null
    private var mData: ByteBuffer? = null
    private var mDetectedResults: Array<DetectedResult>? = null
    private val mRectList: MutableList<RectF> = ArrayList()
    private var mImageViewWidth = 0
    private var mImageViewHeight = 0

    private val recordViewModel: RecordViewModel by viewModels<RecordViewModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        mImageView = findViewById(R.id.image)
        mBtnOpenImage = findViewById(R.id.btn_open_image)
        mBtnCloseImage = findViewById(R.id.btn_close_image)
        mBtnOpenCamera = findViewById(R.id.btn_open_camera)
        mBtnCloseCamera = findViewById(R.id.btn_close_camera)
        mBtnStart = findViewById(R.id.btn_start)
        mBtnStop = findViewById(R.id.btn_stop)
        mBtnRecording = findViewById(R.id.btn_record)
        mBtnCamXPreview = findViewById(R.id.btn_camx_start)
        camXPreview = findViewById(R.id.camXViewFinder)
        mTvLogs = findViewById(R.id.tv_logs)
        checkStoragePermission()
        resetUI()
        mBtnOpenImage?.setOnClickListener { openImage() }
        mBtnCloseImage?.setOnClickListener { closeImage() }
        mBtnOpenCamera?.setOnClickListener { openCamera() }
        mBtnCloseCamera?.setOnClickListener { closeCamera() }
        mBtnStart?.setOnClickListener { startDetect() }
        mBtnStop?.setOnClickListener { stopDetect() }
        mBtnRecording?.setOnClickListener {
            if (recordViewModel.isRecording()) {
                recordViewModel.stopRecording()
                mBtnRecording?.setText(R.string.btn_start_recording)
            } else {
                recordViewModel.startRecording()
                mBtnRecording?.setText(R.string.btn_stop_recording)
            }
        }
        mBtnCamXPreview?.setOnClickListener {
            activityResultLauncher.launch(PERMISSION_CAMERA)
        }

        recordViewModel.getLogDisplay().observe(this, Observer(::onRecordLogDisplay))
    }

    private fun onRecordLogDisplay(event: Event<String>?) {
        event?.getContentIfNotHandled()?.let {
            Toast.makeText(this, it, Toast.LENGTH_LONG).show()
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

    private fun checkStoragePermission() {
        if ((ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED)
        ) {
            if (ActivityCompat.shouldShowRequestPermissionRationale(
                    this,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                )
            ) {
                Toast.makeText(
                    this,
                    "Please accept the relevant permissions, otherwise you can not use this application normally!",
                    Toast.LENGTH_SHORT
                ).show()
            }
            ActivityCompat.requestPermissions(this, PERMISSIONS_STORAGE, REQUEST_CODE)
        }
    }

    private val activityResultLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions())
        { permissions ->
            // Handle Permission granted/rejected
            var permissionGranted = true
            permissions.entries.forEach {
                if (it.key in PERMISSION_CAMERA && !it.value)
                    permissionGranted = false
            }
            if (!permissionGranted) {
                Toast.makeText(
                    baseContext,
                    "Camera permission request denied",
                    Toast.LENGTH_SHORT
                ).show()
            } else {
                startCamera()
            }
        }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            // Used to bind the lifecycle of cameras to the lifecycle owner
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            // Preview
            val preview = Preview.Builder()
                .build()
                .also {
                    it.setSurfaceProvider(camXPreview?.surfaceProvider)
                }

            // Select back camera as a default
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                // Unbind use cases before rebinding
                cameraProvider.unbindAll()

                // Bind use cases to camera
                cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview
                )

            } catch (exc: Exception) {
                Log.e(TAG, "Use case binding failed", exc)
            }

        }, ContextCompat.getMainExecutor(this))
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
        mTvLogs?.text = "Start binding Vision service"
        val ret = OwnVision.getInstance()?.bindService(this, object : BindStateListener {
            override fun onBind() {
                Log.d(TAG, "onBind")
                mIsBind = true
                try {
                    mBtnCloseCamera?.isEnabled = true
                    mBtnOpenCamera?.isEnabled = false
                    mBtnStart?.isEnabled = true
                    mTvLogs?.text = "bound Vision service"
                    //Obtain internal calibration data
                    val intrinsics =
                        OwnVision.getInstance()?.getIntrinsics(VisionStreamType.FISH_EYE)
                    Log.d(TAG, "intrinsics: $intrinsics")
//                    OwnVision.getInstance()?.startVision(VisionStreamType.FISH_EYE)
                    OwnVision.getInstance()?.startVision(VisionStreamType.FISH_EYE,
                        object : OwnVision.FrameListener {
                            override fun onNewFrame(streamType: Int, frame: Frame?) {
                                Log.d(TAG, "Got a new frame from the Vision service")
                                if (frame != null) {
                                    try {
                                        onNewFrame(frame)
                                    } catch (e: Exception) {
                                        Log.d(TAG, "Exception $e")
                                        e.printStackTrace()
                                    }
                                }
                            }
                        })
                    mVisionWorkThread = VisionWorkThread()
//                    mVisionWorkThread?.start()
//                    mTvLogs?.text = "Started Vision thread"
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
        if (ret != true) {
            Log.d(TAG, "Vision Service does not exist")
            OwnVision.getInstance()?.unbindService()
        }
    }

    private fun unbindAndStopVision() {
        try {
            OwnVision.getInstance()?.stopVision(VisionStreamType.FISH_EYE)
        } catch (e: Exception) {
            Log.d(TAG, "error:", e)
        }
        OwnVision.getInstance()?.unbindService()
    }

    private fun showImage() {
        runOnUiThread(Runnable {
            mTvLogs?.text = "Attempting to show image"
            synchronized(mBitmapLock) {
                mRectList.clear()
                val results = mDetectedResults
                if (results != null) {
                    for (result: DetectedResult in results) {
                        mRectList.add(
                            RectF(
                                result.x1 / BITMAP_SCALE, result.y1 / BITMAP_SCALE,
                                result.x2 / BITMAP_SCALE, result.y2 / BITMAP_SCALE
                            )
                        )
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
                        runOnUiThread {
                            Toast.makeText(
                                this@AIBoxActivity,
                                "The picture does not exist!",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    } else if (mIsDetecting) {
                        val size: Int = currentBitmap.byteCount
                        if (mData == null || mData?.capacity() != size) {
                            mData = ByteBuffer.allocateDirect(size)
                        }
                        mData?.rewind()
                        val bitmap: Bitmap? = currentBitmap.copy(currentBitmap.config, true)
                        mBitmap?.copyPixelsToBuffer(mData!!)
                        mBitmap = bitmap
                        mDetectedResults = VisionNative.nativeDetect(
                            mData,
                            PixelFormat.RGBA8888,
                            mBitmap!!.width,
                            mBitmap!!.height
                        )
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
                    Log.d(TAG, "on VisionWorkThread")
                    val visionInstance = OwnVision.getInstance()
                    val frame = visionInstance?.getLatestFrame(VisionStreamType.FISH_EYE)
                    if (frame != null) {
                        onNewFrame(frame)
                        visionInstance.returnFrame(frame)
                    }
                } catch (e: Exception) {
                    Log.d(TAG, "Exception $e")
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

    private fun onNewFrame(frame: Frame) {
        Log.d(
            TAG,
            "ts: " + frame.info.platformTimeStamp + "  " + frame.info.imuTimeStamp
        )
        val resolution = frame.info.resolution
        val width = Resolution.getWidth(resolution)
        val height = Resolution.getHeight(resolution)
        synchronized(mBitmapLock) {
            if (mBitmap == null) {
                mBitmap =
                    Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
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
                synchronized(mBitmapLock) {
                    mDetectedResults =
                        VisionNative.nativeDetect(mData, pixelFormat, width, height)
                }
            } else {
                synchronized(mBitmapLock) { mDetectedResults = null }
            }
            val buff = ByteArray(limit)
            frame.byteBuffer.position(0)
            frame.byteBuffer[buff]
            synchronized(mBitmapLock) {
                yuv2RGBBitmap(
                    buff,
                    mBitmap,
                    width,
                    height
                )
            }
        } else {
            Log.d(TAG, "An unsupported format")
        }
        if (mBitmap != null) {
            showImage()
        }
        synchronized(mBitmapLock) {
            val devicesPicturesDir =
                getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
            val appPicturesDir = File(devicesPicturesDir, "AIBoxFootage")
            recordViewModel.saveImage(mBitmap, appPicturesDir)
        }
    }

    private fun yuv2RGBBitmap(data: ByteArray, bitmap: Bitmap?, width: Int, height: Int) {
        val frameSize = width * height
        val rgba = IntArray(frameSize)
        for (i in 0 until height) {
            for (j in 0 until width) {
                var y = (0xff and (data[i * width + j].toInt()))
                val v =
                    (0xff and (data[frameSize + ((i shr 1) * width) + (j and 1.inv()) + 0].toInt()))
                val u =
                    (0xff and (data[frameSize + ((i shr 1) * width) + (j and 1.inv()) + 1].toInt()))
                y = if (y < 16) 16 else y
                var r: Int = (1.164f * (y - 16) + 1.596f * (v - 128)).roundToInt()
                var g: Int =
                    ((1.164f * (y - 16)) - (0.813f * (v - 128)) - (0.391f * (u - 128))).roundToInt()
                var b: Int = (1.164f * (y - 16) + 2.018f * (u - 128)).roundToInt()
                r = if (r < 0) 0 else (if (r > 255) 255 else r)
                g = if (g < 0) 0 else (if (g > 255) 255 else g)
                b = if (b < 0) 0 else (if (b > 255) 255 else b)
                rgba[i * width + j] = -0x1000000 + (b shl 16) + (g shl 8) + r
            }
        }
        bitmap?.setPixels(rgba, 0, width, 0, 0, width, height)
    }

    companion object {
        private val TAG = AIBoxActivity::class.java.simpleName
        private const val LOCAL_IMAGE_PATH = "sdcard/apple.jpeg"
        private const val REQUEST_CODE = 1
        private val PERMISSIONS_STORAGE = arrayOf(
            "android.permission.READ_EXTERNAL_STORAGE",
            "android.permission.WRITE_EXTERNAL_STORAGE"
        )
        private val PERMISSION_CAMERA = arrayOf("android.permission.CAMERA")
        private const val BITMAP_SCALE = 4

        init {
            System.loadLibrary("vision_aibox")
        }
    }
}
