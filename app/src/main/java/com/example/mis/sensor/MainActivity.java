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

    //private GoogleApiClient mApiClient;
    //private BroadcastReceiver broadcastReceiver;
    //private String TAG = MainActivity.class.getSimpleName();
    private double speed;
    private LocationManager locationManager;
    private LocationListener locationListener = new LocationListener() {
        @Override
        public void onLocationChanged(Location location) {
            speed = location.getSpeed();
        }

        @Override
        public void onStatusChanged(String provider, int status, Bundle extras) {}

        @Override
        public void onProviderEnabled(String provider) {}

        @Override
        public void onProviderDisabled(String provider) {}
    };

    private MediaPlayer mediaPlayerJogging;
    private MediaPlayer mediaPlayerCycling;
    //private Uri joggingTrack = Uri.parse("file:///res/raw/jeffspeed68__jam_it.mp3");
    //private Uri cyclingTrack = Uri.parse("file:///res/raw/jeffspeed68__picking_guitars.mp3");

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
        mediaPlayerJogging = MediaPlayer.create(getApplicationContext(), R.raw.jeffspeed68__jam_it);
        mediaPlayerCycling = MediaPlayer.create(getApplicationContext(), R.raw.jeffspeed68__picking_guitars);

        // EXERCISE 3b variables
        // https://code.tutsplus.com/tutorials/how-to-recognize-user-activity-with-activity-recognition--cms-25851
        // ###THIS ONE### https://www.androidhive.info/2017/12/android-user-activity-recognition-still-walking-runnimg-driving-etc/ ###THIS ONE###
        //mApiClient = new GoogleApiClient.Builder(this).addApi(ActivityRecognition.API)
        //        .addConnectionCallbacks(this).addOnConnectionFailedListener(this).build();
        //mApiClient.connect();

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
        locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, locationListener);

        /*try {
            mediaPlayerJogging.setAudioSessionId(AudioManager.STREAM_MUSIC);
            mediaPlayerJogging.setDataSource(getApplicationContext(), joggingTrack);
            mediaPlayerJogging.prepare();
            mediaPlayerCycling.setAudioSessionId(AudioManager.STREAM_MUSIC);
        } catch (Exception e) {
            e.printStackTrace();
            Log.e("MediaPlayer", "Exception on setDataSource()!");
        }*/
        mediaPlayerJogging.start();
        /*
        broadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (intent.getAction().equals("activity_intent")){
                    int type = intent.getIntExtra("type", -1);
                    int confidence = intent.getIntExtra("confidence", 0);
                    handleUserActivity(type, confidence);
                }
            }
        };
        startTracking();
        LocalBroadcastManager.getInstance(getApplicationContext()).registerReceiver(broadcastReceiver, new IntentFilter("activity_intent"));
        */
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
        //LocalBroadcastManager.getInstance(getApplicationContext()).registerReceiver(broadcastReceiver, new IntentFilter("activity_intent"));
    }

    @Override
    protected void onPause() {
        super.onPause();
        //stopTracking();
        mSensorManager.unregisterListener(this);
        mediaPlayerJogging.pause();
        mediaPlayerCycling.pause();
        //LocalBroadcastManager.getInstance(getApplicationContext()).unregisterReceiver(broadcastReceiver);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mediaPlayerJogging.release();
        mediaPlayerCycling.release();
        mSensorManager.unregisterListener(this);
    }

    /*private void handleUserActivity(int type, int confidence){
        String label;
        if(confidence >= 50 && location.getSpeed() > 1.5 && location.getSpeed() < 9){ // if the speed is between 1.5 and 30 m/s (roughly 5.5 - 33 km/h)
            switch(type){                                                             // there is an high probability that the user is jogging or cycling
                case DetectedActivity.RUNNING:{
                    //if(location.getSpeed() > 1.5 && location.getSpeed() < 3){ // speed between 5.5 and 11 km/h: the user is probably running
                        label = "Running!";
                    //}
                    break;
                }
                case DetectedActivity.ON_BICYCLE: {
                    //if(location.getSpeed() > 3 && location.getSpeed() < 9){ // speed between 11 and 33 km/h: the user is probably cycling
                        label = "Cycling!";
                    //}
                    break;
                }
                case DetectedActivity.UNKNOWN:{
                    label = "Unknown!";
                    break;
                }
                default:{
                    label = "Something else!";
                    break;
                }
            }
        }else{
            label = "Unknown!";
        }
        Log.e(TAG, "User activity: " + label + ", Confidence: " + confidence);
    }

    private void startTracking(){
        Intent intent1 = new Intent(getApplicationContext(), BackgroundDetectedActivityService.class);
        startService(intent1);
    }

    private void stopTracking(){
        Intent intent1 = new Intent(getApplicationContext(), BackgroundDetectedActivityService.class);
        stopService(intent1);
    }

    //@Override
    //public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {}*/

    private void activityEvaluation(double[] accXValues, double[] accYValues, double[] accZValues){
        int xPeaks = 0; int yPeaks = 0; int zPeaks = 0;
        for (double xValue : accXValues) { if(Math.abs(xValue) > 7) xPeaks++; }
        for (double yValue : accYValues) { if(Math.abs(yValue) > 7) yPeaks++; }
        for (double zValue : accZValues) { if(Math.abs(zValue) > 7) zPeaks++; }
        Log.e("location.getSpeed", "Speed: "+ String.valueOf(speed));
        Toast.makeText(getApplicationContext(), "Speed: "+ String.valueOf(speed), Toast.LENGTH_SHORT).show();
        if(speed > 1.5 && speed < 3 && (yPeaks / activityWSize) > 0.75){ // Jogging detected, more details in external file
            //Toast.makeText(getApplicationContext(), "User is JOGGING!", Toast.LENGTH_SHORT).show();
            Log.e("activityEvaluation", "User is JOGGING!");
            // TODO: Play some music!!!
            mediaPlayerJogging.start();
        }else if(speed > 3 && speed < 9 && (xPeaks / activityWSize) > 0.5 && (yPeaks / activityWSize) > 0.5){ // Jogging detected, more details in external file
            //Toast.makeText(getApplicationContext(), "User is CYCLING!", Toast.LENGTH_SHORT).show();
            Log.e("activityEvaluation", "User is CYCLING!");
            // TODO: Play other music!!!
            mediaPlayerCycling.start();
        }else{ // The user is neither jogging nor cycling
            //Toast.makeText(getApplicationContext(), "User is doing SOMETHING ELSE!", Toast.LENGTH_SHORT).show();
            Log.e("activityEvaluation", "User is doing SOMETHING ELSE!");
            // TODO: Stop the music!!!
            try {
                //mediaPlayerJogging.stop();
                //mediaPlayerJogging.prepare();
                mediaPlayerCycling.stop();
                mediaPlayerCycling.prepare();
            }catch (Exception e){
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

    // EXERCISE 3b
    // https://code.tutsplus.com/tutorials/how-to-recognize-user-activity-with-activity-recognition--cms-25851
    /*private class ActivityDetectingService extends IntentService {
        public ActivityDetectingService(){
            super("ActivityDetectingService");
        }

        public ActivityDetectingService(String name){
            super(name);
        }

        @Override
        protected void onHandleIntent(Intent intent){
            if(ActivityRecognitionResult.hasResult(intent)){
                ActivityRecognitionResult result = ActivityRecognitionResult.extractResult(intent);
                triggerActionOnActivity(result.getMostProbableActivity());
            }
        }

        private void triggerActionOnActivity(DetectedActivity activity){
            if (activity.getConfidence() >= 50){
                switch (activity.getType()){
                    case DetectedActivity.ON_BICYCLE:{
                        Log.e("ActivityRecognition", "On Bicycle: " + activity.getConfidence());
                        break;
                    }
                    case DetectedActivity.WALKING:{
                        Log.e("ActivityRecognition", "Walking: " + activity.getConfidence());
                        break;
                    }
                    default:{
                        Log.e("ActivityRecognition", "Something else: " + activity.getConfidence());
                    }
                }
            }else{
                Log.e("ActivityRecognition", "Nothing Recognized. Confidence = " + activity.getConfidence());
            }
        }
    }

    @Override
    public void onConnected(@Nullable Bundle bundle){
        Intent intent = new Intent(getApplicationContext(), ActivityDetectingService.class);
        PendingIntent pendingIntent = PendingIntent.getService(getApplicationContext(), 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
        ActivityRecognition.ActivityRecognitionApi.requestActivityUpdates(mApiClient, 3000, pendingIntent);
    }

    @Override
    public void onConnectionSuspended(int i){}

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult){}

    public class DetectedActivitiesIntentService extends IntentService{

        public DetectedActivitiesIntentService(){
            super(DetectedActivitiesIntentService.class.getSimpleName());
        }

        @Override
        public void onCreate(){ super.onCreate(); }

        @SuppressWarnings("unchecked")
        @Override
        protected void onHandleIntent(Intent intent){
            ActivityRecognitionResult result = ActivityRecognitionResult.extractResult(intent);
            DetectedActivity activity = result.getMostProbableActivity();
            Log.e(TAG, "Detected activity: " + activity.getType() + ", " + activity.getConfidence());
            broadcastActivity(activity);
        }

        private void broadcastActivity(DetectedActivity activity){
            Intent intent = new Intent("activity_intent");
            intent.putExtra("type", activity.getType());
            intent.putExtra("confidence", activity.getConfidence());
            LocalBroadcastManager.getInstance(getApplicationContext()).sendBroadcast(intent);
        }
    }

    public class BackgroundDetectedActivityService extends Service {
        private Intent mIntentService;
        private PendingIntent mPendingIntent;
        private ActivityRecognitionClient mActivityRecognitionClient;

        IBinder mBinder = new BackgroundDetectedActivityService.LocalBinder();

        public class LocalBinder extends Binder {
            public BackgroundDetectedActivityService getServerInstance(){
                return BackgroundDetectedActivityService.this;
            }
        }

        public BackgroundDetectedActivityService(){}

        @Override
        public void onCreate(){
            super.onCreate();
            mActivityRecognitionClient = new ActivityRecognitionClient(getApplicationContext());
            mIntentService = new Intent(this, DetectedActivitiesIntentService.class);
            mPendingIntent = PendingIntent.getService(this, 1, mIntentService, PendingIntent.FLAG_UPDATE_CURRENT);
            Task<Void> task = mActivityRecognitionClient.requestActivityUpdates(3000, mPendingIntent);
            task.addOnSuccessListener(new OnSuccessListener<Void>() {
                @Override
                public void onSuccess(Void aVoid) {
                    Toast.makeText(getApplicationContext(), "Activity update REQUESTED!", Toast.LENGTH_SHORT).show();
                }
            });
            task.addOnFailureListener(new OnFailureListener() {
                @Override
                public void onFailure(@NonNull Exception e) {
                    Toast.makeText(getApplicationContext(), "Activity update FAILED!", Toast.LENGTH_SHORT).show();
                }
            });
        }

        @Nullable
        @Override
        public IBinder onBind(Intent intent){
            return mBinder;
        }

        @Override
        public void onDestroy(){
            super.onDestroy();
            Task<Void> task = mActivityRecognitionClient.removeActivityUpdates(mPendingIntent);
            task.addOnSuccessListener(new OnSuccessListener<Void>() {
                @Override
                public void onSuccess(Void aVoid) {
                    Toast.makeText(getApplicationContext(), "Activity update REMOVED!", Toast.LENGTH_SHORT).show();
                }
            });
            task.addOnFailureListener(new OnFailureListener() {
                @Override
                public void onFailure(@NonNull Exception e) {
                    Toast.makeText(getApplicationContext(), "Activity update REMOVING FAILED!", Toast.LENGTH_SHORT).show();
                }
            });
        }
    }*/

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
