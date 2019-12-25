package org.boofcv.android;


import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;

import boofcv.android.ConvertBitmap;
import boofcv.android.VisualizeImageData;
import boofcv.struct.image.GrayU8;
import boofcv.struct.image.ImageBase;

/**
 * Activity where rendering to the bitmap should be managed by the activity entirely
 */
public abstract class DemoBitmapCamera2Activity extends DemoCamera2Activity {

    public DemoBitmapCamera2Activity(Resolution resolution) {
        super(resolution);
        super.bitmapMode = BitmapMode.NONE;
    }

    @Override
    protected void onCameraResolutionChange(int width, int height, int sensorOrientation) {
        super.onCameraResolutionChange(width, height,sensorOrientation);
        if (bitmap.getWidth() != width || bitmap.getHeight() != height)
            bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
    }

    protected void convertToBitmapDisplay(ImageBase image ) {
        // check size just incase the resolution was changed and this process delayed
        if( image.width == bitmap.getWidth() && image.height == bitmap.getHeight() )
            ConvertBitmap.boofToBitmap(image, bitmap, bitmapTmp);
    }

    protected void convertBinaryToBitmapDisplay(GrayU8 image ) {
        // check size just incase the resolution was changed and this process delayed
        if( image.width == bitmap.getWidth() && image.height == bitmap.getHeight() )
            VisualizeImageData.binaryToBitmap(image,false, bitmap, bitmapTmp);
    }

    protected void drawBitmap(Canvas canvas, Matrix imageToView) {
        canvas.drawBitmap(bitmap, imageToView, null);
    }
}
