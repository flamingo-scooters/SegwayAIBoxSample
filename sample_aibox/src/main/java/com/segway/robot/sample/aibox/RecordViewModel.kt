package com.segway.robot.sample.aibox

import android.graphics.Bitmap
import android.os.Environment
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.segway.robot.sample.aibox.tool.Event
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class RecordViewModel : ViewModel() {

    private var shouldRecord: Boolean = false
    private val logDisplay = MutableLiveData<Event<String>>()

    fun getLogDisplay(): LiveData<Event<String>> = logDisplay

    fun startRecording() {
        shouldRecord = true
    }

    fun stopRecording() {
        shouldRecord = false
    }

    fun isRecording() = shouldRecord

    fun saveImage(bitmap: Bitmap?, picturesDir: File?) {
        Log.d(TAG, "About to save image to ${picturesDir?.absolutePath}")
        if (bitmap == null) {
            return
        }
        if (!shouldRecord) {
            return
        }
        if (Environment.getExternalStorageState() == Environment.MEDIA_MOUNTED) {
            if (picturesDir?.exists() == false) {
                picturesDir.mkdirs()
            }
            if (picturesDir != null) {
                val msTimeStamp = SimpleDateFormat(FILENAME_TIMESTAMP, Locale.US).format(Date())
                val fileName = "footage_$msTimeStamp"
                val newFile = File(picturesDir, "$fileName.jpg")
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
                        logDisplay.postValue(Event("Saved image"))
                        Log.d(TAG, "Saved image")
                    } catch (t: Throwable) {
                        val logMessage = "Error resizing/saving bitmap"
                        Log.e(TAG, logMessage)
//                        firebaseCrashlytics.log(logMessage)
//                        firebaseCrashlytics.recordException(t)
                    }

                }
            }
        }
    }

    companion object {
        private const val TAG = "bg-record"
        internal const val FILENAME_TIMESTAMP = "yyyyMMdd_HHmmssSSS"
    }
}