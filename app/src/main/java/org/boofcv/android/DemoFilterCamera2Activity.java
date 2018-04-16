package org.boofcv.android;


import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.view.MotionEvent;

import boofcv.android.ConvertBitmap;
import boofcv.struct.image.GrayU8;
import boofcv.struct.image.ImageBase;

public class DemoFilterCamera2Activity extends DemoCamera2Activity {

    // If true it will show the processed image, otherwise it will
    // display the input image
    protected boolean showProcessed = true;

    public DemoFilterCamera2Activity(Resolution resolution) {
        super(resolution);
        super.showBitmap = false;
    }

    public void activateTouchToShowInput() {
        displayView.setOnTouchListener((view, motionEvent) -> {
            if (motionEvent.getAction() == MotionEvent.ACTION_DOWN) {
                showProcessed = false;
            } else if (motionEvent.getAction() == MotionEvent.ACTION_UP) {
                showProcessed = true;
            }
            return true;
        });
    }

    @Override
    protected void processImage(ImageBase image) {
        if(showProcessed)
            super.processImage(image);
        else {
            synchronized (bitmapLock) {
                ConvertBitmap.boofToBitmap(image, bitmap, bitmapTmp);
            }
        }
    }

    @Override
    protected void onCameraResolutionChange(int width, int height) {
        super.onCameraResolutionChange(width, height);
        synchronized (bitmapLock) {
            if (bitmap.getWidth() != width || bitmap.getHeight() != height)
                bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            bitmapTmp = ConvertBitmap.declareStorage(bitmap, bitmapTmp);
        }
    }

    protected void convertToOutput(GrayU8 blurred ) {
        synchronized (bitmapLock) {
            ConvertBitmap.grayToBitmap(blurred, bitmap, bitmapTmp);
        }
    }

    protected void drawBitmap(Canvas canvas, Matrix imageToView) {
        synchronized (bitmapLock) {
            canvas.drawBitmap(bitmap, imageToView, null);
        }
    }
}
