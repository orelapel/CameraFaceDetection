package com.example.camerafacedetection;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Point;
import android.graphics.Rect;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import com.example.camerafacedetection.Helper.GraphicOverlay;
import com.example.camerafacedetection.Helper.RectOverlay;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.ml.vision.FirebaseVision;
import com.google.firebase.ml.vision.common.FirebaseVisionImage;
import com.google.firebase.ml.vision.face.FirebaseVisionFace;
import com.google.firebase.ml.vision.face.FirebaseVisionFaceDetector;
import com.google.firebase.ml.vision.face.FirebaseVisionFaceDetectorOptions;
import com.hoho.android.usbserial.driver.UsbSerialDriver;
import com.hoho.android.usbserial.driver.UsbSerialPort;
import com.hoho.android.usbserial.driver.UsbSerialProber;
import com.wonderkiln.camerakit.CameraKit;
import com.wonderkiln.camerakit.CameraKitError;
import com.wonderkiln.camerakit.CameraKitEvent;
import com.wonderkiln.camerakit.CameraKitEventListener;
import com.wonderkiln.camerakit.CameraKitImage;
import com.wonderkiln.camerakit.CameraKitVideo;
import com.wonderkiln.camerakit.CameraView;

import java.io.IOException;
import java.util.List;

import dmax.dialog.SpotsDialog;

public class MainActivity extends AppCompatActivity {
    private static final String ACTION_USB_PERMISSION =
            "com.android.example.USB_PERMISSION";
    UsbSerialPort port;

    private Button faceDetectButton;
    private Button startButton;
    private Button changeButton;
    private GraphicOverlay graphicOverlay;
    private CameraView cameraView;
    AlertDialog alertDialog;

    double deltaX;
    double deltaY;
    double L;
    Point midFrameCam;
    Point midRectFace;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        faceDetectButton = findViewById(R.id.detect_face_btn);
        startButton = findViewById(R.id.start);
        changeButton = findViewById(R.id.change_cam);
        graphicOverlay = findViewById(R.id.graphic_overlay);
        cameraView = findViewById(R.id.camera_view);
        midFrameCam = new Point(cameraView.getWidth()/2,cameraView.getHeight()/2);

        alertDialog = new SpotsDialog.Builder()
                .setContext(this)
                .setMessage("Please wait, Processing...")
                .setCancelable(false)
                .build();

        faceDetectButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                cameraView.start();
                cameraView.captureImage();
                graphicOverlay.clear();
            }
        });

        cameraView.addCameraKitListener(new CameraKitEventListener() {
            @Override
            public void onEvent(CameraKitEvent cameraKitEvent) {

            }

            @Override
            public void onError(CameraKitError cameraKitError) {

            }

            @Override
            public void onImage(CameraKitImage cameraKitImage) {
                alertDialog.show();
                Bitmap bitmap = cameraKitImage.getBitmap();
                bitmap = Bitmap.createScaledBitmap(bitmap, cameraView.getWidth(),
                        cameraView.getHeight(), false);
                cameraView.stop();

                processFaceDetection(bitmap);
            }

            @Override
            public void onVideo(CameraKitVideo cameraKitVideo) {

            }
        });

        //faceDetectButton.setEnabled(true);
    }


    private void processFaceDetection(Bitmap bitmap) {
        FirebaseVisionImage firebaseVisionImage = FirebaseVisionImage.fromBitmap(bitmap);

        FirebaseVisionFaceDetectorOptions firebaseVisionFaceDetectorOptions
                = new FirebaseVisionFaceDetectorOptions.Builder().build();

        FirebaseVisionFaceDetector firebaseVisionFaceDetector = FirebaseVision.getInstance()
                .getVisionFaceDetector(firebaseVisionFaceDetectorOptions);
        firebaseVisionFaceDetector.detectInImage(firebaseVisionImage)
                .addOnSuccessListener(new OnSuccessListener<List<FirebaseVisionFace>>() {
                    @Override
                    public void onSuccess(List<FirebaseVisionFace> firebaseVisionFaces) {
                        getFaceResults(firebaseVisionFaces);
                    }
                }).addOnFailureListener(new OnFailureListener() {
            @Override
            public void onFailure(@NonNull Exception e) {
                Toast.makeText(MainActivity.this, "Error: " + e.getMessage()
                        , Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void getFaceResults(List<FirebaseVisionFace> firebaseVisionFaces) {
        int counter = 0;
        for (FirebaseVisionFace face : firebaseVisionFaces) {
            Rect rect = face.getBoundingBox();
            RectOverlay rectOverlay = new RectOverlay(graphicOverlay, rect);

            graphicOverlay.add(rectOverlay);
            midRectFace = new Point(rect.width()/2, rect.height()/2);
            L = rect.width() * rect.height();

            counter += 1;
        }
        alertDialog.dismiss();
    }

    @Override
    protected void onPause() {
        super.onPause();

        cameraView.stop();
    }

    @Override
    protected void onResume() {
        super.onResume();

        cameraView.start();
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
        double k1ToDouble = tryParseToDouble(k1.getText().toString());
        double k2ToDouble = tryParseToDouble(k2.getText().toString());
        double k3ToDouble = tryParseToDouble(k3.getText().toString());
        double cToDouble = tryParseToDouble(c.getText().toString());
        double dToDouble = tryParseToDouble(d.getText().toString());

        // Check if all the values has filled
        if (k1ToDouble !=-1 && k2ToDouble != -1 && k3ToDouble != -1
                && cToDouble != -1 && dToDouble != -1) {
            Toast toast = Toast.makeText(getApplicationContext(),
                    "Please fill all fields", Toast.LENGTH_SHORT);
            toast.show();
            return;
        }

        double a, b, l, e;

        deltaX = midFrameCam.x - midRectFace.x;
        if (deltaX < 0) {
            deltaX = -deltaX;
        }
        deltaY = midFrameCam.y - midRectFace.y;
        if (deltaY < 0) {
            deltaY = -deltaY;
        }

        while (true) {
            // Check if we have this values
            if (deltaX == -1 || deltaY == -1 || L == -1) {
                Toast toast = Toast.makeText(getApplicationContext(),
                        "face did'nt recognized yet", Toast.LENGTH_SHORT);
                toast.show();
            }

            // Calculate values and send them to serial
            a = deltaX * k1ToDouble * cToDouble;
            b = deltaY * k2ToDouble;
            l = (L - dToDouble) * k3ToDouble;
            e = (deltaX * k1ToDouble) * (1 - cToDouble);
            String s1 = "cw," + a + ",", s2 = "r," + e + ",", s3 = "fw," + l + ",", s4 = "U," + b + ",";
            Toast toast = Toast.makeText(getApplicationContext(),
                    s1 + " " + s2 + " " + s3 + " " + s4, Toast.LENGTH_SHORT);
            toast.show();
            try {
                port.write(s1.getBytes(), 10);
                port.write(s2.getBytes(), 10);
                port.write(s3.getBytes(), 10);
                port.write(s4.getBytes(), 10);
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }
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
            // add UsbManager.requestPermission(driver.getDevice(), ..) handling here
            return;
        }

        port = driver.getPorts().get(0); // Most devices have just one port (port 0)
        try {
            port.open(connection);
        } catch (IOException e) {
            e.printStackTrace();
        }
        try {
            port.setParameters(115200, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE);
        } catch (IOException e) {
            e.printStackTrace();
        }


        Toast toast = Toast.makeText(getApplicationContext(),
                "connected successfully to serial", Toast.LENGTH_SHORT);
        toast.show();

        startButton.setEnabled(true);
    }

    public void OnClickDisonnent(View view) {
        try {
            port.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

        Toast toast = Toast.makeText(getApplicationContext(),
                "disconnected successfully from serial", Toast.LENGTH_SHORT);
        toast.show();

        startButton.setEnabled(false);
    }

    public void OnClickChangeCam(View view) {
        if (changeButton.getText().equals("front cam")) {
            cameraView.setFacing(CameraKit.Constants.FACING_FRONT);
            changeButton.setText(R.string.back);
        } else {
            cameraView.setFacing(CameraKit.Constants.FACING_BACK);
            changeButton.setText(R.string.front);
        }
    }
}