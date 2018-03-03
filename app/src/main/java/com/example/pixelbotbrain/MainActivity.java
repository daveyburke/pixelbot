package com.example.pixelbotbrain;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.RectF;
import android.media.MediaPlayer;
import android.speech.tts.TextToSpeech;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.WindowManager;
import android.widget.Toast;

import com.example.pixelbotbrain.tensorflow.Classifier;

import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Timer;
import java.util.TimerTask;

/**
 * Pixelbot is an AI robot that uses an Android device as a brain - the screen animates its face,
 * the front-facing camera is its eye, and the speaker is its mouthpiece. The brain communicates
 * with its body via Bluetooth to an Arduino-controlled set of servos and motors. TensorFlow
 * is used for object detection.
 *
 * Written by Dave Burke (2018)
 */
public class MainActivity extends AppCompatActivity implements
        CameraObjectRecognizer.Listener, BluetoothArduinoBridge.Listener {
    private static final String TAG = "PixelbotBrain";

    private static final int TF_OD_API_INPUT_SIZE = 300;
    private static final String TF_OD_API_MODEL_FILE =
            "file:///android_asset/ssd_mobilenet_v1_android_export.pb";
    private static final String TF_OD_API_LABELS_FILE =
            "file:///android_asset/coco_labels_list.txt";
    private static final float MINIMUM_CONFIDENCE_TF_OD_API = 0.7f;

    private PixelbotBodyController mPixelbotBodyController;
    private CameraObjectRecognizer mCameraObjectRecognizer;
    private PixelbotFace mPixelbotFace;
    private Timer mBlinkTimer;
    private boolean mBodyConnected;
    private long mLastSpeakTime;
    private TextToSpeech mTts;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        mPixelbotFace = findViewById(R.id.pixelbotfaceview);

        if (!hasCameraPermission()) {
            requestCameraPermission();
        }

        mTts = new TextToSpeech(this, new TextToSpeech.OnInitListener () {
            @Override
            public void onInit(int i) { }
        });

        mPixelbotBodyController = new PixelbotBodyController();
        mCameraObjectRecognizer = new CameraObjectRecognizer(this, this);
    }

    public void onResume () {
        super.onResume();
        mCameraObjectRecognizer.onResume();
        mPixelbotBodyController.connectAsync(this);
        startBlinkTimer();
    }

    public void onPause () {
        super.onPause();
        mCameraObjectRecognizer.onPause();
        mPixelbotBodyController.disconnect();
        stopBlinkTimer();
    }

    private void startBlinkTimer() {
        if (mBlinkTimer != null) return;

        mBlinkTimer = new Timer();
        TimerTask timerTask = new TimerTask() {
            public void run() {
                mPixelbotFace.blink();
                if (Math.random() < 0.2) {
                    fartNoise();
                }
            }
        };
        mBlinkTimer.schedule(timerTask, 3000, 3000);
    }

    private void stopBlinkTimer() {
        if (mBlinkTimer != null) {
            mBlinkTimer.cancel();
            mBlinkTimer = null;
        }
    }

    private void speak(String text) {
        if (System.currentTimeMillis() - mLastSpeakTime < 3000) return; // rate limit

        HashMap<String, String> params = new HashMap<String, String>();
        params.put(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, text);
        mTts.speak(text, TextToSpeech.QUEUE_FLUSH, params);
        mLastSpeakTime = System.currentTimeMillis();
    }

    private boolean hasCameraPermission() {
        return checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestCameraPermission() {
        if (shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)) {
            Toast.makeText(this,
                    "Camera permission are required for this demo", Toast.LENGTH_LONG).show();
        }
        requestPermissions(new String[] {Manifest.permission.CAMERA}, 1);
    }

    private void fartNoise(){
        try {
            MediaPlayer player = MediaPlayer.create(this, R.raw.fart);
            player.start();
            player.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                @Override
                public void onCompletion(MediaPlayer mp) {
                    mp.release();
                }

            });
        } catch (Exception e) {
            Log.e(TAG, e.getMessage());
        }
    }

    @Override
    public void onObjectDetected(LinkedList<Classifier.Recognition> recognitions,
                                 int viewFinderWidth, int viewFinderHeight) {
        String objectName = recognitions.get(0).getTitle();
        RectF location = recognitions.get(0).getLocation();

        if (objectName.equals("person")) {
            objectName = "human"; // for dramatic effect!

            for (int i = 1; i < recognitions.size(); i++) {  // prefer other objects over humans
                if (!recognitions.get(i).getTitle().equals("person")) {
                    objectName = recognitions.get(i).getTitle();
                    location = recognitions.get(i).getLocation();
                    break;
                }
            }
        }

        speak(objectName);

        if (mBodyConnected) {
            // Track the object to scale (-128, 127). Invert pan because of front-facing camera
            float panError = -255 * (location.centerX() - viewFinderWidth / 2) / viewFinderWidth;
            float tiltError = 255 * (location.centerY() - viewFinderHeight / 2) / viewFinderHeight;
            float objectSize = 255 * location.width() * location.height() / (viewFinderWidth * viewFinderHeight);
            mPixelbotBodyController.trackServo((byte)panError, (byte)tiltError, true,
                    (byte) objectSize);
        }
    }

    @Override
    public void onBluetoothConnected() {
        Log.d(TAG, "Bluetooth connected");
        mBodyConnected = true;
    }

    @Override
    public void onBluetoothConnectionFailed(String errorMsg) {
        Log.e(TAG, errorMsg);
        mBodyConnected = false;
    }
}
