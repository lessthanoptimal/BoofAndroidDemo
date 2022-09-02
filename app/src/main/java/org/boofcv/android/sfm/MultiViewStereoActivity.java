package org.boofcv.android.sfm;

import static org.boofcv.android.DemoMain.getExternalDirectory;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.GestureDetector;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.apache.commons.io.FilenameUtils;
import org.boofcv.android.DemoCamera2Activity;
import org.boofcv.android.DemoProcessingAbstract;
import org.boofcv.android.R;
import org.boofcv.android.visalize.PointCloud3D;
import org.boofcv.android.visalize.PointCloud3DRenderer;
import org.boofcv.android.visalize.PointCloudSurfaceView;
import org.ddogleg.DDoglegConcurrency;
import org.ddogleg.struct.DogArray;
import org.ddogleg.struct.DogArray_I32;
import org.ddogleg.struct.DogArray_I8;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.locks.ReentrantLock;

import boofcv.BoofVerbose;
import boofcv.abst.geo.bundle.SceneStructureCommon;
import boofcv.abst.geo.bundle.SceneStructureMetric;
import boofcv.abst.tracker.PointTrack;
import boofcv.alg.cloud.PointCloudReader;
import boofcv.alg.geo.bundle.cameras.BundlePinholeSimplified;
import boofcv.alg.geo.rectify.DisparityParameters;
import boofcv.alg.mvs.MultiViewStereoFromKnownSceneStructure;
import boofcv.alg.similar.SimilarImagesFromTracks;
import boofcv.alg.structure.GeneratePairwiseImageGraph;
import boofcv.alg.structure.LookUpCameraInfo;
import boofcv.alg.structure.MetricFromUncalibratedPairwiseGraph;
import boofcv.alg.structure.PairwiseImageGraph;
import boofcv.alg.structure.RefineMetricWorkingGraph;
import boofcv.alg.structure.SceneWorkingGraph;
import boofcv.alg.structure.SparseSceneToDenseCloud;
import boofcv.alg.video.SelectFramesForReconstruction3D;
import boofcv.android.ConvertBitmap;
import boofcv.android.VisualizeImageData;
import boofcv.concurrency.BoofConcurrency;
import boofcv.core.image.ConvertImage;
import boofcv.factory.disparity.ConfigDisparity;
import boofcv.factory.structure.ConfigEpipolarScore3D;
import boofcv.factory.structure.ConfigGeneratePairwiseImageGraph;
import boofcv.factory.structure.ConfigSelectFrames3D;
import boofcv.factory.structure.ConfigSparseToDenseCloud;
import boofcv.factory.structure.FactorySceneReconstruction;
import boofcv.io.UtilIO;
import boofcv.io.geo.MultiViewIO;
import boofcv.io.image.LookUpImageFilesByIndex;
import boofcv.io.points.PointCloudIO;
import boofcv.misc.BoofMiscOps;
import boofcv.struct.Point3dRgbI_F64;
import boofcv.struct.image.GrayF32;
import boofcv.struct.image.GrayU8;
import boofcv.struct.image.ImageGray;
import boofcv.struct.image.ImageType;
import boofcv.struct.image.InterleavedU8;
import georegression.geometry.ConvertRotation3D_F64;
import georegression.struct.point.Point2D_F64;
import georegression.struct.point.Point3D_F64;
import georegression.struct.so.Rodrigues_F64;
import gnu.trove.map.TIntObjectMap;
import gnu.trove.map.hash.TIntObjectHashMap;

/**
 * Provides a UI for collecting images for use in Multi View Stereo
 */
public class MultiViewStereoActivity extends DemoCamera2Activity
        implements AdapterView.OnItemSelectedListener, PopupMenu.OnMenuItemClickListener {
    // TODO Add help button that takes you to a website/youtube video
    // TODO Clean up this class
    // TODO Save similar image info so that tracks are known

    private static final String TAG = "MVS";
    private static final int MAX_SELECT = 30;

    Spinner spinnerDisplay;
    Button buttonConfigure;
    Button buttonSave;
    Button buttonOpen;
    Button buttonReset;
    StereoDisparityDialog dialogDisparity = new StereoDisparityDialog();

    // If image collection should reset and start over
    boolean reset = false;

    // React to user gestures, e.g. change images being viewed
    private GestureDetector mDetector;

    // What step is MVS on
    Mode mode = Mode.COLLECT_IMAGES;
    // What should be displayed to the user
    Display display = Display.COLLECT;
    // Did the user tap the screen?
    boolean screenTap;

    // Used to print debug info to text view
    ByteArrayOutputStream byteOutputStream = new ByteArrayOutputStream();
    PrintStream debugStream = new PrintStream(byteOutputStream);
    RedirectPrintToView repeatedRedirectTask = new RedirectPrintToView();
    private Handler mHandler;

    // Used to display the found disparity as a 3D point cloud
    PointCloudSurfaceView denseCloudView;
    PointCloudSurfaceView sparseCloudView;
    // Displays debugging output from MVS
    TextView debugView;
    ViewGroup layoutViews;

    SparseReconstructionThread threadSparse;

    final SimilarImagesFromTracks<PointTrack> similar =
            new SimilarImagesFromTracks<>(t -> t.featureId, (t, p) -> p.setTo(t.pixel));
    LookUpCameraInfo dbCams = new LookUpCameraInfo();
    final List<String> imageFiles = new ArrayList<>();
    final LookUpImageFilesByIndex imageLookup = new LookUpImageFilesByIndex(imageFiles,
            (path, image) -> ConvertBitmap.bitmapToBoof(BitmapFactory.decodeFile(path), image, null));

    //----------------- Text in processing window
    // Displays current status
    String statusText = "";
    // Warning text to tell the user they are doing something wrong
    String warningText = "";
    // At what time will the warning text be zeroed
    long warningTimeOut;

    //----------------- BEGIN LOCK OWNERSHIP
    final ReentrantLock lockDisplayImage = new ReentrantLock();
    // Color or disparity images
    boolean displayImageRgb;
    // Which image is being displayed
    int displayImageIdx;
    final List<String> displayImagePaths = new ArrayList<>();
    boolean displayImageChanged = false;
    //----------------- END LOCK OWNERSHIP

    //----------------- BEGIN LOCK
    final ReentrantLock lockCloud = new ReentrantLock();
    final List<String> disparityPaths = new ArrayList<>();
    Bitmap bitmapInverseDepth;
    String textDisparity = "";
    DogArray_I8 workspaceDisparity = new DogArray_I8();
    DenseCloudThread threadCloud = null;
    //----------------- END LOCK

    // Workspace where the current reconstruction has files saved to
    File workingDir = new File(".");

    public MultiViewStereoActivity() {
        super(Resolution.R640x480);
        super.bitmapMode = BitmapMode.NONE;

        // Default to this since it's faster
        dialogDisparity.selectedType = ConfigDisparity.Approach.BLOCK_MATCH_5;
        dialogDisparity.listenerOK = () -> {
            // If it already has results, recompute with the new disparity
            if (mode != Mode.VIEW_RESULTS)
                return;
            Log.i(TAG, "Recomputing disparity after user settings change");
            changeMode(Mode.DENSE_STEREO);
        };
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.i(TAG, "ENTER onCreate()");

        LayoutInflater inflater = getLayoutInflater();
        LinearLayout controls = (LinearLayout) inflater.inflate(R.layout.multi_view_stereo_controls, null);

        spinnerDisplay = controls.findViewById(R.id.spinner_view);
        // disable save button until disparity has been computed
        buttonSave = controls.findViewById(R.id.button_save);
        buttonSave.setEnabled(false);

        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(this,
                R.array.mvs_views, android.R.layout.simple_spinner_item);
        // if it doesn't support 3D rendering remove the cloud option
        if (!hasGLES20()) {
            toast("No GLES20. Can't display point cloud");
            List<CharSequence> list = new ArrayList<>();
            for (int i = 0; i < adapter.getCount() - 2; i++) {
                list.add(adapter.getItem(i));
            }
            adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, list);
        }
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerDisplay.setAdapter(adapter);
        spinnerDisplay.setOnItemSelectedListener(this);
        spinnerDisplay.setEnabled(false);

        buttonConfigure = controls.findViewById(R.id.button_configure);
        buttonReset = controls.findViewById(R.id.button_reset);
        buttonOpen = controls.findViewById(R.id.button_load);

        debugView = new TextView(this);
        debugView.setBackgroundColor(0xA0000000);
        debugView.setMovementMethod(new ScrollingMovementMethod());
        debugView.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL));
        mHandler = new Handler();

        setControls(controls);

        mDetector = new GestureDetector(this, new MyGestureDetector(displayView));
        displayView.setOnTouchListener((v, event) -> {
            mDetector.onTouchEvent(event);
            return true;
        });

        denseCloudView = new PointCloudSurfaceView(this);
        sparseCloudView = new PointCloudSurfaceView(this);
        workingDir = createMvsWorkDirectory();

        // Display a helpful message the first time the collection window is shown
        // This really isn't a warning, but it's in the correct location.
        warningText = "Touch to Finish";
        warningTimeOut = System.currentTimeMillis() + 2_000;

        // This will make SBA concurrent
        DDoglegConcurrency.USE_CONCURRENT = BoofConcurrency.USE_CONCURRENT;
    }

    @Override
    protected void startCamera(@NonNull ViewGroup layout, @Nullable TextureView view) {
        super.startCamera(layout, view);
        this.layoutViews = layout;
    }

    @Override
    public void createNewProcessor() {
        setProcessing(new MvsProcessing());
    }

    @Override
    protected void onStop() {
        super.onStop();
        Log.i(TAG, "ENTER onStop()");

        // If there are threads running request that they stop. Otherwise they will keep
        // on running and there will be undefined behavior.
        try {
            Thread sparse = this.threadSparse;
            Thread cloud = this.threadCloud;
            if (sparse != null) sparse.interrupt();
            if (cloud != null) cloud.interrupt();
        } catch (RuntimeException e) {
            Log.d(TAG, "Failed to interrupt threads. " + e.getMessage());
        }
        // onCreate() Should see if these threads are running and wait to be safe otherwise
        //            two processes could modify the work directory
    }

    @Override
    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
        if (parent == spinnerDisplay) {
            // You should only be able to select an item in the view spinner while viewing results
            if (mode != Mode.VIEW_RESULTS)
                return;
            changeDisplay(Display.values()[position]);
        }
    }

    @Override
    public void onNothingSelected(AdapterView<?> parent) {
    }

    public void configurePressed(View view) {
        // TODO After the BoofCV Activity has been updated switch this over to AndroidX API
        dialogDisparity.show(getFragmentManager(), "Disparity Dialog");
//        PopupMenu popup = new PopupMenu(this, view);
//        MenuInflater inflater = popup.getMenuInflater();
//        inflater.inflate(R.menu.mvs_configure, popup.getMenu());
//        popup.setOnMenuItemClickListener(this);
//        popup.show();
    }

    public void resetPressed(View view) {
        if (mode == Mode.COLLECT_IMAGES) {
            reset = true;
        } else if (mode == Mode.VIEW_RESULTS) {
            switch (display) {
                case DENSE_CLOUD:
                    runOnUiThread(() -> denseCloudView.getRenderer().setCameraToHome());
                    break;
                case SPARSE_CLOUD:
                    runOnUiThread(() -> sparseCloudView.getRenderer().setCameraToHome());
                    break;
            }
        }
    }

    public void savePressed(View view) {
        long modifiedTime = workingDir.lastModified();
        File savePath = new File(getExternalDirectory(this), "mvs/" + modifiedTime);

        // This is odd, just zap it to be safe
        if (savePath.exists()) {
            UtilIO.deleteRecursive(savePath);
        }
        try {
            UtilIO.copyRecursive(workingDir, savePath);
        } catch (RuntimeException e) {
            e.printStackTrace();
            toast("Save failed");
        }

        // Prevent the user from spamming and saving multiple times
        runOnUiThread(() -> buttonSave.setEnabled(false));
    }

    public void loadPressed(View view) {
        selectDirectoryDialog("mvs", this::load);
    }

    protected void load(File directory) {
        Log.i(TAG, "Loading " + directory.getPath());

        UtilIO.deleteRecursive(workingDir);
        UtilIO.copyRecursive(directory, workingDir);

        loadWorkDirectory(true);
    }

    protected void loadWorkDirectory(boolean alertOnError) {
        Log.i(TAG, "Loading Work Directory. alert=" + alertOnError);
        loadFileNames(new File(workingDir, "images"), imageFiles);
        if (imageFiles.isEmpty())
            return;
        loadFileNames(new File(workingDir, "disparity"), disparityPaths);
        if (disparityPaths.isEmpty())
            return;
        if (!loadPointCloud(denseCloudView, "dense_cloud.ply", alertOnError))
            return;
        if (!loadPointCloud(sparseCloudView, "sparse_cloud.ply", alertOnError))
            return;
        changeMode(Mode.VIEW_RESULTS);

        // Disable save since it's already saved
        runOnUiThread(() -> {
            denseCloudView.getRenderer().setCameraToHome();
            sparseCloudView.getRenderer().setCameraToHome();
            buttonSave.setEnabled(false);
        });
    }

    private boolean loadPointCloud(PointCloudSurfaceView view, String fileName, boolean alertOnError) {
        try {
            DogArray<Point3dRgbI_F64> cloud = new DogArray<>(Point3dRgbI_F64::new);
            FileInputStream inputStream = new FileInputStream(new File(workingDir, fileName));
            PointCloudIO.load3DRgb64F(PointCloudIO.Format.PLY, inputStream, cloud);
            PointCloud3D cloudShow = view.getRenderer().getCloud();
            cloudShow.clearCloud();
            cloudShow.declarePoints(cloud.size());
            for (int i = 0; i < cloud.size; i++) {
                Point3dRgbI_F64 p = cloud.get(i);
                cloudShow.setPoint(i, p.x, p.y, p.z, p.rgb);
            }
            runOnUiThread(cloudShow::finalizePoints);
            return true;
        } catch (IOException e) {
            e.printStackTrace();
            toast("Failed to load " + fileName);
        }
        return false;
    }

    private void loadFileNames(File directory, List<String> paths) {
        paths.clear();
        List<String> files = UtilIO.listAll(directory.getPath());
        if (files.isEmpty())
            return;

        Collections.sort(files);
        for (int i = 0; i < files.size(); i++) {
            File f = new File(files.get(i));
            if (!f.isFile())
                continue;
            paths.add(f.getPath());
        }
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

        Log.i(TAG, "Mode: " + previousMode + " -> " + mode);

        // Do this outside the UI thread. It will delete previous results
        if (mode == Mode.COLLECT_IMAGES) {
            workingDir = createMvsWorkDirectory();
        }

        runOnUiThread(() -> {
            if (mode == Mode.COLLECT_IMAGES) {
                // disable since there is a sequence of events that need to be followed now
                spinnerDisplay.setEnabled(false);
                buttonSave.setEnabled(false);
                buttonOpen.setEnabled(true);
                buttonConfigure.setEnabled(true);
                changeDisplay(Display.COLLECT);
                reset = true;
            } else if (mode == Mode.SPARSE_RECONSTRUCTION) {
                // Start the process of redirecting output to this view
                buttonSave.setEnabled(false);
                buttonOpen.setEnabled(false);
                buttonConfigure.setEnabled(false);
                debugView.setText("");
                changeDisplay(Display.DEBUG);
                repeatedRedirectTask.run();
                threadSparse = new SparseReconstructionThread();
                threadSparse.start();
            } else if (mode == Mode.DENSE_STEREO) {
                buttonSave.setEnabled(false);
                buttonOpen.setEnabled(false);
                buttonConfigure.setEnabled(false);
                disparityPaths.clear();
                bitmapInverseDepth = null;
                textDisparity = "Multi-Baseline Stereo";
                changeDisplay(Display.DISPARITY);
                threadCloud = new DenseCloudThread();
                threadCloud.start();
            } else if (mode == Mode.VIEW_RESULTS) {
                buttonSave.setEnabled(true);
                buttonOpen.setEnabled(true);
                buttonConfigure.setEnabled(true);
                Log.i(TAG, "Added cloudView");
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
        this.screenTap = false;

        Log.i(TAG, "Display: " + previous + " -> " + display);

        if (previous == Display.DEBUG) {
            layoutViews.removeView(debugView);
        } else if (previous == Display.DENSE_CLOUD) {
            layoutViews.removeView(denseCloudView);
        } else if (previous == Display.SPARSE_CLOUD) {
            layoutViews.removeView(sparseCloudView);
        }

        if (display == Display.DEBUG) {
            buttonReset.setEnabled(false);
            layoutViews.addView(debugView, layoutViews.getChildCount(),
                    new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        } else if (display == Display.DENSE_CLOUD) {
            buttonReset.setEnabled(true);
            layoutViews.addView(denseCloudView, layoutViews.getChildCount(),
                    new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        } else if (display == Display.SPARSE_CLOUD) {
            buttonReset.setEnabled(true);
            layoutViews.addView(sparseCloudView, layoutViews.getChildCount(),
                    new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        } else if (display == Display.IMAGES) {
            buttonReset.setEnabled(false);
            lockDisplayImage.lock();
            try {
                displayImageRgb = true;
                displayImageIdx = 0;
                displayImageChanged = true;
                displayImagePaths.clear();
                displayImagePaths.addAll(imageFiles);
            } finally {
                lockDisplayImage.unlock();
            }
        } else if (display == Display.DISPARITY) {
            buttonReset.setEnabled(false);
            lockDisplayImage.lock();
            try {
                displayImageRgb = false;
                displayImageIdx = 0;
                displayImageChanged = true;
                displayImagePaths.clear();
                displayImagePaths.addAll(disparityPaths);
            } finally {
                lockDisplayImage.unlock();
            }
        } else if (display == Display.COLLECT) {
            buttonReset.setEnabled(true);
        }

        // Update the spinner so that it correctly indicates which display is being shown
        if (display != Display.COLLECT)
            spinnerDisplay.setSelection(display.ordinal());
    }

    /**
     * Use guestures to interact with the GUI
     */
    protected class MyGestureDetector extends GestureDetector.SimpleOnGestureListener {
        View v;

        public MyGestureDetector(View v) {
            this.v = v;
        }

        @Override
        public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
            // Change the image being viewed
            if (display == Display.IMAGES || display == Display.DISPARITY) {
                if (Math.abs(velocityX) < Math.abs(velocityY) * 3)
                    return false;

                lockDisplayImage.lock();
                try {
                    if (velocityX > 0)
                        displayImageIdx--;
                    else
                        displayImageIdx++;
                    final int N = displayImagePaths.size();
                    if (displayImageIdx < 0)
                        displayImageIdx = N - 1;
                    else if (displayImageIdx >= N)
                        displayImageIdx = 0;
                    displayImageChanged = true;
                } finally {
                    lockDisplayImage.unlock();
                }

                return true;
            }
            return false;
        }

        @Override
        public boolean onSingleTapConfirmed(MotionEvent e) {
            screenTap = true;
            if (display == Display.IMAGES || display == Display.DISPARITY) {
                lockDisplayImage.lock();
                try {
                    displayImageIdx++;
                    if (displayImageIdx >= displayImagePaths.size())
                        displayImageIdx = 0;
                    displayImageChanged = true;
                } finally {
                    lockDisplayImage.unlock();
                }
                return true;
            }
            return false;
        }
    }

    protected class MvsProcessing extends DemoProcessingAbstract<InterleavedU8> {
        final SelectFramesForReconstruction3D<GrayU8> selector;
        final ConfigSelectFrames3D config = new ConfigSelectFrames3D();

        final GrayU8 gray = new GrayU8(1, 1);
        Bitmap bitmap;
        final DogArray_I8 bitmapStorage = new DogArray_I8();

        Paint paintDot = new Paint();
        float circleRadius;

        private final Paint paintText = new Paint();
        private final Paint paintWarning = new Paint();
        private final Paint paintWarningBG = new Paint();

        //------------------ OWNED BY LOCK
        final ReentrantLock lockTrack = new ReentrantLock();
        final DogArray<Point2D_F64> trackPixels = new DogArray<>(Point2D_F64::new);
        boolean savedFrame = false;

        public MvsProcessing() {
            super(InterleavedU8.class, 3);

            config.minTranslation.setRelative(0.07, 0);
            config.maxTranslation.setRelative(0.20, 20);
            config.scorer3D.ransacF.iterations = 20; // make everything run faster
            config.skipEvidenceRatio = 0.0; // turn off this check to speed things up
            config.motionInlierPx = 5.0;
            config.thresholdQuick = 0.1;
            config.historyLength = 0; // turn off so that it must select the current frame
            selector = FactorySceneReconstruction.frameSelector3D(config, ImageType.SB_U8);
//            selector.setVerbose(System.out, BoofMiscOps.hashSet(BoofVerbose.RECURSIVE));

            paintDot.setStyle(Paint.Style.FILL);
        }

        @Override
        public void initialize(int imageWidth, int imageHeight, int sensorOrientation) {
            selector.initialize(imageWidth, imageHeight);
            similar.initialize(gray.width, gray.height);
            circleRadius = 2 * cameraToDisplayDensity;
            bitmap = Bitmap.createBitmap(imageWidth, imageHeight, Bitmap.Config.ARGB_8888);

            paintText.setStrokeWidth(3 * displayMetrics.density);
            paintText.setTextSize(24 * displayMetrics.density);
            paintText.setTextAlign(Paint.Align.LEFT);
            paintText.setARGB(0xFF, 0xFF, 0xB0, 0);
            paintText.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.BOLD));

            paintWarning.setStrokeWidth(3 * displayMetrics.density);
            paintWarning.setTextSize(30 * displayMetrics.density);
            paintWarning.setARGB(0xFF, 0xFF, 0, 0);
            paintWarning.setTextAlign(Paint.Align.CENTER);

            paintWarningBG.setStyle(Paint.Style.FILL);
            paintWarningBG.setARGB(0xAA, 0, 0, 0);
            paintWarningBG.setStrokeWidth(3 * displayMetrics.density);
        }

        @Override
        public void onDraw(Canvas canvas, Matrix imageToView) {
            canvas.save();
            if (display == Display.IMAGES || display == Display.DISPARITY) {
                canvas.drawText(statusText, 10, canvas.getHeight() - 40 * displayMetrics.density, paintText);
                canvas.concat(imageToView);
                if (bitmap != null) {
                    canvas.drawBitmap(bitmap, 0, 0, null);
                }
            } else if (display == Display.COLLECT) {
                // Draw status on the bottom of the screen. This avoid conflict with FPS text
                canvas.drawText(statusText, 10, canvas.getHeight() - 40 * displayMetrics.density, paintText);

                if (lockTrack.isLocked())
                    return;
                lockTrack.lock();
                try {
                    int red = savedFrame ? 0xFF : 0x00;
                    paintDot.setColor((0xFF << 24) | (red << 16));
                    canvas.concat(imageToView);
                    canvas.drawBitmap(bitmap, 0, 0, null);
                    trackPixels.forEach(p ->
                            canvas.drawCircle((float) p.x, (float) p.y, circleRadius * 3.5f, paintDot));
                } finally {
                    lockTrack.unlock();
                }
            }

            // Draw the warning text on top of the image or anything else that's rendered
            canvas.restore();
            if (!warningText.isEmpty()) {
                // draw a background behind the text to make it easier to see
                Rect textBounds = new Rect();
                paintWarning.getTextBounds(warningText, 0, warningText.length(), textBounds);

                // Draw area center
                float cx = canvas.getWidth() / 2.0f;
                float cy = canvas.getHeight() / 2.0f;

                // Figure out the starting point for the background box
                float bgWidth = textBounds.width() * 1.2f; // make it a bit bigger
                float bgHeight = textBounds.height() * 1.6f; // hmm scaling isn't linear. Is this device dependent?
                float bgX0 = cx + textBounds.left - bgWidth / 2.0f;
                float bgY0 = cy + (textBounds.top + textBounds.bottom) / 2.0f - bgHeight / 2.0f;

                // Draw the box and text
                canvas.drawRect(bgX0, bgY0, bgX0 + bgWidth, bgY0 + bgHeight, paintWarningBG);
                canvas.drawText(warningText, cx, cy, paintWarning);

                // See if the text has been displayed for too long
                if (warningTimeOut < System.currentTimeMillis())
                    warningText = "";
            }
        }

        @Override
        public void process(InterleavedU8 color) {
            // Load display images here since this won't lock the UI thread
            {
                boolean changed;
                String path = null;
                lockDisplayImage.lock();
                try {
                    changed = displayImageChanged;
                    if (displayImagePaths.size() > displayImageIdx) {
                        path = displayImagePaths.get(displayImageIdx);
                    }
                } finally {
                    lockDisplayImage.unlock();
                }

                if ((display == Display.IMAGES || display == Display.DISPARITY) && changed && path != null) {
                    displayImageChanged = false;
                    bitmap = BitmapFactory.decodeFile(path);
                    if (displayImageRgb)
                        statusText = "Image " + displayImageIdx;
                    else {
                        statusText = "Disparity " + FilenameUtils.getBaseName(path).split("_")[1];
                    }
                } else if (display == Display.DISPARITY && path == null) {
                    statusText = textDisparity;
                    bitmap = null;
                } else if (display == Display.IMAGES && path == null) {
                    statusText = "No Images";
                    bitmap = null;
                }
            }

            // TODO should it shut off the camera when not in use?
            if (mode != Mode.COLLECT_IMAGES)
                return;

            ConvertImage.average(color, gray);

            boolean selectedFrame = selector.next(gray);

            // If it was force to select a frame and didn't even consider that it could be 3D
            // The user is most likely rotating the camera. The first frame is never 3D
            if (selectedFrame && !selector.isConsidered3D() &&
                    selector.getSelectedFrames().size > 1) {
                warningText = "";
                switch (selector.getCause()) {
                    case EXCESSIVE_MOTION: warningText = "Translate! Do Not Rotate!"; break;
                    case TRACKING_FAILURE: warningText = "Tracking Problems"; break;
                }
                if (!warningText.isEmpty()) {
                    warningTimeOut = System.currentTimeMillis() + 2_000;
//                    System.out.println(warningText);
                }
            }

            lockTrack.lock();
            try {
                // Handle when the user requests that it start over
                if (reset) {
                    warningText = "";
                    reset = false;
                    selector.initialize(gray.width, gray.height);
                    similar.initialize(gray.width, gray.height);
                    dbCams = new LookUpCameraInfo();
                    imageFiles.clear();
                } else {
                    // Copy into bitmap for visualization
                    ConvertBitmap.interleavedToBitmap(color, bitmap, bitmapStorage);
                    selector.lookupKeyFrameTracksInCurrentFrame(trackPixels);

                    // The user tapped the screen, which is a request to go onto reconstruction
                    if (screenTap) {
                        changeMode(Mode.SPARSE_RECONSTRUCTION);
                        return;
                    }
                    // If selected then save the image disk
                    if (selectedFrame) {
                        similar.processFrame(selector.getActiveTracks(), imageFiles.size());

                        // Assume the intrinsic parameters stay constant throughout the sequence
                        if (imageFiles.size() == 0) {
                            dbCams.addCameraCanonical(bitmap.getWidth(), bitmap.getHeight(), 60.0);
                        }
                        dbCams.addView(imageFiles.size() + "", 0);

                        File dest = new File(workingDir, String.format(
                                Locale.getDefault(), "images/image%03d.png",
                                imageFiles.size()));
                        try {
                            FileOutputStream fos = new FileOutputStream(dest);
                            bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos);
                            fos.close();
                            imageFiles.add(dest.getPath());
                        } catch (IOException e) {
                            e.printStackTrace();
                            Log.e(TAG, "Failed to save " + dest.getPath());
                            toast("Failed to save image: " + dest.getPath());
                            return;
                        }

                        if (imageFiles.size() >= MAX_SELECT) {
                            changeMode(Mode.SPARSE_RECONSTRUCTION);
                            return;
                        }
                    } else if (!selector.isSufficientFeaturePairs()) {
                        warningText = "Lost Track";
                        warningTimeOut = System.currentTimeMillis() + 2_000;
                    }

                    savedFrame = selectedFrame;

                    statusText = "Images: " + selector.getSelectedFrames().size;

//                    Log.i(TAG, String.format("consider=%s motion=%5.1f 3d=%.1f h=%.1f pairs=%4d keyFrames=%d",
//                            selector.isConsidered3D(), selector.getFrameMotion(),
//                            error3D, errorH, selector.getPairs().size,
//                            selector.getSelectedFrames().size));
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

            if (byteOutputStream.size() > 0) {
                String text = byteOutputStream.toString();
                byteOutputStream.reset();
                runOnUiThread(() -> {
                    debugView.append(text);
                    // Automatically scroll to bottom as more text is added
                    final int scrollAmount = debugView.getLayout().getLineTop(debugView.getLineCount()) - debugView.getHeight();
                    debugView.scrollTo(0, Math.max(scrollAmount, 0));
                });
            }
            mHandler.postDelayed(this, 50);
        }
    }

    /**
     * Create the working directory for MVS
     */
    public File createMvsWorkDirectory() {
        File savePath = new File(getExternalDirectory(this), "mvs_work/");

        if (savePath.exists()) {
            // If it already exists, it's from an old run and we can delete it
            UtilIO.deleteRecursive(savePath);
        }
        if (!savePath.mkdirs()) {
            Log.d(TAG, "Failed to create output directory");
            Log.d(TAG, savePath.getAbsolutePath());
            toast("Failed to create output directory");
            return null;
        }

        if (!new File(savePath, "images").mkdir())
            toast("Failed to create images directory");
        if (!new File(savePath, "disparity").mkdir())
            toast("Failed to create disparity directory");
        return savePath;
    }

    class SparseReconstructionThread extends Thread {

        @Override
        public void run() {
            try {
                ConfigGeneratePairwiseImageGraph configPairwise = new ConfigGeneratePairwiseImageGraph();
                configPairwise.score.type = ConfigEpipolarScore3D.Type.FUNDAMENTAL_ROTATION;
                // Speed things up by sacrificing some quality
                configPairwise.score.ransacF.iterations = 100; // KLT tracks tend to be high quality
                GeneratePairwiseImageGraph generatePairwise = FactorySceneReconstruction.generatePairwise(configPairwise);
                MetricFromUncalibratedPairwiseGraph metric = new MetricFromUncalibratedPairwiseGraph();

                debugStream.println("Finding similar images");
                Log.i(TAG, "Similar Images");
                // TODO Save these to disk

                if (Thread.interrupted()) return;

                debugStream.println("Computing Pairwise Graph");
                Log.i(TAG, "\nPairwise Graph");
                generatePairwise.setVerbose(debugStream, null);
                generatePairwise.process(similar, dbCams);
                MultiViewIO.save(generatePairwise.graph, new File(workingDir, "pairwise.yaml").getPath());

                if (Thread.interrupted()) return;

                debugStream.println("\nProjective to Metric");
                Log.i(TAG, "Projective to Metric");
                metric.setVerbose(debugStream, BoofMiscOps.hashSet(BoofVerbose.RECURSIVE));
                if (!metric.process(similar, dbCams, generatePairwise.graph)) {
                    Log.d(TAG, "Metric reconstruction failed");
                    toast("Metric Failed");
                    changeMode(Mode.COLLECT_IMAGES);
                    return;
                }

                // There can be multiple solutions. Just go with the largest one
                SceneWorkingGraph workGraph = metric.getLargestScene();
                MultiViewIO.save(workGraph, new File(workingDir, "working.yaml").getPath());

                if (Thread.interrupted()) return;

                debugStream.println("\nBundle Adjustment");
                Log.i(TAG, "Bundle Adjustment");
                RefineMetricWorkingGraph refine = new RefineMetricWorkingGraph();
                refine.metricSba.keepFraction = 0.95;
                refine.metricSba.getSba().setVerbose(debugStream, null);
                if (!refine.process(similar, workGraph)) {
                    Log.d(TAG, "Bundle Adjustment Failed");
                    toast("Bundle Adjustment Failed");
                    changeMode(Mode.COLLECT_IMAGES);
                    return;
                }
                if (Thread.interrupted()) return;

                SceneStructureMetric scene = refine.metricSba.getStructure();
                MultiViewIO.save(scene, new File(workingDir, "scene.yaml").getPath());

                debugStream.println("----------------------------------------------------------------------------");
                PairwiseImageGraph pairwise = generatePairwise.graph;

                Rodrigues_F64 rod = new Rodrigues_F64();
                for (PairwiseImageGraph.View pv : pairwise.nodes.toList()) {
                    SceneWorkingGraph.View wv = workGraph.lookupView(pv.id);
                    if (wv == null)
                        continue;
                    BundlePinholeSimplified intrinsic = workGraph.getViewCamera(wv).intrinsic;
                    int order = workGraph.listViews.indexOf(wv);
                    ConvertRotation3D_F64.matrixToRodrigues(wv.world_to_view.R, rod);

                    debugStream.printf("view[%2d]='%2s' f=%6.1f k1=%6.3f k2=%6.3f t={%5.1f, %5.1f, %5.1f} r=%5.2f\n", order, wv.pview.id,
                            intrinsic.f, intrinsic.k1, intrinsic.k2,
                            wv.world_to_view.T.x, wv.world_to_view.T.y, wv.world_to_view.T.z, rod.theta);
                }
                debugStream.println("    Views Used " + scene.views.size + " / " + pairwise.nodes.size);
                debugStream.println("----------------------------------------------------------------------------");
                Log.i(TAG, "Finished sparse reconstruction");

                if (Thread.interrupted()) return;
                changeMode(Mode.DENSE_STEREO);
            } catch (Exception e) {
                e.printStackTrace();
                toast("Reconstruction failed. " + e.getMessage());
                changeMode(Mode.COLLECT_IMAGES);
            }
        }
    }

    class DenseCloudThread<T extends ImageGray<T>> extends Thread {
        @Override
        public void run() {
            // Create and configure MVS
            ConfigSparseToDenseCloud configDense = new ConfigSparseToDenseCloud();
            configDense.disparity.setTo(dialogDisparity.createDisparityConfig());
            // Attempt to speed things up by considering fewer images
            configDense.mvs.maximumCenterOverlap = 0.7;
            configDense.mvs.maxCombinePairs = 5;

            SparseSceneToDenseCloud<GrayU8> sparseToDense =
                    FactorySceneReconstruction.sparseSceneToDenseCloud(configDense, ImageType.SB_U8);
            sparseToDense.getMultiViewStereo().setVerbose(debugStream,
                    BoofMiscOps.hashSet(BoofVerbose.RECURSIVE, BoofVerbose.RUNTIME));

            MultiViewStereoFromKnownSceneStructure<GrayU8> mvs = sparseToDense.getMultiViewStereo();

            // Improve stereo by removing small regions, which tends to be noise. Consider adjusting the region size.
            if (dialogDisparity.filterType != StereoDisparityDialog.FilterType.NONE)
                sparseToDense.getMultiViewStereo().getComputeFused().setDisparitySmoother(dialogDisparity.createSmoother());
            // Print out profiling info from multi baseline stereo
            mvs.getComputeFused().setVerboseProfiling(debugStream);

            // This allows us to display intermediate results to the user while they wait for all of this to finish
            mvs.setListener(new MultiViewStereoFromKnownSceneStructure.Listener<GrayU8>() {
                @Override
                public void handlePairDisparity(String left, String right, GrayU8 rectLeft,
                                                GrayU8 rectRight, GrayF32 disparity, DisparityParameters parameters) {
                }

                @Override
                public void handleFused(String name, GrayF32 inverseDepth) {
                    lockCloud.lock();
                    try {
                        bitmapInverseDepth = ConvertBitmap.checkDeclare(inverseDepth, bitmapInverseDepth);
                        VisualizeImageData.grayMagnitude(inverseDepth, 0,
                                bitmapInverseDepth, workspaceDisparity);
                    } finally {
                        lockCloud.unlock();
                    }
                    // at this point disparity is read only so this is safe and won't block the UI
                    File file = new File(workingDir, String.format("disparity/visualized_%s.png", name));
                    Log.i(TAG, "Saving " + file.getPath());
                    disparityPaths.add(file.getPath());
                    try {
                        bitmapInverseDepth.compress(Bitmap.CompressFormat.PNG, 100, new FileOutputStream(file));
                    } catch (IOException e) {
                        e.printStackTrace();
                    }

                    // Tell it that the image to display has changed
                    lockDisplayImage.lock();
                    try {
                        displayImagePaths.clear();
                        displayImagePaths.addAll(disparityPaths);
                        displayImageIdx = disparityPaths.size() - 1;
                        displayImageChanged = true;
                    } finally {
                        lockDisplayImage.unlock();
                    }
                }
            });

            // MVS stereo needs to know which view pairs have enough 3D information to act as a stereo pair and
            // the quality of that 3D information. This is used to guide which views act as "centers" for accumulating
            // 3D information which is then converted into the point cloud.
            //
            // StereoPairGraph contains this information and we will create it from Pairwise and Working graphs.
            if (Thread.interrupted()) return;

            Log.i(TAG, "Loading Graphs");
            final PairwiseImageGraph pairwise = MultiViewIO.load(
                    new File(workingDir, "pairwise.yaml").getPath(), (PairwiseImageGraph) null);
            final SceneWorkingGraph working = MultiViewIO.load(
                    new File(workingDir, "working.yaml").getPath(), pairwise, null);
            final SceneStructureMetric scene = MultiViewIO.load(
                    new File(workingDir, "scene.yaml").getPath(), (SceneStructureMetric) null);
            if (Thread.interrupted()) return;

            Log.i(TAG, "Creating MVS Graph");
            TIntObjectMap<String> viewToId = new TIntObjectHashMap<>();
            BoofMiscOps.forIdx(working.listViews, ( workIdxI, wv ) -> viewToId.put(wv.index, wv.pview.id));
            try {
                if (!sparseToDense.process(scene, viewToId, imageLookup))
                    throw new RuntimeException("Dense reconstruction failed!");
            } catch (RuntimeException e) {
                if (Thread.interrupted()) return;
                e.printStackTrace();
                toast("Dense reconstruction failed");
                changeMode(Mode.COLLECT_IMAGES);
                return;
            }

            Log.i(TAG, "Creating Cloud for Display. Size=" + mvs.getCloud().size());
            textDisparity = "Cloud Size " + mvs.getCloud().size();

            if (Thread.interrupted()) return;
            updateDenseCloud(sparseToDense, scene);
            updateSparseCloud(scene);
            if (Thread.interrupted()) return;
            // Let the user view everything
            changeMode(Mode.VIEW_RESULTS);
        }
    }

    private void updateDenseCloud(SparseSceneToDenseCloud<GrayU8> sparseToDense,
                                  SceneStructureMetric scene) {
        // Colorize the cloud to make it easier to view. This is done by projecting points back into the
        // first view they were seen in and reading the color
        PointCloud3D cloudShow = denseCloudView.getRenderer().getCloud();
        cloudShow.clearCloud();
        cloudShow.declarePoints(sparseToDense.getCloud().size());
        List<Point3D_F64> cloudMvs = sparseToDense.getCloud();
        DogArray_I32 colorsRgb = sparseToDense.getColorRgb();

        double minZ = Double.MAX_VALUE;
        double maxZ = 0.0;

        for (int i = 0; i < cloudMvs.size(); i++) {
            minZ = Math.min(minZ, cloudMvs.get(i).z);
            maxZ = Math.max(maxZ, cloudMvs.get(i).z);
        }
        double scale = -0.05 * PointCloud3DRenderer.MAX_RANGE / (0.1 + minZ);
        Log.i(TAG, "dense cloud range " + minZ + " " + maxZ);

        for (int i = 0; i < cloudMvs.size(); i++) {
            Point3D_F64 p = cloudMvs.get(i);
            int rgb = colorsRgb.get(i);
            cloudShow.setPoint(i, scale * p.x, scale * p.y, scale * p.z, rgb);
        }

        // Save the dense cloud to disk
        try {
            OutputStream output = new FileOutputStream(new File(workingDir, "dense_cloud.ply"));
            PointCloudIO.save3D(PointCloudIO.Format.PLY,
                    PointCloudReader.wrap3FRGB(cloudShow.points.data, cloudShow.colors.data, 0, cloudMvs.size()),
                    true, output);
            output.close();
        } catch (IOException ignore) {}

        // Show the results
        runOnUiThread(cloudShow::finalizePoints);
    }

    private void updateSparseCloud(SceneStructureMetric scene) {
        // Colorize the cloud to make it easier to view. This is done by projecting points back into the
        // first view they were seen in and reading the color
        PointCloud3D cloudShow = sparseCloudView.getRenderer().getCloud();
        cloudShow.clearCloud();
        cloudShow.declarePoints(scene.points.size());

        double minZ = Double.MAX_VALUE;
        double maxZ = 0.0;

        for (int i = 0; i < scene.points.size(); i++) {
            SceneStructureCommon.Point p = scene.points.get(i);
            double z = p.coordinate[2];
            double w = scene.isHomogenous() ? p.coordinate[3] : 1.0;
            z /= w;
            minZ = Math.min(minZ, z);
            maxZ = Math.max(maxZ, z);
        }
        double scale = -0.05 * PointCloud3DRenderer.MAX_RANGE / (0.1 + minZ);
        Log.i(TAG, "sparse cloud range " + minZ + " " + maxZ);

        for (int i = 0; i < scene.points.size; i++) {
            SceneStructureCommon.Point p = scene.points.get(i);

            double x = p.coordinate[0];
            double y = p.coordinate[1];
            double z = p.coordinate[2];
            double w = scene.isHomogenous() ? p.coordinate[3] : 1.0;

            // give it some colors to keep things interesting
            int r = ((int) (z * 255.0)) % 0xFF;
            int g = ((int) (x * 255.0) + 150) % 0xFF;
            int b = 0x200;

            cloudShow.setPoint(i, scale * x / w, scale * y / w, scale * z / w, (r << 16) | (g << 8) | b);
        }

        // Save the dense cloud to disk
        try {
            var output = new FileOutputStream(new File(workingDir, "sparse_cloud.ply"));
            PointCloudIO.save3D(PointCloudIO.Format.PLY,
                    PointCloudReader.wrap3FRGB(cloudShow.points.data, cloudShow.colors.data, 0, scene.points.size()),
                    true, output);
            output.close();
        } catch (IOException ignore) {
        }

        // Show the results
        runOnUiThread(cloudShow::finalizePoints);
    }

    enum Mode {
        COLLECT_IMAGES,
        SPARSE_RECONSTRUCTION,
        DENSE_STEREO,
        VIEW_RESULTS
    }

    enum Display {
        DEBUG,
        IMAGES,
        DISPARITY,
        SPARSE_CLOUD,
        DENSE_CLOUD,
        COLLECT
    }
}
