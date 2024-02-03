package com.segway.robot.sample.aibox.vision

import android.os.RemoteException

class OwnVisionServiceException(message: String?, t: Throwable?) : RuntimeException(message, t) {

    constructor(t: Throwable?) : this(null, t)

    constructor(message: String?) : this(message, null)


    companion object {

        fun toRemoteException(e: OwnVisionServiceException): RemoteException {
            return RemoteException(e.message)
        }

        fun toVisionServiceException(e: RemoteException): OwnVisionServiceException {
            return OwnVisionServiceException(e.message, e.cause)
        }

        fun toVisionServiceException(e: Exception): OwnVisionServiceException {
            return OwnVisionServiceException(e.message, e.cause)
        }

        fun getServiceNotConnectedException(): OwnVisionServiceException {
            return OwnVisionServiceException("The vision service is not connected.")
        }

        fun getAprSenseNotStaredException(): OwnVisionServiceException {
            return OwnVisionServiceException("AprSense is not started.")
        }
    }
}