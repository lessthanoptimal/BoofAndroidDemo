package org.boofcv.android.segmentation;

import android.graphics.Canvas;
import android.graphics.Matrix;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.widget.LinearLayout;
import android.widget.SeekBar;

import org.boofcv.android.DemoBitmapCamera2Activity;
import org.boofcv.android.DemoProcessingAbstract;
import org.boofcv.android.R;

import java.nio.ByteBuffer;

import boofcv.alg.color.ColorFormat;
import boofcv.android.ConvertBitmap;
import boofcv.android.ImplConvertBitmap;
import boofcv.struct.image.ImageType;
import boofcv.struct.image.InterleavedU8;
import georegression.struct.point.Point2D_F64;

/**
 * User clicks on the image and the color at that location
 * is then used to segment the image and the video feed
 */
public class ColorHistogramSegmentationActivity extends DemoBitmapCamera2Activity {
    private static final String TAG = "ColorSegmentation";

    Mode mode = Mode.VIDEO;
    // location in image pixels that was touched
    Point2D_F64 sampleLocation = new Point2D_F64();
    // U and V value being sampled
    int valueU;
    int valueV;

    // How far from the color value can it accept
    volatile int tolerance;

    public ColorHistogramSegmentationActivity() {
        super(Resolution.MEDIUM);

    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LayoutInflater inflater = getLayoutInflater();
        LinearLayout controls = (LinearLayout)inflater.inflate(R.layout.color_segment_controls,null);

        SeekBar seek = controls.findViewById(R.id.slider_tolerance);

        setControls(controls);

        tolerance = seek.getProgress();
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progess, boolean b) {
                tolerance = progess;
            }

            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        displayView.setOnTouchListener((view, event) -> {
            if( event.getAction() != MotionEvent.ACTION_DOWN ) {
                return false;
            }

            if( mode == Mode.VIDEO ) {
                applyToPoint(viewToImage,event.getX(),event.getY(),sampleLocation);
                mode = Mode.SAMPLE_COLOR;
                return true;
            } else if( mode == Mode.SEGMENTING ) {
                mode = Mode.VIDEO;
                return true;
            }

            return false;
        });
    }

    @Override
    public void createNewProcessor() {
        setProcessing(new SegmentProcess());
    }

    private class SegmentProcess extends DemoProcessingAbstract<InterleavedU8>
    {

        public SegmentProcess() {
            super(ImageType.il(3,InterleavedU8.class));
        }

        @Override
        public void initialize(int imageWidth, int imageHeight, int sensorOrientation) {
        }

        @Override
        public void onDraw(Canvas canvas, Matrix imageToView) {
            drawBitmap(canvas,imageToView);
        }

        @Override
        public void process(InterleavedU8 input) {

            if( mode == Mode.SAMPLE_COLOR ) {
                int x = (int)sampleLocation.x, y =(int)sampleLocation.y;
                if( input.isInBounds(x,y)) {
                    int index = input.getIndex(x,y,1);
                    valueU = input.data[index]&0xFF;
                    valueV = input.data[index+1]&0xFF;
                    mode = Mode.SEGMENTING;
                } else {
                    mode = Mode.VIDEO;
                }
            }

            if( mode == Mode.VIDEO ) {
                ConvertBitmap.boofToBitmap(ColorFormat.YUV, input, bitmap, bitmapTmp);
            } else if( mode == Mode.SEGMENTING ) {
                int tolerance = ColorHistogramSegmentationActivity.this.tolerance;
                tolerance *= tolerance;
                ImplConvertBitmap.interleavedYuvToArgb8888(input,bitmapTmp);
                int indexDst = 0;
                for (int y = 0; y < input.height; y++) {
                    int indexSrc = input.startIndex + y*input.stride;
                    for (int x = 0; x < input.width; x++) {
                        int Y = input.data[indexSrc++]&0xFF;
                        int u = input.data[indexSrc++]&0xFF;
                        int v = input.data[indexSrc++]&0xFF;

                        int du = valueU-u;
                        int dv = valueV-v;

                        // if Y is close to saturation and color information isn't reliable
                        // make it black too
                        if( Y < 15 || Y > 240 || du*du + dv*dv > tolerance ) {
                            bitmapTmp[indexDst++] = 0;
                            bitmapTmp[indexDst++] = 0;
                            bitmapTmp[indexDst++] = 0;
                            bitmapTmp[indexDst++] = (byte)255;
                        } else {
                            indexDst += 4;
                        }
                    }
                }
                bitmap.copyPixelsFromBuffer(ByteBuffer.wrap(bitmapTmp));
            }
        }

        @Override
        public ColorFormat getColorFormat() {
            return ColorFormat.YUV;
        }
    }

    enum Mode {
        VIDEO,
        SAMPLE_COLOR,
        SEGMENTING
    }
}
