package org.boofcv.android.recognition;

import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.ToggleButton;

import org.boofcv.android.DemoCamera2Activity;
import org.boofcv.android.DemoProcessingAbstract;
import org.boofcv.android.R;
import org.boofcv.android.misc.MiscUtil;
import org.ddogleg.struct.FastQueue;

import java.util.HashMap;
import java.util.Map;

import boofcv.abst.fiducial.QrCodeDetector;
import boofcv.alg.fiducial.qrcode.QrCode;
import boofcv.factory.fiducial.ConfigQrCode;
import boofcv.factory.fiducial.FactoryFiducial;
import boofcv.struct.image.GrayU8;
import georegression.metric.Intersection2D_F64;
import georegression.struct.point.Point2D_F64;

/**
 * Used to detect and read information from QR codes
 */
public class QrCodeDetectActivity extends DemoCamera2Activity {
    private static final String TAG = "QrCodeDetect";

    // Switches what information is displayed
    Mode mode = Mode.NORMAL;
    // Does it render failed detections too?
    boolean showFailures = false;

    // Where the number of unique messages are listed
    TextView textUnqiueCount;
    // List of unique qr codes
    public static final Object uniqueLock = new Object();
    public static final Map<String,QrCode> unique = new HashMap<>();
    // qr which has been selected and should be viewed
    public static String selectedQR = null;
    // TODO don't use a static method and forget detection if the activity is exited by the user

    // Location in image coordinates that the user is touching
    Point2D_F64 touched = new Point2D_F64();
    boolean touching = false;
    boolean touchProcessed = false;


    // Which standard configuration to use
    Detector detectorType = Detector.STANDARD;
    Spinner spinnerDetector;

    public QrCodeDetectActivity() {
        super(Resolution.HIGH);
        super.changeResolutionOnSlow = true;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LayoutInflater inflater = getLayoutInflater();
        LinearLayout controls = (LinearLayout)inflater.inflate(R.layout.qrcode_detect_controls,null);

        spinnerDetector = controls.findViewById(R.id.spinner_algs);
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(this,
                R.array.qrcode_detectors, android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerDetector.setAdapter(adapter);
        spinnerDetector.setSelection(detectorType.ordinal());
        spinnerDetector.setOnItemSelectedListener(new SelectedListener());

        final ToggleButton toggle = controls.findViewById(R.id.show_failures);
        toggle.setChecked(showFailures);
        toggle.setOnCheckedChangeListener((buttonView, isChecked) -> showFailures = isChecked);

        textUnqiueCount = controls.findViewById(R.id.total_unique);
        textUnqiueCount.setText("0");

        setControls(controls);
        displayView.setOnTouchListener(new TouchListener());
    }

    private class SelectedListener implements AdapterView.OnItemSelectedListener {

        @Override
        public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
            detectorType = Detector.values()[position];
            createNewProcessor();
        }

        @Override
        public void onNothingSelected(AdapterView<?> parent) {

        }
    }

    private class TouchListener implements View.OnTouchListener {

        @Override
        public boolean onTouch(View view, MotionEvent motionEvent) {
            switch( motionEvent.getAction() ) {
                case MotionEvent.ACTION_DOWN:{
                    touching = true;
                } break;

                case MotionEvent.ACTION_UP:{
                    touching = false;
                } break;
            }

            if( touching ) {
                applyToPoint(viewToImage,motionEvent.getX(),motionEvent.getY(),touched);
            }

            return true;
        }
    }

    @Override
    public void createNewProcessor() {
        setProcessing(new QrCodeProcessing());
    }

    public void pressedListView( View view ) {
        Intent intent = new Intent(this, QrCodeListActivity.class );
        startActivity(intent);
    }

    protected class QrCodeProcessing extends DemoProcessingAbstract<GrayU8> {

        QrCodeDetector<GrayU8> detector;

        FastQueue<QrCode> detected = new FastQueue<>(QrCode.class,true);
        FastQueue<QrCode> failures = new FastQueue<>(QrCode.class,true);

        Paint colorDetected = new Paint();
        Paint colorFailed = new Paint();
        Path path = new Path();

        int uniqueCount = 0;
        int oldValue = -1;

        public QrCodeProcessing() {
            super(GrayU8.class);

            ConfigQrCode config;

            switch( detectorType ) {
                case FAST:{
                    config = ConfigQrCode.fast();
                }break;

                default: {
                    config = new ConfigQrCode();
                }
            }

            detector = FactoryFiducial.qrcode(config,GrayU8.class);

            colorDetected.setARGB(0xA0,0,0xFF,0);
            colorDetected.setStyle(Paint.Style.FILL);
            colorFailed.setARGB(0xA0,0xFF,0x11,0x11);
            colorFailed.setStyle(Paint.Style.FILL);
        }

        @Override
        public void initialize(int imageWidth, int imageHeight, int sensorOrientation) {
            touchProcessed = false;
            selectedQR = null;
            touching = false;

            synchronized (uniqueLock) {
                uniqueCount = unique.size();
            }
        }

        @Override
        public void onDraw(Canvas canvas, Matrix imageToView) {
            canvas.concat(imageToView);
            synchronized (lockGui) {
                if( oldValue != uniqueCount ) {
                    oldValue = uniqueCount;
                    textUnqiueCount.setText(uniqueCount + "");
                }
                switch( mode ) {
                    case NORMAL:{
                        for( int i = 0; i < detected.size; i++ ) {
                            QrCode qr = detected.get(i);
                            MiscUtil.renderPolygon(qr.bounds,path,canvas,colorDetected);

                            if(touching && Intersection2D_F64.containConvex(qr.bounds,touched)) {
                                selectedQR = qr.message;
                            }
                        }
                        for( int i = 0; showFailures && i < failures.size; i++ ) {
                            QrCode qr = failures.get(i);
                            MiscUtil.renderPolygon(qr.bounds,path,canvas,colorFailed);
                        }
                    }break;

                    case GRAPH:{
                        // TODO implement this in the future
                    }break;
                }
            }

            // touchProcessed is needed to prevent multiple intent from being sent
            if( selectedQR != null && !touchProcessed ) {
                touchProcessed = true;
                Intent intent = new Intent(QrCodeDetectActivity.this, QrCodeListActivity.class );
                startActivity(intent);
            }
        }

        @Override
        public void process(GrayU8 input) {
            detector.process(input);

            synchronized (uniqueLock) {
                for (QrCode qr : detector.getDetections()) {
                    if (qr.message == null) {
                        Log.e(TAG, "qr with null message?!?");
                    }
                    if (!unique.containsKey(qr.message)) {
                        Log.i(TAG,"Adding new qr code with message of length="+qr.message.length());
                        unique.put(qr.message, qr.clone());
                    }
                }
                uniqueCount = unique.size();
            }

            synchronized (lockGui) {
                detected.reset();
                for (QrCode qr : detector.getDetections()) {
                    detected.grow().set(qr);
                }

                failures.reset();
                for (QrCode qr : detector.getFailures()) {
                    if( qr.failureCause.ordinal() >= QrCode.Failure.ERROR_CORRECTION.ordinal()) {
                        failures.grow().set(qr);
                    }
                }
            }
        }
    }

    enum Mode {
        NORMAL,
        GRAPH
    }

    enum Detector {
        STANDARD,
        FAST
    }

}
