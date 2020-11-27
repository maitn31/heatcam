package com.example.heatcam.MeasurementApp.Fragments.IntroFragment;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.PointF;
import android.graphics.drawable.AnimationDrawable;
import android.os.Bundle;
import android.util.Log;
import android.util.Size;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.InitializationException;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.example.heatcam.MeasurementApp.FaceDetector.CameraXViewModel;
import com.example.heatcam.MeasurementApp.FaceDetector.FaceDetectListener;
import com.example.heatcam.MeasurementApp.FaceDetector.FaceDetectorProcessor;
import com.example.heatcam.MeasurementApp.FrontCamera.FrontCameraProperties;
import com.example.heatcam.MeasurementApp.ThermalCamera.SerialListeners.LowResolution16BitCamera;
import com.example.heatcam.MeasurementApp.Main.MainActivity;
import com.example.heatcam.MeasurementApp.Fragments.Measurement.MeasurementStartFragment;
import com.example.heatcam.R;
import com.example.heatcam.MeasurementApp.ThermalCamera.SerialPort.SerialPortModel;
import com.example.heatcam.MeasurementApp.FaceDetector.VisionImageProcessor;
import com.google.mlkit.common.MlKitException;
import com.google.mlkit.vision.face.Face;
import com.google.mlkit.vision.face.FaceDetectorOptions;
import com.google.mlkit.vision.face.FaceLandmark;

public class IntroFragment extends Fragment implements FaceDetectListener {

    private final String TAG = "IntroFragment";

    private VisionImageProcessor imageProcessor;
    private ProcessCameraProvider cameraProvider;
    private CameraSelector cameraSelector;
    private ImageAnalysis analysisCase;

    private int minDistanceToMeasure = 500;
    //private TextView txtV;


    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        final View view = inflater.inflate(R.layout.heatcam_intro_fragment, container, false);
        SharedPreferences sharedPrefs = getActivity().getPreferences(Context.MODE_PRIVATE);
        minDistanceToMeasure = Integer.parseInt(sharedPrefs.getString("PREFERENCE_MEASURE_START_MIN_DISTANCE", "500"));
        view.setKeepScreenOn(true);

       // txtV = view.findViewById(R.id.txtDist);

        //moving background
        ConstraintLayout constraintLayout = (ConstraintLayout) view.findViewById(R.id.ConstraintLayout);
        AnimationDrawable animationDrawable = (AnimationDrawable) constraintLayout.getBackground();
        animationDrawable.setEnterFadeDuration(2000);
        animationDrawable.setExitFadeDuration(4000);
        animationDrawable.start();

        cameraSelector = new CameraSelector.Builder()
                .requireLensFacing(CameraSelector.LENS_FACING_FRONT)
                .build();

        new ViewModelProvider(this, ViewModelProvider.AndroidViewModelFactory.getInstance(getActivity().getApplication()))
                .get(CameraXViewModel.class)
                .getProcessCameraProvider()
                .observe(
                        getViewLifecycleOwner(),
                        provider -> {
                            cameraProvider = provider;
                            bindAllCameraUseCases();
                        }
                );
        try {
            checkCamera(view.getContext());
        } catch (Exception e) {
            e.printStackTrace();
        }


        return view;
    }

    private void checkCamera(Context context) {
        SerialPortModel serialPortModel = SerialPortModel.getInstance();
        if(!serialPortModel.hasCamera()) {
            SharedPreferences sharedPrefs = getActivity().getPreferences(Context.MODE_PRIVATE);
            LowResolution16BitCamera cam = new LowResolution16BitCamera();
            cam.setMaxFilter(sharedPrefs.getFloat(getString(R.string.preference_max_filter), -1));
            cam.setMinFilter(sharedPrefs.getFloat(getString(R.string.preference_min_filter), -1));
            serialPortModel.setSioListener(cam);
            serialPortModel.scanDevices(context);
            serialPortModel.changeTiltSpeed(7);
        } else {
            serialPortModel.changeTiltAngle(75);
        }

    }

    @Override
    public void onResume() {
        super.onResume();
        bindAllCameraUseCases();
    }

    @Override
    public void onPause() {
        super.onPause();
        if (imageProcessor != null) {
            imageProcessor.stop();
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (imageProcessor != null) {
            imageProcessor.stop();
        }
    }

    private void bindAllCameraUseCases() {
        if (cameraProvider != null) {
            // As required by CameraX API, unbinds all use cases before trying to re-bind any of them.
            cameraProvider.unbindAll();
            bindFaceAnalysisUseCase();
        }
    }

    private void bindFaceAnalysisUseCase() {
        if (cameraProvider == null) {
            return;
        }
        if (analysisCase != null) {
            cameraProvider.unbind(analysisCase);
        }
        if (imageProcessor != null) {
            imageProcessor.stop();
        }

        try {
            FaceDetectorOptions faceDetectOptions = new FaceDetectorOptions.Builder()
                    .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                    .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
                    .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
                    .setMinFaceSize(0.40f)
                    .enableTracking()
                    .build();

            imageProcessor = new FaceDetectorProcessor(getContext(), faceDetectOptions, this);
        } catch (Exception e) {
            e.printStackTrace();
        }

        analysisCase = new ImageAnalysis.Builder()
                .setTargetResolution(new Size( 1, 1))
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build();

        analysisCase.setAnalyzer(
                ContextCompat.getMainExecutor(getContext()),
                imageProxy -> {
                    try {
                        imageProcessor.processImageProxy(imageProxy);
                    } catch (MlKitException e) {
                        Log.e(TAG, "Failed to process image. Error: " + e.getLocalizedMessage());
                    }
                }
        );

        cameraProvider.bindToLifecycle(/* lifecycleOwner= */ this, cameraSelector, analysisCase);

    }

    @Override
    public void faceDetected(Face face, Bitmap originalCameraImage) {
        PointF leftEyeP = face.getLandmark(FaceLandmark.LEFT_EYE).getPosition();
        PointF rightEyeP = face.getLandmark(FaceLandmark.RIGHT_EYE).getPosition();
        checkFaceDistance(leftEyeP, rightEyeP, originalCameraImage.getWidth(), originalCameraImage.getHeight());
    }

    @Override
    public void faceNotDetected() {

    }

    public void checkFaceDistance(PointF leftEye, PointF rightEye, int imgWidth, int imgHeight) {
        float dist = 0;
        try {
            dist = FrontCameraProperties.getProperties()
                    .getDistance(new Size(imgWidth, imgHeight), leftEye, rightEye);
        } catch (InitializationException e) {
            e.printStackTrace();
        }

        if (dist > 0 && dist < minDistanceToMeasure) {
            switchToMeasurementStartFragment();
        }
    }

    private void switchToMeasurementStartFragment() {

        Fragment f = new MeasurementStartFragment();
        getActivity().getSupportFragmentManager().beginTransaction()
                .setCustomAnimations(R.animator.slide_in_left, R.animator.slide_in_right, 0, 0)
                .replace(R.id.fragmentCamera, f, "measure_start")
                .commit();
        MainActivity.setAutoMode(true);
    }

}
