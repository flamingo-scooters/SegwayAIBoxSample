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
                val msTimeStamp = SimpleDateFormat("yyyyMMdd_HHmmssSSS", Locale.US).format(Date())
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