package org.boofcv.android.sfm;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Spinner;

import org.boofcv.android.DemoCamera2Activity;
import org.boofcv.android.DemoProcessingAbstract;
import org.boofcv.android.R;
import org.boofcv.android.visalize.PointCloudSurfaceView;
import org.ddogleg.struct.DogArray;
import org.ddogleg.struct.DogArray_I8;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

import boofcv.alg.mvs.video.SelectFramesForReconstruction3D;
import boofcv.android.ConvertBitmap;
import boofcv.core.image.ConvertImage;
import boofcv.factory.mvs.ConfigSelectFrames3D;
import boofcv.factory.mvs.FactoryMultiViewStereo;
import boofcv.struct.image.GrayU8;
import boofcv.struct.image.ImageType;
import boofcv.struct.image.InterleavedU8;
import georegression.struct.point.Point2D_F64;

/**
 * Provides a UI for collecting images for use in Multi View Stereo
 */
public class MultiViewStereoActivity extends DemoCamera2Activity
        implements AdapterView.OnItemSelectedListener
{
    private static String TAG = "MVS";
    private static final int MAX_SELECT = 10;

    Spinner spinnerView;
    Spinner spinnerAlgs;
    Button buttonSave;

    // If image collection should reset and start over
    boolean reset = false;

    // Used to display the found disparity as a 3D point cloud
    PointCloudSurfaceView cloudView;
    ViewGroup layoutViews;

    public MultiViewStereoActivity() {
        super(Resolution.R640x480);
        super.bitmapMode = BitmapMode.NONE;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LayoutInflater inflater = getLayoutInflater();
        LinearLayout controls = (LinearLayout)inflater.inflate(R.layout.multi_view_stereo_controls,null);

        spinnerView = controls.findViewById(R.id.spinner_view);
        // disable save button until disparity has been computed
        buttonSave = controls.findViewById(R.id.button_save);
        buttonSave.setEnabled(false);

        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(this,
                R.array.disparity_views, android.R.layout.simple_spinner_item);
        // if it doesn't support 3D rendering remove the cloud option
        if( !hasGLES20() ) {
            List<CharSequence> list = new ArrayList<>();
            for( int i = 0; i < adapter.getCount()-1; i++ ) {
                list.add(adapter.getItem(i));
            }
            adapter = new ArrayAdapter<>(this,android.R.layout.simple_spinner_item,list);
        }
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerView.setAdapter(adapter);
        spinnerView.setOnItemSelectedListener(this);

        spinnerAlgs = controls.findViewById(R.id.spinner_algs);
        adapter = ArrayAdapter.createFromResource(this,
                R.array.disparity_algs, android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerAlgs.setAdapter(adapter);
        spinnerAlgs.setOnItemSelectedListener(this);

        setControls(controls);
    }

    @Override
    protected void startCamera(@NonNull ViewGroup layout, @Nullable TextureView view ) {
        super.startCamera(layout,view);
        this.layoutViews = layout;
    }

    @Override
    public void createNewProcessor() {
        setProcessing(new MvsProcessing());
    }

    @Override
    protected void onResume() {
        super.onResume();
//        changeView(DisparityActivity.DView.ASSOCIATION,false);
    }

    @Override
    public void onPointerCaptureChanged(boolean hasCapture) {

    }

    @Override
    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {

    }

    @Override
    public void onNothingSelected(AdapterView<?> parent) {

    }

    public void resetPressed( View view ) {
        reset = true;
    }

    protected class MvsProcessing extends DemoProcessingAbstract<InterleavedU8> {
        final SelectFramesForReconstruction3D<GrayU8> selector;
        final ConfigSelectFrames3D config = new ConfigSelectFrames3D();

        final DogArray<GrayU8> frames = new DogArray<>(GrayU8::new);

        final GrayU8 gray = new GrayU8(1,1);
        Bitmap bitmap;
        final DogArray_I8 bitmapStorage = new DogArray_I8();

        Paint paintDot = new Paint();
        float circleRadius;

        private Paint paintText = new Paint();

        // Displays current status
        String statusText = "";

        //------------------ OWNED BY LOCK
        final ReentrantLock lockTrack = new ReentrantLock();
        final DogArray<Point2D_F64> trackPixels = new DogArray<>(Point2D_F64::new);
        double fraction3D = 0.0;

        public MvsProcessing() {
            super(InterleavedU8.class,3);

            config.minTranslation.setRelative(0.05,0);
            config.maxTranslation.setRelative(0.15,20);
            config.robustIterations = 20; // make everything run faster
            config.skipEvidenceRatio = 0.0; // turn off this check to speed things up
            config.motionInlierPx = 5.0;
            config.threshold3D = 0.5;
            config.thresholdQuick = 0.1;
            config.historyLength = 0; // turn off so that it must select the current frame
            selector = FactoryMultiViewStereo.frameSelector3D(config, ImageType.SB_U8);

            paintDot.setStyle(Paint.Style.FILL);
        }

        @Override
        public void initialize(int imageWidth, int imageHeight, int sensorOrientation) {
            selector.initialize(imageWidth, imageHeight);
            circleRadius = 2*cameraToDisplayDensity;
            bitmap = Bitmap.createBitmap(imageWidth, imageHeight, Bitmap.Config.ARGB_8888);

            paintText.setStrokeWidth(3*displayMetrics.density);
            paintText.setTextSize(24*displayMetrics.density);
            paintText.setTextAlign(Paint.Align.LEFT);
            paintText.setARGB(0xFF,0xFF,0xB0,0);
            paintText.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.BOLD));
        }

        @Override
        public void onDraw(Canvas canvas, Matrix imageToView) {

            // Draw status on the bottom of the screen. This avoid conflict with FPS text
            canvas.drawText(statusText,10,canvas.getHeight()-40*displayMetrics.density,paintText);

            if (lockTrack.isLocked())
                return;
            lockTrack.lock();
            try {
                int red = (int)(0xFF*(fraction3D));
                paintDot.setColor((0xFF << 24) | (red <<16));
                canvas.concat(imageToView);
                canvas.drawBitmap(bitmap,0,0,null);
                trackPixels.forEach(p->
                        canvas.drawCircle((float) p.x, (float) p.y, circleRadius*3.5f, paintDot));
            } finally {
                lockTrack.unlock();
            }
        }

        @Override
        public void process(InterleavedU8 color) {
            ConvertImage.average(color, gray);

            selector.next(gray);

            lockTrack.lock();
            try {
                // Handle when the user requests that it start over
                if (reset) {
                    reset = false;
                    selector.initialize(gray.width, gray.height);
                }
                ConvertBitmap.interleavedToBitmap(color,bitmap,bitmapStorage);
                selector.lookupKeyFrameTracksInCurrentFrame(trackPixels);

                // For visually the 3D quality, use a function which will smoothly increase in
                // value and isn't excessively sensitive to small values
                fraction3D = 0.0;
                // Sqrt below because it the fit score is going to be pixel error squared
                double error3D = Math.sqrt(selector.getFitScore3D());
                double errorH = Math.sqrt(selector.getFitScoreH());
                if (selector.isConsidered3D()) {
                    fraction3D = (1.0 + error3D) / (1.0 + errorH);
                    fraction3D = Math.max(0.0, 1.0 - fraction3D / config.threshold3D);
                }

                statusText = "Images: "+selector.getSelectedFrames().size;

                Log.i(TAG,String.format("consider=%s motion=%5.1f 3d=%.1f h=%.1f pairs=%4d keyFrames=%d",
                        selector.isConsidered3D(), selector.getFrameMotion(),
                        error3D,  errorH, selector.getPairs().size,
                        selector.getSelectedFrames().size));
            } finally {
                lockTrack.unlock();
            }
        }
    }
}
