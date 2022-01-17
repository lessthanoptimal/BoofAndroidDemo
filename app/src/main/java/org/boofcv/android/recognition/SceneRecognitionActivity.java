package org.boofcv.android.recognition;

import static org.boofcv.android.DemoMain.getExternalDirectory;

import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.LinearLayout;

import net.lingala.zip4j.ZipFile;

import org.boofcv.android.DemoCamera2Activity;
import org.boofcv.android.DemoProcessingAbstract;
import org.boofcv.android.R;
import org.ddogleg.struct.DogArray;
import org.ddogleg.struct.DogArray_I8;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.Locale;

import boofcv.abst.scene.SceneRecognition;
import boofcv.abst.scene.WrapFeatureToSceneRecognition;
import boofcv.android.ConvertBitmap;
import boofcv.core.image.ConvertImage;
import boofcv.io.UtilIO;
import boofcv.io.recognition.RecognitionIO;
import boofcv.misc.BoofMiscOps;
import boofcv.struct.image.GrayU8;
import boofcv.struct.image.ImageType;
import boofcv.struct.image.InterleavedU8;

/**
 * Demonstration for scene recognition. A continuous preview of the camera is shown to the user
 * who can select different views to save to the image database (DB). Then after at least
 * one image has been added it will show the best matching image from the DB to the current
 * view along with its score and ID.
 */
public class SceneRecognitionActivity extends DemoCamera2Activity
        implements AdapterView.OnItemSelectedListener {
    public final static String TAG = "SceneRecogntion";
    public final static String DATA_DIRECTORY = "scene_recognition";
    public final static String MODEL = "model";
    public final static String IMAGES = "images";

    public final static String MODEL_ADDRESS = "http://boofcv.org/notwiki/largefiles/scene_recognition_default38_inria_holidays.zip";

    final Object lockRecognizer = new Object();
    SceneRecognition<GrayU8> recognizer;
    //------------- END Lock

    Status status;
    // only modify on UI thread
    private DownloadRecognitionModel download;

    Button buttonAdd;
    Button buttonSave;
    Button buttonClearDB;
    Button buttonHelp;

    // the user has requested that a new image be added
    boolean requestAddImage;

    public SceneRecognitionActivity() {
        super(Resolution.R640x480);
        super.bitmapMode = BitmapMode.NONE;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LayoutInflater inflater = getLayoutInflater();
        LinearLayout controls = (LinearLayout) inflater.inflate(R.layout.scene_recognition_controls, null);

        buttonAdd = controls.findViewById(R.id.button_add);
        buttonSave = controls.findViewById(R.id.button_save);
        buttonClearDB = controls.findViewById(R.id.button_clear_db);
        buttonHelp = controls.findViewById(R.id.button_help);

        setControls(controls);

        activateControls(false);

        // Load/Download the model
        runOnUiThread(() -> {
            download = new DownloadRecognitionModel();
            download.execute();
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
    }

    @Override
    protected void onDestroy() {
        abortDownload();
        super.onDestroy();
    }

    @Override
    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {

    }

    @Override
    public void onNothingSelected(AdapterView<?> parent) {

    }

    @Override
    public void createNewProcessor() {
        setProcessing(new RecognitionProcessing());
    }

    public void addPressed(View view) {
        requestAddImage = true;
    }

    public void helpPressed(View view) {
        Intent intent = new Intent(this, SceneRecognitionHelpActivity.class);
        startActivity(intent);
    }

    /**
     * Stop processing, delete
     *
     * @param view
     */
    public void clearDbPressed(View view) {
        setStatus(Status.CLEARING);

        // TODO do this the proper android way
        new Thread() {
            @Override
            public void run() {
                synchronized (lockRecognizer) {
                    // save the model without the image DB
                    recognizer.clearDatabase();
                }
                saveRecognizer();
                deleteImages();
                setStatus(Status.RUNNING);
            }
        }.start();
    }

    /**
     * Stop processing, delete
     *
     * @param view
     */
    public void saveDataBase(View view) {
        setStatus(Status.SAVING);

        // TODO do this the proper android way
        new Thread() {
            @Override
            public void run() {
                saveRecognizer();
                setStatus(Status.RUNNING);
            }
        }.start();
    }

    public void saveRecognizer() {
        synchronized (lockRecognizer) {
            File baseDir = new File(getExternalDirectory(SceneRecognitionActivity.this), DATA_DIRECTORY);
            RecognitionIO.saveFeatureToScene((WrapFeatureToSceneRecognition) recognizer, new File(baseDir, MODEL));
        }
    }

    protected void activateControls(boolean enabled) {
        runOnUiThread(() -> {
            buttonAdd.setEnabled(enabled);
            buttonSave.setEnabled(enabled);
            buttonHelp.setEnabled(enabled);
            buttonClearDB.setEnabled(enabled);
        });
    }

    private void abortDownload() {
        runOnUiThread(() -> {
            if (download != null) {
                download.stopRequested = true;
                download.dismissDialog();
            }
        });
    }

    private void deleteImages() {
        File baseDir = new File(getExternalDirectory(SceneRecognitionActivity.this), DATA_DIRECTORY);
        File imageDir = new File(baseDir, IMAGES);
        if (imageDir.exists())
            UtilIO.deleteRecursive(imageDir);
        if (!new File(baseDir, IMAGES).mkdirs()) {
            Log.e(TAG, "Failed to create image directory");
        }
    }

    protected class RecognitionProcessing extends DemoProcessingAbstract<InterleavedU8> {

        final Object lockGUI = new Object();
        Bitmap bitmapPreview;
        Bitmap bitmapMatch;
        public DogArray_I8 storage = new DogArray_I8();
        //----------------------- END LOCK

        GrayU8 query = new GrayU8(1, 1);

        // Recent query results. inteded to reduce number of times an image is loaded
        DogArray<QueryCache> cache = new DogArray<>(QueryCache::new);
        // Score of the best fit image in DB
        double bestFitError;

        Paint paint = new Paint();
        Rect bounds = new Rect();

        DogArray<SceneRecognition.Match> matches = new DogArray<>(SceneRecognition.Match::new);

        // transform between image and output
        public Matrix renderToScreen = new Matrix();

        public RecognitionProcessing() {
            super(InterleavedU8.class, 3);
        }

        @Override
        public void initialize(int width, int height, int sensorOrientation) {
            bitmapPreview = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);

            paint.setColor(Color.RED);
            paint.setTextSize(40 * displayMetrics.density);
        }

        @Override
        public void onDraw(Canvas canvas, Matrix imageToView) {
            if (status != Status.RUNNING) {
                String text = "Status = " + status;
                int textLength = (int) paint.measureText(text);
                canvas.drawText(text, (canvas.getWidth() - textLength) / 2, canvas.getHeight() / 2, paint);
                return;
            }

            int drawWidth = (int) (canvas.getWidth() * 0.47);
            int drawHeight = canvas.getHeight();

            {
                double scale = Math.min(
                        drawWidth / (double) bitmapPreview.getWidth(),
                        drawHeight / (double) bitmapPreview.getHeight());
                renderToScreen.reset();
                renderToScreen.postScale((float) scale, (float) scale);
            }

            canvas.concat(renderToScreen);

            synchronized (lockGUI) {
                canvas.drawBitmap(bitmapPreview, 0, 0, null);
            }

            Bitmap bitmapMatch = this.bitmapMatch;
            if (bitmapMatch == null)
                return;

            double scale = Math.min(
                    drawWidth / (double) bitmapMatch.getWidth(),
                    drawHeight / (double) bitmapMatch.getHeight());
            renderToScreen.reset();
            renderToScreen.postScale((float) scale, (float) scale);

            // Render the error so you can see how good it thinks the fit is
            synchronized (lockGUI) {
                int offsetX = (int) (bitmapPreview.getWidth() * 1.03);
                canvas.drawBitmap(bitmapMatch, offsetX, 0, null);
                String text = String.format(Locale.getDefault(), "%4.2f", bestFitError);
                paint.getTextBounds(text, 0, text.length(), bounds);
                canvas.drawText(text, offsetX, 10 + bounds.height(), paint);
            }
        }

        @Override
        public void process(InterleavedU8 input) {
            synchronized (lockGUI) {
                ConvertBitmap.boofToBitmap(input, bitmapPreview, storage);
            }

            if (status != Status.RUNNING) {
                return;
            }

            ConvertImage.average(input, query);

            // Add the image to the database if requested
            if (requestAddImage) {
                requestAddImage = false;
                String name = System.currentTimeMillis() + "";
                // Add it to the data base
                synchronized (lockRecognizer) {
                    recognizer.addImage(name, query);
                }

                // Save the image to disk
                File destinationHome = new File(getExternalDirectory(SceneRecognitionActivity.this), DATA_DIRECTORY);
                File imageDir = new File(destinationHome, IMAGES);
                File imagePath = new File(imageDir, name + ".jpg");
                try {
                    bitmapPreview.compress(Bitmap.CompressFormat.JPEG, 85, new FileOutputStream(imagePath));
                } catch (IOException e) {
                    Log.i(TAG, "Failed to save image to DB: " + imagePath);
                }
                return;
            }

            // Find the best match
            findBestMatch();
        }

        private void findBestMatch() {
            synchronized (lockRecognizer) {
                try {
                    recognizer.query(query, (m) -> true, 1, matches);
                } catch (RuntimeException e) {
                    e.printStackTrace();
                    Log.e(TAG, "Query failed!");
                    setStatus(Status.ERROR);
                }
            }
            if (matches.isEmpty())
                return;

            String id = matches.get(0).id;
            QueryCache cached = null;
            for (int i = 0; i < cache.size; i++) {
                QueryCache c = cache.get(i);
                if (c.name.equals(id)) {
                    cached = c;
                    cache.swap(cache.size - 1, i);
                    break;
                }
            }

            if (cached == null) {
                // Remove the least recently used
                if (cache.size >= 5)
                    cache.remove(0);

                File destinationHome = new File(getExternalDirectory(SceneRecognitionActivity.this), DATA_DIRECTORY);
                File imageDir = new File(destinationHome, IMAGES);
                cached = cache.grow();
                cached.name = id;
                cached.bitmap = BitmapFactory.decodeFile(new File(imageDir, id + ".jpg").getPath());
                if (cached.bitmap == null) {
                    Log.e(TAG, "Failed to decode " + id);
                    cache.removeTail();
                    return;
                }
            }

            bestFitError = matches.get(0).error;
            bitmapMatch = cached.bitmap;
        }
    }

    /**
     * Background Async Task to download file
     */
    class DownloadRecognitionModel extends AsyncTask<String, String, String>
            implements DialogInterface.OnCancelListener {
        private final String TAG = "DownloadRecognition";
        private ProgressDialog pDialog;
        private boolean stopRequested = false;

        /**
         * Before starting background thread Show Progress Bar Dialog
         */
        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            pDialog = new ProgressDialog(SceneRecognitionActivity.this);
            runOnUiThread(() -> {
                pDialog.setMessage("Initializing");
                pDialog.setIndeterminate(false);
                pDialog.setMax(100);
                pDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
                pDialog.setCancelable(true);
                pDialog.setOnCancelListener(this);
                pDialog.show();
            });
        }

        /**
         * Downloading file in background thread
         */
        @Override
        protected String doInBackground(String... f_url) {
            Thread.currentThread().setName("DownloadModel");
            File destinationHome, fileZip, modelPath;

            // Output stream
            destinationHome = new File(getExternalDirectory(SceneRecognitionActivity.this), DATA_DIRECTORY);
            fileZip = new File(destinationHome, "scene_recognition_model.zip");
            modelPath = new File(destinationHome, MODEL);

            try {
                // see if it can load the model
                if (attemptToLoadModel(modelPath))
                    return null;

                // Delete the file if it already exists
                if (modelPath.exists()) {
                    Log.i(TAG, "  Deleting existing model directory: '" + modelPath + "'");
                    UtilIO.deleteRecursive(modelPath);
                }

                // download the file
                runOnUiThread(() -> pDialog.setMessage("Downloading Model"));
                downloadModelFile(fileZip, destinationHome, MODEL_ADDRESS);
            } catch (IOException e) {
                Log.e(TAG, "Error: " + e.getMessage());
                failAndCleanUp(fileZip, modelPath, e);
                return null;
            }

            if (stopRequested) {
                Log.i(TAG, "Download stop requested. Cleaning up. ");
                if (fileZip.exists() && !fileZip.delete()) {
                    Log.e("Error: ", "Failed to delete " + fileZip.getName());
                }
                setStatus(SceneRecognitionActivity.Status.ERROR);
            } else {
                // Try loading the model now that it's downloaded
                try {
                    runOnUiThread(() -> pDialog.setMessage("Decompressing Model"));

                    // Make sure the initial destination isn't full of crap
                    File tmpModelDir = new File(destinationHome, "scene_recognition");
                    if (tmpModelDir.exists())
                        UtilIO.deleteRecursive(tmpModelDir);

                    Log.i(TAG, "Decompressing " + fileZip.getPath());
                    Log.i(TAG, "  destination " + destinationHome.getPath());
                    ZipFile zipFile = new ZipFile(fileZip);
                    zipFile.extractAll(destinationHome.getAbsolutePath());
                    // Rename the decompressed directory to model
                    if (!new File(destinationHome, "scene_recognition").renameTo(modelPath)) {
                        Log.e(TAG, "Failed to rename");
                        Log.e(TAG, "   src='" + new File(destinationHome, "scene_recognition").getPath() + "'");
                        Log.e(TAG, "   dst='" + modelPath.getPath() + "'");
                        throw new IOException("Failed to rename");
                    }

                    if (!attemptToLoadModel(modelPath))
                        return null;
                } catch (IOException e) {
                    Log.w(TAG, "Failed to load model on second attempt.");
                    failAndCleanUp(fileZip, modelPath, e);
                }
            }

            return null;
        }

        private boolean attemptToLoadModel(File modelPath) {
            runOnUiThread(() -> pDialog.setMessage("Loading Model"));
            try {
                setStatus(SceneRecognitionActivity.Status.LOADING);
                Log.i(TAG, "Loading model: " + modelPath.getPath());
                synchronized (lockRecognizer) {
                    recognizer = RecognitionIO.loadFeatureToScene(modelPath, ImageType.SB_U8);
                }
                Log.d(TAG, "loaded model");

                // Create images if it doesn't exist yet
                File baseDir = new File(getExternalDirectory(SceneRecognitionActivity.this), DATA_DIRECTORY);
                File imageDir = new File(baseDir, IMAGES);
                if (!imageDir.exists())
                    imageDir.mkdirs();
                setStatus(SceneRecognitionActivity.Status.RUNNING);
                return true;
            } catch (RuntimeException e) {
                Log.w(TAG, "Failed to load model on first attempt.  Downloading");
                Log.w(TAG, "    message: '" + e.getMessage() + "'");
            }
            return false;
        }

        private void failAndCleanUp(File destinationZip, File decompressedPath, IOException e) {
            e.printStackTrace();
            setStatus(SceneRecognitionActivity.Status.ERROR);
            // If there's a bug in the code and it was saved as a directory, zap it
            try {
                UtilIO.deleteRecursive(destinationZip);
                // Delete the model it failed to load
                UtilIO.deleteRecursive(decompressedPath);
            } catch (RuntimeException e2) {
                Log.i(TAG, "Failed to delete directories when cleaning up");
            }
        }

        private void downloadModelFile(File destinationZip, File decompressedPath, String sourcePath) throws IOException {
            int count;
            Log.w(TAG, "download location " + sourcePath);

            // Clean up previous download to be safe
            if (destinationZip.exists())
                if (!destinationZip.delete())
                    throw new IOException("Failed to delete: " + destinationZip.getPath());

            URL url = new URL(sourcePath);
            URLConnection connection = url.openConnection();
            connection.connect();

            InputStream input = new BufferedInputStream(url.openStream(), 8192);

            // this will be useful so that you can show a typical 0 to 100% progress bar
            final int fileSize = connection.getContentLength();
            runOnUiThread(() -> {
                int sizeMB = fileSize / 1024 / 1024;
                pDialog.setMessage("Downloading " + sizeMB + " MB");
            });

            if (!decompressedPath.exists())
                BoofMiscOps.checkTrue(decompressedPath.mkdirs(), "Failed to create destination");

            Log.i(TAG, "   destination path   = " + destinationZip.getAbsolutePath());
            Log.i(TAG, "   decompression path = " + decompressedPath.getAbsolutePath());

            OutputStream output = new FileOutputStream(destinationZip);

            setStatus(SceneRecognitionActivity.Status.DOWNLOADING);
            byte[] data = new byte[1024];
            long total = 0;

            while ((count = input.read(data)) != -1 && !stopRequested) {
                total += count;
                // publishing the progress....
                // After this onProgressUpdate will be called
                publishProgress("" + (int) ((total * 100) / fileSize));

                // writing data to file
                output.write(data, 0, count);
            }

            Log.i(TAG, " downloaded bytes " + total);

            // flushing output
            output.flush();

            // closing streams
            output.close();
            input.close();
        }

        /**
         * Updating progress bar
         */
        protected void onProgressUpdate(String... progress) {
            // setting progress percentage
            pDialog.setProgress(Integer.parseInt(progress[0]));
        }

        /**
         * After completing background task Dismiss the progress dialog
         **/
        @Override
        protected void onPostExecute(String file_url) {
            Log.i(TAG, "onPostExecute()");
            download = null;
            // dismiss the dialog after the file was downloaded
            runOnUiThread(() -> dismissDialog());
        }

        public void dismissDialog() {
            if (pDialog.isShowing()) {
                pDialog.dismiss();
            }
        }

        @Override
        public void onCancel(DialogInterface dialogInterface) {
            stopRequested = true;
        }
    }

    private void setStatus(Status status) {
        runOnUiThread(() -> activateControls(status == Status.RUNNING));
        this.status = status;
    }

    /**
     * Saved results from previous queries
     */
    private static class QueryCache {
        public Bitmap bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888);
        public String name = "";
    }

    enum Status {
        DOWNLOADING,
        LOADING,
        RUNNING,
        SAVING,
        CLEARING,
        ERROR
    }
}
