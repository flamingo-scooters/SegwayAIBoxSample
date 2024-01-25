package com.segway.robot.sample.vision

import android.app.Activity
import android.graphics.Bitmap
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.Toast
import com.segway.robot.sdk.vision.BindStateListener
import com.segway.robot.sdk.vision.Vision
import com.segway.robot.sdk.vision.calibration.RS2Intrinsic
import com.segway.robot.sdk.vision.frame.Frame
import com.segway.robot.sdk.vision.stream.PixelFormat
import com.segway.robot.sdk.vision.stream.Resolution
import com.segway.robot.sdk.vision.stream.VisionStreamType
import java.util.Timer
import java.util.TimerTask

/**
 * Vision SDK demo
 */
class MainActivity : Activity() {
    private var mBitmap: Bitmap? = null
    private var mCameraView: ImageView? = null
    private var mTimer: Timer? = null
    private var mImageDisplay: ImageDisplay? = null
    private var mBtnBind: Button? = null
    private var mBtnUnbind: Button? = null
    private var mBtnStartVision1: Button? = null
    private var mBtnStartVision2: Button? = null
    private var mBtnStopVision: Button? = null

    @Volatile
    private var mIsBind = false
    private val mLock = Any()

    protected override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        mCameraView = findViewById<ImageView>(R.id.iv_camera)
        mBtnBind = findViewById<Button>(R.id.bind)
        mBtnUnbind = findViewById<Button>(R.id.unbind)
        mBtnStartVision1 = findViewById<Button>(R.id.start_vision_1)
        mBtnStartVision2 = findViewById<Button>(R.id.start_vision_2)
        mBtnStopVision = findViewById<Button>(R.id.stop_vision)
        mBtnBind?.setOnClickListener { v: View? ->
            val ret = Vision.getInstance().bindService(this, object : BindStateListener {
                override fun onBind() {
                    Log.d(TAG, "onBind")
                    mIsBind = true
                    //Obtain internal calibration data
                    val intrinsics: RS2Intrinsic = Vision.getInstance().getIntrinsics(VisionStreamType.FISH_EYE)
                    Log.d(TAG, "intrinsics: $intrinsics")
                }

                override fun onUnbind(reason: String?) {
                    Log.d(TAG, "onUnbind")
                    mIsBind = false
                }
            })
            if (!ret) {
                Log.d(TAG, "Vision Service does not exist")
            }
        }
        mBtnUnbind?.setOnClickListener { v: View? ->
            if (mIsBind) {
                Vision.getInstance().stopVision(VisionStreamType.FISH_EYE)
                Vision.getInstance().unbindService()
            }
            if (mTimer != null) {
                mTimer!!.cancel()
                mTimer = null
            }
            mBtnStartVision1?.isEnabled = true
            mBtnStartVision2?.isEnabled = true
            mIsBind = false
        }
        mBtnStartVision1?.setOnClickListener { v: View? ->
            if (!mIsBind) {
                Toast.makeText(this, "The vision service is not connected.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            Vision.getInstance().startVision(VisionStreamType.FISH_EYE, Vision.FrameListener { streamType, frame -> parseFrame(frame) })
            mBtnStartVision2?.isEnabled = false
        }
        mBtnStartVision2?.setOnClickListener { v: View? ->
            if (!mIsBind) {
                Toast.makeText(this, "The vision service is not connected.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            Vision.getInstance().startVision(VisionStreamType.FISH_EYE) { streamType, frame -> parseFrame(frame) }
            mBtnStartVision2?.isEnabled = false
        }
        mBtnStopVision!!.setOnClickListener { v: View? ->
            if (!mIsBind) {
                Toast.makeText(this, "The vision service is not connected.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            Vision.getInstance().stopVision(VisionStreamType.FISH_EYE)
            if (mTimer != null) {
                mTimer!!.cancel()
                mTimer = null
            }
            mBtnStartVision1?.isEnabled = true
            mBtnStartVision2?.isEnabled = true
        }
    }

    private fun parseFrame(frame: Frame) {
        synchronized(mLock) {
            val resolution = frame.info.resolution
            val width = Resolution.getWidth(resolution)
            val height = Resolution.getHeight(resolution)
            if (mBitmap == null) {
                mBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                mImageDisplay = ImageDisplay(width, height)
            }
            val pixelFormat = frame.info.pixelFormat
            if (pixelFormat == PixelFormat.YUV420 || pixelFormat == PixelFormat.YV12) {
                val limit = frame.byteBuffer.limit()
                val buff = ByteArray(limit)
                frame.byteBuffer.position(0)
                frame.byteBuffer[buff]
                yuv2RGBBitmap(buff, mBitmap!!, width, height)
            } else {
                Log.d(TAG, "An unsupported format")
            }
        }
        runOnUiThread(mImageDisplay)
    }

    protected override fun onDestroy() {
        super.onDestroy()
        Vision.getInstance().stopVision(VisionStreamType.FISH_EYE)
        if (mTimer != null) {
            mTimer!!.cancel()
            mTimer = null
        }
    }

    internal inner class ImageDisplayTimerTask : TimerTask() {
        override fun run() {
            synchronized(mLock) {
                var frame: Frame? = null
                try {
                    frame = Vision.getInstance().getLatestFrame(VisionStreamType.FISH_EYE)
                } catch (e: Exception) {
                    Log.e(TAG, "IllegalArgumentException  " + e.message)
                }
                if (frame != null) {
                    parseFrame(frame)
                    Vision.getInstance().returnFrame(frame)
                }
            }
        }
    }

    internal inner class ImageDisplay(width: Int, height: Int) : Runnable {
        var mWidth: Int
        var mHeight: Int
        var setParamsFlag = false
        var zoom = 0.5f

        init {
            mWidth = (width * zoom).toInt()
            mHeight = (height * zoom).toInt()
        }

        override fun run() {
            if (!setParamsFlag) {
                val params: ViewGroup.LayoutParams = mCameraView!!.layoutParams
                params.width = mWidth
                params.height = mHeight
                mCameraView!!.layoutParams = params
                setParamsFlag = true
            }
            mCameraView!!.setImageBitmap(mBitmap)
        }
    }

    private fun yuv2RGBBitmap(data: ByteArray, bitmap: Bitmap, width: Int, height: Int) {
        val frameSize = width * height
        val rgba = IntArray(frameSize)
        for (i in 0 until height) {
            for (j in 0 until width) {
                var y = 0xff and data[i * width + j].toInt()
                val v = 0xff and data[frameSize + (i shr 1) * width + (j and 1.inv()) + 0].toInt()
                val u = 0xff and data[frameSize + (i shr 1) * width + (j and 1.inv()) + 1].toInt()
                y = if (y < 16) 16 else y
                var r = Math.round(1.164f * (y - 16) + 1.596f * (v - 128))
                var g = Math.round(1.164f * (y - 16) - 0.813f * (v - 128) - 0.391f * (u - 128))
                var b = Math.round(1.164f * (y - 16) + 2.018f * (u - 128))
                r = if (r < 0) 0 else if (r > 255) 255 else r
                g = if (g < 0) 0 else if (g > 255) 255 else g
                b = if (b < 0) 0 else if (b > 255) 255 else b
                rgba[i * width + j] = -0x1000000 + (b shl 16) + (g shl 8) + r
            }
        }
        bitmap.setPixels(rgba, 0, width, 0, 0, width, height)
    }

    companion object {
        private const val TAG = "VisionSample"
    }
}
