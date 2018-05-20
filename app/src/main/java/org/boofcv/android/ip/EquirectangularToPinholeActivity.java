package org.boofcv.android.ip;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.PixelFormat;
import android.os.Bundle;
import android.support.v4.view.GestureDetectorCompat;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.widget.FrameLayout;

import org.boofcv.android.R;
import org.ejml.data.FMatrixRMaj;
import org.ejml.dense.row.CommonOps_FDRM;

import boofcv.alg.distort.ImageDistort;
import boofcv.alg.distort.spherical.PinholeToEquirectangular_F32;
import boofcv.alg.interpolate.InterpolatePixel;
import boofcv.alg.interpolate.InterpolationType;
import boofcv.android.ConvertBitmap;
import boofcv.core.image.border.BorderType;
import boofcv.factory.distort.FactoryDistort;
import boofcv.factory.interpolate.FactoryInterpolation;
import boofcv.struct.calib.CameraPinhole;
import boofcv.struct.image.GrayU8;
import boofcv.struct.image.Planar;
import georegression.geometry.ConvertRotation3D_F32;
import georegression.struct.EulerType;

/**
 * Shows an equirectangular image with a pinhole camera super imposed on top of it.
 */
// TODO change view using touch
public class EquirectangularToPinholeActivity extends Activity {

    public static final String TAG = "Equirectangular";

    private GestureDetectorCompat mDetector;
    final Object controlLock = new Object();
    final FMatrixRMaj workR = CommonOps_FDRM.identity(3);

    CameraPinhole pinholeModel = new CameraPinhole(200,200,0,250,250,500,500);

    PinholeToEquirectangular_F32 pinholeToEqui;
    ImageDistort<Planar<GrayU8>,Planar<GrayU8>> distorter;

    Planar<GrayU8> equiImage = new Planar<>(GrayU8.class,1,1,3);
    Planar<GrayU8> pinholeImage;
    byte[] bitmapTmp = new byte[1];

    protected Matrix imageToView = new Matrix();

    final Object lockOutput = new Object();
    Bitmap workBitmap;
    Bitmap outputBitmap;

    DisplayView view;

    // Forgot how to create a thread pool that discards has a pool size of 2 and discards
    // the most recently added. So i just wrote my own in a few lines of code
    private final Object lockThread = new Object();
    private RenderView renderNow;
    private boolean pendingRender;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Free up more screen space
        android.app.ActionBar actionBar = getActionBar();
        if( actionBar != null ) {
            actionBar.hide();
        }

        setContentView(R.layout.equirectangular);

        FrameLayout surfaceLayout = findViewById(R.id.image_frame);

        view = new DisplayView(this);
        surfaceLayout.addView(view);

        mDetector = new GestureDetectorCompat(this,new TouchControls());

        workBitmap = Bitmap.createBitmap(pinholeModel.width,pinholeModel.height, Bitmap.Config.ARGB_8888);
        outputBitmap = Bitmap.createBitmap(pinholeModel.width,pinholeModel.height, Bitmap.Config.ARGB_8888);
        bitmapTmp = ConvertBitmap.declareStorage(outputBitmap, bitmapTmp);

        Log.i(TAG, "outputBitmap = "+outputBitmap.getWidth()+"x"+outputBitmap.getHeight());

        Bitmap icon = BitmapFactory.decodeResource(getResources(),R.drawable.canyon360);

        Log.i(TAG, "icon = "+icon.getWidth()+"x"+icon.getHeight());

        equiImage.reshape(icon.getWidth(),icon.getHeight());
        ConvertBitmap.bitmapToBoof(icon,equiImage,null);

        // Declare storage for pinhole camera image
        pinholeImage = equiImage.createNew(pinholeModel.width, pinholeModel.height);

        // Create the image distorter which will render the image
        InterpolatePixel<Planar<GrayU8>> interp = FactoryInterpolation.
                createPixel(0, 255, InterpolationType.BILINEAR, BorderType.EXTENDED, equiImage.getImageType());
        distorter = FactoryDistort.distort(false,interp,equiImage.getImageType());

        // This is where the magic is done.  It defines the transform rfom equirectangular to pinhole
        pinholeToEqui = new PinholeToEquirectangular_F32();
        pinholeToEqui.setEquirectangularShape(equiImage.width,equiImage.height);
        pinholeToEqui.setPinhole(pinholeModel);

        // Pass in the transform to the image distorter
        distorter.setModel(pinholeToEqui);

        renderView();

   }

   private void renderView() {
        synchronized (lockThread) {
            if( renderNow == null ) {
                pendingRender = false;
                renderNow = new RenderView();
                new Thread(renderNow,"RenderEqui").start();
            } else {
                Log.i(TAG,"pendingRender=true");
                pendingRender = true;
            }
        }
   }

    @Override
    public boolean onTouchEvent(MotionEvent event){
        if (this.mDetector.onTouchEvent(event)) {
            return true;
        }
        return super.onTouchEvent(event);
    }

    private class TouchControls implements GestureDetector.OnGestureListener {

        @Override
        public boolean onDown(MotionEvent motionEvent) {
            return false;
        }

        @Override
        public void onShowPress(MotionEvent motionEvent) {

        }

        @Override
        public boolean onSingleTapUp(MotionEvent motionEvent) {
            return false;
        }

        @Override
        public boolean onScroll(MotionEvent motionEvent, MotionEvent motionEvent1, float v, float v1)
        {
            synchronized (controlLock) {
                float deltaX = 2*(v/view.getWidth());
                float deltaY = -2*(v1/view.getWidth());

                FMatrixRMaj tmp1 = new FMatrixRMaj(3,3);
                ConvertRotation3D_F32.eulerToMatrix(EulerType.YXZ,deltaX, deltaY, 0,tmp1);
                FMatrixRMaj tmp2 = workR.copy();
                CommonOps_FDRM.mult(tmp2,tmp1,workR);
                renderView();
            }

            return true;
        }

        @Override
        public void onLongPress(MotionEvent motionEvent) {

        }

        @Override
        public boolean onFling(MotionEvent motionEvent, MotionEvent motionEvent1, float v, float v1) {
            return false;
        }
    }

    class RenderView implements Runnable {
        @Override
        public void run() {
            while( true ) {
                synchronized (controlLock) {
                    FMatrixRMaj tmp = pinholeToEqui.getRotation().copy();
                    CommonOps_FDRM.mult(tmp,workR,pinholeToEqui.getRotation());
                    CommonOps_FDRM.setIdentity(workR);
                }
                distorter.apply(equiImage, pinholeImage);
                synchronized (lockOutput) {
                    ConvertBitmap.boofToBitmap(pinholeImage, outputBitmap, bitmapTmp);
                }
                runOnUiThread(() -> view.invalidate());

                synchronized (lockThread) {
                    if( pendingRender ) {
                        pendingRender = false;
                    } else {
                        renderNow = null;
                        break;
                    }
                }
            }
        }
    }

    /**
     * Custom view for visualizing results
     */
    public class DisplayView extends SurfaceView implements SurfaceHolder.Callback {

        SurfaceHolder mHolder;
        Canvas outputCanvas = new Canvas();

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
            float W = canvas.getWidth();
            float H = canvas.getHeight();

            float scale = Math.min(W/outputBitmap.getWidth(),H/outputBitmap.getHeight());


            float offsetX=(W-outputBitmap.getWidth()*scale)/2;
            float offsetY=(H-outputBitmap.getHeight()*scale)/2;

            imageToView.reset();
            imageToView.postScale(scale,scale);
            imageToView.postTranslate(offsetX,offsetY);

            synchronized (lockOutput) {
                canvas.drawBitmap(outputBitmap,imageToView,null);
            }

        }

        @Override
        public void surfaceCreated(SurfaceHolder surfaceHolder) {}

        @Override
        public void surfaceChanged(SurfaceHolder surfaceHolder, int i, int i1, int i2) {}

        @Override
        public void surfaceDestroyed(SurfaceHolder surfaceHolder) {}
    }
}
