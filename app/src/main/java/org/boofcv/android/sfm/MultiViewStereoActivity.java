package org.boofcv.android.sfm;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.Spinner;
import android.widget.TextView;

import org.boofcv.android.DemoCamera2Activity;
import org.boofcv.android.DemoProcessingAbstract;
import org.boofcv.android.R;
import org.boofcv.android.visalize.PointCloud3D;
import org.boofcv.android.visalize.PointCloud3DRenderer;
import org.boofcv.android.visalize.PointCloudSurfaceView;
import org.ddogleg.struct.DogArray;
import org.ddogleg.struct.DogArray_I8;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.locks.ReentrantLock;

import boofcv.abst.geo.bundle.SceneStructureMetric;
import boofcv.abst.tracker.PointTrack;
import boofcv.alg.cloud.PointCloudReader;
import boofcv.alg.mvs.ColorizeMultiViewStereoResults;
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
import boofcv.android.VisualizeImageData;
import boofcv.core.image.ConvertImage;
import boofcv.core.image.LookUpColorRgbFormats;
import boofcv.factory.disparity.ConfigDisparityBMBest5;
import boofcv.factory.disparity.FactoryStereoDisparity;
import boofcv.factory.mvs.ConfigSelectFrames3D;
import boofcv.factory.mvs.FactoryMultiViewStereo;
import boofcv.io.UtilIO;
import boofcv.io.geo.MultiViewIO;
import boofcv.io.image.LookUpImageFilesByIndex;
import boofcv.io.points.PointCloudIO;
import boofcv.misc.BoofMiscOps;
import boofcv.struct.image.GrayF32;
import boofcv.struct.image.GrayU8;
import boofcv.struct.image.ImageType;
import boofcv.struct.image.InterleavedU8;
import georegression.struct.point.Point2D_F64;
import georegression.struct.point.Point3D_F64;

import static org.boofcv.android.DemoMain.getExternalDirectory;

/**
 * Provides a UI for collecting images for use in Multi View Stereo
 */
public class MultiViewStereoActivity extends DemoCamera2Activity
        implements AdapterView.OnItemSelectedListener, PopupMenu.OnMenuItemClickListener
{
    private static final String TAG = "MVS";
    private static final int MAX_SELECT = 10;

    Spinner spinnerDisplay;
    Button buttonConfigure;
    Button buttonSave;

    // If image collection should reset and start over
    boolean reset = false;

    // What step is MVS on
    Mode mode = Mode.COLLECT_IMAGES;
    // What should be displayed to the user
    Display display = Display.COLLECT;

    // Used to print debug info to text view
    ByteArrayOutputStream byteOutputStream = new ByteArrayOutputStream();
    PrintStream debugStream = new PrintStream(byteOutputStream);
    RedirectPrintToView repeatedRedirectTask = new RedirectPrintToView();
    private Handler mHandler;

    // Used to display the found disparity as a 3D point cloud
    PointCloudSurfaceView cloudView;
    // Displays debugging output from MVS
    TextView debugView;
    ViewGroup layoutViews;

    SparseReconstructionThread threadSparse;

    final LookUpSimilarGivenTracks<PointTrack> similar =
            new LookUpSimilarGivenTracks<>(t->t.featureId,(t,p)->p.setTo(t.pixel));
    final List<String> imageFiles = new ArrayList<>();
    final LookUpImageFilesByIndex imageLookup = new LookUpImageFilesByIndex(imageFiles, (path,image)->{
        ConvertBitmap.bitmapToBoof(BitmapFactory.decodeFile(path),image,null);
    });

    // BEGIN LOCK OWNERSHIP
    final ReentrantLock lockDisplayImage = new ReentrantLock();
    // Which image is being displayed
    int displayImageIdx;
    // Which disparity is being displayed
    int displayDisparityIdx;
    boolean displayImageChanged=false;
    // END LOCK OWNERSHIP

    SceneWorkingGraph working = null;
    PairwiseImageGraph pairwise = null;
    SceneStructureMetric scene = null;

    final ReentrantLock lockCloud = new ReentrantLock();
    Bitmap bitmapDisparity;
    String textDisparity="";
    DogArray_I8 workspaceDisparity = new DogArray_I8();
    DenseCloudThread threadCloud = null;

    // Workspace where the current reconstruction has files saved to
    File workingDir = new File(".");

    public MultiViewStereoActivity() {
        super(Resolution.R640x480);
        super.bitmapMode = BitmapMode.NONE;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LayoutInflater inflater = getLayoutInflater();
        LinearLayout controls = (LinearLayout)inflater.inflate(R.layout.multi_view_stereo_controls,null);

        spinnerDisplay = controls.findViewById(R.id.spinner_view);
        // disable save button until disparity has been computed
        buttonSave = controls.findViewById(R.id.button_save);
        buttonSave.setEnabled(false);

        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(this,
                R.array.mvs_views, android.R.layout.simple_spinner_item);
        // if it doesn't support 3D rendering remove the cloud option
        if( !hasGLES20() ) {
            toast("No GLES20. Can't display point cloud");
            List<CharSequence> list = new ArrayList<>();
            for( int i = 0; i < adapter.getCount()-2; i++ ) {
                list.add(adapter.getItem(i));
            }
            adapter = new ArrayAdapter<>(this,android.R.layout.simple_spinner_item,list);
        }
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerDisplay.setAdapter(adapter);
        spinnerDisplay.setOnItemSelectedListener(this);
        spinnerDisplay.setEnabled(false);

        buttonConfigure = controls.findViewById(R.id.button_configure);

        debugView = new TextView(this);
        debugView.setBackgroundColor(0xA0000000);
        debugView.setMovementMethod(new ScrollingMovementMethod());
        mHandler = new Handler();

        setControls(controls);

        cloudView = new PointCloudSurfaceView(this);
        workingDir = createMvsWorkDirectory();
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
    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
        if (parent== spinnerDisplay) {
            // You should only be able to select an item in the view spinner while viewing results
            if (mode!=Mode.VIEW_RESULTS)
                return;
            changeDisplay(Display.values()[position]);
        }
    }

    @Override
    public void onNothingSelected(AdapterView<?> parent) {

    }

    public void configurePressed( View view ) {
        PopupMenu popup = new PopupMenu(this, view);
        MenuInflater inflater = popup.getMenuInflater();
        inflater.inflate(R.menu.mvs_configure, popup.getMenu());
        popup.setOnMenuItemClickListener(this);
        popup.show();
    }

    public void resetPressed( View view ) {
        reset = true;
    }

    public void savePressed( View view ) {
        reset = true;
    }

    @Override
    public boolean onMenuItemClick(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.max_images:
                return true;
            case R.id.disparity:
                return true;
            default:
                return false;
        }
    }

    private void changeMode(Mode mode) {
        if (this.mode == mode)
            return;

        Mode previousMode = this.mode;
        this.mode = mode;

        Log.i(TAG,"Mode: "+previousMode+" -> "+mode);

        // Do this outside the UI thread. It will delete previous results
        if (mode==Mode.COLLECT_IMAGES) {
            workingDir = createMvsWorkDirectory();
        }

        runOnUiThread(()->{
            if (mode==Mode.COLLECT_IMAGES) {
                // disable since there is a sequence of events that need to be followed now
                spinnerDisplay.setEnabled(false);
                buttonSave.setEnabled(false);
                changeDisplay(Display.COLLECT);
                reset = true;
            } else if (mode==Mode.SPARSE_RECONSTRUCTION) {
                // Start the process of redirecting output to this view
                debugView.setText("");
                changeDisplay(Display.DEBUG);
                repeatedRedirectTask.run();
                threadSparse = new SparseReconstructionThread();
                threadSparse.start();
            } else if (mode==Mode.DENSE_STEREO) {
                bitmapDisparity = null;
                textDisparity = "Multi-Baseline Stereo";
                changeDisplay(Display.DISPARITY);
                threadCloud = new DenseCloudThread();
                threadCloud.start();
            } else if (mode==Mode.VIEW_RESULTS) {
                Log.i(TAG,"Added cloudView");
                // Enable the view spinner so the user can select what should be viewed
                spinnerDisplay.setEnabled(true);
                buttonSave.setEnabled(true);
                changeDisplay(Display.DENSE_CLOUD);
            }
        });
    }

    private void changeDisplay(Display display) {
        if (this.display == display)
            return;

        if (Looper.getMainLooper().getThread() != Thread.currentThread()) {
            throw new RuntimeException("Must be in UI thread");
        }

        Display previous = this.display;
        this.display = display;

        Log.i(TAG,"Display: "+previous+" -> "+display);

        if (previous==Display.DEBUG) {
            layoutViews.removeView(debugView);
        } else if(previous==Display.DENSE_CLOUD) {
            layoutViews.removeView(cloudView);
        }

        if (display==Display.DEBUG) {
            layoutViews.addView(debugView,layoutViews.getChildCount(),
                    new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        } else if (display==Display.DENSE_CLOUD) {
            layoutViews.addView(cloudView,layoutViews.getChildCount(),
                    new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        } else if (display==Display.IMAGES) {
            lockDisplayImage.lock();
            displayImageIdx = 0;
            displayImageChanged = true;
            lockDisplayImage.unlock();
        } else if (display==Display.DISPARITY) {
            lockDisplayImage.lock();
            displayDisparityIdx = 0;
            displayImageChanged = true;
            lockDisplayImage.unlock();
        }

        // Update the spinner so that it correctly indicates which display is being shown
        spinnerDisplay.setSelection(display.ordinal());
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
            if (display == Display.DISPARITY) {
                // Draw status on the bottom of the screen. This avoid conflict with FPS text
                canvas.drawText(textDisparity,10,canvas.getHeight()-40*displayMetrics.density,paintText);
                lockCloud.lock();
                try {
                    if (bitmapDisparity != null)
                        canvas.drawBitmap(bitmapDisparity, 0, 0, null);
                } finally {
                    lockCloud.unlock();
                }
            } else if(display == Display.IMAGES) {
                canvas.concat(imageToView);
                canvas.drawBitmap(bitmap,0,0,null);
            } else if(display == Display.COLLECT){
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
        }

        @Override
        public void process(InterleavedU8 color) {
            // Load display images here since this won't lock the UI thread
            {
                boolean changed;
                int index;
                lockDisplayImage.lock();
                changed = displayImageChanged;
                index = displayImageIdx;
                lockDisplayImage.unlock();
                if (display == Display.IMAGES && changed) {
                    displayImageChanged = false;
                    bitmap = BitmapFactory.decodeFile(imageFiles.get(index));
                }
            }

            // TODO should it shut off the camera when not in use?
            if (mode != Mode.COLLECT_IMAGES)
                return;

            ConvertImage.average(color, gray);

            boolean selectedFrame = selector.next(gray);

            lockTrack.lock();
            try {
                // Handle when the user requests that it start over
                if (reset) {
                    reset = false;
                    selector.initialize(gray.width, gray.height);
                    similar.reset();
                    imageFiles.clear();
                } else {
                    // Copy into bitmap for visualization
                    ConvertBitmap.interleavedToBitmap(color, bitmap, bitmapStorage);
                    selector.lookupKeyFrameTracksInCurrentFrame(trackPixels);

                    // If selected then save the image disk
                    if (selectedFrame) {
                        similar.addFrame(gray.width, gray.height, selector.getActiveTracks());

                        File dest = new File(workingDir,String.format(
                                Locale.getDefault(),"images/image%03d.png",
                                similar.getImageIDs().size()));
                        try {
                            FileOutputStream fos = new FileOutputStream(dest);
                            bitmap.compress(Bitmap.CompressFormat.PNG,90,fos);
                            fos.close();
                            imageFiles.add(dest.getPath());
                        } catch (IOException e) {
                            e.printStackTrace();
                            Log.e(TAG,"Failed to save "+dest.getPath());
                            toast("Failed to save image: "+dest.getPath());
                        }

                        if (imageFiles.size() >= MAX_SELECT) {
                            changeMode(Mode.SPARSE_RECONSTRUCTION);
                            return;
                        }
                    }

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

                    statusText = "Images: " + selector.getSelectedFrames().size;

                    Log.i(TAG, String.format("consider=%s motion=%5.1f 3d=%.1f h=%.1f pairs=%4d keyFrames=%d",
                            selector.isConsidered3D(), selector.getFrameMotion(),
                            error3D, errorH, selector.getPairs().size,
                            selector.getSelectedFrames().size));
                }
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
            if (mode != Mode.SPARSE_RECONSTRUCTION && mode != Mode.DENSE_STEREO)
                return;

            if (byteOutputStream.size()>0){
                String text = byteOutputStream.toString();
                byteOutputStream.reset();
                runOnUiThread(()-> {
                    debugView.append(text);
                    // Automatically scroll to bottom as more text is added
                    final int scrollAmount = debugView.getLayout().getLineTop(debugView.getLineCount()) - debugView.getHeight();
                    debugView.scrollTo(0, Math.max(scrollAmount, 0));
                });
            }
            mHandler.postDelayed(this,50);
        }
    }

    /**
     * Create the working directory for MVS
     */
    public File createMvsWorkDirectory() {
        File savePath = new File(getExternalDirectory(this),"mvs_work/");

        if (savePath.exists()) {
            // If it already exists, it's from an old run and we can delete it
            UtilIO.deleteRecursive(savePath);
        }
        if( !savePath.mkdirs() ) {
            Log.d(TAG,"Failed to create output directory");
            Log.d(TAG,savePath.getAbsolutePath());
            toast("Failed to create output directory");
            return null;
        }

        if (!new File(savePath,"images").mkdir())
            toast("Failed to create images directory");
        if (!new File(savePath,"disparity").mkdir())
            toast("Failed to create disparity directory");
        return savePath;
    }

    class SparseReconstructionThread extends Thread {
        @Override
        public void run() {
            GeneratePairwiseImageGraph generatePairwise = new GeneratePairwiseImageGraph();
            MetricFromUncalibratedPairwiseGraph metric = new MetricFromUncalibratedPairwiseGraph();

            debugStream.println("Finding similar images");
            Log.i(TAG,"Similar Images");
            similar.computeSimilarRelationships(true,200, 6);
            // TODO Save these to disk

            debugStream.println("Computing Pairwise Graph");
            Log.i(TAG,"Pairwise Graph");
            generatePairwise.setVerbose(debugStream, null);
            generatePairwise.process(similar);
            MultiViewIO.save(generatePairwise.graph,new File(workingDir,"pairwise.yaml").getPath());

            debugStream.println("Projective to Metric");
            Log.i(TAG,"Projective to Metric");
            metric.setVerbose(debugStream, null);
            metric.getInitProjective().setVerbose(debugStream, null);
            metric.getExpandMetric().setVerbose(debugStream, null);
            if (!metric.process(similar, generatePairwise.graph)) {
                toast("Metric Failed");
                changeMode(Mode.COLLECT_IMAGES);
                return;
            }
            MultiViewIO.save(metric.workGraph,new File(workingDir,"working.yaml").getPath());

            debugStream.println("Bundle Adjustment");
            Log.i(TAG,"Bundle Adjustment");
            RefineMetricWorkingGraph refine = new RefineMetricWorkingGraph();
            refine.bundleAdjustment.keepFraction = 0.95;
            refine.bundleAdjustment.getSba().setVerbose(debugStream, null);
            if (!refine.process(similar, metric.workGraph)) {
                toast("Bundle Adjustment Failed");
                changeMode(Mode.COLLECT_IMAGES);
                return;
            }
            scene = refine.bundleAdjustment.getStructure();
            MultiViewIO.save(scene,new File(workingDir,"scene.yaml").getPath());

            debugStream.println("----------------------------------------------------------------------------");
            working = metric.workGraph;
            pairwise = generatePairwise.graph;

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
            Log.i(TAG,"Finished sparse reconstruction");

            changeMode(Mode.DENSE_STEREO);
        }
    }

    class DenseCloudThread extends Thread {
        @Override
        public void run() {
            ConfigDisparityBMBest5 configDisparity = new ConfigDisparityBMBest5();
            configDisparity.validateRtoL = 1;
            configDisparity.texture = 0.15;
            configDisparity.regionRadiusX = configDisparity.regionRadiusY = 4;
            configDisparity.disparityRange = 120;
            configDisparity.disparityMin = 5;

            // Create and configure MVS
            //
            // Note that the stereo disparity algorithm used must output a GrayF32 disparity image as much of the code
            // is hard coded to use it. MVS would not work without sub-pixel enabled.
            MultiViewStereoFromKnownSceneStructure<GrayU8> mvs = new MultiViewStereoFromKnownSceneStructure<>(imageLookup, ImageType.SB_U8);
            mvs.setStereoDisparity(FactoryStereoDisparity.blockMatchBest5(configDisparity, GrayU8.class, GrayF32.class));
            // Improve stereo by removing small regions, which tends to be noise. Consider adjusting the region size.
            mvs.getComputeFused().setDisparitySmoother(FactoryStereoDisparity.removeSpeckle(null, GrayF32.class));
            // Print out profiling info from multi baseline stereo
            mvs.getComputeFused().setVerboseProfiling(debugStream);

            // This allows us to display intermediate results to the user while they wait for all of this to finish
            mvs.setListener(new MultiViewStereoFromKnownSceneStructure.Listener<GrayU8>() {
                @Override
                public void handlePairDisparity( String left, String right, GrayU8 rect0, GrayU8 rect1,
                                                 GrayF32 disparity, GrayU8 mask, DisparityParameters parameters ) {}

                @Override
                public void handleFusedDisparity( String name,
                                                  GrayF32 disparity, GrayU8 mask, DisparityParameters parameters ) {
                    lockCloud.lock();
                    try {
                        textDisparity = "Image "+name;
                        bitmapDisparity = ConvertBitmap.checkDeclare(disparity, bitmapDisparity);
                        VisualizeImageData.disparity(disparity,parameters.disparityRange,0,
                                bitmapDisparity, workspaceDisparity);

                        // TODO save to disk and create a list for later display
                    } finally {
                        lockCloud.unlock();
                    }
                }
            });

            // MVS stereo needs to know which view pairs have enough 3D information to act as a stereo pair and
            // the quality of that 3D information. This is used to guide which views act as "centers" for accumulating
            // 3D information which is then converted into the point cloud.
            //
            // StereoPairGraph contains this information and we will create it from Pairwise and Working graphs.

            Log.i(TAG,"Creating MVS Graph");
            StereoPairGraph mvsGraph = new StereoPairGraph();
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
            Log.i(TAG,"Compute MVS Cloud");
            try {
                mvs.process(scene, mvsGraph);
            } catch( RuntimeException e ) {
                e.printStackTrace();
                toast("Dense reconstruction failed");
                changeMode(Mode.COLLECT_IMAGES);
                return;
            }
            // Free up some memory
            working = null;
            pairwise = null;

            Log.i(TAG,"Creating Cloud for Display. Size="+mvs.getCloud().size());
            textDisparity = "Cloud Size "+mvs.getCloud().size();
            // Colorize the cloud to make it easier to view. This is done by projecting points back into the
            // first view they were seen in and reading the color
            PointCloud3D cloudShow = cloudView.getRenderer().getCloud();
            cloudShow.clearCloud();
            cloudShow.declarePoints(mvs.getCloud().size());
            List<Point3D_F64> cloudMvs = mvs.getCloud();
            ColorizeMultiViewStereoResults<InterleavedU8> colorizeMvs =
                    new ColorizeMultiViewStereoResults<>(new LookUpColorRgbFormats.IL_U8(), imageLookup);

            double maxZ = 0.0;
            for (int i = 0; i < cloudMvs.size(); i++) {
                maxZ = Math.max(maxZ, cloudMvs.get(i).z);
            }

            double scale = -0.1*PointCloud3DRenderer.MAX_RANGE/maxZ;

            colorizeMvs.processMvsCloud(scene, mvs,
                    (idx, r, g, b) -> {
                        Point3D_F64 p = cloudMvs.get(idx);
                        cloudShow.setPoint(idx, scale*p.x, scale*p.y, scale*p.z, (r << 16) | (g << 8) | b);
                    });

            // Save the dense cloud to disk
            try {
                OutputStream output = new FileOutputStream(new File(workingDir, "dense_cloud.ply"));
                PointCloudIO.save3D(PointCloudIO.Format.PLY,
                        PointCloudReader.wrap3FRGB(cloudShow.points.data, cloudShow.colors.data, 0, cloudMvs.size()),
                        true, output);
                output.close();
            } catch (IOException ignore){}

            // Show the results
            runOnUiThread(cloudShow::finalizePoints);

            // Let the user view everything
            changeMode(Mode.VIEW_RESULTS);
        }
    }

    enum Mode {
        COLLECT_IMAGES,
        SPARSE_RECONSTRUCTION,
        DENSE_STEREO,
        VIEW_RESULTS
    }

    enum Display {
        IMAGES,
        DEBUG,
        DISPARITY,
        SPARSE_CLOUD,
        DENSE_CLOUD,
        COLLECT
    }
}
