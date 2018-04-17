package org.boofcv.android.recognition;

import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
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
import boofcv.factory.fiducial.FactoryFiducial;
import boofcv.struct.image.GrayU8;

/**
 * Used to detect and read information from QR codes
 */
public class QrCodeDetectActivity extends DemoCamera2Activity {

    // Switches what information is displayed
    Mode mode = Mode.NORMAL;
    // Does it render failed detections too?
    boolean showFailures = true;

    // Where the number of unique messages are listed
    TextView textUnqiueCount;
    // List of unique qr codes
    Map<String,QrCode> unique = new HashMap<>(); // TODO save this across screen rotations

    public QrCodeDetectActivity() {
        super(Resolution.HIGH);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LayoutInflater inflater = getLayoutInflater();
        LinearLayout controls = (LinearLayout)inflater.inflate(R.layout.qrcode_detect_controls,null);

        final ToggleButton toggle = controls.findViewById(R.id.show_failures);
        toggle.setOnCheckedChangeListener((buttonView, isChecked) -> showFailures = isChecked);

        textUnqiueCount = controls.findViewById(R.id.total_unique);
        textUnqiueCount.setText("0");

        setControls(controls);
    }

    @Override
    protected void onResume() {
        super.onResume();
        setProcessing(new QrCodeProcessing() );
    }

    public void pressedListView( View view ) {
        Intent intent = new Intent(this, QrCodeListActivity.class );
        startActivity(intent);
    }

    protected class QrCodeProcessing extends DemoProcessingAbstract<GrayU8> {

        QrCodeDetector<GrayU8> detector = FactoryFiducial.qrcode(null,GrayU8.class);

        FastQueue<QrCode> detected = new FastQueue<>(QrCode.class,true);
        FastQueue<QrCode> failures = new FastQueue<>(QrCode.class,true);

        Paint colorDetected = new Paint();
        Paint colorFailed = new Paint();
        Path path = new Path();


        int uniqueCount = 0;

        public QrCodeProcessing() {
            super(GrayU8.class);

            colorDetected.setARGB(0xA0,0,0xFF,0);
            colorDetected.setStyle(Paint.Style.FILL);
            colorFailed.setARGB(0xA0,0xFF,0x11,0x11);
            colorFailed.setStyle(Paint.Style.FILL);
        }

        @Override
        public void initialize(int imageWidth, int imageHeight) {

        }

        @Override
        public void onDraw(Canvas canvas, Matrix imageToView) {
            canvas.setMatrix(imageToView);
            synchronized (lockGui) {
                textUnqiueCount.setText(uniqueCount+"");

                switch( mode ) {
                    case NORMAL:{
                        for( int i = 0; i < detected.size; i++ ) {
                            QrCode qr = detected.get(i);
                            MiscUtil.renderPolygon(qr.bounds,path,canvas,colorDetected);
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
        }

        @Override
        public void process(GrayU8 input) {
            detector.process(input);

            for (QrCode qr : detector.getDetections()) {
                if( !unique.containsKey(qr.message)) {
                    unique.put(qr.message,qr);
                }
            }
            uniqueCount = unique.size();

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
}
