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
    boolean previousScale=false;

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
            previousScale = true;
            return true;
        } else if( previousScale ) {
            // reset the location to avoid a large jump after pinching has stopped
            previousScale = false;
            previousX = x;
            previousY = y;
            return true;
        }

        // MotionEvent reports input details from the touch screen
        // and other input controls. In this case, you are only
        // interested in events where the touch position changed.


        switch (e.getAction()) {
            case MotionEvent.ACTION_MOVE:

                float dx = 5.0f*(x - previousX)/getWidth();
                float dy = 5.0f*(previousY - y)/getHeight();

                synchronized (renderer.lock) {
                    renderer.tranX += dx;
                    renderer.tranY += dy;
                }

                requestRender();
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
}
