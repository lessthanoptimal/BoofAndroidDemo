package org.boofcv.android;

import android.app.ProgressDialog;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.hardware.camera2.CameraDevice;
import android.os.Bundle;
import android.os.Looper;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.Size;
import android.view.MotionEvent;
import android.view.SurfaceView;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.Toast;

import java.util.Locale;

import boofcv.android.ConvertBitmap;
import boofcv.android.camera2.VisualizeCamera2Activity;
import boofcv.misc.MovingAverage;
import boofcv.struct.calib.CameraPinholeRadial;
import boofcv.struct.image.ImageBase;
import georegression.struct.point.Point2D_F64;

/**
 * Camera activity specifically designed for this demonstration. Image processing algorithms
 * can be swapped in and out
 */
public abstract class DemoCamera2Activity extends VisualizeCamera2Activity {

    protected final Object lockProcessor = new Object();
    protected DemoProcessing processor;

    // If true it will show the processed image, otherwise it will
    // display the input image
    protected boolean showProcessed = true;

    // Used to inform the user that its doing some calculations
    ProgressDialog progressDialog;
    protected final Object lockProgress = new Object();

    protected DisplayMetrics displayMetrics;

    //START Timing data structures locked on super.lockTiming
    protected int totalFramesProcessed; // total frames processed for the specific processing algorithm
    protected MovingAverage periodProcess = new MovingAverage(0.8); // milliseconds
    protected MovingAverage periodRender = new MovingAverage(0.8); // milliseconds
    //END

    // if a process is taking too long poentially trigger a change in resolution to sleep things up
    protected boolean changeResolutionOnSlow = false;
    protected boolean triggerSlow;
    protected final static double TRIGGER_HORIBLY_SLOW = 2000.0;
    protected final static double TRIGGER_SLOW = 400.0;

    // Work variables for rendering performance
    private Paint paintText = new Paint();
    private Rect bounds0 = new Rect();
    private Rect bounds1 = new Rect();
    private Rect bounds2 = new Rect();

    public DemoCamera2Activity(Resolution resolution) {
        super.targetResolution = resolutionToPixels(resolution);

        super.showBitmap = true;
        super.visualizeOnlyMostRecent = true;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        displayMetrics = Resources.getSystem().getDisplayMetrics();

        paintText.setStrokeWidth(3*displayMetrics.density);
        paintText.setTextSize(24*displayMetrics.density);
        paintText.setTextAlign(Paint.Align.LEFT);
        paintText.setARGB(0xFF,0xFF,0xB0,0);
        paintText.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.BOLD));
    }

    /**
     * Creates a new process and calls setProcess(new ASDASDASD). This is invoked
     * after a new camera device has been opened
     */
    public abstract void createNewProcessor();

    protected void setControls(@Nullable LinearLayout controls ) {
        setContentView(R.layout.standard_camera2);
        LinearLayout parent = findViewById(R.id.root_layout);
        if( controls != null)
            parent.addView(controls);

        FrameLayout surfaceLayout = findViewById(R.id.camera_frame_layout);
        startCamera(surfaceLayout,null);
    }

    @Override
    protected void onCameraResolutionChange( int width , int height ) {
        Log.i("Demo","onCameraResolutionChange called. "+width+"x"+height);
        super.onCameraResolutionChange(width,height);
        triggerSlow = false;
        DemoProcessing p = processor;
        if( p != null ) {
            p.initialize(width,height);
        } else {
            Log.i("Demo","  processor is null");
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

        if( processor == null) {
            return;
        }

        if( !processor.isThreadSafe() && threadPool.getMaximumPoolSize() > 1 )
            throw new RuntimeException("Process is not thread safe but the pool is larger than 1!");

        if( processor.getImageType().isSameType(image.getImageType())) {
            long before = System.nanoTime();
            processor.process(image);
            long after = System.nanoTime();

            double milliseconds = (after-before)*1e-6;

            double timeProcess,timeConvert;
            synchronized (lockTiming) {
                totalFramesProcessed++;
                // give it a few frames to warm up
                if( totalFramesProcessed >= TIMING_WARM_UP ) {
                    periodProcess.update(milliseconds);
                    triggerSlow |= periodProcess.getAverage() > TRIGGER_SLOW;
                } else {
                    // if things are extremely slow right off the bat abort and change resolution
                    triggerSlow |= milliseconds >= TRIGGER_HORIBLY_SLOW;
                }

                timeProcess = periodProcess.getAverage();
                timeConvert = periodConvert.getAverage();
            }

            if( verbose ) {
                Log.i("DemoTiming",String.format("Total Frames %4d curr %5.1f ave process %5.1f convert %5.1f at %dx%d",
                        totalFramesProcessed,milliseconds,timeProcess,timeConvert,image.width,image.height));
            }

        }

        if( DemoMain.preference.autoReduce && changeResolutionOnSlow && triggerSlow ) {
            handleReduceResolution(processor);
        }
    }

    private void handleReduceResolution(DemoProcessing processor) {
        int original = mCameraSize.getWidth()*mCameraSize.getHeight();
        targetResolution = Math.max(320*240,original/4);
        if( original != targetResolution ) {
            // Prevent new instances from launching. I hope. Not sure if this will work if
            // there's multiple worker threads
            synchronized (lockProcessor) {
                this.processor = null;
                if( processor != null ) {
                    processor.stop();
                }
            }

            if( verbose )
                Log.i("Demo","Changing resolution because of slow process. pixels="+targetResolution);
            runOnUiThread(()->{
                Toast.makeText(DemoCamera2Activity.this,"Reducing resolution for performance",Toast.LENGTH_SHORT).show();
                closeCamera();
                openCamera(viewWidth,viewHeight);

            });
        } else {
            changeResolutionOnSlow = false;
            triggerSlow = false;
            Log.i("Demo","Slow but at minimum resolution already");
        }
    }

    @Override
    protected void onCameraOpened( @NonNull CameraDevice cameraDevice ) {
        // Adding a delay before starting the process seems to allow things to run better
        // An image can be displayed
        new java.util.Timer().schedule(
                new java.util.TimerTask() {
                    @Override
                    public void run() {
                        runOnUiThread(()->createNewProcessor());
                    }
                },
                500);
    }

    @Override
    protected void onPause() {
        super.onPause();
        synchronized (lockProcessor) {
            if( processor != null )
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
                Log.i("Demo","initializing processor "+s.getWidth()+"x"+s.getHeight());
                processor.initialize(s.getWidth(),s.getHeight());
            } else {
                Log.i("Demo","skipping initializing processor. size not known");
            }

            synchronized (lockTiming) {
                totalFramesProcessed = 0;
                periodProcess.reset();
                periodRender.reset();
            }
        }
    }

    @Override
    protected void onDrawFrame(SurfaceView view , Canvas canvas ) {
        long startTime = System.nanoTime();
        super.onDrawFrame(view,canvas);

        if( !showProcessed ) {
            if( !showBitmap ) { // if true then it has already been rendered
                synchronized (bitmapLock) {
                    canvas.drawBitmap(bitmap, imageToView, null);
                }
            }
        } else {
            DemoProcessing processor;
            synchronized (lockProcessor) {
                processor = this.processor;
            }
            if (processor != null)
                processor.onDraw(canvas, imageToView);
        }
        long stopTime = System.nanoTime();

        double processPeriod;
        double renderPeriod;

        synchronized (lockTiming) {
            periodRender.update((stopTime-startTime)*1e-6);
            renderPeriod = periodRender.getAverage();
            processPeriod = periodProcess.getAverage();
        }
        if( DemoMain.preference.showSpeed)
            renderSpeed(canvas, processPeriod, renderPeriod);
    }

    /**
     * Renders how fast the algorithm and rendering is running
     */
    private void renderSpeed(Canvas canvas, double processPeriod, double renderPeriod) {
        canvas.setMatrix(identity);

        Locale local = Locale.getDefault();
        String line0 = String.format(local,"%dx%d",bitmap.getWidth(),bitmap.getHeight());
        String line1 = String.format(local,"%6.1fms Alg",processPeriod);
        String line2 = String.format(local,"%6.1fms Vis",renderPeriod);

        float spaceH = 10*displayMetrics.density;
        paintText.getTextBounds(line0, 0, line0.length(), bounds0);
        paintText.getTextBounds(line1, 0, line1.length(), bounds1);
        paintText.getTextBounds(line2, 0, line2.length(), bounds2);

        float width = Math.max(bounds0.width(),bounds1.width());
        width = Math.max(width,bounds2.width());
        float centerX = canvas.getWidth()/2;
//        float centerY = canvas.getHeight()/2;
        float height = bounds0.height()+bounds1.height()+bounds2.height()+2*spaceH;
        float x0 = centerX-width/2 + (width-bounds0.width())/2;
        float y0 = height/2;
        float y1 = y0 + bounds0.height() + spaceH;
        float y2 = y1 + bounds1.height() + spaceH;

        canvas.drawText(line0,x0,y0,paintText);
        canvas.drawText(line1,centerX-width/2,y1,paintText);
        canvas.drawText(line2,centerX-width/2,y2,paintText);
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
     * Some times the size of a font of stroke needs to be specified in the input image
     * but then gets scaled to image resolution. This compensates for that.
     */
    public float screenDensityAdjusted() {
        if( mCameraSize == null )
            return displayMetrics.density;

        int rotation = getWindowManager().getDefaultDisplay().getRotation();
        int screenWidth = (rotation==0||rotation==2)?displayMetrics.widthPixels:displayMetrics.heightPixels;
        int cameraWidth = mSensorOrientation==0||mSensorOrientation==180?
                mCameraSize.getWidth():mCameraSize.getHeight();

        return displayMetrics.density*cameraWidth/screenWidth;
    }

    public CameraPinholeRadial lookupIntrinsics() {
        return DemoMain.preference.lookup(mCameraSize.getWidth(),mCameraSize.getHeight());
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
