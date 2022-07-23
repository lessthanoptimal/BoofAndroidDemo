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
import org.ddogleg.struct.DogArray;

import java.util.HashMap;
import java.util.Map;

import boofcv.abst.fiducial.AztecCodeDetector;
import boofcv.alg.fiducial.aztec.AztecCode;
import boofcv.factory.fiducial.ConfigAztecCode;
import boofcv.factory.fiducial.FactoryFiducial;
import boofcv.struct.image.GrayU8;
import georegression.metric.Intersection2D_F64;
import georegression.struct.point.Point2D_F64;

/**
 * Used to detect and read information from Aztec codes
 */
public class AztecCodeDetectActivity extends DemoCamera2Activity {
    private static final String TAG = "AztecCodeDetect";

    // Switches what information is displayed
    Mode mode = Mode.NORMAL;
    // Does it render failed detections too?
    boolean showFailures = false;

    // Where the number of unique messages are listed
    TextView textUnqiueCount;
    // List of unique markers
    public static final Object uniqueLock = new Object();
    public static final Map<String, AztecCode> unique = new HashMap<>();
    // Marker which has been selected and should be viewed
    public static String selectedMessage = null;
    // TODO don't use a static method and forget detection if the activity is exited by the user

    // Location in image coordinates that the user is touching
    Point2D_F64 touched = new Point2D_F64();
    boolean touching = false;
    boolean touchProcessed = false;


    // Which standard configuration to use
    Detector detectorType = Detector.STANDARD;
    Spinner spinnerDetector;

    public AztecCodeDetectActivity() {
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
        setProcessing(new MarkerProcessing());
    }

    public void pressedListView( View view ) {
        Intent intent = new Intent(this, AztecCodeListActivity.class );
        startActivity(intent);
    }

    protected class MarkerProcessing extends DemoProcessingAbstract<GrayU8> {

       AztecCodeDetector<GrayU8> detector;

        DogArray<AztecCode> detected = new DogArray<>(AztecCode::new);
        DogArray<AztecCode> failures = new DogArray<>(AztecCode::new);

        Paint colorDetected = new Paint();
        Paint colorFailed = new Paint();
        Path path = new Path();

        int uniqueCount = 0;
        int oldValue = -1;

        public MarkerProcessing() {
            super(GrayU8.class);

            ConfigAztecCode config;

            if (detectorType == Detector.FAST) {
                config = ConfigAztecCode.fast();
            } else {
                config = new ConfigAztecCode();
            }

            detector = FactoryFiducial.aztec(config, GrayU8.class);

            colorDetected.setARGB(0xA0,0,0xFF,0);
            colorDetected.setStyle(Paint.Style.FILL);
            colorFailed.setARGB(0xA0,0xFF,0x11,0x11);
            colorFailed.setStyle(Paint.Style.FILL);
        }

        @Override
        public void initialize(int imageWidth, int imageHeight, int sensorOrientation) {
            touchProcessed = false;
            selectedMessage = null;
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
                            AztecCode marker = detected.get(i);
                            MiscUtil.renderPolygon(marker.bounds,path,canvas,colorDetected);

                            if(touching && Intersection2D_F64.containsConvex(marker.bounds,touched)) {
                                selectedMessage = marker.message;
                            }
                        }
                        for( int i = 0; showFailures && i < failures.size; i++ ) {
                            AztecCode marker = failures.get(i);
                            MiscUtil.renderPolygon(marker.bounds,path,canvas,colorFailed);
                        }
                    }break;

                    case GRAPH:{
                        // TODO implement this in the future
                    }break;
                }
            }

            // touchProcessed is needed to prevent multiple intent from being sent
            if( selectedMessage != null && !touchProcessed ) {
                touchProcessed = true;
                var intent = new Intent(AztecCodeDetectActivity.this, AztecCodeListActivity.class );
                startActivity(intent);
            }
        }

        @Override
        public void process(GrayU8 input) {
            detector.process(input);

            synchronized (uniqueLock) {
                for (AztecCode marker : detector.getDetections()) {
                    if (marker.message == null) {
                        Log.e(TAG, "marker with null message?!?");
                    }
                    if (!unique.containsKey(marker.message)) {
                        Log.i(TAG,"Adding new marker with message of length="+marker.message.length());
                        unique.put(marker.message, new AztecCode().setTo(marker));
                    }
                }
                uniqueCount = unique.size();
            }

            synchronized (lockGui) {
                detected.reset();
                for (AztecCode marker : detector.getDetections()) {
                    detected.grow().setTo(marker);
                }

                failures.reset();
                for (AztecCode marker : detector.getFailures()) {
                    if( marker.failure.ordinal() >= AztecCode.Failure.MESSAGE_ECC.ordinal()) {
                        failures.grow().setTo(marker);
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
