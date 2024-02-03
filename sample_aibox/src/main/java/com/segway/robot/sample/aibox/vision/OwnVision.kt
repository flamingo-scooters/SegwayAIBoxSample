package com.segway.robot.sample.aibox.vision

import android.content.Context
import android.util.Log
import com.segway.robot.sdk.vision.BindStateListener
import com.segway.robot.sdk.vision.ImageStreamCallback
import com.segway.robot.sdk.vision.calibration.RS2Intrinsic
import com.segway.robot.sdk.vision.frame.Frame
import com.segway.robot.sdk.vision.frame.FrameImpl

/** Vision allows you to access the camera data supported by AI BOX.
 *  Vision provides two ways to obtain camera data. The usage is as follows: 1. Connect to Vision Service. call the bindService(Context, BindStateListener) method. The following operations have to be conducted only when service is connected, or an VisionServiceException will be thrown. 2. Open the camera. The specified camera can be turned on through the startVision(int) method and the startVision(int, Vision.FrameListener) method. If the startVision(int) method is selected, get the latest frame data through getLatestFrame(int), and remember to release the resources through the returnFrame(Frame) method. If the startVision(int, Vision.FrameListener) method is selected, you can continuously obtain frame data through the callback method Vision.FrameListener.onNewFrame(int, Frame). 3. Obtain camera internal calibration parameters of the specified camera. call the getIntrinsics(int). 4. Close the camera. call the stopVision(int)
 **/
class OwnVision {
    private val TAG = OwnVision::class.java.simpleName
    private var mVisionManager: OwnVisionManager = OwnVisionManager()

    /**
     * Definition of the frame listener.
     */
    interface FrameListener {
        /**
         * @param streamType The stream type
         * @param frame      The stream frames
         */
        fun onNewFrame(streamType: Int, frame: Frame?)
    }


    /**
     * Connect to the Vision Service.
     *
     * @param context  any Android Context.
     * @param listener the callback object will feedback connection state.
     * @return If you have successfully connect to the service, `true` will be returned;
     * if the connection is not made, `false` will be returned and you will not
     * receive the serving object. In addition, you still need to call
     * [.unbindService] to release the connection.
     */
    @Synchronized
    fun bindService(context: Context?, listener: BindStateListener?): Boolean {
        return mVisionManager!!.bindService(context, listener)
    }

    /**
     * Disconnect from the Vision Service.
     */
    @Synchronized
    fun unbindService() {
        mVisionManager!!.unbindService()
    }

    /**
     * Open the camera of the specified type [VisionStreamType], Then you can obtain the
     * latest data through the [.getLatestFrame] method.
     *
     * The service must be connected, otherwise a VisionServiceException [VisionServiceException]
     * will be thrown.
     *
     * @param streamType The stream type
     */
    @Synchronized
    fun startVision(streamType: Int) {
        mVisionManager!!.startImageStreamToBufferByMemoryFile(streamType)
    }

    /**
     * Open the camera of the specified type [VisionStreamType]. You can retrieve data from
     * the callback listener [FrameListener].
     *
     * Note that the callback listener cannot be null.
     * The service must be connected, otherwise a VisionServiceException [VisionServiceException]
     * will be thrown.
     *
     * @param streamType The stream type
     */
    @Synchronized
    fun startVision(streamType: Int, listener: FrameListener?) {
        if (listener == null) {
            Log.e(TAG, "illegalStateException")
            throw IllegalArgumentException("Listener is null")
        }
        mVisionManager!!.startImageTransferMemoryFileBuffer(streamType,
            ImageStreamCallback { frameInfo, buffer ->
                val frame: Frame = FrameImpl(frameInfo, buffer)
                listener.onNewFrame(streamType, frame)
            })
    }

    /**
     * Close the camera of the specified stream type.
     *
     * The service must be connected, otherwise a VisionServiceException [VisionServiceException]
     * will be thrown.
     *
     * @param streamType The stream type
     */
    @Synchronized
    fun stopVision(streamType: Int) {
        mVisionManager!!.stopImageTransferMemoryFileBuffer(streamType)
    }

    /**
     * Get latest frame data of camera from buffer pool.
     *
     * After this method called, you need to call returnFrame [.returnFrame] to release
     * the frame. If not, the latest frame will not be updated.
     *
     * @param streamType The stream type
     * @return The stream frame
     */
    fun getLatestFrame(streamType: Int): Frame? {
        return mVisionManager.getLatestFrameForStream(streamType, 0)
    }

    /**
     * Call this method to release the frame if the data will be used again.
     *
     * @param frame The stream frame
     */
    fun returnFrame(frame: Frame) {
        mVisionManager!!.returnFrameToStream(frame.info.streamType, frame)
    }

    /**
     * Obtain the intrinsics of the camera.
     *
     * The service must be connected, otherwise a VisionServiceException [VisionServiceException]
     * will be thrown.
     * Null will be returned, if the sensor has not been calibrated, or there are other exceptions.
     *
     * @param type The stream type
     * @return the intrinsics of the sensor.
     */
    fun getIntrinsics(type: Int): RS2Intrinsic? {
        return mVisionManager.getIntrinsics(type)
    }

    companion object {
        private var sInstance: OwnVision? = null

        fun getInstance(): OwnVision? {
            if (sInstance == null) {
                sInstance = OwnVision()
            }
            return sInstance
        }
    }
}