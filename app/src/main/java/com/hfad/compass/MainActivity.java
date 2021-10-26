package com.hfad.compass;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.content.IntentSender;
import android.content.pm.PackageManager;
import android.hardware.GeomagneticField;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.common.api.ResolvableApiException;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationAvailability;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.LocationSettingsRequest;
import com.google.android.gms.location.LocationSettingsResponse;
import com.google.android.gms.location.SettingsClient;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;

import java.util.Collections;

public class MainActivity extends AppCompatActivity implements SensorEventListener {


    Window window;
    WindowManager.LayoutParams layoutParams;
    SensorManager sensorManager;
    Sensor magnetometerSensor;
    Sensor accelometerSensor;
    private Sensor gravitySensor;
    private Sensor rotationVectorSensor;

    //location
    FusedLocationProviderClient fusedLocationClient;
    LocationRequest locationRequest;
    LocationSettingsRequest.Builder builder;
    SettingsClient client;
    LocationListener listener;
    Location lastLocation;


    TextView azimuthTextView;
    ImageView compassFrontImageView;
    ObjectAnimator rotationAnimation;


    boolean permissionRequested;
    boolean settingsChangeRequested;

    GeomagneticField geomagneticField;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        azimuthTextView = (TextView) findViewById(R.id.azimuthTextView);
        compassFrontImageView = (ImageView) findViewById(R.id.compassFrontImageView);

        locationRequest = LocationRequest.create()
                .setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY)
                .setInterval(5000)
                .setFastestInterval(1000);

        builder = new LocationSettingsRequest.Builder().addAllLocationRequests(Collections.singleton(locationRequest));
        client = LocationServices.getSettingsClient(this);
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        listener = new LocationListener() {
            @Override
            public void onLocationChanged(@NonNull Location location) {
                lastLocation = location;

                if(location != null) {
                    GeomagneticField geomagneticField = new GeomagneticField((float)location.getLatitude(), (float)location.getLongitude(), location.getBearing(), location.getTime());
                    geomagneticField.getDeclination();
                }
            }
        };

        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        magnetometerSensor = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
        accelometerSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        gravitySensor = sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY);
        rotationVectorSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
        window = getWindow();
        layoutParams = window.getAttributes();
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);


    }


    @Override
    protected void onResume() {
        super.onResume();

        if(rotationVectorSensor != null) {
            sensorManager.registerListener(this, rotationVectorSensor, SensorManager.SENSOR_DELAY_NORMAL);

            operationMode = 2;

        }
        else if(magnetometerSensor != null && gravitySensor != null) {
            sensorManager.registerListener(this, magnetometerSensor, SensorManager.SENSOR_DELAY_NORMAL);
            sensorManager.registerListener(this, gravitySensor, SensorManager.SENSOR_DELAY_NORMAL);

            operationMode = 1;
        }
        else if(magnetometerSensor != null && accelometerSensor != null) {
            sensorManager.registerListener(this, magnetometerSensor, SensorManager.SENSOR_DELAY_NORMAL);
            sensorManager.registerListener(this, accelometerSensor, SensorManager.SENSOR_DELAY_NORMAL);

            operationMode = 0;
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        sensorManager.unregisterListener(this);
    }

    int operationMode = 0;
    int azimuth;
    int oldAzimuth;

    float[] magnetometerField = new float[3];
    float[] accelometerField = new float[3];
    float[] gravity = new float[3];
    float[] rotationVector = new float[5];

    float[] rotationMatrix = new float[9];
    float[] inclinationMatrix = new float[9];
    float[] orientation = new float[3];

    @Override
    public void onSensorChanged(SensorEvent event) {
        switch (event.sensor.getType()){
            case Sensor.TYPE_MAGNETIC_FIELD:
                System.arraycopy(event.values, 0, magnetometerField, 0, event.values.length);
                break;
            case Sensor.TYPE_ACCELEROMETER:
                System.arraycopy(event.values, 0, accelometerField, 0, event.values.length);
                break;
            case Sensor.TYPE_GRAVITY:
                System.arraycopy(event.values, 0, gravity, 0, event.values.length);
                break;
            case Sensor.TYPE_ROTATION_VECTOR:
                System.arraycopy(event.values, 0, rotationVector, 0, event.values.length);
                break;
        }

        if(operationMode == 0 || operationMode == 1) {
            if (SensorManager.getRotationMatrix(rotationMatrix, inclinationMatrix, (operationMode == 0) ? accelometerField : gravity, magnetometerField)) {
                float azimuthRad = SensorManager.getOrientation(rotationMatrix, orientation)[0];
                double azimuthDeg = Math.toDegrees(azimuthRad);

                azimuth = ((int) azimuthDeg + 360) % 360;

            }
        }
            else if(operationMode == 2) {
                SensorManager.getRotationMatrixFromVector(rotationMatrix, rotationVector);
                azimuth = (int) (Math.toDegrees(SensorManager.getOrientation(rotationMatrix, orientation)[0] + 360) % 360);
            }

        azimuthTextView.setText(String.valueOf(azimuth) + "°");
        //compassFrontImageView.setRotation(-azimuth);

        float tempAzimuth;
        float tempCurrentAzimuth;

        if (Math.abs(azimuth - oldAzimuth) > 180) {
            if (oldAzimuth < azimuth) {
                tempCurrentAzimuth = oldAzimuth + 360;
                tempAzimuth = azimuth;
            } else {
                tempCurrentAzimuth = oldAzimuth;
                tempAzimuth = azimuth + 360;
            }
            rotationAnimation = ObjectAnimator.ofFloat(compassFrontImageView, "rotation", -tempCurrentAzimuth, -tempAzimuth);
            rotationAnimation.setDuration(250);
            rotationAnimation.start();
        } else {
            rotationAnimation = ObjectAnimator.ofFloat(compassFrontImageView, "rotation", -oldAzimuth, -azimuth);
            rotationAnimation.setDuration(250);
            rotationAnimation.start();
        }
        oldAzimuth = azimuth;

    }


    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.actionbar_menu, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {

        switch (item.getItemId()) {
            case R.id.help:

                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setTitle("Compass");
                builder.setMessage("This is compass app, part of the ITAcademy and LinkAcademy Android development program");

                AlertDialog dialog = builder.create();
                dialog.show();

                return true;
            default:
                return super.onOptionsItemSelected(item);

        }
    }
}