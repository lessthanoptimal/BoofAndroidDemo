package org.boofcv.android;

import android.graphics.Canvas;
import android.util.Size;
import android.view.SurfaceView;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import boofcv.struct.image.ImageBase;

/**
 * Camera activity specifically designed for this demonstration. Image processing algorithms
 * can be swapped in and out
 */
public class DemoCamera2Activity extends VisualizeCamera2Activity {

    protected final Object lockProcessor = new Object();
    protected DemoProcessing processor;

    public DemoCamera2Activity(Resolution resolution) {
        super.targetResolution = resolutionToPixels(resolution);

        super.showBitmap = true;
        super.visualizeOnlyMostRecent = true;
    }

    protected void setControls(LinearLayout controls ) {
        setContentView(R.layout.standard_camera2);
        LinearLayout parent = findViewById(R.id.root_layout);
        parent.addView(controls);

        FrameLayout surfaceLayout = findViewById(R.id.camera_frame_layout);
        startCamera(surfaceLayout,null);
    }

    @Override
    protected void onCameraResolutionChange( int width , int height ) {
        super.onCameraResolutionChange(width,height);
        DemoProcessing p = processor;
        if( p != null ) {
            p.initialize(width,height);
        }
    }

    @Override
    protected void processImage(ImageBase image) {
        DemoProcessing processor;
        synchronized (lockProcessor) {
            processor = this.processor;
        }

        if( processor != null) {
            if( !processor.isThreadSafe() && threadPool.getMaximumPoolSize() > 1 )
                throw new RuntimeException("Process is not thread safe but the pool is larger than 1!");
            processor.process(image);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        synchronized (lockProcessor) {
            processor.stop();
        }
    }

    /**
     * Changes the processor used to process the video frames
     */
    public void setProcessing(DemoProcessing processor ) {
        synchronized (lockProcessor) {
            // shut down the previous processor
            if( this.processor != null ) {
                this.processor.stop();
            }
            // switch it over to the new one
            setImageType(processor.getImageType());
            this.processor = processor;

            // If the camera has already started running initialize it now. otherwise it will
            // be initialized when the size is set
            Size s = this.mCameraSize;
            if( s != null ) {
                processor.initialize(s.getWidth(),s.getHeight());
            }
        }
    }

    @Override
    protected void onDrawFrame(SurfaceView view , Canvas canvas ) {
        super.onDrawFrame(view,canvas);

        synchronized (lockProcessor) {
            if( processor != null )
                processor.onDraw(canvas, imageToView);
        }
    }

    public int resolutionToPixels(Resolution resolution) {
        switch (resolution) {
            case LOW:
            case R320x240:
                return 320*240;

            case MEDIUM:
            case R640x480:
                return 640*480;

            case HIGH:
                return 1024*768;

            case MAX:
                return Integer.MAX_VALUE;

                default:
                    throw new IllegalArgumentException("Unknown");
        }
    }

    /**
     * Algorithm which require an exact resolution should request
     * a specific one. Algorithms produce better results with more
     * resolution should choose a relative one.
     */
    public enum Resolution {
        LOW,MEDIUM,HIGH,MAX,R320x240,R640x480;

    }
}
