package org.boofcv.android.recognition;

import android.app.Dialog;
import android.app.ProgressDialog;
import android.graphics.Bitmap;
import android.graphics.Canvas;
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
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.Toast;

import net.lingala.zip4j.core.ZipFile;
import net.lingala.zip4j.exception.ZipException;

import org.boofcv.android.DemoVideoDisplayActivity;
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

import boofcv.abst.scene.ImageClassifier;
import boofcv.android.ConvertBitmap;
import boofcv.android.gui.VideoImageProcessing;
import boofcv.factory.scene.ClassifierAndSource;
import boofcv.factory.scene.FactoryImageClassifier;
import boofcv.struct.image.GrayF32;
import boofcv.struct.image.ImageType;
import boofcv.struct.image.Planar;

/**
 * Displays a video stream until the user clicks the window.  It then attempts to classify what
 * is currently in view.  Results are rendered and the static frame is shown until tapped again
 */
public class ImageClassificationActivity extends DemoVideoDisplayActivity
        implements AdapterView.OnItemSelectedListener {
    public static final String MODEL_PATH = "classifier_models";

    Spinner spinnerClassifier;
    Button deleteButton;

    private String modelName;
    int selectedModel;

    ImageClassifier<Planar<GrayF32>> classifier;
    List<String> sources;
    Status status = Status.INITIALIZING;
    Planar<GrayF32> workImage = ImageType.pl(3, GrayF32.class).createImage(1,1);
    long startTime;

    // Progress Dialog
    private ProgressDialog pDialog;
    public static final int progress_bar_type = 0;

    private boolean guiEnabled = true;

    boolean screenTouched = false;

    // Don't automatically

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LayoutInflater inflater = getLayoutInflater();
        LinearLayout controls = (LinearLayout) inflater.inflate(R.layout.image_recognition_controls, null);

        LinearLayout parent = getViewContent();
        parent.addView(controls);

        spinnerClassifier = (Spinner) controls.findViewById(R.id.spinner_algs);
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(this,
                R.array.image_classifiers, android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerClassifier.setAdapter(adapter);
        spinnerClassifier.setOnItemSelectedListener(this);

        deleteButton = (Button) findViewById(R.id.button_delete);

        StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();
        StrictMode.setThreadPolicy(policy);

        FrameLayout iv = getViewPreview();
        final GestureDetector mDetector = new GestureDetector(this, new MyGestureDetector());
        iv.setOnTouchListener(new View.OnTouchListener(){
            @Override
            public boolean onTouch(View v, MotionEvent event)
            {
                mDetector.onTouchEvent(event);
                return true;
            }});
    }

    @Override
    public void onItemSelected(AdapterView<?> adapterView, View view, int pos, long id) {
        startClassifier(pos);
    }

    @Override
    public void onNothingSelected(AdapterView<?> adapterView) {

    }

    @Override
    public void onResume() {
        super.onResume();
        setProcessing(new ClassifierProcessing());
    }

    public void pressedDeleteModel( View view ) {
        System.out.println("pressed delete model");
        if( status == Status.WAITING || status == Status.IDLE || status == Status.ERROR ) {
            System.out.println("trying to delete");

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
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                guiEnabled = false;
                spinnerClassifier.setEnabled(false);
            }
        });

    }

    protected void activateControls() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                guiEnabled = true;
                spinnerClassifier.setEnabled(true);
            }
        });
    }

    @Override
    protected Dialog onCreateDialog(int id) {
        switch (id) {
            case progress_bar_type: // we set this to 0
                pDialog = new ProgressDialog(this);
                pDialog.setMessage("Initializing");
                pDialog.setIndeterminate(false);
                pDialog.setMax(100);
                pDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
                pDialog.setCancelable(false);
                pDialog.show();
                return pDialog;
            default:
                return null;
        }
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
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                deactiveControls();
                new DownloadNetworkModel().execute(path);
            }});
    }

    protected class ClassifierProcessing extends VideoImageProcessing<Planar<GrayF32>> {

        private Paint textPaint = new Paint();
        private Paint bestPaint = new Paint();
        private Paint dimPaint = new Paint();

        public ClassifierProcessing() {
            super(ImageType.pl(3, GrayF32.class));

            textPaint.setARGB(255, 255, 100, 100);
            textPaint.setTextSize(16);
            textPaint.setTypeface(Typeface.create("monospace", Typeface.NORMAL));

            bestPaint.setARGB(255, 255, 0, 0);
            bestPaint.setTextSize(16);
            bestPaint.setTypeface(Typeface.create("monospace", Typeface.BOLD));

            dimPaint.setARGB(200,0,0,0);
        }


        @Override
        protected void process(Planar<GrayF32> input, Bitmap output, byte[] storage) {

            if( status == Status.CLASSIFIED || status == Status.PROCESSING ) {
                ConvertBitmap.multiToBitmap(workImage, output, storage);
            } else {
                ConvertBitmap.multiToBitmap(input, output, storage);
            }

            if( screenTouched == true  ) {
                screenTouched = false;
                if( status == Status.WAITING ) {
                    download(sources.get(0));
                } else if (status == Status.IDLE) {
                    startTime = System.currentTimeMillis();
                    status = Status.PROCESSING;
                    workImage.setTo(input);
                    deactiveControls();
                    new ProcessImageTask(workImage).execute();
                } else if( status == Status.CLASSIFIED ) {
                    status = Status.IDLE;
                }
            }

        }

        @Override
        protected void render(  Canvas canvas , double imageToOutput ) {
            super.render(canvas,imageToOutput);

            if( classifier == null )
                return;

            if( status == Status.CLASSIFIED ) {
                List<ImageClassifier.Score> scores = classifier.getAllResults();
                List<String> categories = classifier.getCategories();

                int N = Math.min(4, scores.size());

                int y = 30;
                int x = 5;

                ImageClassifier.Score best = scores.get(0);

                canvas.drawRect(0, 0, canvas.getWidth(), y + 10 + (N + 1) * 20, dimPaint);
                canvas.drawText(String.format("%12s %8.1e ", categories.get(best.category), best.score), x, y, bestPaint);
                for (int i = 1; i < N; i++) {
                    ImageClassifier.Score s = scores.get(i);
                    String which = categories.get(s.category);

                    canvas.drawText(String.format("%12s %8.1e ", which, s.score), x + 10, y + 10 + i * 20, textPaint);

                }

            } else if( status == Status.PROCESSING ) {
                long ellapsed = System.currentTimeMillis()-startTime;
                canvas.drawText(String.format("Processing Image %03d s",ellapsed/1000), 50, 50, textPaint);
                canvas.drawText("CNN still needs to be optimized", 50, 80, textPaint);

            } else {
                canvas.drawText("Status " + status, 50, 50, textPaint);
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
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(ImageClassificationActivity.this,
                                "Deleting "+modelName,Toast.LENGTH_SHORT).show();
                    }});

            deleteDir(decompressedPath);
        } else {
            if( verbose )
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(ImageClassificationActivity.this,
                                "Nothing to delete",Toast.LENGTH_SHORT).show();
                    }});
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
    class DownloadNetworkModel extends AsyncTask<String, String, String> {

        /**
         * Before starting background thread Show Progress Bar Dialog
         */
        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            showDialog(progress_bar_type);
        }

        /**
         * Downloading file in background thread
         */
        @Override
        protected String doInBackground(String... f_url) {
            int count;
            File destinationZip,initialPath,decompressedPath;
            final String fileName;
            try {
                // Output stream
                initialPath = getDir(MODEL_PATH, MODE_PRIVATE);
                fileName = new File(f_url[0]).getName();
                destinationZip = new File(initialPath, new File(f_url[0]).getName());
                decompressedPath = new File(initialPath, modelName);

                // see if it can load the model
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        pDialog.setMessage("Loading "+modelName);
                    }
                });
                try {
                    setStatus(ImageClassificationActivity.Status.LOADING);
                    classifier.loadModel(decompressedPath);
                    setStatus(ImageClassificationActivity.Status.IDLE);
                    Log.d("ICA","loaded model "+modelName);
                    return null;
                } catch( IOException e ) {
                    Log.w("ICA","Failed to load model on first attempt.  Downloading");
                    Log.w("ICA","    message = "+e.getMessage());
                }

                // download the file
                downloadModelFile(destinationZip, decompressedPath, f_url[0]);

            } catch (IOException e) {
                Log.e("Error: ", e.getMessage());
                setStatus(ImageClassificationActivity.Status.ERROR);
                return null;
            }

            // Try loading the model now that it's downloaded
            try {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        pDialog.setMessage("Decompressing "+fileName);
                    }
                });

                setStatus(ImageClassificationActivity.Status.DECOMPRESSING);

                deleteModelData(false); // clean up first

                ZipFile zipFile = new ZipFile(destinationZip);
                zipFile.extractAll(initialPath.getAbsolutePath());
                if( !destinationZip.delete() ) {
                    Log.e("Error: ","Failed to delete "+destinationZip.getName());
                }

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        pDialog.setMessage("Loading "+modelName);
                    }
                });
                setStatus(ImageClassificationActivity.Status.LOADING);
                classifier.loadModel(decompressedPath);
                setStatus( ImageClassificationActivity.Status.IDLE);
            } catch( ZipException | IOException e ) {
                Log.w("ICA","Failed to load model on second attempt.");
                e.printStackTrace();
                setStatus(ImageClassificationActivity.Status.ERROR);
            }

            return null;
        }

        private void downloadModelFile(File destinationZip, File decompressedPath, String sourcePath) throws IOException {
            int count;
            Log.w("ICA","download location "+ sourcePath);

            URL url = new URL(sourcePath);
            URLConnection connection = url.openConnection();
            connection.connect();

            InputStream input = new BufferedInputStream(url.openStream(),
                    8192);

            // this will be useful so that you can show a typical 0 to 100% progress bar
            final int fileSize = connection.getContentLength();
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    int sizeMB = fileSize/1024/1024;
                    pDialog.setMessage("Downloading "+sizeMB+" MB");
                }
            });


            System.out.println("   destination path   = "+destinationZip.getAbsolutePath());
            System.out.println("   decompression path = "+decompressedPath.getAbsolutePath());

            OutputStream output = new FileOutputStream(destinationZip);

            setStatus(ImageClassificationActivity.Status.DOWNLOADING);
            byte data[] = new byte[1024];
            long total = 0;

            while ((count = input.read(data)) != -1 ) {
                total += count;
                // publishing the progress....
                // After this onProgressUpdate will be called
                publishProgress("" + (int) ((total * 100) / fileSize));

                // writing data to file
                output.write(data, 0, count);
            }

            System.out.println(" downloaded bytes "+total);

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
            // dismiss the dialog after the file was downloaded
            dismissDialog(progress_bar_type);

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

        if( status == Status.IDLE || status == Status.ERROR || status == Status.CLASSIFIED) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if( !guiEnabled ) {
                        activateControls();
                    }
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
        IDLE,
        PROCESSING,
        CLASSIFIED,
        USER_CANCEL,
        ERROR
    }
}
