package com.example.camerafacedetection;


import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.PointF;

import com.example.camerafacedetection.Helper.GraphicOverlay;
import com.google.android.gms.vision.CameraSource;
import com.google.firebase.ml.vision.face.FirebaseVisionFace;


/**
 * Graphic instance for rendering face position, orientation, and landmarks within an associated
 * graphic overlay view.
 */
public class FaceGraphic extends GraphicOverlay.Graphic {
    private static final float FACE_POSITION_RADIUS = 10.0f;
    private static final float ID_TEXT_SIZE = 40.0f;
    private static final float ID_Y_OFFSET = 50.0f;
    private static final float ID_X_OFFSET = -50.0f;
    private static final float BOX_STROKE_WIDTH = 5.0f;

    private static final int[] COLOR_CHOICES = {
            Color.BLUE //, Color.CYAN, Color.GREEN, Color.MAGENTA, Color.RED, Color.WHITE, Color.YELLOW
    };
    private static int currentColorIndex = 0;

    private int facing;

    private final Paint facePositionPaint;
    private final Paint idPaint;
    private final Paint boxPaint;

    private volatile FirebaseVisionFace firebaseVisionFace;

    public FaceGraphic(GraphicOverlay overlay) {
        super(overlay);

        currentColorIndex = (currentColorIndex + 1) % COLOR_CHOICES.length;
        final int selectedColor = COLOR_CHOICES[currentColorIndex];

        facePositionPaint = new Paint();
        facePositionPaint.setColor(selectedColor);

        idPaint = new Paint();
        idPaint.setColor(selectedColor);
        idPaint.setTextSize(ID_TEXT_SIZE);

        boxPaint = new Paint();
        boxPaint.setColor(selectedColor);
        boxPaint.setStyle(Paint.Style.STROKE);
        boxPaint.setStrokeWidth(BOX_STROKE_WIDTH);
    }

    /**
     * Updates the face instance from the detection of the most recent frame. Invalidates the relevant
     * portions of the overlay to trigger a redraw.
     */
    public void updateFace(FirebaseVisionFace face, int facing) {
        firebaseVisionFace = face;
        this.facing = facing;
        postInvalidate();
    }

    /** Draws the face annotations for position on the supplied canvas. */
    @Override
    public void draw(Canvas canvas) {
        FirebaseVisionFace face = firebaseVisionFace;
        if (face == null) {
            return;
        }

        // Draws a circle at the position of the detected face, with the face's track id below.
        float x = translateX(face.getBoundingBox().centerX());
        float y = translateY(face.getBoundingBox().centerY());
        canvas.drawCircle(x, y, FACE_POSITION_RADIUS, facePositionPaint);
        canvas.drawCircle(656, 869, FACE_POSITION_RADIUS, facePositionPaint);
        canvas.drawText("mid point: " + roundAvoid(x,2) + "," + roundAvoid(y,2),
                x + 3 * ID_X_OFFSET, y + ID_Y_OFFSET, idPaint);

        // Draws a bounding box around the face.
        float xOffset = scaleX(face.getBoundingBox().width() / 2.0f);
        float yOffset = scaleY(face.getBoundingBox().height() / 2.0f);
        float left = x - xOffset;
        float top = y - yOffset;
        float right = x + xOffset;
        float bottom = y + yOffset;
        canvas.drawRect(left, top, right, bottom, boxPaint);

        canvas.drawText("edge: " + roundAvoid(right-left,2),
                left + ID_X_OFFSET, bottom + ID_Y_OFFSET, idPaint);
    }

    public static double roundAvoid(double value, int places) {
        double scale = Math.pow(10, places);
        return Math.round(value * scale) / scale;
    }

    public PointF getMidRect() {
        float x = translateX(firebaseVisionFace.getBoundingBox().centerX());
        float y = translateY(firebaseVisionFace.getBoundingBox().centerY());
        return new PointF(x,y);
    }

    public double getEdge() {
        float x = translateX(firebaseVisionFace.getBoundingBox().centerX());

        float xOffset = scaleX(firebaseVisionFace.getBoundingBox().width() / 2.0f);
        float left = x - xOffset;
        float right = x + xOffset;
        return (right - left);
    }
}
