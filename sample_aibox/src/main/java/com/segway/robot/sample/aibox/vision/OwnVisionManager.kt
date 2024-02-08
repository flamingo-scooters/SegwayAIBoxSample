package com.segway.robot.sample.aibox.vision

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.os.RemoteException
import android.os.SharedMemory
import android.system.OsConstants
import android.util.Log
import android.util.SparseArray
import com.segway.robot.sdk.vision.BindStateListener
import com.segway.robot.sdk.vision.IVisionServiceProxy
import com.segway.robot.sdk.vision.ImageStreamCallback
import com.segway.robot.sdk.vision.calibration.RS2Intrinsic
import com.segway.robot.sdk.vision.frame.Frame
import com.segway.robot.sdk.vision.frame.FrameInfo
import com.segway.robot.sdk.vision.internal.framebuffer.CountableFrame
import com.segway.robot.sdk.vision.internal.framebuffer.FrameBuffer
import com.segway.robot.sdk.vision.internal.framebuffer.RecyclableFrame2
import com.segway.robot.sdk.vision.internal.framebuffer.RecyclableFrame2.FrameReleaseHandler
import com.segway.robot.sdk.vision.internal.ipc.MemoryFileBuffer
import com.segway.robot.sdk.vision.internal.ipc.MemoryFileBufferCallback
import com.segway.robot.sdk.vision.stream.StreamInfo
import java.io.FileDescriptor
import java.io.FileInputStream
import java.io.IOException
import java.lang.reflect.Constructor
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.min

class OwnVisionManager {
    private val TAG = OwnVision::class.java.simpleName

    private val SERVICE_PACKAGE_NAME = "com.segway.robot.host.coreservice.vision"
    private val SERVICE_CLASS_NAME = "com.segway.robot.host.coreservice.vision.VisionService"
    private var mContext: Context? = null
    private var mListener: BindStateListener? = null
    private val isBind = AtomicBoolean(false)
    private var mVisionServiceProxy: IVisionServiceProxy? = null
    private var mSharedMemoryConstructor: Constructor<SharedMemory>? = null
    private val mDummy = Any()
    private val mImageCallbackMap = SparseArray<Any?>()
    private val mMemoryFileBufferCacheArray = SparseArray<ByteBuffer?>()
    private val mFrameBufferMap = SparseArray<FrameBuffer?>()

    private val mServiceConnection: ServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            try {
                val visionServiceProxy = IVisionServiceProxy.Stub.asInterface(service)
                mVisionServiceProxy = visionServiceProxy
                visionServiceProxy.registerClient(Binder(), mContext?.packageName)
                isBind.set(true)
                mListener?.onBind()
            } catch (e: Exception) {
                val error = "Register client to service error"
                Log.e(TAG, error, e)
            }
        }

        override fun onServiceDisconnected(name: ComponentName) {
            mListener!!.onUnbind("Service disconnected.")
            isBind.set(false)
            clearImageCallMap()
            clearFrameBufferMap()
            synchronized(mMemoryFileBufferCacheArray) { mMemoryFileBufferCacheArray.clear() }
        }
    }

    fun bindService(context: Context?, listener: BindStateListener?): Boolean {
        if (isBind.get()) {
            Log.i(TAG, "bindService: already bind!")
            return true
        }
        mContext = context
        mListener = listener
        val startServiceIntent = Intent()
        startServiceIntent.setClassName(SERVICE_PACKAGE_NAME, SERVICE_CLASS_NAME)
        startServiceIntent.putExtra("PackageNameFPC", mContext!!.packageName)
        startServiceIntent.putExtra("ServiceType", 1)
        Log.d("zxhtest", "bind $SERVICE_CLASS_NAME")
        return mContext!!.bindService(
            startServiceIntent,
            mServiceConnection,
            Context.BIND_AUTO_CREATE
        )
    }

    fun unbindService() {
        if (mContext == null || !isBind.get()) {
            Log.w(TAG, "unbindService: vision service not bind!")
            return
        }
        if (mVisionServiceProxy != null) {
            try {
                mVisionServiceProxy!!.unregisterClient(mContext!!.packageName)
            } catch (e: Exception) {
                Log.e(TAG, "unregister client error", e)
            }
        }
        mContext!!.unbindService(mServiceConnection)
        clearImageCallMap()
        clearFrameBufferMap()
        synchronized(mMemoryFileBufferCacheArray) { mMemoryFileBufferCacheArray.clear() }
        mContext = null
        isBind.set(false)
    }

    @Throws(OwnVisionServiceException::class)
    private fun checkConnected() {
        if (mVisionServiceProxy == null) {
            throw OwnVisionServiceException.getServiceNotConnectedException()
        }
    }

    fun startImageTransferMemoryFileBuffer(streamType: Int, callback: ImageStreamCallback?) {
        checkConnected()
        if (callback == null) {
            Log.e(TAG, "illegalStateException")
            throw IllegalArgumentException("The image stream callback cannot be null.")
        }
        synchronized(mImageCallbackMap) {
            if (mImageCallbackMap[streamType] != null) {
                throw OwnVisionServiceException("The stream is duplicated.")
            }
            mImageCallbackMap.put(streamType, mDummy)
        }
        try {
            synchronized(mMemoryFileBufferCacheArray) { mMemoryFileBufferCacheArray.clear() }
            mVisionServiceProxy!!.startImageTransferMemoryFileBuffer(
                streamType,
                object : MemoryFileBufferCallback.Stub() {
                    var prevTimeStamp: Long = 0

                    @Throws(RemoteException::class)
                    override fun onNewImage(memoryFileBuffer: MemoryFileBuffer) {
                        try {
                            Log.d(TAG, "new image from buffer")
                            val imageBuffer = getMappedBufferFromMemoryFile(
                                streamType,
                                memoryFileBuffer.index,
                                0,
                                memoryFileBuffer.imageFD.fileDescriptor,
                                memoryFileBuffer.imageFD,
                                memoryFileBuffer.imageSize
                            )
                            val infoBuffer = getMappedBufferFromMemoryFile(
                                streamType,
                                memoryFileBuffer.index,
                                1,
                                memoryFileBuffer.infoFD.fileDescriptor,
                                memoryFileBuffer.infoFD,
                                memoryFileBuffer.infoSize
                            )
                            if (imageBuffer == null || infoBuffer == null) {
                                Log.e(TAG, "get mapped buffer error")
                                releaseMemoryFileBuffer(streamType, memoryFileBuffer.index)
                                return
                            }
                            val frameInfo = FrameInfo.fromByteBuffer(infoBuffer)
                            if (frameInfo.streamType != streamType) {
                                Log.w(
                                    TAG, "Illegal frame detected, " +
                                            " stream type = " + frameInfo.streamType +
                                            " index = " + memoryFileBuffer.index +
                                            " clean share mem cache and drop frame"
                                )
                                synchronized(mMemoryFileBufferCacheArray) { mMemoryFileBufferCacheArray.clear() }
                                releaseMemoryFileBuffer(streamType, memoryFileBuffer.index)
                                return
                            }
                            val ts = frameInfo.imuTimeStamp
                            if (frameInfo.streamType != streamType) {
                                Log.e(TAG, "__error frame detected! frame info = $frameInfo")
                                synchronized(mMemoryFileBufferCacheArray) { mMemoryFileBufferCacheArray.clear() }
                                releaseMemoryFileBuffer(streamType, memoryFileBuffer.index)
                                return
                            }
                            if (ts > 0 && ts <= prevTimeStamp) {
                                Log.w(
                                    TAG,
                                    "__get frame ts($ts) low than previous($prevTimeStamp), clean share mem cache and drop frame"
                                )
                                synchronized(mMemoryFileBufferCacheArray) { mMemoryFileBufferCacheArray.clear() }
                                releaseMemoryFileBuffer(streamType, memoryFileBuffer.index)
                                return
                            }
                            prevTimeStamp = ts
                            Log.d(TAG, "new image with stream type ${frameInfo.streamType}")
                            callback.onNewImage(frameInfo, imageBuffer)
                            releaseMemoryFileBuffer(streamType, memoryFileBuffer.index)
                        } finally {
                            try {
                                memoryFileBuffer.imageFD.close()
                            } catch (ignored: IOException) {
                            }
                            try {
                                memoryFileBuffer.infoFD.close()
                            } catch (ignored: IOException) {
                            }
                        }
                    }
                })
        } catch (e: Exception) {
            Log.e(TAG, "startImageTransferMemoryFileBuffer error", e)
            throw OwnVisionServiceException(e)
        }
    }

    fun startImageStreamToBufferByMemoryFile(streamType: Int) {
        checkConnected()
        synchronized(mImageCallbackMap) {
            if (mImageCallbackMap[streamType] != null) {
                throw OwnVisionServiceException("stream duplicated")
            }
            mImageCallbackMap.put(streamType, mDummy)
        }
        var frameBuffer: FrameBuffer?
        synchronized(mFrameBufferMap) {
            if (mFrameBufferMap[streamType] == null) {
                mFrameBufferMap.put(
                    streamType,
                    FrameBuffer()
                )
                Log.v(
                    TAG,
                    "startImageStreamToBufferByMemoryFilem() FrameBufferMap.put type: $streamType"
                )
            }
            frameBuffer = mFrameBufferMap[streamType]
        }
        val finalFrameBuffer = frameBuffer
        val frameReleaseHandler =
            FrameReleaseHandler { streamType, index ->
                if (mVisionServiceProxy == null) {
                    Log.w(TAG, "mAprSenseAIDLService is null and service may be disconnected")
                    return@FrameReleaseHandler
                }
                try {
                    mVisionServiceProxy!!.releaseMemoryFileBuffer(streamType, index)
                } catch (e: Exception) {
                    Log.e(TAG, "Release memory file error", e)
                }
            }
        try {
            synchronized(mMemoryFileBufferCacheArray) { mMemoryFileBufferCacheArray.clear() }
            mVisionServiceProxy!!.startImageTransferMemoryFileBuffer(
                streamType,
                object : MemoryFileBufferCallback.Stub() {
                    var prevTimeStamp: Long = 0

                    @Throws(RemoteException::class)
                    override fun onNewImage(memoryFileBuffer: MemoryFileBuffer) {
                        try {
                            val imageBuffer = getMappedBufferFromMemoryFile(
                                streamType,
                                memoryFileBuffer.index,
                                0,
                                memoryFileBuffer.imageFD.fileDescriptor,
                                memoryFileBuffer.imageFD,
                                memoryFileBuffer.imageSize
                            )
                            val infoBuffer = getMappedBufferFromMemoryFile(
                                streamType,
                                memoryFileBuffer.index,
                                1,
                                memoryFileBuffer.infoFD.fileDescriptor,
                                memoryFileBuffer.infoFD,
                                memoryFileBuffer.infoSize
                            )
                            if (imageBuffer == null || infoBuffer == null) {
                                Log.e(TAG, "get mapped buffer error")
                                releaseMemoryFileBuffer(streamType, memoryFileBuffer.index)
                                return
                            }
                            val frameInfo = FrameInfo.fromByteBuffer(infoBuffer)
                            if (frameInfo.streamType != streamType) {
                                Log.w(
                                    TAG, "Illegal frame detected, " +
                                            " stream type = " + frameInfo.streamType +
                                            " index = " + memoryFileBuffer.index +
                                            " clean share mem cache and drop frame"
                                )
                                synchronized(mMemoryFileBufferCacheArray) { mMemoryFileBufferCacheArray.clear() }
                                releaseMemoryFileBuffer(streamType, memoryFileBuffer.index)
                                return
                            }
                            val ts = frameInfo.imuTimeStamp
                            if (frameInfo.streamType != streamType) {
                                Log.e(TAG, "__error frame detected! frame info = $frameInfo")
                                synchronized(mMemoryFileBufferCacheArray) { mMemoryFileBufferCacheArray.clear() }
                                releaseMemoryFileBuffer(streamType, memoryFileBuffer.index)
                                return
                            }
                            if (ts > 0 && ts <= prevTimeStamp) {
                                Log.w(
                                    TAG,
                                    "__get frame ts($ts) low than previous($prevTimeStamp), clean share mem cache and drop frame"
                                )
                                synchronized(mMemoryFileBufferCacheArray) { mMemoryFileBufferCacheArray.clear() }
                                releaseMemoryFileBuffer(streamType, memoryFileBuffer.index)
                                return
                            }
                            prevTimeStamp = ts
                            val recyclableFrame2 = RecyclableFrame2.create(
                                streamType,
                                memoryFileBuffer.index,
                                imageBuffer.capacity(),
                                frameInfo,
                                imageBuffer,
                                frameReleaseHandler
                            )
                            finalFrameBuffer!!.add(recyclableFrame2)
                        } finally {
                            try {
                                memoryFileBuffer.imageFD.close()
                            } catch (ignored: IOException) {
                            }
                            try {
                                memoryFileBuffer.infoFD.close()
                            } catch (ignored: IOException) {
                            }
                        }
                    }
                })
        } catch (e: Exception) {
            Log.e(TAG, "startImageTransferMemoryFileBuffer error", e)
            throw OwnVisionServiceException(e)
        }
    }

    fun stopImageTransferMemoryFileBuffer(streamType: Int) {
        checkConnected()
        synchronized(mImageCallbackMap) {
            if (mImageCallbackMap[streamType] == null) {
                Log.w(
                    TAG,
                    "stopImageTransferMemoryFileBuffer: stream $streamType is not start transfer."
                )
                return
            }
            mImageCallbackMap.remove(streamType)
        }
        try {
            mVisionServiceProxy!!.stopImageTransferMemoryFileBuffer(streamType)
        } catch (e: Exception) {
            throw OwnVisionServiceException(e)
        }
        synchronized(mMemoryFileBufferCacheArray) { mMemoryFileBufferCacheArray.clear() }
        synchronized(mFrameBufferMap) {
            val frameBuffer =
                mFrameBufferMap[streamType]
            if (frameBuffer != null) {
                frameBuffer.release()
                mFrameBufferMap.remove(streamType)
            }
        }
    }

    fun getLatestFrameForStream(streamType: Int, previousTid: Long): Frame? {
        var frameBuffer: FrameBuffer?
        synchronized(mFrameBufferMap) {
            frameBuffer = mFrameBufferMap[streamType]
            Log.d(TAG, "mFrameBufferMap got ${mFrameBufferMap.size()}")
        }
        if (frameBuffer == null) {
            Log.e(TAG, "getLatestFrameForStream null frameBuffer for stream type $streamType")
            throw IllegalArgumentException("stream image transfer not initialized.")
        }
        return frameBuffer?.getLatest(previousTid)
    }

    fun returnFrameToStream(streamType: Int, frame: Frame?) {
        var frameBuffer: FrameBuffer?
        if (frame == null) {
            Log.e(TAG, "illegalStateException")
            throw IllegalArgumentException("can not return null frame")
        }
        if (frame.info.streamType != streamType) {
            Log.e(TAG, "illegalStateException")
            throw IllegalArgumentException("try to return frame type = " + frame.info.streamType + " to buffer type = " + streamType)
        }
        synchronized(mFrameBufferMap) { frameBuffer = mFrameBufferMap[streamType] }
        if (frameBuffer == null) {
            Log.w(TAG, "return image for stream $streamType but not initialized")
            (frame as CountableFrame).lockCountDown()
            return
        }
        frameBuffer!!.returnFrame(frame)
    }

    @Throws(OwnVisionServiceException::class)
    fun getActivatedStreamProfiles(): Array<StreamInfo?>? {
        checkConnected()
        return try {
            mVisionServiceProxy!!.activatedStreamProfile
        } catch (e: Exception) {
            throw OwnVisionServiceException(e)
        }
    }

    private fun getMappedBufferFromMemoryFile(
        type: Int,
        index: Int,
        info: Int,
        fileDescriptor: FileDescriptor,
        parcelFileDescriptor: ParcelFileDescriptor,
        size: Int
    ): ByteBuffer? {
        var mappedByteBuffer: ByteBuffer?
        val key = type shl 8 or (index shl 1) or info
        synchronized(mMemoryFileBufferCacheArray) {
            mappedByteBuffer = mMemoryFileBufferCacheArray[key]
        }
        if (mappedByteBuffer != null) {
            mappedByteBuffer?.rewind()
            return mappedByteBuffer
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            try {
                val sharedMemory = SharedMemory.fromFileDescriptor(parcelFileDescriptor)
                mappedByteBuffer = sharedMemory.map(OsConstants.PROT_READ, 0, size)
            } catch (e: Exception) {
                Log.e(TAG, "map buffer from memory file error", e)
                return null
            }
        } else {
            var fileInputStream: FileInputStream? = null
            try {
                fileInputStream = FileInputStream(fileDescriptor)
                val channel = fileInputStream.channel
                Log.d(TAG, "Opening channel to read $size bytes")
                mappedByteBuffer =
                    channel.map(FileChannel.MapMode.READ_WRITE, channel.position(), size.toLong())
            } catch (e: IOException) {
                Log.e(TAG, "map buffer from memory file error", e)
                return null
            } finally {
                if (fileInputStream != null) {
                    try {
                        fileInputStream.close()
                    } catch (ignored: IOException) {
                    }
                }
            }
        }
        synchronized(mMemoryFileBufferCacheArray) {
            mMemoryFileBufferCacheArray.put(
                key,
                mappedByteBuffer
            )
        }
        if (mappedByteBuffer != null) {
            mappedByteBuffer?.rewind()
        }
        return mappedByteBuffer
    }

    fun getIntrinsics(type: Int): RS2Intrinsic? {
        checkConnected()
        val rs2Intrinsic: RS2Intrinsic
        rs2Intrinsic = try {
            mVisionServiceProxy!!.getIntrinsics(type)
        } catch (e: Exception) {
            throw OwnVisionServiceException(e)
        }
        return if (rs2Intrinsic.width != 0) {
            rs2Intrinsic
        } else null
    }


    fun releaseMemoryFileBuffer(streamType: Int, index: Int) {
        checkConnected()
        try {
            mVisionServiceProxy!!.releaseMemoryFileBuffer(streamType, index)
        } catch (e: Exception) {
            throw OwnVisionServiceException(e)
        }
    }

    private fun clearImageCallMap() {
        synchronized(mImageCallbackMap) {
            for (i in 0 until mImageCallbackMap.size()) {
                val streamType = mImageCallbackMap.keyAt(i)
                try {
                    mVisionServiceProxy!!.stopImageTransferMemoryFileBuffer(streamType)
                } catch (e: Exception) {
                    Log.w(
                        TAG,
                        "try to stop image streaming while unbind but caught exception:",
                        e
                    )
                }
            }
            mImageCallbackMap.clear()
        }
    }

    private fun clearFrameBufferMap() {
        synchronized(mFrameBufferMap) {
            for (i in 0 until mFrameBufferMap.size()) {
                val frameBuffer =
                    mFrameBufferMap.valueAt(i)
                frameBuffer!!.release()
            }
            mFrameBufferMap.clear()
            Log.v(TAG, "onServiceDisconnect() mFrameBufferMap clear()")
        }
    }

}