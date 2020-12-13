package org.boofcv.android;

import android.app.ActivityManager;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.pm.ConfigurationInfo;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.os.Bundle;
import android.os.Looper;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;
import android.util.Size;
import android.view.MotionEvent;
import android.view.SurfaceView;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.Toast;

import org.acra.ACRA;

import java.util.Locale;

import boofcv.android.ConvertBitmap;
import boofcv.android.camera2.VisualizeCamera2Activity;
import boofcv.concurrency.BoofConcurrency;
import boofcv.misc.MovingAverage;
import boofcv.struct.calib.CameraPinholeBrown;
import boofcv.struct.image.ImageBase;
import georegression.struct.point.Point2D_F64;

/**
 * Camera activity specifically designed for this demonstration. Image processing algorithms
 * can be swapped in and out
 *
 * useful variables and methods
 * visualizationPending
 */
public abstract class DemoCamera2Activity extends VisualizeCamera2Activity {

    private static final String TAG = "DemoCamera2";

    //######## Start variables owned by lock
    protected final Object lockProcessor = new Object();
    protected DemoProcessing processor;
    // if false no processor functions will be called. Will not be set to true until the
    // resolution is known and init has been called
    protected boolean cameraInitialized;
    // camera width and height when processor was initialized
    protected int cameraWidth,cameraHeight,cameraOrientation;
    //####### END

    // If true it will show the processed image, otherwise it will
    // display the input image
    protected boolean showProcessed = true;

    // Used to inform the user that its doing some calculations
    ProgressDialog progressDialog;
    protected final Object lockProgress = new Object();

    //START Timing data structures locked on super.lockTiming
    protected int totalFramesProcessed; // total frames processed for the specific processing algorithm
    protected MovingAverage periodProcess = new MovingAverage(0.8); // milliseconds
    protected MovingAverage periodRender = new MovingAverage(0.8); // milliseconds
    //END

    // If this is true then visualization data has not been rendered and the input image
    // will not be processed. This ensures that visualization and the background image
    // are rendered together with each other
    // To use this feature set the variable to true inside the process(image) block after visuals
    // have been made
    protected volatile boolean visualizationPending = false;
    // NOTE: The current approach isn't perfect. it's possible for an old do nothing visual to
    //       mark this variable as false before the now one is passed in

    // if a process is taking too long potentially trigger a change in resolution to sleep things up
    protected boolean changeResolutionOnSlow = false;
    protected boolean triggerSlow;
    protected final static double TRIGGER_HORIBLY_SLOW = 2000.0;
    protected final static double TRIGGER_SLOW = 400.0;

    // Work variables for rendering performance
    private Paint paintText = new Paint();
    private Rect bounds0 = new Rect();
    private Rect bounds1 = new Rect();
    private Rect bounds2 = new Rect();
    private final Matrix tempMatrix = new Matrix();

    protected DemoApplication app;

    public DemoCamera2Activity(Resolution resolution) {
        super.targetResolution = resolutionToPixels(resolution);

        super.bitmapMode = BitmapMode.UNSAFE;
        super.visualizeOnlyMostRecent = true;

//        super.verbose = true;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Assign app before letting the parent initialize because there's a chance the camera
        // could be initialized before this gets assigned and generate a NPE
        app = (DemoApplication)getApplication();
        super.onCreate(savedInstanceState);

        BoofConcurrency.USE_CONCURRENT = app.preference.useConcurrent;
        ACRA.getErrorReporter().putCustomData("BOOFCV-CONCURRENT", ""+BoofConcurrency.USE_CONCURRENT);
        Log.i(TAG,"USE_CONCURRENT = "+BoofConcurrency.USE_CONCURRENT);

        paintText.setStrokeWidth(3*displayMetrics.density);
        paintText.setTextSize(24*displayMetrics.density);
        paintText.setTextAlign(Paint.Align.LEFT);
        paintText.setARGB(0xFF,0xFF,0xB0,0);
        paintText.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.BOLD));
    }

    @Override
    protected void onStop() {
        super.onStop();
        // this is an attempt to prevent the leaked window error.
        synchronized (lockProgress) {
            if( progressDialog != null ) {
                progressDialog.dismiss();
                progressDialog = null;
            }
        }
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
    protected boolean selectCamera( String cameraId , CameraCharacteristics characteristics) {
        return cameraId.equals(app.preference.cameraId);
    }

    @Override
    protected int selectResolution(int widthTexture, int heightTexture, Size[] resolutions) {
        // Auto mode on resolution
        if (app.preference.resolution == 0)
            return super.selectResolution(widthTexture, heightTexture, resolutions);

        // A specific on requested
        CameraSpecs s = DemoMain.defaultCameraSpecs(app);
        Size target = s.sizes.get( app.preference.resolution-1);
        for( int i = 0; i < resolutions.length; i++  ) {
            Size r = resolutions[i];
            if( r.getWidth() == target.getWidth() && r.getHeight() == r.getHeight() ) {
                return i;
            }
        }
        // failed to find the requested. Fall back to default behavior
        Log.e("Demo","Can't find requested resolution");
        return super.selectResolution(widthTexture, heightTexture, resolutions);
    }

    @Override
    protected void onCameraResolutionChange( int width , int height, int cameraOrientation ) {
        Log.i("Demo","onCameraResolutionChange called. "+width+"x"+height);
        super.onCameraResolutionChange(width,height,cameraOrientation);

        ACRA.getErrorReporter().putCustomData("Resolution", width+" x "+height);

        triggerSlow = false;
        DemoProcessing p;
        synchronized ( lockProcessor) {
            p = processor;
            this.cameraWidth = width;
            this.cameraHeight = height;
            this.cameraOrientation = cameraOrientation;
        }
        if( p != null ) {
            try {
                p.initialize(cameraWidth, cameraHeight, cameraOrientation);
            } catch( OutOfMemoryError e ) {
                ACRA.getErrorReporter().handleSilentException(e);
                synchronized ( lockProcessor) {
                    processor = null; // free memory
                }
                e.printStackTrace();
                Log.e(TAG,"Out of memory. "+e.getMessage());
                runOnUiThread(()->{
                    finish(); // leave the activity
                    Toast.makeText(this,"Out of Memory. Try lower resolution",Toast.LENGTH_LONG).show();
                });
            }
        } else {
            Log.i("Demo","  processor is null");
        }
        // Wait until initialize has been called to prevent it from being called twice immediately
        // and to prevent process or visualize from being initialize has been called
        synchronized (lockProcessor) {
            this.cameraInitialized = true;
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
        // the previous visualization has yet to be rendered.
        if( visualizationPending )
            return;

        if( !showProcessed) {
            ConvertBitmap.boofToBitmap(image, bitmap, bitmapTmp);
            return;
        }
        DemoProcessing processor;
        synchronized (lockProcessor) {
            if( !cameraInitialized || this.processor == null )
                return;
            processor = this.processor;
        }

        if( !processor.isThreadSafe() && threadPool.getMaximumPoolSize() > 1 )
            throw new RuntimeException("Process is not thread safe but the pool is larger than 1!");

        if( processor.getImageType().isSameType(image.getImageType())) {
            long before = System.nanoTime();
            try {
                processor.process(image);
            } catch( OutOfMemoryError e ) {
                ACRA.getErrorReporter().handleSilentException(e);
                runOnUiThread(()->{
                    finish(); // leave the activity
                    Toast.makeText(this,"Out of Memory. Try lower resolution",Toast.LENGTH_LONG).show();
                });
                return;
            }
            long after = System.nanoTime();

            double milliseconds = (after-before)*1e-6;

//            double timeProcess,timeConvert;
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

//                timeProcess = periodProcess.getAverage();
//                timeConvert = periodConvert.getAverage();
            }

            if( verbose ) {
//                Log.i("DemoTiming",String.format("Total Frames %4d curr %5.1f ave process %5.1f convert %5.1f at %dx%d",
//                        totalFramesProcessed,milliseconds,timeProcess,timeConvert,image.width,image.height));
            }

        }

        if( app.preference.resolution == 0 &&
                app.preference.autoReduce &&
                changeResolutionOnSlow && triggerSlow ) {
            handleReduceResolution(processor);
        }
    }

    private void handleReduceResolution(DemoProcessing processor) {
        int original = cameraWidth*cameraHeight;
        targetResolution = Math.max(320*240,original/4);
        if( original != targetResolution ) {
            // Prevent new instances from launching. I hope. Not sure if this will work if
            // there's multiple worker threads
            synchronized (lockProcessor) {
                cameraInitialized = false;
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
            setImageType(processor.getImageType(),processor.getColorFormat());
            this.processor = processor;

            // If the camera is not initialized then all these values are not known. It will be
            // initialized when they are known
            // This must also be called before leaving the lock to prevent process or visualize
            // from being called on it before initialize has completed
            if( cameraInitialized ) {
                Log.i("Demo", "initializing processor " + cameraWidth + "x" + cameraHeight);
                processor.initialize(cameraWidth, cameraHeight, cameraOrientation);
            }
        }
        synchronized (lockTiming) {
            totalFramesProcessed = 0;
            periodProcess.reset();
            periodRender.reset();
        }
    }

    @Override
    protected void onDrawFrame(SurfaceView view , Canvas canvas ) {
        long startTime = System.nanoTime();
        super.onDrawFrame(view,canvas);

        if( !showProcessed ) {
            if( bitmapMode == BitmapMode.NONE ) { // if true then it has already been rendered
                canvas.drawBitmap(bitmap, imageToView, null);
            }
        } else {
            DemoProcessing processor = null;
            synchronized (lockProcessor) {
                if( cameraInitialized )
                    processor = this.processor;
            }
            if (processor != null)
                processor.onDraw(canvas, imageToView);
        }
        long stopTime = System.nanoTime();

        visualizationPending = false;

        double processPeriod;
        double renderPeriod;

        synchronized (lockTiming) {
            periodRender.update((stopTime-startTime)*1e-6);
            renderPeriod = periodRender.getAverage();
            processPeriod = periodProcess.getAverage();
        }
        if( app.preference.showSpeed)
            renderSpeed(canvas, processPeriod, renderPeriod);
    }

    /**
     * Renders how fast the algorithm and rendering is running
     */
    private void renderSpeed(Canvas canvas, double processPeriod, double renderPeriod) {
        // attempt to bring it back to the original origin
        // can't just set to identity because on older phones there
        // will be an offset and getMatrix() doesn't include that info...
        canvas.getMatrix().invert(tempMatrix);
        canvas.concat(tempMatrix);

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
     * @param cancelable
     */
    protected void setProgressMessage(final String message, boolean cancelable) {
        runOnUiThread(() -> {
            if( isFinishing() || isDestroyed() )
                return;

            synchronized ( lockProgress ) {
                if( progressDialog != null ) {
                    // a dialog is already open, change the message
                    progressDialog.setMessage(message);
                    return;
                }
                progressDialog = new ProgressDialog(this);
                if( cancelable ) {
                    progressDialog.setCancelable(true);
                    progressDialog.setOnCancelListener(dialogInterface -> {
                        synchronized ( lockProgress ) {
                            progressDialog = null;
                        }
                        progressCanceled();
                    });
                }
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

    protected void progressCanceled(){}

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

    final float[] pts = new float[2];

    /**
     * Applies the matrix to the specified point. Make sure only ONE thread uses this at any
     * moment.
     */
    public void applyToPoint(Matrix matrix , double x , double y , Point2D_F64 out ) {
        pts[0] = (float)x;
        pts[1] = (float)y;
        matrix.mapPoints(pts);
        out.x = pts[0];
        out.y = pts[1];
    }

    public static void applyToPoint(Matrix matrix , double x , double y , Point2D_F64 out, float[] pts) {
        pts[0] = (float)x;
        pts[1] = (float)y;
        matrix.mapPoints(pts);
        out.x = pts[0];
        out.y = pts[1];
    }

    public CameraPinholeBrown lookupIntrinsics() {
        // look up the camera parameters. If it hasn't been calibrated used what camera2 says
        CameraPinholeBrown intrinsic = app.preference.lookup(cameraWidth,cameraHeight);
        if( intrinsic == null ) {
            intrinsic = new CameraPinholeBrown();
            // Not sure why this happens but gracefully exit if it does
            if( !cameraIntrinsicNominal(intrinsic) ) {
                ACRA.getErrorReporter().handleSilentException(new Exception("cameraIntrinsicNominal() failed!"));
                runOnUiThread(()->{
                    finish(); // leave the activity
                    Toast.makeText(this,"Error: Can't access camera size. Try again.",Toast.LENGTH_LONG).show();
                });
            }
        }
        return intrinsic;
    }

    protected boolean hasGLES20() {
        ActivityManager am = (ActivityManager)
                getSystemService(Context.ACTIVITY_SERVICE);
        ConfigurationInfo info = am.getDeviceConfigurationInfo();
        return info.reqGlEsVersion >= 0x20000;
    }

    /**
     * Display a message using Toast
     */
    protected void toast(String message) {
        runOnUiThread(() -> Toast.makeText(this,message,Toast.LENGTH_LONG).show());
    }

    public boolean isCameraCalibrated() {
        return app.preference.lookup(cameraWidth,cameraHeight) != null;
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
