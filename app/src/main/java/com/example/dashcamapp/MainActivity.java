package com.example.dashcamapp;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.Camera;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.MediaRecorder;
import android.media.MediaScannerConnection;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.widget.Button;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import java.util.concurrent.TimeUnit;

public class MainActivity extends AppCompatActivity implements SurfaceHolder.Callback, SensorEventListener {
    private static final int REQUEST_PERMISSIONS = 100;
    private static final String TAG = "DashcamApp";
    private static final String UPLOAD_URL = "http://172.17.2.169:3000/upload";

    private SurfaceView surfaceView;
    private SurfaceHolder surfaceHolder;
    private Camera camera;
    private MediaRecorder mediaRecorder;
    private Button recordButton;
    private File videoFile;
    private boolean isRecording = false;
    private File outputFile;

    private SensorManager sensorManager;
    private Sensor linearAccelerationSensor;
    private static final float ACCELERATION_THRESHOLD = 3.0f;
    private static final long INTERVAL_MS = 750; // Interval set to 750ms
    private long lastCheckTime = 0;
    private float[] lastAccelValues = new float[3];

    private final OkHttpClient client = new OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        surfaceView = findViewById(R.id.surfaceView);
        recordButton = findViewById(R.id.recordButton);
        surfaceHolder = surfaceView.getHolder();
        surfaceHolder.addCallback(this);

        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        if (sensorManager != null) {
            linearAccelerationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION);
        }

        if (!checkPermissions()) {
            requestPermissions();
        }

        recordButton.setOnClickListener(v -> {
            if (isRecording) {
                stopRecording();
            } else {
                startRecording();
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (checkPermissions() && camera == null) {
            try {
                camera = Camera.open();
                camera.setDisplayOrientation(90);
                camera.setPreviewDisplay(surfaceHolder);
            } catch (IOException e) {
                Log.e(TAG, "Failed to open camera", e);
                Toast.makeText(this, "Failed to open camera", Toast.LENGTH_SHORT).show();
                finish();
            }
        }

        if (linearAccelerationSensor != null) {
            sensorManager.registerListener(this, linearAccelerationSensor, SensorManager.SENSOR_DELAY_NORMAL);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        releaseCamera();
        sensorManager.unregisterListener(this);
    }

    private boolean checkPermissions() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestPermissions() {
        ActivityCompat.requestPermissions(this, new String[]{
                Manifest.permission.CAMERA,
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                Manifest.permission.READ_EXTERNAL_STORAGE
        }, REQUEST_PERMISSIONS);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_PERMISSIONS) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Permissions granted", Toast.LENGTH_SHORT).show();
                try {
                    camera = Camera.open();
                    camera.setDisplayOrientation(90);
                    camera.setPreviewDisplay(surfaceHolder);
                    camera.startPreview();
                } catch (IOException e) {
                    Log.e(TAG, "Error setting up camera preview", e);
                }
            } else {
                Toast.makeText(this, "Permissions denied", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void startRecording() {
        if (camera == null) {
            camera = Camera.open();
            camera.setDisplayOrientation(90);
        }
        camera.unlock();

        mediaRecorder = new MediaRecorder();
        mediaRecorder.setCamera(camera);
        mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        mediaRecorder.setVideoSource(MediaRecorder.VideoSource.CAMERA);
        mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
        mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
        mediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
        mediaRecorder.setOrientationHint(90);
        mediaRecorder.setVideoSize(1280, 720);

        outputFile = getOutputMediaFile();
        if (outputFile != null) {
            mediaRecorder.setOutputFile(outputFile.getAbsolutePath());
        } else {
            Toast.makeText(this, "Failed to create output file", Toast.LENGTH_SHORT).show();
            return;
        }

        mediaRecorder.setPreviewDisplay(surfaceHolder.getSurface());

        try {
            mediaRecorder.prepare();
            mediaRecorder.start();
            isRecording = true;
            recordButton.setText("STOP RECORDING");
            Toast.makeText(this, "Recording Started", Toast.LENGTH_SHORT).show();

            // Stop recording after 5 minutes (300,000 milliseconds)
            new Handler().postDelayed(this::stopRecording, 300000); // 5 minutes
        } catch (IOException e) {
            Log.e(TAG, "Error starting media recorder", e);
            Toast.makeText(this, "Failed to start recording", Toast.LENGTH_SHORT).show();
            releaseMediaRecorder();
        }
    }

    private void stopRecording() {
        if (isRecording && mediaRecorder != null) {
            try {
                mediaRecorder.stop();
                mediaRecorder.reset();
                mediaRecorder.release();
                mediaRecorder = null;
                isRecording = false;
                recordButton.setText("START RECORDING");

                MediaScannerConnection.scanFile(this, new String[]{outputFile.getAbsolutePath()},
                        new String[]{"video/mp4"},
                        (path, uri) -> {
                            Log.d("MediaScanner", "Scanned: " + path);
                            // Upload the video after recording is complete
                            uploadVideo(outputFile);
                        });

                Toast.makeText(this, "Recording saved: " + outputFile.getAbsolutePath(), Toast.LENGTH_LONG).show();
                Log.d("Dashcam", "Recording stopped, video saved at: " + outputFile.getAbsolutePath());
            } catch (RuntimeException e) {
                Log.e("Dashcam", "Error stopping MediaRecorder: " + e.getMessage());
                mediaRecorder.reset();
                mediaRecorder.release();
                mediaRecorder = null;
            }
        }
    }

    private File getOutputMediaFile() {
        File mediaStorageDir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES), "DashcamVideos");

        if (!mediaStorageDir.exists()) {
            if (!mediaStorageDir.mkdirs()) {
                Log.e(TAG, "Failed to create directory");
                return null;
            }
        }

        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        videoFile = new File(mediaStorageDir.getPath() + File.separator + "VID_" + timeStamp + ".mp4");
        return videoFile;
    }

    private void releaseMediaRecorder() {
        if (mediaRecorder != null) {
            try {
                mediaRecorder.reset();
                mediaRecorder.release();
                mediaRecorder = null;
                if (camera != null) {
                    camera.lock();
                }
            } catch (Exception e) {
                Log.e(TAG, "Error releasing MediaRecorder", e);
            }
        }
    }

    private void releaseCamera() {
        if (camera != null) {
            camera.stopPreview();
            camera.release();
            camera = null;
        }
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        try {
            camera = Camera.open();
            camera.setDisplayOrientation(90);
            camera.setPreviewDisplay(holder);
            camera.startPreview();

            camera.autoFocus((success, cam) -> {
                if (success) {
                    Toast.makeText(MainActivity.this, "Autofocus successful", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(MainActivity.this, "Autofocus failed", Toast.LENGTH_SHORT).show();
                }
            });

        } catch (IOException e) {
            Log.e(TAG, "Error setting up camera preview", e);
        }
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        if (surfaceHolder.getSurface() == null) {
            return;
        }

        try {
            camera.stopPreview();
            camera.setPreviewDisplay(holder);
            camera.startPreview();
        } catch (Exception e) {
            Log.e(TAG, "Error restarting camera preview", e);
        }
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        releaseCamera();
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_LINEAR_ACCELERATION) {
            float x = event.values[0];
            float y = event.values[1];
            float z = event.values[2];

            long currentTime = System.currentTimeMillis();
            if (lastCheckTime == 0) {
                lastAccelValues[0] = x;
                lastAccelValues[1] = y;
                lastAccelValues[2] = z;
                lastCheckTime = currentTime;
                return;
            }

            if (currentTime - lastCheckTime >= INTERVAL_MS) {
                float dx = x - lastAccelValues[0];
                float dy = y - lastAccelValues[1];
                float dz = z - lastAccelValues[2];

                float deviation = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
                Log.d(TAG, "Acceleration Deviation: " + deviation);

                if (deviation > ACCELERATION_THRESHOLD && !isRecording) {
                    startRecording();
                    Toast.makeText(this, "Significant motion detected — recording started", Toast.LENGTH_SHORT).show();
                }

                lastAccelValues[0] = x;
                lastAccelValues[1] = y;
                lastAccelValues[2] = z;
                lastCheckTime = currentTime;
            }
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        Log.d(TAG, "Sensor accuracy changed: " + sensor.getName() + " to " + accuracy);
    }

    private void uploadVideo(File videoFile) {
        if (!videoFile.exists()) {
            Log.e(TAG, "Video file does not exist: " + videoFile.getAbsolutePath());
            runOnUiThread(() -> {
                Toast.makeText(MainActivity.this, "Error: Video file not found", Toast.LENGTH_LONG).show();
            });
            return;
        }

        new Thread(() -> {
            try {
                Log.d(TAG, "Starting video upload: " + videoFile.getAbsolutePath());
                Log.d(TAG, "File size: " + videoFile.length() + " bytes");

                RequestBody videoBody = RequestBody.create(MediaType.parse("video/mp4"), videoFile);
                RequestBody requestBody = new MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("video", videoFile.getName(), videoBody)
                    .build();

                Request request = new Request.Builder()
                    .url(UPLOAD_URL)
                    .post(requestBody)
                    .build();

                Log.d(TAG, "Sending request to: " + UPLOAD_URL);

                try (Response response = client.newCall(request).execute()) {
                    final String responseBody = response.body().string();
                    Log.d(TAG, "Upload response: " + responseBody);
                    
                    runOnUiThread(() -> {
                        if (response.isSuccessful()) {
                            Toast.makeText(MainActivity.this, "Video uploaded successfully", Toast.LENGTH_LONG).show();
                        } else {
                            String error = "Upload failed: " + response.code() + " - " + responseBody;
                            Log.e(TAG, error);
                            Toast.makeText(MainActivity.this, error, Toast.LENGTH_LONG).show();
                        }
                    });
                }
            } catch (IOException e) {
                String error = "Upload failed: " + e.getMessage();
                Log.e(TAG, error, e);
                runOnUiThread(() -> {
                    Toast.makeText(MainActivity.this, error, Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }
}
