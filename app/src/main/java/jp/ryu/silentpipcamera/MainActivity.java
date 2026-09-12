package jp.ryu.silentpipcamera;

import android.Manifest;
import android.app.PictureInPictureParams;
import android.content.ContentValues;
import android.content.pm.PackageManager;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.*;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.*;
import android.provider.MediaStore;
import android.util.Rational;
import android.util.Size;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import java.io.FileDescriptor;
import java.util.*;

public class MainActivity extends android.app.Activity {
    private TextureView preview;
    private View controls;
    private TextView status;
    private Button record;
    private CameraDevice camera;
    private CameraCaptureSession session;
    private MediaRecorder recorder;
    private HandlerThread cameraThread;
    private Handler cameraHandler;
    private String cameraId;
    private boolean front = false;
    private boolean recording = false;
    private Uri pendingVideo;
    private android.os.ParcelFileDescriptor outputFile;
    private final Size videoSize = new Size(1920, 1080);

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_main);
        preview = findViewById(R.id.preview);
        controls = findViewById(R.id.controls);
        status = findViewById(R.id.status);
        record = findViewById(R.id.record);
        record.setOnClickListener(v -> { if (recording) stopRecording(); else startRecording(); });
        findViewById(R.id.switchCamera).setOnClickListener(v -> {
            if (recording) return;
            front = !front;
            closeCamera();
            openCamera();
        });
        findViewById(R.id.pip).setOnClickListener(v -> enterPip());
        preview.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            public void onSurfaceTextureAvailable(SurfaceTexture s, int w, int h) { openCamera(); }
            public void onSurfaceTextureSizeChanged(SurfaceTexture s, int w, int h) {}
            public boolean onSurfaceTextureDestroyed(SurfaceTexture s) { return true; }
            public void onSurfaceTextureUpdated(SurfaceTexture s) {}
        });
        setPictureInPictureParams(new PictureInPictureParams.Builder()
                .setAspectRatio(new Rational(16, 9)).setAutoEnterEnabled(true).setSeamlessResizeEnabled(true).build());
    }

    @Override protected void onResume() {
        super.onResume();
        cameraThread = new HandlerThread("Camera");
        cameraThread.start();
        cameraHandler = new Handler(cameraThread.getLooper());
        if (preview.isAvailable() && camera == null) openCamera();
    }

    @Override protected void onPause() {
        super.onPause();
        if (!isInPictureInPictureMode() && !recording) closeCamera();
    }

    @Override protected void onDestroy() {
        if (recording) stopRecording();
        closeCamera();
        if (cameraThread != null) cameraThread.quitSafely();
        super.onDestroy();
    }

    @Override public void onPictureInPictureModeChanged(boolean inPip, android.content.res.Configuration c) {
        super.onPictureInPictureModeChanged(inPip, c);
        controls.setVisibility(inPip ? View.GONE : View.VISIBLE);
        status.setVisibility(inPip ? View.GONE : View.VISIBLE);
    }

    private void enterPip() {
        enterPictureInPictureMode(new PictureInPictureParams.Builder().setAspectRatio(new Rational(16, 9)).build());
    }

    private void openCamera() {
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.CAMERA}, 10);
            return;
        }
        try {
            CameraManager cm = getSystemService(CameraManager.class);
            cameraId = chooseCamera(cm);
            cm.openCamera(cameraId, new CameraDevice.StateCallback() {
                public void onOpened(CameraDevice c) { camera = c; createPreview(); }
                public void onDisconnected(CameraDevice c) { c.close(); camera = null; }
                public void onError(CameraDevice c, int e) { c.close(); camera = null; show("カメラを開けません"); }
            }, cameraHandler);
        } catch (Exception e) { show("カメラエラー: " + e.getMessage()); }
    }

    private String chooseCamera(CameraManager cm) throws CameraAccessException {
        int wanted = front ? CameraCharacteristics.LENS_FACING_FRONT : CameraCharacteristics.LENS_FACING_BACK;
        for (String id : cm.getCameraIdList()) {
            Integer facing = cm.getCameraCharacteristics(id).get(CameraCharacteristics.LENS_FACING);
            if (facing != null && facing == wanted) return id;
        }
        return cm.getCameraIdList()[0];
    }

    private Surface previewSurface() {
        SurfaceTexture st = preview.getSurfaceTexture();
        st.setDefaultBufferSize(videoSize.getWidth(), videoSize.getHeight());
        return new Surface(st);
    }

    private void createPreview() {
        if (camera == null || !preview.isAvailable()) return;
        try {
            Surface p = previewSurface();
            camera.createCaptureSession(Collections.singletonList(p), new CameraCaptureSession.StateCallback() {
                public void onConfigured(CameraCaptureSession s) {
                    session = s;
                    try {
                        CaptureRequest.Builder b = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
                        b.addTarget(p);
                        b.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO);
                        session.setRepeatingRequest(b.build(), null, cameraHandler);
                        show("無音撮影できます");
                    } catch (Exception e) { show("プレビューエラー"); }
                }
                public void onConfigureFailed(CameraCaptureSession s) { show("プレビューを開始できません"); }
            }, cameraHandler);
        } catch (Exception e) { show("プレビューエラー: " + e.getMessage()); }
    }

    private void startRecording() {
        if (camera == null) return;
        try {
            ContentValues cv = new ContentValues();
            cv.put(MediaStore.Video.Media.DISPLAY_NAME, "SilentPiP_" + System.currentTimeMillis() + ".mp4");
            cv.put(MediaStore.Video.Media.MIME_TYPE, "video/mp4");
            cv.put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/SilentPiPCamera");
            cv.put(MediaStore.Video.Media.IS_PENDING, 1);
            pendingVideo = getContentResolver().insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, cv);
            outputFile = getContentResolver().openFileDescriptor(pendingVideo, "w");
            FileDescriptor fd = outputFile.getFileDescriptor();

            recorder = new MediaRecorder();
            recorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);
            recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            recorder.setOutputFile(fd);
            recorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
            recorder.setVideoSize(videoSize.getWidth(), videoSize.getHeight());
            recorder.setVideoFrameRate(30);
            recorder.setVideoEncodingBitRate(12_000_000);
            recorder.prepare();

            Surface p = previewSurface();
            Surface r = recorder.getSurface();
            session.close();
            camera.createCaptureSession(Arrays.asList(p, r), new CameraCaptureSession.StateCallback() {
                public void onConfigured(CameraCaptureSession s) {
                    session = s;
                    try {
                        CaptureRequest.Builder b = camera.createCaptureRequest(CameraDevice.TEMPLATE_RECORD);
                        b.addTarget(p); b.addTarget(r);
                        b.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO);
                        session.setRepeatingRequest(b.build(), null, cameraHandler);
                        recorder.start();
                        recording = true;
                        runOnUiThread(() -> { record.setText("録画停止"); status.setText("● 無音録画中"); });
                    } catch (Exception e) { failRecording(e); }
                }
                public void onConfigureFailed(CameraCaptureSession s) { failRecording(new Exception("録画設定失敗")); }
            }, cameraHandler);
        } catch (Exception e) { failRecording(e); }
    }

    private void stopRecording() {
        recording = false;
        try { if (session != null) session.stopRepeating(); } catch (Exception ignored) {}
        try { if (recorder != null) recorder.stop(); } catch (Exception ignored) {}
        if (recorder != null) { recorder.reset(); recorder.release(); recorder = null; }
        try { if (outputFile != null) outputFile.close(); } catch (Exception ignored) {}
        outputFile = null;
        if (pendingVideo != null) {
            ContentValues cv = new ContentValues(); cv.put(MediaStore.Video.Media.IS_PENDING, 0);
            getContentResolver().update(pendingVideo, cv, null, null);
            pendingVideo = null;
        }
        runOnUiThread(() -> { record.setText("録画開始"); status.setText("保存しました"); Toast.makeText(this, "動画を保存しました", Toast.LENGTH_SHORT).show(); });
        createPreview();
    }

    private void failRecording(Exception e) {
        recording = false;
        runOnUiThread(() -> show("録画エラー: " + e.getMessage()));
    }

    private void closeCamera() {
        if (session != null) { session.close(); session = null; }
        if (camera != null) { camera.close(); camera = null; }
    }

    private void show(String s) { runOnUiThread(() -> status.setText(s)); }

    @Override public void onRequestPermissionsResult(int req, String[] p, int[] g) {
        super.onRequestPermissionsResult(req, p, g);
        if (req == 10 && g.length > 0 && g[0] == PackageManager.PERMISSION_GRANTED) openCamera();
    }
}
