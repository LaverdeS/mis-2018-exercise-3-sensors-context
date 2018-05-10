package com.example.mis.sensor;

import android.Manifest;
import android.app.IntentService;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Binder;
import android.os.IBinder;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.graphics.Color;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.AsyncTask;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.util.LogPrinter;
import android.view.View;
import android.widget.SeekBar;
import android.widget.Toast;

import com.example.mis.sensor.FFT;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.ActivityRecognition;
import com.google.android.gms.location.ActivityRecognitionClient;
import com.google.android.gms.location.ActivityRecognitionResult;
import com.google.android.gms.location.DetectedActivity;

import android.location.LocationListener;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.jjoe64.graphview.GraphView;
import com.jjoe64.graphview.LegendRenderer;
import com.jjoe64.graphview.series.DataPoint;
import com.jjoe64.graphview.series.LineGraphSeries;

import java.io.IOException;
import java.util.Random;

import static android.location.LocationManager.GPS_PROVIDER;
import static java.util.Arrays.sort;

public class MainActivity extends AppCompatActivity implements SensorEventListener {

    //example variables
    private double[] rndAccExamplevalues;
    private double[] accMagValues;
    private double[] accXValues;
    private double[] accYValues;
    private double[] accZValues;
    private double[] freqCounts;
    private AsyncTask<double[], Void, double[]> fftTask;
    private SensorManager mSensorManager;
    private Sensor accelerometer;
    private SeekBar mSeekBar1;
    private SeekBar mSeekBar2;
    private GraphView graph1;
    private GraphView graph2;
    private LineGraphSeries<DataPoint> accelXSeries = new LineGraphSeries<>();
    private LineGraphSeries<DataPoint> accelYSeries = new LineGraphSeries<>();
    private LineGraphSeries<DataPoint> accelZSeries = new LineGraphSeries<>();
    private LineGraphSeries<DataPoint> accelMagSeries = new LineGraphSeries<>();
    private LineGraphSeries<DataPoint> FFTSeries = new LineGraphSeries<>();
    private int samplingPeriod = 200000; //in microseconds
    private int wsize = 256;
    private int activityWSize = 7;
    private int buffer = 0;

    private double speed;
    private LocationManager locationManager;
    private LocationListener locationListener = new LocationListener() {
        @Override
        public void onLocationChanged(Location location) {
            Log.e("locationListener", "Location changed");
            if(location.hasSpeed()) {
                speed = location.getSpeed();
            }
        }

        @Override
        public void onStatusChanged(String provider, int status, Bundle extras) {}

        @Override
        public void onProviderEnabled(String provider) {Log.e("locationListener", "###PROVIDER ENABLED###");}

        @Override
        public void onProviderDisabled(String provider) {}
    };

    private static MediaPlayer mediaPlayerJogging;// = new MediaPlayer();
    private static MediaPlayer mediaPlayerCycling;// = new MediaPlayer();
    private MediaPlayer activePlayer = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //EXERCISE 3a variables
        mSeekBar1 = findViewById(R.id.seekBar1);
        mSeekBar1.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            private int tempProgress;

            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                tempProgress = progress;
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                samplingPeriod = (tempProgress * 20000) + 100000; // in microseconds
                mSensorManager.unregisterListener(MainActivity.this);
                mSensorManager.registerListener(MainActivity.this, accelerometer, samplingPeriod);
            }
        });

        mSeekBar2 = findViewById(R.id.seekBar2);
        mSeekBar2.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            private int temporaryWsize;

            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                temporaryWsize = progress + 4;
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                activityWSize = temporaryWsize;
                wsize = (int) Math.pow(2, temporaryWsize);
                accMagValues = new double[wsize];
                accXValues = new double[activityWSize];
                accYValues = new double[activityWSize];
                accZValues = new double[activityWSize];
                //Log.println(Log.DEBUG, "debug", String.valueOf(accMagValues[0]));
                Toast.makeText(getApplicationContext(),
                        "Window size is now " + String.valueOf(wsize),
                        Toast.LENGTH_SHORT).show();
            }
        });

        graph1 = findViewById(R.id.graph1);
        graph1.onDataChanged(false, false);
        graph1.getViewport().setScrollableY(false);
        graph1.getViewport().setScrollable(true);
        graph1.getViewport().setBackgroundColor(Color.DKGRAY);
        graph1.getViewport().setBorderColor(Color.LTGRAY);
        graph1.getGridLabelRenderer().setHorizontalLabelsVisible(false);

        graph1.addSeries(accelXSeries);
        accelXSeries.setColor(Color.RED);
        graph1.addSeries(accelYSeries);
        accelYSeries.setColor(Color.GREEN);
        graph1.addSeries(accelZSeries);
        accelZSeries.setColor(Color.BLUE);
        graph1.addSeries(accelMagSeries);
        accelMagSeries.setColor(Color.WHITE);

        graph2 = findViewById(R.id.graph2);
        graph2.onDataChanged(false, false);
        graph2.getViewport().setScrollableY(false);
        graph2.getViewport().setScrollable(false);
        graph2.getViewport().setBackgroundColor(Color.DKGRAY);
        graph2.getViewport().setBorderColor(Color.LTGRAY);
        graph2.getGridLabelRenderer().setHorizontalLabelsVisible(false);

        graph2.addSeries(FFTSeries);
        FFTSeries.setColor(Color.YELLOW);

        accMagValues = new double[wsize];
        accXValues = new double[activityWSize];
        accYValues = new double[activityWSize];
        accZValues = new double[activityWSize];
        mediaPlayerJogging = MediaPlayer.create(getApplicationContext(), R.raw.tours__01__enthusiast);
        mediaPlayerJogging.setLooping(true);
        mediaPlayerCycling = MediaPlayer.create(this, R.raw.jeffspeed68__picking_guitars);
        mediaPlayerCycling.setLooping(true);
        //mediaPlayerJogging.start();


        //initiate and fill example array with random values
        //rndAccExamplevalues = new double[64];
        //randomFill(rndAccExamplevalues);
        //new FFTAsynctask(64).execute(rndAccExamplevalues);

        mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        if (mSensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION) != null) {
            // Success! There's a accelerometer.
            accelerometer = mSensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION);
            mSensorManager.registerListener(this, accelerometer, samplingPeriod);
        } else {
            // Failure! No accelerometer.
            Toast.makeText(getApplicationContext(), "ERROR! No Acceleromenter available!", Toast.LENGTH_LONG).show();
        }

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return;
        }
        locationManager = (LocationManager) this.getSystemService(Context.LOCATION_SERVICE);
        //https://developer.android.com/training/permissions/requesting#java
        if(ContextCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 0);
        }
        locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, locationListener);

    }

    private float computeMagnitude(float[] values){
        return (float) Math.sqrt(Math.pow(values[0], 2) + Math.pow(values[1], 2) + Math.pow(values[2], 2));
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        Log.e("SensorValue", "X: " + String.valueOf(event.values[0]) + " Y: " + String.valueOf(event.values[1]) + " Z: " + String.valueOf(event.values[2]));
        accelXSeries.appendData(new DataPoint(event.timestamp, event.values[0]), true, 500);
        accelYSeries.appendData(new DataPoint(event.timestamp, event.values[1]), true, 500);
        accelZSeries.appendData(new DataPoint(event.timestamp, event.values[2]), true, 500);
        accelMagSeries.appendData(new DataPoint(event.timestamp, computeMagnitude(event.values)), true, wsize);
        graph1.onDataChanged(true, true);
        accMagValues[buffer % wsize] = computeMagnitude(event.values);
        accXValues[buffer % activityWSize] = event.values[0]; // aka RED line
        accYValues[buffer % activityWSize] = event.values[1]; // aka GREEN line
        accZValues[buffer % activityWSize] = event.values[2]; // aka BLUE line
        if(buffer % activityWSize == 0 && buffer > 0){
            activityEvaluation(accXValues, accYValues, accZValues);
        }
        buffer++;
        new FFTAsynctask(wsize).execute(accMagValues);
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        Toast.makeText(getApplicationContext(),
                "Now the sampling period is " + String.valueOf(samplingPeriod/1000) + " ms",
                Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        //startTracking();
        mSensorManager.registerListener(this, accelerometer, samplingPeriod);
        //activePlayer.start();
        //LocalBroadcastManager.getInstance(getApplicationContext()).registerReceiver(broadcastReceiver, new IntentFilter("activity_intent"));
    }

    @Override
    protected void onPause() {
        super.onPause();
        //stopTracking();
        mSensorManager.unregisterListener(this);
        activePlayer.pause();
        //mediaPlayerJogging.pause();
        //mediaPlayerCycling.pause();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mediaPlayerJogging.release();
        mediaPlayerCycling.release();
        activePlayer = null;
        mSensorManager.unregisterListener(this);
    }

    private void activityEvaluation(double[] accXValues, double[] accYValues, double[] accZValues){
        int xPeaks = 0; int yPeaks = 0; int zPeaks = 0;
        for (double xValue : accXValues) { if(Math.abs(xValue) > 4) xPeaks++; }
        for (double yValue : accYValues) { if(Math.abs(yValue) > 4) yPeaks++; }
        for (double zValue : accZValues) { if(Math.abs(zValue) > 4) zPeaks++; }
        Log.e("location.getSpeed", "Speed: "+ String.valueOf(speed));
        Toast.makeText(getApplicationContext(), "Speed: "+ String.valueOf(speed), Toast.LENGTH_SHORT).show();
        if((yPeaks / activityWSize) > 0.4 && (xPeaks / activityWSize) < 0.4){ // Jogging detected, more details in external file     #### speed > 1.5 && speed < 3 &&
            //Toast.makeText(getApplicationContext(), "User is JOGGING!", Toast.LENGTH_SHORT).show();
            Log.e("activityEvaluation", "User is JOGGING!");
            // Play some music!!!
            //mediaPlayerJogging = MediaPlayer.create(getApplicationContext(), R.raw.tours__01__enthusiast);
            if(activePlayer == null){
                mediaPlayerJogging.start();
                activePlayer = mediaPlayerJogging;
            }else{
                if(activePlayer.equals(mediaPlayerCycling)) {
                    try {
                        mediaPlayerCycling.stop();
                        mediaPlayerCycling.prepare();
                        mediaPlayerJogging.start();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        }else if((xPeaks / activityWSize) > 0.2 && (yPeaks / activityWSize) > 0.2){ // Jogging detected, more details in external file      #### speed > 3 && speed < 9 &&
            //Toast.makeText(getApplicationContext(), "User is CYCLING!", Toast.LENGTH_SHORT).show();
            Log.e("activityEvaluation", "User is CYCLING!");
            // Play other music!!!
            //mediaPlayerCycling = MediaPlayer.create(getApplicationContext(), R.raw.jeffspeed68__jam_it);
            if(activePlayer == null){
                mediaPlayerCycling.start();
                activePlayer = mediaPlayerCycling;
            }else{
                if(activePlayer.equals(mediaPlayerCycling)) {
                    try {
                        mediaPlayerJogging.stop();
                        mediaPlayerJogging.prepare();
                        mediaPlayerCycling.start();
                    }catch(Exception e){
                        e.printStackTrace();
                    }
                }
            }
        }else{ // The user is neither jogging nor cycling
            //Toast.makeText(getApplicationContext(), "User is doing SOMETHING ELSE!", Toast.LENGTH_SHORT).show();
            Log.e("activityEvaluation", "User is doing SOMETHING ELSE!");
            // Stop the music!!!
            try {
                mediaPlayerJogging.stop();
                mediaPlayerJogging.prepare();
                mediaPlayerCycling.stop();
                mediaPlayerCycling.prepare();
                activePlayer = null;
            }catch(Exception e){
                e.printStackTrace();
            }
        }
    }

    /**
         * Implements the fft functionality as an async task
         * FFT(int n): constructor with fft length
         * fft(double[] x, double[] y)
         */

    private class FFTAsynctask extends AsyncTask<double[], Void, double[]> {

        private int wsize; //window size must be power of 2

        // constructor to set window size
        FFTAsynctask(int wsize) {
            this.wsize = wsize;
        }

        @Override
        protected double[] doInBackground(double[]... values) {

            double[] realPart = values[0].clone(); // actual acceleration values
            double[] imagPart = new double[this.wsize]; // init empty

            /**
             * Init the FFT class with given window size and run it with your input.
             * The fft() function overrides the realPart and imagPart arrays!
             */
            FFT fft = new FFT(this.wsize);
            fft.fft(realPart, imagPart);
            //init new double array for magnitude (e.g. frequency count)
            double[] magnitude = new double[this.wsize];


            //fill array with magnitude values of the distribution
            for (int i = 0; this.wsize > i ; i++) {
                magnitude[i] = Math.sqrt(Math.pow(realPart[i], 2) + Math.pow(imagPart[i], 2));
            }

            return magnitude;
        }

        @Override
        protected void onPostExecute(double[] values) {
            //hand over values to global variable after background task is finished
            freqCounts = values;
            DataPoint[] data = new DataPoint[this.wsize];
            for (int i = 0; i < this.wsize; i++) {
                data[i] = new DataPoint(i, freqCounts[i]);
            }
            FFTSeries.resetData(data);
        }
    }

    /**
     * little helper function to fill example with random double values
     */
    public void randomFill(double[] array){
        Random rand = new Random();
        for(int i = 0; array.length > i; i++){
            array[i] = rand.nextDouble();
        }
    }

}
