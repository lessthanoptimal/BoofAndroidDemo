package org.boofcv.android;

import android.app.ProgressDialog;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.os.Looper;
import android.util.Size;
import android.view.MotionEvent;
import android.view.SurfaceView;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import boofcv.android.ConvertBitmap;
import boofcv.struct.image.ImageBase;
import boofcv.struct.image.ImageType;
import georegression.struct.point.Point2D_F64;

/**
 * Camera activity specifically designed for this demonstration. Image processing algorithms
 * can be swapped in and out
 */
public class DemoCamera2Activity extends VisualizeCamera2Activity {

    protected final Object lockProcessor = new Object();
    protected DemoProcessing processor;

    // If true it will show the processed image, otherwise it will
    // display the input image
    protected boolean showProcessed = true;

    // Used to inform the user that its doing some calculations
    ProgressDialog progressDialog;
    protected final Object lockProgress = new Object();

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
        if( !showProcessed) {
            synchronized (bitmapLock) {
                ConvertBitmap.boofToBitmap(image, bitmap, bitmapTmp);
            }
            return;
        }
        DemoProcessing processor;
        synchronized (lockProcessor) {
            processor = this.processor;
        }

        if( processor != null) {
            if( !processor.isThreadSafe() && threadPool.getMaximumPoolSize() > 1 )
                throw new RuntimeException("Process is not thread safe but the pool is larger than 1!");
            // TODO update in v0.30
            if( isSameType(processor.getImageType(),image.getImageType()))
                processor.process(image);
        }
    }

    public static boolean isSameType(ImageType a , ImageType b ) {
        if( a.getFamily() != b.getFamily() )
            return false;
        if( a.getDataType() != b.getDataType() )
            return false;
        if( a.getNumBands() != b.getNumBands() )
            return false;
        return true;
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

        if( !showProcessed ) {
            synchronized (bitmapLock) {
                canvas.drawBitmap(bitmap, imageToView, null);
            }
        } else {
            synchronized (lockProcessor) {
                if (processor != null)
                    processor.onDraw(canvas, imageToView);
            }
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
     * Displays an indeterminate progress dialog.   If the dialog is already open this will change the message being
     * displayed.  Function blocks until the dialog has been declared.
     *
     * @param message Text shown in dialog
     */
    protected void setProgressMessage(final String message) {
        runOnUiThread(() -> {
            synchronized ( lockProgress ) {
                if( progressDialog != null ) {
                    // a dialog is already open, change the message
                    progressDialog.setMessage(message);
                    return;
                }
                progressDialog = new ProgressDialog(this);
                progressDialog.setMessage(message);
                progressDialog.setIndeterminate(true);
                progressDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
            }

            // don't show the dialog until 1 second has passed
            long showTime = System.currentTimeMillis()+1000;
            while( showTime > System.currentTimeMillis() ) {
                Thread.yield();
            }
            // if it hasn't been dismissed, show the dialog
            synchronized ( lockProgress ) {
                if( progressDialog != null )
                    progressDialog.show();
            }
        });

        // block until the GUI thread has been called
        while( progressDialog == null  ) {
            Thread.yield();
        }
    }

    /**
     * Dismisses the progress dialog.  Can be called even if there is no progressDialog being shown.
     */
    protected void hideProgressDialog() {
        // do nothing if the dialog is already being displayed
        synchronized ( lockProgress ) {
            if( progressDialog == null )
                return;
        }

        if (Looper.getMainLooper().getThread() == Thread.currentThread()) {
            // if inside the UI thread just dismiss the dialog and avoid a potential locking condition
            synchronized ( lockProgress ) {
                progressDialog.dismiss();
                progressDialog = null;
            }
        } else {
            runOnUiThread(() -> {
                synchronized ( lockProgress ) {
                    progressDialog.dismiss();
                    progressDialog = null;
                }
            });

            // block until dialog has been dismissed
            while( progressDialog != null  ) {
                Thread.yield();
            }
        }
    }

    float pts[] = new float[2];
    public void applyToPoint(Matrix matrix , double x , double y , Point2D_F64 out ) {
        pts[0] = (float)x;
        pts[1] = (float)y;
        matrix.mapPoints(pts);
        out.x = pts[0];
        out.y = pts[1];
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
