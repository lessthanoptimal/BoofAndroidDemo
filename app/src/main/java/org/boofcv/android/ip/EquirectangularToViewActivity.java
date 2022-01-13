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
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.FrameLayout;
import android.widget.Spinner;

import org.boofcv.android.R;
import org.ddogleg.struct.DogArray_I8;
import org.ejml.data.FMatrixRMaj;
import org.ejml.dense.row.CommonOps_FDRM;

import boofcv.alg.distort.ImageDistort;
import boofcv.alg.distort.spherical.CameraToEquirectangular_F32;
import boofcv.alg.distort.spherical.CylinderToEquirectangular_F32;
import boofcv.alg.interpolate.InterpolatePixel;
import boofcv.alg.interpolate.InterpolationType;
import boofcv.android.ConvertBitmap;
import boofcv.factory.distort.FactoryDistort;
import boofcv.factory.interpolate.FactoryInterpolation;
import boofcv.struct.border.BorderType;
import boofcv.struct.calib.CameraPinhole;
import boofcv.struct.calib.CameraUniversalOmni;
import boofcv.struct.image.GrayU8;
import boofcv.struct.image.Planar;
import georegression.geometry.ConvertRotation3D_F32;
import georegression.metric.UtilAngle;
import georegression.struct.EulerType;

/**
 * Shows an equirectangular image with a pinhole camera super imposed on top of it.
 */
public class EquirectangularToViewActivity extends Activity {


    public static final String TAG = "Equirectangular";

    private GestureDetectorCompat mDetector;


    // BEGIN lock
    final Object controlLock = new Object();
    final FMatrixRMaj workR = CommonOps_FDRM.identity(3);
    // END


    // BEGIN Lock
    final Object controlDistort = new Object();
    CameraToEquirectangular_F32 cameraToEqui = new CameraToEquirectangular_F32();
    CylinderToEquirectangular_F32 cylinderToEqui = new CylinderToEquirectangular_F32();
    ImageDistort<Planar<GrayU8>,Planar<GrayU8>> distorter;
    // END

    // BEGIN Lock
    final Object lockOutput = new Object();
    Bitmap outputBitmap;
    Planar<GrayU8> equiImage = new Planar<>(GrayU8.class,1,1,3);
    Planar<GrayU8> renderImage = new Planar<>(GrayU8.class,1,1,3);
    final DogArray_I8 bitmapTmp = new DogArray_I8();
    // END

    DisplayView view;
    ModeType renderModel;

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

        // Initialize data structures. listener might be called right away once GUI is initialized
        Bitmap icon = BitmapFactory.decodeResource(getResources(),R.drawable.canyon360);
        Log.i(TAG, "equi icon = "+icon.getWidth()+"x"+icon.getHeight());
        equiImage.reshape(icon.getWidth(),icon.getHeight());
        ConvertBitmap.bitmapToBoof(icon,equiImage,null);

        // Create the image distorter which will render the image
        InterpolatePixel<Planar<GrayU8>> interp = FactoryInterpolation.
                createPixel(0, 255, InterpolationType.BILINEAR, BorderType.EXTENDED, equiImage.getImageType());
        distorter = FactoryDistort.distort(false,interp,equiImage.getImageType());

        // This is where the magic is done.  It defines the transform from equirectangular to pinhole
        cameraToEqui.setEquirectangularShape(equiImage.width,equiImage.height);
        cylinderToEqui.setEquirectangularShape(equiImage.width,equiImage.height);

        // Now setup the GUI
        setContentView(R.layout.equirectangular);

        FrameLayout surfaceLayout = findViewById(R.id.image_frame);
        view = new DisplayView(this);
        surfaceLayout.addView(view);

        Spinner spinner = findViewById(R.id.spinner_equi_models);
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(this,
                R.array.equi_models, android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                changeModel(500,500,ModeType.values()[position]);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {

            }
        });


        mDetector = new GestureDetectorCompat(this,new TouchControls());
   }

   private void changeModel( int width , int height , ModeType type ) {

       if( renderModel == type )
           return;
       renderModel = type;

       synchronized (controlDistort) {
           synchronized (lockOutput) {
               renderImage.reshape(width, height);
               outputBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
           }

           switch (type) {
               case PINHOLE: {
                   CameraPinhole model = new CameraPinhole(width / 2, height / 2, 0,
                           width / 2, height / 2, width, height);
                   cameraToEqui.setCameraModel(model);
                   distorter.setModel(cameraToEqui);
               } break;

               case FISHEYE: {
                   CameraUniversalOmni model = new CameraUniversalOmni(20);
                   model.fsetK(width*1.35, height*1.35, 0,
                           width / 2, height / 2, width, height);
                   model.fsetMirror(3f);
                   model.fsetRadial(7.308e-1f,1.855e1f);
                   model.fsetTangental(-1.288e-2f,-1.1342e-2f);
                   cameraToEqui.setCameraModel(model);
                   distorter.setModel(cameraToEqui);
               } break;

               case CYLINDER: {
                   double vfov = UtilAngle.radian(120);
                   cylinderToEqui.configure(width, height, (float)vfov);
                   this.distorter.setModel(cylinderToEqui);
               } break;

           }
       }

       renderView();
   }

    private void renderView() {
        synchronized (lockThread) {
            if( renderNow == null ) {
                pendingRender = false;
                renderNow = new RenderView();
                new Thread(renderNow,"RenderEqui").start();
            } else {
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

        FMatrixRMaj controlR = new FMatrixRMaj(3,3);

        @Override
        public void run() {
            while( true ) {
                synchronized (controlLock) {
                    controlR.setTo(workR);
                    CommonOps_FDRM.setIdentity(workR);
                }

                synchronized (controlDistort) {
                    FMatrixRMaj tmp = cameraToEqui.getRotation().copy();
                    CommonOps_FDRM.mult(tmp,controlR,cameraToEqui.getRotation());
                    tmp = cylinderToEqui.getRotation().copy();
                    CommonOps_FDRM.mult(tmp,controlR,cylinderToEqui.getRotation());
                    distorter.apply(equiImage, renderImage);
                }
                synchronized (lockOutput) {
                    ConvertBitmap.boofToBitmap(renderImage, outputBitmap, bitmapTmp);
                }
                if( view != null )
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
        protected Matrix imageToView = new Matrix();

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

            synchronized (lockOutput) {
                if( outputBitmap == null )
                    return;

                float scale = Math.min(W/outputBitmap.getWidth(),H/outputBitmap.getHeight());

                float offsetX=(W-outputBitmap.getWidth()*scale)/2;
                float offsetY=(H-outputBitmap.getHeight()*scale)/2;

                imageToView.reset();
                imageToView.postScale(scale,scale);
                imageToView.postTranslate(offsetX,offsetY);

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

    enum ModeType {
        PINHOLE,
        FISHEYE,
        CYLINDER
    }
}
