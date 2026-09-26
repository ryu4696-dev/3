package jp.ryu.foldarnav;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.hardware.*;
import android.location.*;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.*;
import android.view.*;
import android.widget.*;

public class MainActivity extends Activity implements LocationListener, SensorEventListener {
    private static final int REQ_PERMS = 10;
    private static final int REQ_CAPTURE = 11;

    private MediaProjectionManager projectionManager;
    private CameraArView arView;
    private SensorManager sensorManager;
    private Sensor rotationSensor;
    private LocationManager locationManager;
    private boolean arStarted = false;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        projectionManager = getSystemService(MediaProjectionManager.class);
        sensorManager = getSystemService(SensorManager.class);
        rotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
        locationManager = getSystemService(LocationManager.class);
        showSetup();
    }

    private void showSetup() {
        arStarted = false;

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setPadding(48, 48, 48, 48);
        root.setBackgroundColor(Color.rgb(10,10,12));

        TextView title = new TextView(this);
        title.setText("Fold AR Nav");
        title.setTextColor(Color.WHITE);
        title.setTextSize(30);
        title.setGravity(Gravity.CENTER);
        root.addView(title, new LinearLayout.LayoutParams(-1, -2));

        TextView guide = new TextView(this);
        guide.setText("\n① Googleマップを左側でナビ開始\n② このアプリを右側に分割表示\n③ AR開始を押し、画面共有で「Googleマップ」を選択\n\n開始後、この側はカメラ＋ARルートだけになります。");
        guide.setTextColor(Color.LTGRAY);
        guide.setTextSize(16);
        guide.setGravity(Gravity.CENTER);
        root.addView(guide, new LinearLayout.LayoutParams(-1, -2));

        Button maps = new Button(this);
        maps.setText("Googleマップを開く");
        maps.setOnClickListener(v -> openMapsAdjacent());
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(-1, -2);
        bp.topMargin = 36;
        root.addView(maps, bp);

        Button start = new Button(this);
        start.setText("AR開始");
        start.setOnClickListener(v -> begin());
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(-1, -2);
        sp.topMargin = 16;
        root.addView(start, sp);

        setContentView(root);
    }

    private void openMapsAdjacent() {
        Intent i = getPackageManager().getLaunchIntentForPackage("com.google.android.apps.maps");
        if (i == null) i = new Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q="));

        i.addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK |
                Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT |
                Intent.FLAG_ACTIVITY_MULTIPLE_TASK);

        try {
            startActivity(i);
        } catch (Exception e) {
            Toast.makeText(this, "Googleマップを開けません", Toast.LENGTH_SHORT).show();
        }
    }

    private void begin() {
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED ||
                checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {

            requestPermissions(new String[]{
                    Manifest.permission.CAMERA,
                    Manifest.permission.ACCESS_FINE_LOCATION
            }, REQ_PERMS);
            return;
        }

        requestProjection();
    }

    private void requestProjection() {
        startActivityForResult(
                projectionManager.createScreenCaptureIntent(),
                REQ_CAPTURE);
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode,
            String[] permissions,
            int[] grants) {

        super.onRequestPermissionsResult(requestCode, permissions, grants);

        if (requestCode == REQ_PERMS) {
            boolean ok = true;
            for (int g : grants) ok &= g == PackageManager.PERMISSION_GRANTED;

            if (ok) requestProjection();
            else Toast.makeText(
                    this,
                    "カメラと位置情報が必要です",
                    Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onActivityResult(
            int requestCode,
            int resultCode,
            Intent data) {

        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode != REQ_CAPTURE) return;

        if (resultCode != RESULT_OK || data == null) {
            Toast.makeText(
                    this,
                    "画面共有が許可されていません",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        Intent svc = new Intent(this, ScreenCaptureService.class);
        svc.putExtra(ScreenCaptureService.EXTRA_RESULT_CODE, resultCode);
        svc.putExtra(ScreenCaptureService.EXTRA_RESULT_DATA, data);
        startForegroundService(svc);

        startArUi();
    }

    private void startArUi() {
        arStarted = true;
        arView = new CameraArView(this);
        setContentView(arView);

        enterImmersive();
        startSensorsAndLocation();
        arView.resumeCamera();
    }

    private void enterImmersive() {
        WindowInsetsController c = getWindow().getInsetsController();
        if (c != null) {
            c.hide(
                    WindowInsets.Type.statusBars() |
                    WindowInsets.Type.navigationBars());

            c.setSystemBarsBehavior(
                    WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
        }
    }

    private void startSensorsAndLocation() {
        if (rotationSensor != null) {
            sensorManager.registerListener(
                    this,
                    rotationSensor,
                    SensorManager.SENSOR_DELAY_GAME);
        }

        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {

            try {
                locationManager.requestLocationUpdates(
                        LocationManager.GPS_PROVIDER,
                        500,
                        0f,
                        this);

                locationManager.requestLocationUpdates(
                        LocationManager.NETWORK_PROVIDER,
                        1000,
                        0f,
                        this);
            } catch (Exception ignored) {}
        }
    }

    @Override protected void onResume() {
        super.onResume();
        if (arStarted && arView != null) arView.resumeCamera();
    }

    @Override protected void onPause() {
        if (arView != null) arView.pauseCamera();
        super.onPause();
    }

    @Override protected void onDestroy() {
        sensorManager.unregisterListener(this);
        try { locationManager.removeUpdates(this); } catch (Exception ignored) {}

        if (isFinishing()) {
            stopService(new Intent(this, ScreenCaptureService.class));
        }

        super.onDestroy();
    }

    @Override public void onBackPressed() {
        if (arStarted) {
            if (arView != null) arView.pauseCamera();
            stopService(new Intent(this, ScreenCaptureService.class));
            showSetup();
        } else {
            super.onBackPressed();
        }
    }

    @Override public void onLocationChanged(Location l) {
        NavigationState s = NavigationState.get();
        s.latitude = l.getLatitude();
        s.longitude = l.getLongitude();
        s.speedMps = l.hasSpeed() ? l.getSpeed() : 0f;
        if (l.hasBearing()) s.bearingDeg = l.getBearing();
    }

    @Override public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() != Sensor.TYPE_ROTATION_VECTOR) return;

        float[] r = new float[9];
        float[] o = new float[3];

        SensorManager.getRotationMatrixFromVector(r, event.values);
        SensorManager.getOrientation(r, o);

        NavigationState s = NavigationState.get();
        s.imuAzimuthDeg = (float)Math.toDegrees(o[0]);
        s.imuPitchDeg = (float)Math.toDegrees(o[1]);
        s.imuRollDeg = (float)Math.toDegrees(o[2]);
    }

    @Override public void onAccuracyChanged(Sensor sensor, int accuracy) {}
}
