package org.boofcv.android.recognition;

import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.StrictMode;
import android.util.Log;
import android.view.GestureDetector;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.Toast;

import net.lingala.zip4j.ZipFile;

import org.boofcv.android.DemoBitmapCamera2Activity;
import org.boofcv.android.DemoProcessingAbstract;
import org.boofcv.android.R;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.List;
import java.util.Locale;

import boofcv.abst.scene.ImageClassifier;
import boofcv.alg.misc.GImageMiscOps;
import boofcv.core.image.ConvertImage;
import boofcv.factory.scene.ClassifierAndSource;
import boofcv.factory.scene.FactoryImageClassifier;
import boofcv.struct.image.GrayF32;
import boofcv.struct.image.ImageType;
import boofcv.struct.image.InterleavedU8;
import boofcv.struct.image.Planar;

/**
 * Displays a video stream until the user clicks the window.  It then attempts to classify what
 * is currently in view.  Results are rendered and the static frame is shown until tapped again
 */
public class ImageClassificationActivity extends DemoBitmapCamera2Activity
        implements AdapterView.OnItemSelectedListener {
    private static final String TAG = "Classify";
    public static final String MODEL_PATH = "classifier_models";

    Spinner spinnerClassifier;
    Button deleteButton;

    private String modelName;
    int selectedModel;

    ImageClassifier<Planar<GrayF32>> classifier;
    List<String> sources;
    Status status = Status.INITIALIZING;

    Planar<GrayF32> workImage = ImageType.pl(3, GrayF32.class).createImage(1,1);
    Planar<GrayF32> workImage2 = ImageType.pl(3, GrayF32.class).createImage(1,1);

    long startTime;

    // Progress Dialog
    private boolean guiEnabled = true;

    boolean screenTouched = false;

    public ImageClassificationActivity() {
        super(Resolution.R640x480);
    }

    // only modify on UI thread
    private DownloadNetworkModel download;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LayoutInflater inflater = getLayoutInflater();
        LinearLayout controls = (LinearLayout) inflater.inflate(R.layout.image_recognition_controls, null);

        spinnerClassifier = controls.findViewById(R.id.spinner_algs);
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(this,
                R.array.image_classifiers, android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerClassifier.setAdapter(adapter);
        spinnerClassifier.setOnItemSelectedListener(this);

        deleteButton = findViewById(R.id.button_delete);

        StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();
        StrictMode.setThreadPolicy(policy);

        setControls(controls);

        final GestureDetector mDetector = new GestureDetector(this, new MyGestureDetector());
        displayView.setOnTouchListener((v, event) -> {
            mDetector.onTouchEvent(event);
            return true;
        });
    }

    @Override
    protected void onDestroy() {
        abortDownload();
        super.onDestroy();
    }

    @Override
    public void onItemSelected(AdapterView<?> adapterView, View view, int pos, long id) {
        startClassifier(pos);
    }

    @Override
    public void onNothingSelected(AdapterView<?> adapterView) {

    }

    @Override
    public void createNewProcessor() {
        setProcessing(new ClassifierProcessing());
    }

    public void pressedDeleteModel( View view ) {
        Log.i(TAG,"pressed delete model");
        if( status == Status.WAITING || status == Status.IDLE || status == Status.ERROR ) {
            Log.i(TAG,"trying to delete");
            deleteModelData(true);
        }
    }

    protected class MyGestureDetector extends GestureDetector.SimpleOnGestureListener
    {
        @Override
        public boolean onDown(MotionEvent e) {
            screenTouched = true;
            return true;
        }
    }

    protected void deactiveControls() {
        runOnUiThread(() -> {
            guiEnabled = false;
            spinnerClassifier.setEnabled(false);
        });
    }

    protected void activateControls() {
        runOnUiThread(() -> {
            guiEnabled = true;
            spinnerClassifier.setEnabled(true);
        });
    }

    private void startClassifier(int which) {

        ClassifierAndSource selected;
        if (which == 0) {
            selected = FactoryImageClassifier.vgg_cifar10();
            modelName = "likevgg_cifar10";
        } else if (which == 1) {
            selected = FactoryImageClassifier.nin_imagenet();
            modelName = "nin_imagenet";
        } else
            throw new RuntimeException("Egads");


        classifier = selected.getClassifier();
        sources = selected.getSource();
        selectedModel = which;
        // Don't start downloading immediately after the activity launches
        // wait for the user to tap the screen.  However, if the user changes
        // model after this download automatically.
        if( status == Status.INITIALIZING )
            setStatus(ImageClassificationActivity.Status.WAITING);
        else {
            download(sources.get(0));
        }
    }

    private void download( final String path ) {
        runOnUiThread(() -> {
            if( download != null ) {
                Toast.makeText(this,"Already downloading a model!",Toast.LENGTH_SHORT).show();
            } else {
                deactiveControls();
                download = new DownloadNetworkModel();
                download.execute(path);
            }
        });
    }

    private void abortDownload() {
        runOnUiThread(() -> {
            if( download != null ) {
                download.stopRequested = true;
                download.dismissDialog();
            }
        });
    }

    // Input is an interleaved U8 image because planar float images were extremely slow on
    // some older hardware
    protected class ClassifierProcessing extends DemoProcessingAbstract<InterleavedU8> {

        private Paint textPaint = new Paint();
        private Paint bestPaint = new Paint();
        private Paint dimPaint = new Paint();

        public ClassifierProcessing() {
            super(ImageType.il(3, InterleavedU8.class));

            textPaint.setARGB(255, 255, 100, 100);
            textPaint.setTextSize(18*displayMetrics.density);
            textPaint.setTypeface(Typeface.create("monospace", Typeface.NORMAL));
            textPaint.setTextAlign(Paint.Align.CENTER);

            bestPaint.setARGB(255, 255, 0, 0);
            bestPaint.setTextSize(18*displayMetrics.density);
            bestPaint.setTypeface(Typeface.create("monospace", Typeface.BOLD));
            bestPaint.setTextAlign(Paint.Align.CENTER);

            dimPaint.setARGB(200,0,0,0);
        }

        @Override
        public void initialize(int imageWidth, int imageHeight, int sensorOrientation) {
            workImage.reshape(imageWidth,imageHeight);
            workImage2.reshape(imageHeight,imageWidth);
        }

        @Override
        public void onDraw(Canvas canvas, Matrix imageToView) {
            drawBitmap(canvas,imageToView);
            if( classifier == null )
                return;

            int H = (int)(1.1*textPaint.getTextSize()+1); // This isn't actually the correct way to do this
            Locale locale = Locale.getDefault();
            if( status == Status.CLASSIFIED ) {
                List<ImageClassifier.Score> scores = classifier.getAllResults();
                List<String> categories = classifier.getCategories();

                int N = Math.min(4, scores.size());

                int y = canvas.getHeight()/2 - (int)(H*N/2);
                int x = canvas.getWidth()/2;

                ImageClassifier.Score best = scores.get(0);

                int blackHeight = (N+3)*H;
                canvas.drawRect(0, y-blackHeight/2, canvas.getWidth(), y + blackHeight/2, dimPaint);
                y -= (N-1)*H/2;
                for (int i = 0; i < N; i++) {
                    Paint paint = i==0?bestPaint:textPaint;
                    ImageClassifier.Score s = scores.get(i);
                    String which = categories.get(s.category);

                    canvas.drawText(String.format(locale,"%12s %8.1e ", which, s.score), x + 10, y +i * H, paint);
                }

            } else if( status == Status.PROCESSING ) {
                int x = canvas.getWidth()/2;
                int y = canvas.getHeight()/2;

                long ellapsed = System.currentTimeMillis()-startTime;
                canvas.drawText(String.format(locale,"Processing Image %03d s",ellapsed/1000), x, y, textPaint);
                canvas.drawText("CNN still being optimized", x, y+H, textPaint);

            } else {
                int x = canvas.getWidth()/2;
                int y = canvas.getHeight()/2;
                String message;
                if( status == Status.WAITING ) {
                    message = "Touch the screen";
                } else {
                    message = "Status "+status.toString();
                }
                canvas.drawText(message, x, y, textPaint);
            }
        }

        @Override
        public void process(InterleavedU8 input) {

            if( status != Status.CLASSIFIED && status != Status.PROCESSING ) {
                convertToBitmapDisplay(input);
            }

            if(screenTouched) {
                screenTouched = false;
                if( status == Status.WAITING ) {
                    download(sources.get(0));
                } else if (status == Status.IDLE) {
                    startTime = System.currentTimeMillis();
                    status = Status.PROCESSING;
                    convertToBitmapDisplay(input);
                    ConvertImage.convertU8F32(input,workImage);

                    // rotate so that it appears to the CNN the say way to appears to the user
                    int displayRotation = (getWindowManager().getDefaultDisplay().getRotation())*90;
                    int total = cameraOrientation - displayRotation;
                    if( total < 0 ) {
                        total += 360;
                    }
                    total %= 360;

                    Log.d(TAG,"display "+displayRotation+" camera "+cameraOrientation+" total "+total);

                    Planar<GrayF32> adjusted;
                    switch( total ) {
                        case 0:
                            adjusted = workImage;
                            break;
                        case 90:
                            GImageMiscOps.rotateCW(workImage,workImage2);
                            adjusted = workImage2;
                            break;
                        case 180:
                            GImageMiscOps.rotateCW(workImage,workImage2);
                            GImageMiscOps.rotateCW(workImage2,workImage);
                            adjusted = workImage;
                            break;
                        case 270:
                            GImageMiscOps.rotateCCW(workImage,workImage2);
                            adjusted = workImage2;
                            break;
                        default:
                            throw new RuntimeException("Unexpected angle "+total);
                    }

                    deactiveControls();
                    new ProcessImageTask(adjusted).execute();
                } else if( status == Status.CLASSIFIED ) {
                    status = Status.IDLE;
                }
            }

        }
    }

    /**
     * Deletes the model data
     * @param verbose
     */
    private void deleteModelData(boolean verbose) {
        File initialPath = getDir(MODEL_PATH, MODE_PRIVATE);
        File decompressedPath = new File(initialPath, modelName);

        if( decompressedPath.exists() ) {
            if( verbose )
                runOnUiThread(() -> Toast.makeText(ImageClassificationActivity.this,
                        "Deleting "+modelName,Toast.LENGTH_SHORT).show());

            deleteDir(decompressedPath);
        } else {
            if( verbose )
                runOnUiThread(() -> Toast.makeText(ImageClassificationActivity.this,
                        "Nothing to delete",Toast.LENGTH_SHORT).show());
        }
    }

    void deleteDir(File file) {
        File[] contents = file.listFiles();
        if (contents != null) {
            for (File f : contents) {
                deleteDir(f);
            }
        }
        file.delete();
    }

    /**
     * Background Async Task to download file
     */
    class DownloadNetworkModel extends AsyncTask<String, String, String>
        implements DialogInterface.OnCancelListener
    {
        private final String TAG = "ICA";
        private ProgressDialog pDialog;
        private boolean stopRequested = false;

        /**
         * Before starting background thread Show Progress Bar Dialog
         */
        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            runOnUiThread(()->{
                pDialog = new ProgressDialog(ImageClassificationActivity.this);
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
            File destinationZip,initialPath,decompressedPath;
            final String fileName;
            try {
                // Output stream
                initialPath = getDir(MODEL_PATH, MODE_PRIVATE);
                fileName = new File(f_url[0]).getName();
                destinationZip = new File(initialPath, new File(f_url[0]).getName());
                decompressedPath = new File(initialPath, modelName);

                // see if it can load the model
                runOnUiThread(() -> pDialog.setMessage("Loading "+modelName));
                try {
                    setStatus(ImageClassificationActivity.Status.LOADING);
                    Log.i(TAG,"before load model "+decompressedPath.getPath());
                    classifier.loadModel(decompressedPath);
                    Log.d(TAG,"loaded model "+modelName);
                    setStatus(ImageClassificationActivity.Status.IDLE);
                    return null;
                } catch( IOException e ) {
                    Log.w(TAG,"Failed to load model on first attempt.  Downloading");
                    Log.w(TAG,"    message = "+e.getMessage());
                }

                // download the file
                downloadModelFile(destinationZip, decompressedPath, f_url[0]);

            } catch (IOException e) {
                Log.e("Error: ", e.getMessage());
                setStatus(ImageClassificationActivity.Status.ERROR);
                return null;
            }

            if( stopRequested ) {
                Log.i(TAG, "Download stop requested. Cleaning up. ");
                if (destinationZip.exists() && !destinationZip.delete()) {
                    Log.e("Error: ", "Failed to delete " + destinationZip.getName());
                }
                setStatus(ImageClassificationActivity.Status.WAITING);
            } else {
                // Try loading the model now that it's downloaded
                try {
                    runOnUiThread(() -> pDialog.setMessage("Decompressing " + fileName));

                    Log.i(TAG, "Decompressing " + decompressedPath.getPath());
                    setStatus(ImageClassificationActivity.Status.DECOMPRESSING);

                    deleteModelData(false); // clean up first

                    ZipFile zipFile = new ZipFile(destinationZip);
                    zipFile.extractAll(initialPath.getAbsolutePath());
                    if (!destinationZip.delete()) {
                        Log.e("Error: ", "Failed to delete " + destinationZip.getName());
                    }

                    runOnUiThread(() -> pDialog.setMessage("Loading " + modelName));
                    setStatus(ImageClassificationActivity.Status.LOADING);
                    classifier.loadModel(decompressedPath);
                    setStatus(ImageClassificationActivity.Status.IDLE);
                } catch (Exception e) {
                    Log.w(TAG, "Failed to load model on second attempt.");
                    setStatus(ImageClassificationActivity.Status.ERROR);
                }
            }

            return null;
        }

        private void downloadModelFile(File destinationZip, File decompressedPath, String sourcePath) throws IOException {
            int count;
            Log.w(TAG,"download location "+ sourcePath);

            URL url = new URL(sourcePath);
            URLConnection connection = url.openConnection();
            connection.connect();

            InputStream input = new BufferedInputStream(url.openStream(),8192);

            // this will be useful so that you can show a typical 0 to 100% progress bar
            final int fileSize = connection.getContentLength();
            runOnUiThread(() -> {
                int sizeMB = fileSize/1024/1024;
                pDialog.setMessage("Downloading "+sizeMB+" MB");
            });


            Log.i(TAG,"   destination path   = "+destinationZip.getAbsolutePath());
            Log.i(TAG,"   decompression path = "+decompressedPath.getAbsolutePath());

            OutputStream output = new FileOutputStream(destinationZip);

            setStatus(ImageClassificationActivity.Status.DOWNLOADING);
            byte data[] = new byte[1024];
            long total = 0;

            while ((count = input.read(data)) != -1 && !stopRequested ) {
                total += count;
                // publishing the progress....
                // After this onProgressUpdate will be called
                publishProgress("" + (int) ((total * 100) / fileSize));

                // writing data to file
                output.write(data, 0, count);
            }

            Log.i(TAG," downloaded bytes "+total);

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
            Log.i(TAG,"onPostExecute()");
            download = null;
            // dismiss the dialog after the file was downloaded
            runOnUiThread(()->dismissDialog());
        }

        public void dismissDialog() {
            if( pDialog.isShowing() ) {
                pDialog.dismiss();
            }
        }

        @Override
        public void onCancel(DialogInterface dialogInterface) {
            stopRequested = true;
        }
    }

    class ProcessImageTask extends AsyncTask<String, String, String> {
        Planar<GrayF32> workImage;

        public ProcessImageTask( Planar<GrayF32> workImage ) {
            this.workImage = workImage;
        }

        @Override
        protected String doInBackground(String... strings) {
            classifier.classify(workImage);
            setStatus(ImageClassificationActivity.Status.CLASSIFIED);
            return null;
        }
    }

    private void setStatus( Status status ) {

        if( status == Status.IDLE || status == Status.WAITING ||
                status == Status.ERROR || status == Status.CLASSIFIED)
        {
            runOnUiThread(() -> {
                if( !guiEnabled ) {
                    activateControls();
                }
            });
        }

        this.status = status;
    }

    enum Status {
        INITIALIZING,
        WAITING,
        LOADING,
        DOWNLOADING,
        DECOMPRESSING,
        IDLE, // model has been loaded if in this state
        PROCESSING,
        CLASSIFIED,
        ERROR
    }
}
