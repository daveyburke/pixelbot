/* Copyright 2018 Dave Burke. All Rights Reserved.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
==============================================================================*/

package com.example.pixelbotbrain;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;

import com.example.pixelbotbrain.tensorflow.Classifier;
import com.example.pixelbotbrain.tensorflow.ImageUtils;
import com.example.pixelbotbrain.tensorflow.TensorFlowObjectDetectionAPIModel;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

/**
 * Camera / Tensorflow object recognition. Derived/simplified from:
 * github.com/tensorflow/tensorflow/tree/master/tensorflow/examples/android
 */
public class CameraObjectRecognizer implements ImageReader.OnImageAvailableListener {
    private static final String TAG = "CameraObjectRecognizer";

    private static final int TF_OD_API_INPUT_SIZE = 300;
    private static final String TF_OD_API_MODEL_FILE =
            "file:///android_asset/ssd_mobilenet_v1_android_export.pb";
    private static final String TF_OD_API_LABELS_FILE =
            "file:///android_asset/coco_labels_list.txt";
    private static final float MINIMUM_CONFIDENCE_TF_OD_API = 0.6f;

    private static final String TRACKABLE_OBJECTS [] = { "person", "airplane", "car", "bus",
            "train", "truck", "boat", "bird", "cat", "dog", "horse", "sheep", "cow", "elephant",
            "bear", "zebra", "giraffe", "umbrella", "handbag", "tennis racket", "bottle",
            "wine glass", "cup", "fork", "knife", "spoon", "bowl", "banana", "apple", "sandwich",
            "orange", "broccoli", "carrot", "hot dog", "pizza", "donut", "cake", "cell phone",
            "teddy bear", "toothbrush" };

    private Context mContext;
    private Listener mListener;
    private CameraDevice mCameraDevice;
    private String mCameraId;
    private HandlerThread mBackgroundThread;
    private Handler mBackgroundHandler;
    private ImageReader mPreviewReader;
    private CameraCaptureSession mCaptureSession;
    private CaptureRequest mPreviewRequest;
    private int mPreviewWidth = 640;
    private int mPreviewHeight = 480;
    private Classifier mDetector;
    private Bitmap mRgbFrameBitmap;
    private Bitmap mCroppedBitmap;
    private Matrix mFrameToCropTransform;
    private Matrix mCropToFrameTransform;
    private boolean mComputingDetection = false;
    int[] mRgbBytes;

    public interface Listener {
        public void onObjectDetected(LinkedList<Classifier.Recognition> recognitions,
                                     int viewFinderWidth, int viewFinderHeight);
    }

    public CameraObjectRecognizer(Context context, Listener listener) {
        mContext = context;
        mListener = listener;
    }

    public void onPause() {
        closeCamera();
        stopBackgroundThread();
    }

    public void onResume() {
        startBackgroundThread();
        openCamera();
    }

    private void startBackgroundThread() {
        mBackgroundThread = new HandlerThread("BackgroundThread");
        mBackgroundThread.start();
        mBackgroundHandler = new Handler(mBackgroundThread.getLooper());
    }

    private void stopBackgroundThread() {
        mBackgroundThread.quitSafely();
        try {
            mBackgroundThread.join();
            mBackgroundThread = null;
            mBackgroundHandler = null;
        } catch (final InterruptedException e) {
            Log.e(TAG, e.getMessage());
        }
    }

    private synchronized void runInBackground(final Runnable runnable) {
        if (mBackgroundHandler != null) {
            mBackgroundHandler.post(runnable);
        }
    }

    @Override
    public void onImageAvailable(ImageReader imageReader) {
        if (mComputingDetection) return;
        mComputingDetection = true;

        final Image image = imageReader.acquireLatestImage();
        if (image == null) return;

        // Get the RGB frame
        byte[][] yuvBytes = new byte[3][];
        if (mRgbBytes == null) {
            mRgbBytes = new int[mPreviewWidth * mPreviewHeight];
        }
        final Image.Plane[] planes = image.getPlanes();
        fillBytes(planes, yuvBytes);
        int yRowStride = planes[0].getRowStride();
        final int uvRowStride = planes[1].getRowStride();
        final int uvPixelStride = planes[1].getPixelStride();
        ImageUtils.convertYUV420ToARGB8888(
                yuvBytes[0],
                yuvBytes[1],
                yuvBytes[2],
                mPreviewWidth,
                mPreviewHeight,
                yRowStride,
                uvRowStride,
                uvPixelStride,
                mRgbBytes);
        mRgbFrameBitmap.setPixels(mRgbBytes, 0, mPreviewWidth, 0, 0,
                mPreviewWidth, mPreviewHeight);

        final Canvas canvas = new Canvas(mCroppedBitmap);
        canvas.drawBitmap(mRgbFrameBitmap, mFrameToCropTransform, null);

        image.close();

        // Detect objects on background handler
        runInBackground(
                new Runnable() {
                    @Override
                    public void run() {
                        final List<Classifier.Recognition> results =
                                mDetector.recognizeImage(mCroppedBitmap);

                        final LinkedList<Classifier.Recognition> mappedRecognitions =
                                new LinkedList<Classifier.Recognition>();

                        for (final Classifier.Recognition result : results) {
                            final RectF location = result.getLocation();
                            if (location != null &&
                                    result.getConfidence() >= MINIMUM_CONFIDENCE_TF_OD_API) {
                                // Only include if it's one of our trackable objects
                                for (int i = 0; i < TRACKABLE_OBJECTS.length; i++) {
                                    if (result.getTitle().equals(TRACKABLE_OBJECTS[i])) {
                                        mCropToFrameTransform.mapRect(location);
                                        result.setLocation(location);
                                        mappedRecognitions.add(result);
                                        break;
                                    }
                                }
                            }
                        }
                        if (mappedRecognitions.size() > 0) {
                            mListener.onObjectDetected(mappedRecognitions, mPreviewWidth,
                                    mPreviewHeight);
                        }
                        mComputingDetection = false;
                    }
                });
    }

    private void openCamera() {
        selectCamera();

        // Create detector
        int cropSize = TF_OD_API_INPUT_SIZE;
        try {
            mDetector = TensorFlowObjectDetectionAPIModel.create(
                    mContext.getAssets(), TF_OD_API_MODEL_FILE, TF_OD_API_LABELS_FILE, TF_OD_API_INPUT_SIZE);
        } catch (final IOException e) {
            Log.e(TAG, "Exception initializing classifier!", e);
            return;
        }

        // Calculate crop/rotate matrix to/from camera preview to/from TensorFlow inference
        mRgbFrameBitmap = Bitmap.createBitmap(mPreviewWidth, mPreviewHeight,
                Bitmap.Config.ARGB_8888);
        mCroppedBitmap = Bitmap.createBitmap(cropSize, cropSize, Bitmap.Config.ARGB_8888);

        mFrameToCropTransform =
                ImageUtils.getTransformationMatrix(
                        mPreviewWidth, mPreviewHeight,
                        cropSize, cropSize,
                        0, false);  // assume landscape camera/display
        mCropToFrameTransform = new Matrix();
        mFrameToCropTransform.invert(mCropToFrameTransform);

        // Open camera - preview session is started via the state callback
        final CameraManager manager =
                (CameraManager) mContext.getSystemService(Context.CAMERA_SERVICE);
        try {
            manager.openCamera(mCameraId, mStateCallback, mBackgroundHandler);
        } catch (final CameraAccessException e) {
            Log.e(TAG, e.getMessage());
        }
    }

    private void selectCamera() {
        final CameraManager manager =
                (CameraManager) mContext.getSystemService(Context.CAMERA_SERVICE);
        try {
            for (final String cameraId : manager.getCameraIdList()) {
                final CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);

                final Integer facing = characteristics.get(CameraCharacteristics.LENS_FACING);
                if (facing != null && facing == CameraCharacteristics.LENS_FACING_BACK) {
                    continue;
                }

                final StreamConfigurationMap map =
                        characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);

                if (map == null) {
                    continue;
                }

                mCameraId = cameraId;
            }
        } catch (CameraAccessException e) {
            Log.e(TAG, "Not allowed to access camera " + e.getMessage());
        }
    }

    private void createCameraPreviewSession() {
        try {
            final CaptureRequest.Builder previewRequestBuilder =
                    mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);

            // Create the reader for the preview frames.
            mPreviewReader =
                    ImageReader.newInstance(
                            mPreviewWidth, mPreviewHeight, ImageFormat.YUV_420_888, 2);

            mPreviewReader.setOnImageAvailableListener(this, mBackgroundHandler);
            previewRequestBuilder.addTarget(mPreviewReader.getSurface());

            // Here, we create a CameraCaptureSession for camera preview.
            mCameraDevice.createCaptureSession(
                    Arrays.asList(mPreviewReader.getSurface()),
                    new CameraCaptureSession.StateCallback() {
                        @Override
                        public void onConfigured(final CameraCaptureSession cameraCaptureSession) {
                            if (null == mCameraDevice) {
                                return;
                            }

                            mCaptureSession = cameraCaptureSession;
                            try {
                                previewRequestBuilder.set(
                                        CaptureRequest.CONTROL_AF_MODE,
                                        CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
                                previewRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE,
                                        CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH);
                                mPreviewRequest = previewRequestBuilder.build();
                                mCaptureSession.setRepeatingRequest(
                                        mPreviewRequest, mCaptureCallback, mBackgroundHandler);
                            } catch (final CameraAccessException e) {
                                Log.e(TAG, e.getMessage());
                            }
                        }

                        @Override
                        public void onConfigureFailed(final CameraCaptureSession cameraCaptureSession) {
                            Log.e(TAG, "Failed");
                        }
                    },
                    null);
        } catch (final CameraAccessException e) {
            Log.e(TAG, e.getMessage());
        }
    }

    private void closeCamera() {
        if (null != mCaptureSession) {
            mCaptureSession.close();
            mCaptureSession = null;
        }
        if (null != mCameraDevice) {
            mCameraDevice.close();
            mCameraDevice = null;
        }
        if (null != mPreviewReader) {
            mPreviewReader.close();
            mPreviewReader = null;
        }
    }

    protected void fillBytes(final Image.Plane[] planes, final byte[][] yuvBytes) {
        for (int i = 0; i < planes.length; ++i) {
            final ByteBuffer buffer = planes[i].getBuffer();
            if (yuvBytes[i] == null) {
                yuvBytes[i] = new byte[buffer.capacity()];
            }
            buffer.get(yuvBytes[i]);
        }
    }

    private final CameraDevice.StateCallback mStateCallback =
            new CameraDevice.StateCallback() {
                @Override
                public void onOpened(final CameraDevice cd) {
                    mCameraDevice = cd;
                    createCameraPreviewSession();
                }

                @Override
                public void onDisconnected(final CameraDevice cd) {
                    cd.close();
                    mCameraDevice = null;
                }

                @Override
                public void onError(final CameraDevice cd, final int error) {
                    cd.close();
                    mCameraDevice = null;
                }
            };

    private final CameraCaptureSession.CaptureCallback mCaptureCallback =
            new CameraCaptureSession.CaptureCallback() {
                @Override
                public void onCaptureProgressed(
                        final CameraCaptureSession session,
                        final CaptureRequest request,
                        final CaptureResult partialResult) {}

                @Override
                public void onCaptureCompleted(
                        final CameraCaptureSession session,
                        final CaptureRequest request,
                        final TotalCaptureResult result) {}
            };
}
