package org.boofcv.android;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.PixelFormat;
import android.media.Image;
import android.os.Bundle;
import android.util.Log;
import android.util.Size;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.TextureView;
import android.widget.FrameLayout;

import java.nio.ByteBuffer;

import boofcv.android.ConvertBitmap;
import boofcv.struct.image.GrayU8;

// TODO don't show color preview
public class DisplayCamera2Activity extends BoofCamera2VideoActivity {

    private static final String TAG = "DisplayCamera2";

    TextureView textureView; // used to display camera preview directly to screen
    DisplayView displayView; // used to render visuals

    final Object imageLock = new Object();
    GrayU8 gray = new GrayU8(1,1);

    Bitmap bitmap = Bitmap.createBitmap(1,1, Bitmap.Config.ARGB_8888);
    byte[] convertTmp =  new byte[1];
    Matrix bitmapToView = new Matrix();

    // number of pixels it searches for when choosing camera resolution
    protected int targetResolution = 640*480;

    // if true it will sketch the bitmap to fill the view
    protected boolean stretchToFill = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Free up more screen space
        android.support.v7.app.ActionBar actionBar = getSupportActionBar();
        if( actionBar != null ) {
            actionBar.hide();
        }

        setContentView(R.layout.display_camera2);

        FrameLayout surfaceLayout = findViewById(R.id.camera_frame_layout);
        textureView = findViewById(R.id.texture);
        displayView = new DisplayView(this);
        surfaceLayout.addView(displayView);

        startCamera(textureView);
    }

    @Override
    protected int selectResolution( int widthTexture, int heightTexture, Size[] resolutions  ) {
        int bestIndex = -1;
        double bestAspect = Double.MAX_VALUE;
        double bestArea = 0;

        for( int i = 0; i < resolutions.length; i++ ) {
            Size s = resolutions[i];
            int width = s.getWidth();
            int height = s.getHeight();

            double aspectScore = Math.abs(width*height-targetResolution);

            if( aspectScore < bestAspect ) {
                bestIndex = i;
                bestAspect = aspectScore;
                bestArea = width*height;
            } else if( Math.abs(aspectScore-bestArea) <= 1e-8 ) {
                bestIndex = i;
                double area = width*height;
                if( area > bestArea ) {
                    bestArea = area;
                }
            }
        }

        return bestIndex;
    }

    @Override
    protected void onCameraResolutionChange(int width, int height) {
        // declare images
        gray.reshape(width,height);
        if( bitmap.getWidth() != gray.width || bitmap.getHeight() != gray.height)
            bitmap = Bitmap.createBitmap(gray.width,gray.height, Bitmap.Config.ARGB_8888);
        convertTmp = ConvertBitmap.declareStorage(bitmap,convertTmp);

        // Compute transform from bitmap to view coordinates
        int rotatedWidth = bitmap.getWidth();
        int rotatedHeight = bitmap.getHeight();

        int rotation = getWindowManager().getDefaultDisplay().getRotation();
        int offsetX=0,offsetY=0;

        if (Surface.ROTATION_0 == rotation || Surface.ROTATION_180 == rotation) {
            rotatedWidth = bitmap.getHeight();
            rotatedHeight = bitmap.getWidth();
            offsetX = (rotatedWidth-rotatedHeight)/2;
            offsetY = (rotatedHeight-rotatedWidth)/2;
        }

        float scale = Math.min(
                (float)textureView.getWidth()/rotatedWidth,
                (float)textureView.getHeight()/rotatedHeight);

        bitmapToView.reset();
        bitmapToView.postRotate(90 * (-rotation+1), bitmap.getWidth()/2, bitmap.getHeight()/2);
        bitmapToView.postTranslate(offsetX,offsetY);
        bitmapToView.postScale(scale,scale);
        if( stretchToFill ) {
            bitmapToView.postScale(
                    textureView.getWidth()/(rotatedWidth*scale),
                    textureView.getHeight()/(rotatedHeight * scale));
        } else {
            bitmapToView.postTranslate(
                    (textureView.getWidth() - rotatedWidth * scale) / 2,
                    (textureView.getHeight() - rotatedHeight * scale) / 2);
        }

        Log.i(TAG,"camera resolution "+width+" "+height);
    }

    @Override
    protected void processFrame(Image image) {

        Image.Plane plane = image.getPlanes()[0];

        synchronized (imageLock) {
            gray.reshape(plane.getRowStride(), image.getHeight());
            gray.width = image.getWidth();
            gray.subImage = true;

            ByteBuffer buffer = plane.getBuffer();
            buffer.rewind();
            buffer.get(gray.data, 0, plane.getRowStride() * image.getHeight());
        }

        // update visuals
        runOnUiThread(() -> displayView.invalidate());
    }

    protected void onDrawFrame( SurfaceView view , Canvas canvas ) {
        synchronized (imageLock) {
            ConvertBitmap.grayToBitmap(gray,bitmap,convertTmp);
        }

        canvas.drawBitmap(bitmap,bitmapToView,null);
    }

    /**
     * Custom view for visualizing results
     */
    private class DisplayView extends SurfaceView implements SurfaceHolder.Callback {

        SurfaceHolder mHolder;

        public DisplayView(Context context) {
            super(context);
            mHolder = getHolder();

            // configure it so that its on top and will be transparent
            setZOrderOnTop(true);    // necessary
            mHolder.setFormat(PixelFormat.TRANSPARENT);

            // if this is not set it will not draw
            setWillNotDraw(false);
        }


        @Override
        public void onDraw(Canvas canvas) {
            onDrawFrame(this,canvas);
        }

        @Override
        public void surfaceCreated(SurfaceHolder surfaceHolder) {}

        @Override
        public void surfaceChanged(SurfaceHolder surfaceHolder, int i, int i1, int i2) {}

        @Override
        public void surfaceDestroyed(SurfaceHolder surfaceHolder) {}
    }
}
