package org.boofcv.android.recognition;

import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.Toast;
import android.widget.ToggleButton;

import org.boofcv.android.DemoBitmapCamera2Activity;
import org.boofcv.android.DemoProcessingAbstract;
import org.boofcv.android.R;
import org.boofcv.android.misc.RenderCube3D;
import org.ddogleg.struct.FastQueue;
import org.ddogleg.struct.GrowQueue_F64;
import org.ddogleg.struct.GrowQueue_I64;

import boofcv.abst.fiducial.CalibrationFiducialDetector;
import boofcv.abst.fiducial.FiducialDetector;
import boofcv.abst.fiducial.FiducialStability;
import boofcv.abst.fiducial.SquareBase_to_FiducialDetector;
import boofcv.abst.fiducial.calib.CalibrationDetectorChessboard;
import boofcv.abst.fiducial.calib.CalibrationDetectorCircleHexagonalGrid;
import boofcv.abst.fiducial.calib.CalibrationDetectorCircleRegularGrid;
import boofcv.abst.fiducial.calib.CalibrationDetectorSquareGrid;
import boofcv.abst.geo.calibration.DetectorFiducialCalibration;
import boofcv.android.ConvertBitmap;
import boofcv.android.VisualizeImageData;
import boofcv.factory.distort.LensDistortionFactory;
import boofcv.struct.calib.CameraPinholeBrown;
import boofcv.struct.image.GrayU8;
import boofcv.struct.image.ImageBase;
import georegression.struct.se.Se3_F64;

/**
 * Base class for square fiducials
 *
 * @author Peter Abeles
 */
public abstract class FiducialSquareActivity extends DemoBitmapCamera2Activity
		implements View.OnTouchListener
{
	public static final String TAG = "FiducialSquareActivity";

	final Object lock = new Object();
	volatile boolean robust = true;
	volatile int binaryThreshold = 100;

	Se3_F64 targetToCamera = new Se3_F64();

	Class help;

	// this text is displayed
	protected String textToDraw = "";

	// If true then the background will be the thresholded image
	protected boolean showThreshold = false;

	// if false it won't process images
	protected volatile boolean detectFiducial = true;

	protected boolean disableControls = false;

	// Which layout it should use
	int layout = R.layout.fiducial_controls;

	FiducialSquareActivity(Class help) {
		super(Resolution.MEDIUM);
		super.changeResolutionOnSlow = true;
		this.help = help;
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		LayoutInflater inflater = getLayoutInflater();
		LinearLayout controls = (LinearLayout)inflater.inflate(layout,null);

		final ToggleButton toggle = controls.findViewById(R.id.toggle_robust);
		final SeekBar seek = controls.findViewById(R.id.slider_threshold);

		if( disableControls ) {
			toggle.setEnabled(false);
			seek.setEnabled(false);
		} else {
			robust = toggle.isChecked();
			binaryThreshold = seek.getProgress();

			seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
				@Override
				public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
					synchronized (lock) {
						binaryThreshold = progress;
						createNewProcessor();
					}
				}
				@Override public void onStartTrackingTouch(SeekBar seekBar) {}
				@Override public void onStopTrackingTouch(SeekBar seekBar) {}
			});
			toggle.setOnCheckedChangeListener((buttonView, isChecked) -> {
                synchronized (lock) {
                    robust = isChecked;
                    if (robust) {
                        seek.setEnabled(false);
                    } else {
                        seek.setEnabled(true);
                    }
					createNewProcessor();
                }
            });
		}

		setControls(controls);
		displayView.setOnTouchListener(this);
	}

	public void pressedHelp( View view ) {
		Intent intent = new Intent(this, help );
		startActivity(intent);
	}

	@Override
	protected void onResume() {
		super.onResume();
	}

	@Override
	public boolean onTouch(View v, MotionEvent event) {
		if( MotionEvent.ACTION_DOWN == event.getActionMasked()) {
			showThreshold = !showThreshold;
			return true;
		}
		return false;
	}

	@Override
	public void createNewProcessor() {
		if( detectFiducial )
			setProcessing(new FiducialProcessor(createDetector()) );
	}

	protected abstract FiducialDetector<GrayU8> createDetector();

	protected class FiducialProcessor<T extends ImageBase<T>> extends DemoProcessingAbstract<T>
	{
		FiducialDetector<T> detector;

		Paint paintStability = new Paint();
		Paint paintStabilityBad = new Paint();

		RenderCube3D renderCube = new RenderCube3D();
		Paint paintSelected = new Paint();
		private Paint paintTextView = new Paint(); // text drawn directory to screen

		Rect bounds = new Rect();

		double currentStability;
		double maxStability = 0.3;
		FiducialStability stabilityResults = new FiducialStability();

		final FastQueue<Se3_F64> listPose = new FastQueue<>(Se3_F64.class,true);
		final GrowQueue_F64 listWidths = new GrowQueue_F64();
		final GrowQueue_I64 listIDs = new GrowQueue_I64();
		CameraPinholeBrown intrinsic;

		protected FiducialProcessor( FiducialDetector<T> detector ) {
			super(detector.getInputType());

			this.detector = detector;

			paintStability.setColor(Color.argb(0xFF / 2, 0, 0xFF, 0));
			paintStability.setStyle(Paint.Style.FILL);
			paintStabilityBad.setColor(Color.argb(0xFF / 2, 0xFF, 0, 0));
			paintStabilityBad.setStyle(Paint.Style.FILL);

			paintSelected.setColor(Color.argb(0xFF / 2, 0xFF, 0, 0));


			// Create out paint to use for drawing
			paintTextView.setARGB(255, 255, 100, 100);

		}

		@Override
		public void initialize(int imageWidth, int imageHeight, int sensorOrientation)
		{
			// sanity check requirements
			if( imageWidth == 0 || imageHeight == 0 )
				throw new RuntimeException("BUG! Called with zero width and height");

			if( !isCameraCalibrated() ) {
				Toast.makeText(FiducialSquareActivity.this, "Calibrate camera for better results!", Toast.LENGTH_LONG).show();
			}

			// the adjustment requires knowing what the camera's resolution is. The camera
			// must be initialized at this point
			paintTextView.setTextSize(24*cameraToDisplayDensity);

			renderCube.initialize(cameraToDisplayDensity);

			intrinsic = lookupIntrinsics();
			detector.setLensDistortion(LensDistortionFactory.narrow(intrinsic),imageWidth,imageHeight);

//			Log.i(TAG,"intrinsic fx = "+intrinsic.fx+" fy = "+intrinsic.fy);
//			Log.i(TAG,"intrinsic cx = "+intrinsic.cx+" cy = "+intrinsic.cy);
//			Log.i(TAG,"intrinsic width = "+intrinsic.width+"  imgW = "+imageWidth);

		}

		@Override
		public void onDraw(Canvas canvas, Matrix imageToView) {
			canvas.drawBitmap(bitmap,imageToView,null);

			double stability;

			canvas.save();
			canvas.concat(imageToView);
			synchronized (listPose) {
				for ( int i = 0; i < listPose.size; i++ ) {
					double width = listWidths.get(i);
					long id = listIDs.get(i);
					renderCube.drawCube(""+id, listPose.get(i), intrinsic, width, canvas);
				}

				stability = currentStability/maxStability;
			}

			canvas.restore();

			// draw stability bar
			float w = canvas.getWidth()/10;
			float bottom = canvas.getHeight()/4;
			if( stability > 0.5 )
				canvas.drawRect(0,(float)((1.0 - stability)*bottom),w,bottom,paintStabilityBad);
			else
				canvas.drawRect(0,(float)((1.0 - stability)*bottom),w,bottom,paintStability);

			if( textToDraw != null ) {
				renderDrawText(canvas);
			}
		}

		@Override
		public void process( T input )
		{
			if( !detectFiducial) {
				ConvertBitmap.boofToBitmap(input, bitmap, bitmapTmp);
				return;
			}

			detector.detect(input);


			if (!showThreshold) {
				ConvertBitmap.boofToBitmap(input, bitmap, bitmapTmp);
			} else {
				GrayU8 binary;
				if (detector instanceof CalibrationFiducialDetector) {
					DetectorFiducialCalibration a = ((CalibrationFiducialDetector) detector).getCalibDetector();
					if (a instanceof CalibrationDetectorChessboard) {
						binary = ((CalibrationDetectorChessboard) a).getDetector().getDetector().getBinary();
					} else if( a instanceof CalibrationDetectorSquareGrid ){
						binary = ((CalibrationDetectorSquareGrid) a).getAlgorithm().getBinary();
					} else if( a instanceof CalibrationDetectorCircleHexagonalGrid){
						binary = ((CalibrationDetectorCircleHexagonalGrid) a).getDetector().getBinary();
					} else if( a instanceof CalibrationDetectorCircleRegularGrid){
						binary = ((CalibrationDetectorCircleRegularGrid) a).getDetector().getBinary();
					} else {
						throw new RuntimeException("Unknown class "+a.getClass().getSimpleName());
					}
				} else {
					binary = ((SquareBase_to_FiducialDetector) detector).getAlgorithm().getBinary();
				}
				VisualizeImageData.binaryToBitmap(binary, false,bitmap, bitmapTmp);
			}

			// save the results for displaying in the UI thread
			synchronized (listPose) {
				listPose.reset();
				listWidths.reset();
				listIDs.reset();

				currentStability = 0;
				for (int i = 0; i < detector.totalFound(); i++) {
					detector.computeStability(i,1.5,stabilityResults);
					currentStability = Math.max(stabilityResults.orientation,currentStability);

					detector.getFiducialToCamera(i, targetToCamera);
					listPose.grow().set(targetToCamera);
					listWidths.add(detector.getWidth(i));
					listIDs.add(detector.getId(i));
				}
			}
		}

		private void renderDrawText( Canvas canvas ) {
			paintTextView.getTextBounds(textToDraw,0, textToDraw.length(),bounds);

			int textLength = bounds.width();
			int textHeight = bounds.height();

			int x0 = canvas.getWidth()/2 - textLength/2;
			int y0 = canvas.getHeight()/2 + textHeight/2;

			canvas.drawText(textToDraw, x0, y0, paintTextView);
		}
	}
}
