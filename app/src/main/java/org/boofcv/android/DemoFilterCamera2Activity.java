package org.boofcv.android;


import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;

import boofcv.android.ConvertBitmap;
import boofcv.struct.image.GrayU8;

public class DemoFilterCamera2Activity extends DemoCamera2Activity {
    public DemoFilterCamera2Activity(Resolution resolution) {
        super(resolution);
        super.showBitmap = false;
    }

    @Override
    protected void onCameraResolutionChange(int width, int height) {
        super.onCameraResolutionChange(width, height);
        synchronized (bitmapLock) {
            if (bitmap.getWidth() != width || bitmap.getHeight() != height)
                bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            convertTmp = ConvertBitmap.declareStorage(bitmap, convertTmp);
        }
    }

    protected void convertToOutput(GrayU8 blurred ) {
        synchronized (bitmapLock) {
            ConvertBitmap.grayToBitmap(blurred, bitmap, convertTmp);
        }
    }

    protected void drawBitmap(Canvas canvas, Matrix imageToView) {
        synchronized (bitmapLock) {
            canvas.drawBitmap(bitmap, imageToView, null);
        }
    }
}
