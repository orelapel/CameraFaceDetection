package com.example.camerafacedetection;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.PointF;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import com.example.camerafacedetection.Helper.GraphicOverlay;
import com.hoho.android.usbserial.driver.UsbSerialDriver;
import com.hoho.android.usbserial.driver.UsbSerialPort;
import com.hoho.android.usbserial.driver.UsbSerialProber;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {
    private static final String ACTION_USB_PERMISSION =
            "com.android.example.USB_PERMISSION";
    UsbSerialPort port;

    private Button startButton;
    private Button changeButton;
    boolean shouldStop=false;
    boolean recognized = true;

    private static final int PERMISSION_REQUESTS = 1;
    private static final String TAG = "LivePreviewActivity";
    private CameraSource cameraSource = null;
    private CameraSourcePreview preview;
    private GraphicOverlay graphicOverlay;
    private FaceDetectionProcessor faceDetectionProcessor;
    final Handler handler = new Handler();

    double deltaX;
    double deltaY;
    double L;
    PointF midFrameCam;
    PointF midRectFace;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        changeButton = findViewById(R.id.change_cam);
        preview = (CameraSourcePreview) findViewById(R.id.firePreview);
        graphicOverlay = (GraphicOverlay) findViewById(R.id.fireFaceOverlay);

        if (allPermissionsGranted()) {
            createCameraSource();
            startCameraSource();
        } else {
            getRuntimePermissions();
        }
    }

    /**
     * Starts or restarts the camera source, if it exists. If the camera source doesn't exist yet
     * (e.g., because onResume was called before the camera source was created), this will be called
     * again when the camera source is created.
     */
    private void startCameraSource() {
        if (cameraSource != null) {
            try {

                if (preview == null) {
                    Log.d(TAG, "resume: Preview is null");
                }
                if (graphicOverlay == null) {
                    Log.d(TAG, "resume: graphOverlay is null");
                }
                preview.start(cameraSource, graphicOverlay);
            } catch (IOException e) {
                Log.e(TAG, "Unable to start camera source.", e);
                cameraSource.release();
                cameraSource = null;
            }
        }
    }

    private void createCameraSource() {
        // If there's no existing cameraSource, create one.
        if (cameraSource == null) {
            cameraSource = new CameraSource(this, graphicOverlay);
        }
        faceDetectionProcessor = new FaceDetectionProcessor();
        cameraSource.setMachineLearningFrameProcessor(faceDetectionProcessor);
    }

    @Override
    protected void onPause() {
        super.onPause();

        preview.stop();
    }

    @Override
    protected void onResume() {
        super.onResume();

        startCameraSource();
    }
    @Override
    public void onDestroy() {
        super.onDestroy();
        if (cameraSource != null) {
            cameraSource.release();
        }
        if (!shouldStop) {
            shouldStop = true;
            try {
                port.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public static double tryParseToDouble(String strNum) {
        if (strNum == null) {
            return -1;
        }
        double d;
        try {
            d = Double.parseDouble(strNum);
        } catch (NumberFormatException nfe) {
            return -1;
        }
        return d;
    }

    public void OnClickStart(View view) {
        // Take the values from the EditText
        EditText k1 = (EditText)findViewById(R.id.k1);
        EditText k2 = (EditText)findViewById(R.id.k2);
        EditText k3 = (EditText)findViewById(R.id.k3);
        EditText c = (EditText)findViewById(R.id.c);
        EditText d = (EditText)findViewById(R.id.d);

        // Try to parse values to double
        final double k1ToDouble = tryParseToDouble(k1.getText().toString());
        final double k2ToDouble = tryParseToDouble(k2.getText().toString());
        final double k3ToDouble = tryParseToDouble(k3.getText().toString());
        final double cToDouble = tryParseToDouble(c.getText().toString());
        final double dToDouble = tryParseToDouble(d.getText().toString());

        // Check if all the values has filled
        if (k1ToDouble ==-1 && k2ToDouble == -1 && k3ToDouble == -1
                && cToDouble == -1 && dToDouble == -1) {
            Toast toast = Toast.makeText(getApplicationContext(),
                    "Please fill all fields", Toast.LENGTH_SHORT);
            toast.show();
            return;
        }

        // Calculate the middle point of the frame.
        midFrameCam = new PointF((float) (preview.getWidth()/2.0), (float) (preview.getHeight()/2.0));

        // Repeating send values to serial every 1000 millis.
        Runnable r = new Runnable() {
            @Override
            public void run() {
                if (!shouldStop) {
                    sendValues(k1ToDouble, k2ToDouble, k3ToDouble, cToDouble, dToDouble);
                    handler.postDelayed(this, 1000);
                }
            }
        };
        Thread thread = new Thread(r);
        thread.start();
    }

    public void sendValues(double k1,double k2,double k3, double c, double d) {
        final double a, b, l, e;

        // Get the middle point of the rectangle face and the edge of this rectangle.
        midRectFace = faceDetectionProcessor.getMidRect();
        L = faceDetectionProcessor.getEdge();

        // If the size of the rectangle is 0 - the face did'nt recognized yet.
        if (L == 0 && recognized) {
            recognized = false;
            runOnUiThread(new Runnable() {
                public void run() {
                    Toast.makeText(MainActivity.this,
                            "face did'nt recognized yet",Toast.LENGTH_LONG).show();
                }
            });
            return;
        }
        else if (L==0) {
            return;
        }
        recognized = true;

        // Calculate the values of deltaX and deltaY.
        deltaX = midFrameCam.x - midRectFace.x;
        deltaY = midFrameCam.y - midRectFace.y;

        // Calculate values and send them to serial
        a = roundAvoid(deltaX * k1 * c,2);
        b = roundAvoid(deltaY * k2,2);
        l = roundAvoid((L - d) * k3,2);
        e = roundAvoid((deltaX * k1) * (1 - c),2);
        final String s1 = "cw," + a + ",", s2 = "r," + e + ",", s3 = "fw," + l + ",", s4 = "U," + b + ",";
        runOnUiThread(new Runnable() {
            public void run() {
                Toast.makeText(MainActivity.this,
                        s1 + " " + s2 + " " + s3 + " " + s4,Toast.LENGTH_LONG).show();
            }
        });
        try {
            port.write(s1.getBytes(), 10);
            port.write(s2.getBytes(), 10);
            port.write(s3.getBytes(), 10);
            port.write(s4.getBytes(), 10);
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    public static double roundAvoid(double value, int places) {
        double scale = Math.pow(10, places);
        return Math.round(value * scale) / scale;
    }

    public void OnClickConnent(View view) {
        // Find all available drivers from attached devices.
        UsbManager manager = (UsbManager) getSystemService(Context.USB_SERVICE);
        List<UsbSerialDriver> availableDrivers = UsbSerialProber.getDefaultProber().findAllDrivers(manager);
        if (availableDrivers.isEmpty()) {
            return;
        }

        // Open a connection to the first available driver.
        UsbSerialDriver driver = availableDrivers.get(0);
        PendingIntent permissionIntent = PendingIntent.getBroadcast(this, 0,
                new Intent(ACTION_USB_PERMISSION), 0);
        manager.requestPermission(driver.getDevice(), permissionIntent);
        UsbDeviceConnection connection = manager.openDevice(driver.getDevice());
        if (connection == null) {
            return;
        }

        port = driver.getPorts().get(0);
        try {
            port.open(connection);
        } catch (IOException e) {
            e.printStackTrace();
        }
        try {
            port.setParameters(115200, 8, UsbSerialPort.STOPBITS_1,
                    UsbSerialPort.PARITY_NONE);
        } catch (IOException e) {
            e.printStackTrace();
        }

        Toast toast = Toast.makeText(getApplicationContext(),
                "connected successfully to serial", Toast.LENGTH_SHORT);
        toast.show();

        // Enable the start button and update shouldStop to false.
        startButton = findViewById(R.id.send);
        startButton.setEnabled(true);
        shouldStop = false;
    }

    public void OnClickDisonnent(View view) {
        // Close the connection.
        try {
            port.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

        Toast toast = Toast.makeText(getApplicationContext(),
                "disconnected successfully from serial", Toast.LENGTH_SHORT);
        toast.show();

        // Disable the start button and update shouldStop to true.
        startButton.setEnabled(false);
        shouldStop = true;
    }

    public void OnClickChangeCam(View view) {
        if (changeButton.getText().equals("front cam")) {
            cameraSource.setFacing(CameraSource.CAMERA_FACING_FRONT);
            changeButton.setText(R.string.back);
        } else {
            cameraSource.setFacing(CameraSource.CAMERA_FACING_BACK);
            changeButton.setText(R.string.front);
        }
        preview.stop();
        startCameraSource();
    }

    private String[] getRequiredPermissions() {
        try {
            PackageInfo info =
                    this.getPackageManager()
                            .getPackageInfo(this.getPackageName(), PackageManager.GET_PERMISSIONS);
            String[] ps = info.requestedPermissions;
            if (ps != null && ps.length > 0) {
                return ps;
            } else {
                return new String[0];
            }
        } catch (Exception e) {
            return new String[0];
        }
    }

    private boolean allPermissionsGranted() {
        for (String permission : getRequiredPermissions()) {
            if (!isPermissionGranted(this, permission)) {
                return false;
            }
        }
        return true;
    }

    private void getRuntimePermissions() {
        List<String> allNeededPermissions = new ArrayList<>();
        for (String permission : getRequiredPermissions()) {
            if (!isPermissionGranted(this, permission)) {
                allNeededPermissions.add(permission);
            }
        }

        if (!allNeededPermissions.isEmpty()) {
            ActivityCompat.requestPermissions(
                    this, allNeededPermissions.toArray(new String[0]), PERMISSION_REQUESTS);
        }
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode, String[] permissions, int[] grantResults) {
        Log.i(TAG, "Permission granted!");
        if (allPermissionsGranted()) {
            createCameraSource();
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    private static boolean isPermissionGranted(Context context, String permission) {
        if (ContextCompat.checkSelfPermission(context, permission)
                == PackageManager.PERMISSION_GRANTED) {
            Log.i(TAG, "Permission granted: " + permission);
            return true;
        }
        Log.i(TAG, "Permission NOT granted: " + permission);
        return false;
    }
}