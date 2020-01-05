package org.boofcv.android.visalize;

import android.content.Context;
import android.opengl.GLSurfaceView;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;

public class PointCloudSurfaceView extends GLSurfaceView {

    public static final String TAG = "PointCloudSurface";

    private final float TOUCH_SCALE_FACTOR = 180.0f / 1280;
    private float previousX;
    private float previousY;

    final PointCloud3DRenderer renderer = new PointCloud3DRenderer();
    final ScaleGestureDetector mScaleDetector;
    boolean previousTwoTouch=false;

    Motion motion=Motion.TRANSLATE;

    public PointCloudSurfaceView(Context context) {
        super(context);
        setEGLContextClientVersion(2);

        mScaleDetector = new ScaleGestureDetector(getContext(), new PinchZoomListener());
        super.setRenderer(renderer);
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        float x = e.getX();
        float y = e.getY();


        mScaleDetector.onTouchEvent(e); // always returns true :-(
        if( mScaleDetector.isInProgress() ) {
            previousTwoTouch = true;
            return true;
        }

        // When going from two to one touch it can cause a large jump in finger position
        if( e.getPointerCount() == 1 && previousTwoTouch ) {
            previousTwoTouch = false;
            previousX = x;
            previousY = y;
        } else if( e.getPointerCount() == 2 ) {
            previousTwoTouch = true;
        }


        switch (e.getAction()) {
            // Select type of motion depending on where in the image is initially touched
            case MotionEvent.ACTION_DOWN:
                if( x < getWidth()/8f )
                    motion = Motion.ROTATE_X;
                else if( y < getHeight()/8f )
                    motion = Motion.ROTATE_Z;
                else if( x >= getWidth()*(7f/8f) )
                    motion = Motion.ROTATE_Y;
                else
                    motion = Motion.TRANSLATE;
                break;
            case MotionEvent.ACTION_MOVE:
                if( motion == Motion.TRANSLATE ) {
                    float dx = 5.0f * (x - previousX) / getWidth();
                    float dy = 5.0f * (previousY - y) / getHeight();

                    synchronized (renderer.lock) {
                        renderer.tranX += dx;
                        renderer.tranY += dy;
                    }
                } else if( motion == Motion.ROTATE_X ){
                    float dy = 200.0f * (previousY - y) / getHeight();
                    synchronized (renderer.lock) {
                        renderer.rotX += dy;
                    }
                } else if( motion == Motion.ROTATE_Z ){
                    float dx = 200.0f * (x - previousX) / getWidth();
                    synchronized (renderer.lock) {
                        renderer.rotY += dx;
                    }
                } else if( motion == Motion.ROTATE_Y ){
                    float dy = 200.0f * (previousY - y) / getHeight();
                    synchronized (renderer.lock) {
                        renderer.rotZ -= dy;
                    }
                }

                requestRender();
                break;
        }

        previousX = x;
        previousY = y;
        return true;
    }

    class PinchZoomListener extends ScaleGestureDetector.SimpleOnScaleGestureListener {
        @Override
        public boolean onScale(ScaleGestureDetector detector) {
            float scaleFactor = detector.getScaleFactor();

            if( scaleFactor == 1.0 )
                return false;
            float dz = (float)(scaleFactor-1.0);

            synchronized (renderer.lock) {
                renderer.tranZ += dz;
            }
            requestRender();
            return true;
        }
    }

    public PointCloud3DRenderer getRenderer() {
        return renderer;
    }

     enum Motion {
         TRANSLATE,
         ROTATE_X,
         ROTATE_Y,
         ROTATE_Z
     }
}
