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
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.Spinner;

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
// TODO handle person leaving the task while the a download or classify thread is still running
// TODO add a delete model button as a way to clean up and for a new download
public class ImageClassificationActivity extends DemoVideoDisplayActivity
        implements AdapterView.OnItemSelectedListener {
    public static final String MODEL_PATH = "classifier_models";

    Spinner spinnerGradient;

    ClassifierProcessing active;

    private String modelName;

    // Progress Dialog
    private ProgressDialog pDialog;
    public static final int progress_bar_type = 0;

    private boolean guiEnabled = true;

    boolean screenTouched = false;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LayoutInflater inflater = getLayoutInflater();
        LinearLayout controls = (LinearLayout) inflater.inflate(R.layout.image_recognition_controls, null);

        LinearLayout parent = getViewContent();
        parent.addView(controls);

        spinnerGradient = (Spinner) controls.findViewById(R.id.spinner_algs);
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(this,
                R.array.image_classifiers, android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerGradient.setAdapter(adapter);
        spinnerGradient.setOnItemSelectedListener(this);

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
                spinnerGradient.setEnabled(false);
            }
        });

    }

    protected void activateControls() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                guiEnabled = true;
                spinnerGradient.setEnabled(true);
            }
        });
    }

    // TODO handle cancel
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

    // TODO handle case where a classifier is currently being loaded/processing an image
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


        active = new ClassifierProcessing(selected);
        setProcessing(active);
    }

    private void download( String path ) {
        deactiveControls();
        new DownloadNetworkModel().execute(path);
    }

    protected class ClassifierProcessing extends VideoImageProcessing<Planar<GrayF32>> {
        ImageClassifier<Planar<GrayF32>> classifier;
        List<String> sources;

        Status status = Status.INITIALIZING;
        private Paint textPaint = new Paint();
        private Paint bestPaint = new Paint();
        private Paint dimPaint = new Paint();

        Planar<GrayF32> workImage;


        public ClassifierProcessing(ClassifierAndSource cas) {
            super(ImageType.pl(3, GrayF32.class));
            this.classifier = cas.getClassifier();
            this.sources = cas.getSource();

            textPaint.setARGB(255, 255, 100, 100);
            textPaint.setTextSize(16);
            textPaint.setTypeface(Typeface.create("monospace", Typeface.NORMAL));

            bestPaint.setARGB(255, 255, 0, 0);
            bestPaint.setTextSize(16);
            bestPaint.setTypeface(Typeface.create("monospace", Typeface.BOLD));

            dimPaint.setARGB(200,0,0,0);

            workImage = ImageType.pl(3, GrayF32.class).createImage(1,1);

            download(sources.get(0));
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
                if (status == Status.IDLE) {
                    status = Status.PROCESSING;
                    workImage.setTo(input);
                    deactiveControls();
                    new ProcessImageTask().execute();
                } else if( status == Status.CLASSIFIED ) {
                    status = Status.IDLE;
                }
            }

        }

        @Override
        protected void render(  Canvas canvas , double imageToOutput ) {
            super.render(canvas,imageToOutput);

            if( status == Status.CLASSIFIED ) {
                List<ImageClassifier.Score> scores = classifier.getAllResults();
                List<String> categories = classifier.getCategories();

                int N = Math.min(4,scores.size());

                int y = 30;
                int x = 5;

                ImageClassifier.Score best = scores.get(0);

                canvas.drawRect(0,0,canvas.getWidth(),y+10+(N+1)*20,dimPaint);
                canvas.drawText(String.format("%12s %8.1e ",categories.get(best.category),best.score), x, y, bestPaint);
                for (int i = 1; i < N; i++) {
                    ImageClassifier.Score s = scores.get(i);
                    String which = categories.get(s.category);

                    canvas.drawText(String.format("%12s %8.1e ",which,s.score), x+10, y+10+i*20, textPaint);

                }

            } else {
                canvas.drawText("Status " + status, 50, 50, textPaint);
            }
        }
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
            try {
                URL url = new URL(f_url[0]);
                URLConnection conection = url.openConnection();
                conection.connect();


                // download the file
                InputStream input = new BufferedInputStream(url.openStream(),
                        8192);

                // this will be useful so that you can show a tipical 0-100%
                // progress bar
                int lenghtOfFile = conection.getContentLength();

                // Output stream
                initialPath = getDir(MODEL_PATH, MODE_PRIVATE);
                final String fileName = new File(f_url[0]).getName();
                destinationZip = new File(initialPath, new File(f_url[0]).getName());
                decompressedPath = new File(initialPath, modelName);

                // see if it can load the model
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        pDialog.setMessage("Loading");
                    }
                });
                try {
                    setStatus(ImageClassificationActivity.Status.LOADING);
                    active.classifier.loadModel(decompressedPath);
                    setStatus(ImageClassificationActivity.Status.IDLE);
                    return null;
                } catch( IOException e ) {
                    Log.w("ICA","Failed to load model on first attempt.  Downloading");
                    Log.w("ICA","    message = "+e.getMessage());
                }

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        pDialog.setMessage("Downloading "+fileName);
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
                    publishProgress("" + (int) ((total * 100) / lenghtOfFile));

                    // writing data to file
                    output.write(data, 0, count);
                }

                // flushing output
                output.flush();

                // closing streams
                output.close();
                input.close();

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
                        pDialog.setMessage("Decompressing");
                    }
                });

                setStatus(ImageClassificationActivity.Status.DECOMPRESSING);
                ZipFile zipFile = new ZipFile(destinationZip);
                zipFile.extractAll(initialPath.getAbsolutePath());
                if( !destinationZip.delete() ) {
                    Log.e("Error: ","Failed to delete "+destinationZip.getName());
                }

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        pDialog.setMessage("Loading");
                    }
                });
                setStatus(ImageClassificationActivity.Status.LOADING);
                active.classifier.loadModel(decompressedPath);
                setStatus( ImageClassificationActivity.Status.IDLE);
            } catch( ZipException | IOException e ) {
                Log.w("ICA","Failed to load model on second attempt.");
                e.printStackTrace();
                setStatus(ImageClassificationActivity.Status.ERROR);
            }

            return null;
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

        @Override
        protected String doInBackground(String... strings) {
            active.classifier.classify(active.workImage);
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

        active.status = status;
    }

    enum Status {
        INITIALIZING,
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
