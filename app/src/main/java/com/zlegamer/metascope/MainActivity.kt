package com.zlegamer.metascope

import android.Manifest
import android.R.attr.centerX
import android.R.attr.centerY
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.PointF
import android.media.Image
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.util.Size
import android.view.View
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.bumptech.glide.Glide
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.pose.PoseDetection
import com.google.mlkit.vision.pose.PoseDetector
import com.google.mlkit.vision.pose.accurate.AccuratePoseDetectorOptions
import com.zlegamer.metascope.databinding.ActivityMainBinding
import org.opencv.core.Mat
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors


class MainActivity : AppCompatActivity(), imageProcess, CameraInfoAnalyser {

    private lateinit var viewBinding: ActivityMainBinding

    private lateinit var cameraExecutor: ExecutorService

    lateinit var text : String

    lateinit var myName : String
    lateinit var roomName : String
    lateinit var killername : String
    lateinit var profileImg : String

    lateinit var poseDetector : PoseDetector

    var isShot : Boolean = false
    var checkVictim : Boolean = false

    var win : Boolean = false;
    var isEnd : Boolean = false;

    //출력부분 다음에 지울것
    val Win: Thread = object : Thread() {
        override fun run() {
            runOnUiThread {
                viewBinding.result.setVisibility(View.VISIBLE)
                viewBinding.winlosemsg.text="Winner winner chicken dinner!!"
                viewBinding.username.text=myName
                viewBinding.killername.text=killername

            }
        }
    }
    val Lose: Thread = object : Thread() {
        override fun run() {
            runOnUiThread {
                viewBinding.result.setVisibility(View.VISIBLE)
                viewBinding.winlosemsg.text="Better luck next time!"
            }
        }
    }

    external fun initVo(camMatrix: Pair<Double,Double>, room : String, name : String)
    external fun vo_tracker(matAddrInput: Long, camMatrix: Pair<Double,Double>, check : Boolean) : String
    external fun getCameraMatrix()

    @RequiresApi(Build.VERSION_CODES.S)
    override fun onCreate(savedInstanceState: Bundle?) {

        // Accurate pose detector on static images, when depending on the pose-detection-accurate sdk
        val options = AccuratePoseDetectorOptions.Builder()
            .setDetectorMode(AccuratePoseDetectorOptions.SINGLE_IMAGE_MODE)
            .build()

        poseDetector = PoseDetection.getClient(options)

        super.onCreate(savedInstanceState)
        viewBinding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(viewBinding.root)

        val intent : Intent = getIntent();
        myName = intent.getStringExtra("Name").toString()
        roomName = intent.getStringExtra("Room").toString()
        profileImg = intent.getStringExtra("Image").toString()
        Glide.with(this).load(profileImg).override(200,200).into(viewBinding.imageView)

        viewBinding.btnReturn.setOnClickListener { finish() }

        // Request camera permissions
        if (allPermissionsGranted()) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(
                this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS)
        }

        cameraExecutor = Executors.newSingleThreadExecutor()
    }

    @RequiresApi(Build.VERSION_CODES.S)
    @SuppressLint("UnsafeOptInUsageError", "RestrictedApi")
    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        Log.d("Ddd"," usecase binding try?")

        cameraProviderFuture.addListener({
            // Used to bind the lifecycle of cameras to the lifecycle owner
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            val focallength = getFocalLength(this);
            Log.d("MainActivity","focal length : ${focallength.toString()}")

            String.format("")
            initVo(focallength, roomName, myName)

            // Preview
            val preview = Preview.Builder()
                .build()
                .also {
                    it.setSurfaceProvider(viewBinding.viewFinder.surfaceProvider)
                }

            val imageAnalysis = ImageAnalysis.Builder()
                // enable the following line if RGBA output is needed.
                // .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                .setTargetResolution(Size(1280, 720))
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()

            //이미지 프로세싱
            imageAnalysis.setAnalyzer(cameraExecutor, ImageAnalysis.Analyzer { imageProxy ->

                //
                val rotationDegrees = imageProxy.imageInfo.rotationDegrees
                var temp : Mat = imageProxy.image!!.yuvToRgba()
                text = vo_tracker(temp.getNativeObjAddr(), focallength, checkVictim)
                checkVictim = false;
                if(text.length != 0)
                {
                    if(text[0] == 'W')
                    {
                        Win.start()
                    }
                    if(text[0] == 'L')
                    {
                        killername = text.substring(1,text.length)
                        Lose.start()
                    }
                    if(text[0] == 'I')
                    {
                        //죽은사람 정보
                        val msg = text.substring(1)+"이 누군가에 의해 사망하셨습니다"
                        Toast.makeText(getApplicationContext(), msg, Toast.LENGTH_LONG).show()
                    }
                }
                val mediaImage: Image = imageProxy.image!!
                val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)

                //피격판정
                if(isShot) {
                    isShot = false
                    poseDetector.process(image)
                        .addOnSuccessListener { result ->
                            if (result.allPoseLandmarks.size > 0) {
                                val headPos: PointF = result.getPoseLandmark(0)!!.getPosition()
                                Log.d(
                                    "머리 위치",
                                    (headPos.x / image.width).toString() + ", " + headPos.y / image.height
                                )
                                if (centerX - headPos.x / image.width < 0.1 && centerY - headPos.y / image.height < 0.1) {
                                    checkVictim = true;
                                    Log.d("Analyzer", "누군가 맞춤")
                                }
                            }
                        }
                        .addOnFailureListener { e ->
                            Log.d("Analyzer", "이미지 분석 실패 ${e.toString()}")
                        }
                }
                //출력부분 끝

                // 이미지 프로세싱 끝
                imageProxy.close()
            })
            // Select back camera as a default
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                // Unbind use cases before rebinding
                cameraProvider.unbindAll()

                // Bind use cases to camera
                cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview, imageAnalysis)
            } catch(exc: Exception) {
                Log.e(TAG, "Use case binding failed", exc)
            }

        }, ContextCompat.getMainExecutor(this))
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(
            baseContext, it) == PackageManager.PERMISSION_GRANTED
    }
    @RequiresApi(Build.VERSION_CODES.S)
    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>, grantResults:
        IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                startCamera()
            } else {
                Toast.makeText(this,
                    "Permissions not granted by the user.",
                    Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }
    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }

    companion object {

        private const val TAG = "CameraXApp"
        private const val FILENAME_FORMAT = "yyyy-MM-dd-HH-mm-ss-SSS"
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS =
            mutableListOf (
                Manifest.permission.CAMERA,
            ).toTypedArray()
        // Used to load the 'metascope' library on application startup.
        init {
            System.loadLibrary("opencv_java4")
            System.loadLibrary("native-lib")
        }
    }
}