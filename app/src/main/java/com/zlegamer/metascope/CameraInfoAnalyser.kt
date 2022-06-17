package com.zlegamer.metascope

import android.content.Context
import android.hardware.Camera
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import android.hardware.Camera.Size
import android.os.Build
import androidx.annotation.RequiresApi
import org.opencv.core.Mat
import java.lang.Math.atan

interface CameraInfoAnalyser {
    @RequiresApi(Build.VERSION_CODES.S)
    fun getFocalLength(context : Context) : Pair<Double, Double>
    {
        val cameraInfo: CameraManager = context.getSystemService(AppCompatActivity.CAMERA_SERVICE) as CameraManager
        val cameraId = getBackFacingCameraId(cameraInfo)
        val SensorSize = cameraInfo.getCameraCharacteristics(cameraId).get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE)
        val focal_length_MM = cameraInfo.getCameraCharacteristics(cameraId).get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)!![0]
//        val max_resolution = cameraInfo.getCameraCharacteristics(cameraId).get(CameraCharacteristics.SENSOR_INFO_PIXEL_ARRAY_SIZE)

        Log.d("getFocalLength", "sensor size x : ${SensorSize!!.width}")
        Log.d("getFocalLength", "sensor size y : ${SensorSize!!.height}")

        val focal_length_mm = focal_length_MM.toDouble()
        val fx =1280*focal_length_mm / SensorSize!!.width;
        val fy =720*focal_length_mm / SensorSize!!.height;

        Log.d("getFocalLength", "focal length: $focal_length_MM")
        Log.d("getFocalLength", "focal length x : $fx")
        Log.d("getFocalLength", "focal length y : $fy")

        return Pair(fx,fy)
    }

    private fun getBackFacingCameraId(cManager : CameraManager): String {
        try {
            for (cameraId in cManager.cameraIdList) {
                val characteristics = cManager.getCameraCharacteristics(cameraId)
                val cOrientation = characteristics.get(CameraCharacteristics.LENS_FACING)
                if (cOrientation == CameraCharacteristics.LENS_FACING_BACK) return cameraId
            }
        } catch (e: CameraAccessException) {
            e.printStackTrace()
        }
        return "None"
    }
}
//F(mm)/SensorWidth(mm)*imageWidth = F(pixels)