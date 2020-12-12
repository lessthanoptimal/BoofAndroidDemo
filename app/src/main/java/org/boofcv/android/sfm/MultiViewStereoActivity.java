package org.boofcv.android.sfm;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.text.method.ScrollingMovementMethod;
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
import android.widget.TextView;
import android.widget.Toast;

import org.boofcv.android.DemoCamera2Activity;
import org.boofcv.android.DemoProcessingAbstract;
import org.boofcv.android.R;
import org.boofcv.android.visalize.PointCloudSurfaceView;
import org.ddogleg.struct.DogArray;
import org.ddogleg.struct.DogArray_I8;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

import boofcv.abst.geo.bundle.SceneStructureMetric;
import boofcv.abst.tracker.PointTrack;
import boofcv.alg.mvs.DisparityParameters;
import boofcv.alg.mvs.MultiViewStereoFromKnownSceneStructure;
import boofcv.alg.mvs.StereoPairGraph;
import boofcv.alg.mvs.video.SelectFramesForReconstruction3D;
import boofcv.alg.sfm.structure.GeneratePairwiseImageGraph;
import boofcv.alg.sfm.structure.LookUpSimilarGivenTracks;
import boofcv.alg.sfm.structure.MetricFromUncalibratedPairwiseGraph;
import boofcv.alg.sfm.structure.PairwiseImageGraph;
import boofcv.alg.sfm.structure.RefineMetricWorkingGraph;
import boofcv.alg.sfm.structure.SceneWorkingGraph;
import boofcv.android.ConvertBitmap;
import boofcv.core.image.ConvertImage;
import boofcv.factory.disparity.ConfigDisparityBMBest5;
import boofcv.factory.disparity.FactoryStereoDisparity;
import boofcv.factory.mvs.ConfigSelectFrames3D;
import boofcv.factory.mvs.FactoryMultiViewStereo;
import boofcv.io.image.LookUpImageFilesByIndex;
import boofcv.misc.BoofMiscOps;
import boofcv.struct.image.GrayF32;
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
    private static final int MAX_SELECT = 15;

    Spinner spinnerView;
    Spinner spinnerAlgs;
    Button buttonSave;

    // If image collection should reset and start over
    boolean reset = false;

    Mode mode = Mode.COLLECT_IMAGES;

    // Used to print debug info to text view
    ByteArrayOutputStream byteOutputStream = new ByteArrayOutputStream();
    PrintStream debugStream = new PrintStream(byteOutputStream);
    RedirectPrintToView repeatedRedirectTask = new RedirectPrintToView();
    private Handler mHandler;

    // Used to display the found disparity as a 3D point cloud
    PointCloudSurfaceView cloudView;
    // Displays debugging output from MVS
    TextView stdoutView;
    ViewGroup layoutViews;

    SparseReconstructionThread threadSparse;

    final LookUpSimilarGivenTracks<PointTrack> similar =
            new LookUpSimilarGivenTracks<>(t->t.featureId,(t,p)->p.setTo(t.pixel));
    final DogArray<InterleavedU8> selectedImages = new DogArray<>(InterleavedU8::new);
    SceneWorkingGraph working = null;
    PairwiseImageGraph pairwise = null;
    SceneStructureMetric scene = null;


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

        stdoutView = new TextView(this);
        stdoutView.setBackgroundColor(0xA0000000);
        stdoutView.setMovementMethod(new ScrollingMovementMethod());
        mHandler = new Handler();

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

    private void changeMode(Mode mode) {
        if (this.mode == mode)
            return;

        Mode previousMode = this.mode;
        this.mode = mode;

        runOnUiThread(()->{
            if (previousMode==Mode.SPARSE_RECONSTRUCTION) {
                layoutViews.removeView(stdoutView);
            }

            if (mode==Mode.SPARSE_RECONSTRUCTION) {
                stdoutView.setText("");
                layoutViews.addView(stdoutView,layoutViews.getChildCount(),
                        new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
                // Start the process of redirecting output to this view
                repeatedRedirectTask.run();

                threadSparse = new SparseReconstructionThread();
                threadSparse.start();
            }
        });
    }

    protected class MvsProcessing extends DemoProcessingAbstract<InterleavedU8> {
        final SelectFramesForReconstruction3D<GrayU8> selector;
        final ConfigSelectFrames3D config = new ConfigSelectFrames3D();

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
            if (mode != Mode.COLLECT_IMAGES)
                return;

            ConvertImage.average(color, gray);

            boolean selectedFrame = selector.next(gray);

            lockTrack.lock();
            try {
                // Handle when the user requests that it start over
                if (reset) {
                    reset = false;
                    selectedFrame = true;
                    selector.initialize(gray.width, gray.height);
                    similar.reset();
                    selectedImages.reset();
                }

                // This frame was selected as a key frame. Save results
                if (selectedFrame) {
                    similar.addFrame(gray.width, gray.height, selector.getActiveTracks());
                    selectedImages.grow().setTo(color);
                    if (selectedImages.size>=MAX_SELECT) {
                        changeMode(Mode.SPARSE_RECONSTRUCTION);
                    }
                }

                // Copy into bitmap for visualization
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

    /**
     * Redirects text to the text view
     */
    class RedirectPrintToView implements Runnable {
        @Override
        public void run() {
            if (mode != Mode.SPARSE_RECONSTRUCTION)
                return;

            if (byteOutputStream.size()>0){
                String text = byteOutputStream.toString();
                byteOutputStream.reset();
                runOnUiThread(()-> {
                    stdoutView.append(text);
                    // Automatically scroll to bottom as more text is added
                    final int scrollAmount = stdoutView.getLayout().getLineTop(stdoutView.getLineCount()) - stdoutView.getHeight();
                    stdoutView.scrollTo(0, Math.max(scrollAmount, 0));
                });
            }
            mHandler.postDelayed(this,50);
        }
    }

    class SparseReconstructionThread extends Thread {
        boolean stop = false;
        @Override
        public void run() {
            GeneratePairwiseImageGraph generatePairwise = new GeneratePairwiseImageGraph();
            MetricFromUncalibratedPairwiseGraph metric = new MetricFromUncalibratedPairwiseGraph();

            debugStream.println("Finding similar images");
            similar.computeSimilarRelationships(true,200);

            debugStream.println("Computing Pairwise Graph");
            generatePairwise.setVerbose(debugStream, null);
            generatePairwise.process(similar);

            debugStream.println("Projective to Metric");
            metric.setVerbose(debugStream, null);
            metric.getInitProjective().setVerbose(debugStream, null);
            metric.getExpandMetric().setVerbose(debugStream, null);
            if (!metric.process(similar, generatePairwise.graph)) {
                Toast.makeText(MultiViewStereoActivity.this,"Metric Failed", Toast.LENGTH_LONG).show();
                changeMode(Mode.COLLECT_IMAGES);
            }

            debugStream.println("Bundle Adjustment");
            RefineMetricWorkingGraph refine = new RefineMetricWorkingGraph();
            refine.bundleAdjustment.keepFraction = 0.95;
            refine.bundleAdjustment.getSba().setVerbose(debugStream, null);
            if (!refine.process(similar, metric.workGraph)) {
                Toast.makeText(MultiViewStereoActivity.this,"Bundle Adjustment Failed", Toast.LENGTH_LONG).show();
                changeMode(Mode.COLLECT_IMAGES);
            }

            debugStream.println("----------------------------------------------------------------------------");
            working = metric.workGraph;
            pairwise = generatePairwise.graph;
            scene = refine.bundleAdjustment.getStructure();
            for (PairwiseImageGraph.View pv : pairwise.nodes.toList()) {
                SceneWorkingGraph.View wv = working.lookupView(pv.id);
                if (wv == null)
                    continue;
                int order = working.viewList.indexOf(wv);

                debugStream.printf("view[%2d]='%2s' f=%6.1f k1=%6.3f k2=%6.3f t={%5.1f, %5.1f, %5.1f}\n", order, wv.pview.id,
                        wv.intrinsic.f, wv.intrinsic.k1, wv.intrinsic.k2,
                        wv.world_to_view.T.x, wv.world_to_view.T.y, wv.world_to_view.T.z);
            }
            debugStream.println("Printing view info. Used " + scene.views.size + " / " + pairwise.nodes.size);

            changeMode(Mode.DENSE_STEREO);
        }
    }

    class DenseCloudThread extends Thread {
        @Override
        public void run() {
            ConfigDisparityBMBest5 configDisparity = new ConfigDisparityBMBest5();
            configDisparity.validateRtoL = 1;
            configDisparity.texture = 0.5;
            configDisparity.regionRadiusX = configDisparity.regionRadiusY = 4;
            configDisparity.disparityRange = 120;

            // Looks up images based on their index in the file list
            LookUpImageFilesByIndex imageLookup = null;//new LookUpImageFilesByIndex(example.imageFiles);

            // Create and configure MVS
            //
            // Note that the stereo disparity algorithm used must output a GrayF32 disparity image as much of the code
            // is hard coded to use it. MVS would not work without sub-pixel enabled.
            MultiViewStereoFromKnownSceneStructure mvs = new MultiViewStereoFromKnownSceneStructure<>(imageLookup, ImageType.SB_U8);
            mvs.setStereoDisparity(FactoryStereoDisparity.blockMatchBest5(configDisparity, GrayU8.class, GrayF32.class));
            // Improve stereo by removing small regions, which tends to be noise. Consider adjusting the region size.
            mvs.getComputeFused().setDisparitySmoother(FactoryStereoDisparity.removeSpeckle(null, GrayF32.class));
            // Print out profiling info from multi baseline stereo
            mvs.getComputeFused().setVerboseProfiling(debugStream);

            // Grab intermediate results as they are computed
//            mvs.setListener(new MultiViewStereoFromKnownSceneStructure.Listener<>() {
//                @Override
//                public void handlePairDisparity( String left, String right, GrayU8 rect0, GrayU8 rect1,
//                                                 GrayF32 disparity, GrayU8 mask, DisparityParameters parameters ) {
//                    // Displaying individual stereo pair results can be very useful for debugging, but this isn't done
//                    // because of the amount of information it would show
//                }
//
//                @Override
//                public void handleFusedDisparity( String name,
//                                                  GrayF32 disparity, GrayU8 mask, DisparityParameters parameters ) {
//                    // Display the disparity for each center view
//                    BufferedImage colorized = VisualizeImageData.disparity(disparity, null, parameters.disparityRange, 0);
//                    ShowImages.showWindow(colorized, "Center " + name);
//                }
//            });

            // MVS stereo needs to know which view pairs have enough 3D information to act as a stereo pair and
            // the quality of that 3D information. This is used to guide which views act as "centers" for accumulating
            // 3D information which is then converted into the point cloud.
            //
            // StereoPairGraph contains this information and we will create it from Pairwise and Working graphs.

            StereoPairGraph mvsGraph = new StereoPairGraph();
//            SceneStructureMetric _structure = example.scene;
            // Add a vertex for each view
            BoofMiscOps.forIdx(working.viewList, (i, wv ) -> mvsGraph.addVertex(wv.pview.id, i));
            // Compute the 3D score for each connected view
            BoofMiscOps.forIdx(working.viewList, ( workIdxI, wv ) -> {
                PairwiseImageGraph.View pv = pairwise.mapNodes.get(wv.pview.id);
                pv.connections.forIdx(( j, e ) -> {
                    // Look at the ratio of inliers for a homography and fundamental matrix model
                    PairwiseImageGraph.View po = e.other(pv);
                    double ratio = 1.0 - Math.min(1.0, e.countH/(1.0 + e.countF));
                    if (ratio <= 0.25)
                        return;
                    // There is sufficient 3D information between these two views
                    SceneWorkingGraph.View wvo = working.views.get(po.id);
                    int workIdxO = working.viewList.indexOf(wvo);
                    if (workIdxO <= workIdxI)
                        return;
                    mvsGraph.connect(pv.id, po.id, ratio);
                });
            });

            // Compute the dense 3D point cloud
            mvs.process(scene, mvsGraph);
        }
    }

    enum Mode {
        COLLECT_IMAGES,
        SPARSE_RECONSTRUCTION,
        DENSE_STEREO,
        VIEW_CLOUD
    }
}
