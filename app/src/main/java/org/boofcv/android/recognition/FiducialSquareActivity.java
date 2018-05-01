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
import org.boofcv.android.misc.MiscUtil;
import org.ddogleg.struct.FastQueue;
import org.ddogleg.struct.GrowQueue_F64;
import org.ddogleg.struct.GrowQueue_I64;

import boofcv.abst.fiducial.CalibrationFiducialDetector;
import boofcv.abst.fiducial.FiducialDetector;
import boofcv.abst.fiducial.SquareBase_to_FiducialDetector;
import boofcv.abst.fiducial.calib.CalibrationDetectorChessboard;
import boofcv.abst.fiducial.calib.CalibrationDetectorCircleHexagonalGrid;
import boofcv.abst.fiducial.calib.CalibrationDetectorCircleRegularGrid;
import boofcv.abst.fiducial.calib.CalibrationDetectorSquareGrid;
import boofcv.abst.geo.calibration.DetectorFiducialCalibration;
import boofcv.alg.distort.LensDistortionOps;
import boofcv.alg.geo.PerspectiveOps;
import boofcv.android.ConvertBitmap;
import boofcv.android.VisualizeImageData;
import boofcv.struct.calib.CameraPinholeRadial;
import boofcv.struct.image.GrayU8;
import boofcv.struct.image.ImageBase;
import georegression.struct.point.Point2D_F32;
import georegression.struct.point.Point2D_F64;
import georegression.struct.point.Point3D_F64;
import georegression.struct.se.Se3_F64;
import georegression.transform.se.SePointOps_F64;

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

		Paint paintSelected = new Paint();
		Paint paintLine0 = new Paint();
		Paint paintLine1 = new Paint();
		Paint paintLine2 = new Paint();
		Paint paintLine3 = new Paint();
		private Paint paintTextVideo = new Paint(); // drawn in image coordinates
		private Paint paintTextView = new Paint(); // text drawn directory to screen
		private Paint paintTextBorder = new Paint();

		Rect bounds = new Rect();

		final FastQueue<Se3_F64> listPose = new FastQueue<>(Se3_F64.class,true);
		final GrowQueue_F64 listWidths = new GrowQueue_F64();
		final GrowQueue_I64 listIDs = new GrowQueue_I64();
		CameraPinholeRadial intrinsic;

		protected FiducialProcessor( FiducialDetector<T> detector ) {
			super(detector.getInputType());

			this.detector = detector;

			paintSelected.setColor(Color.argb(0xFF / 2, 0xFF, 0, 0));

			paintLine0.setColor(Color.RED);
			paintLine0.setFlags(Paint.ANTI_ALIAS_FLAG);
			paintLine1.setColor(Color.BLACK);
			paintLine1.setFlags(Paint.ANTI_ALIAS_FLAG);
			paintLine2.setColor(Color.BLUE);
			paintLine2.setFlags(Paint.ANTI_ALIAS_FLAG);
			paintLine3.setColor(Color.GREEN);
			paintLine3.setFlags(Paint.ANTI_ALIAS_FLAG);

			// Create out paint to use for drawing
			paintTextVideo.setARGB(255, 255, 100, 100);
			paintTextView.setARGB(255, 255, 100, 100);
			paintTextView.setTextSize(24*displayMetrics.density);

			paintTextBorder.setARGB(255, 0, 0, 0);
			paintTextBorder.setStyle(Paint.Style.STROKE);
		}

		@Override
		public void initialize(int imageWidth, int imageHeight, int sensorOrientation)
		{
			// sanity check requirements
			if( imageWidth == 0 || imageHeight == 0 )
				throw new RuntimeException("BUG! Called with zero width and height");

			if( lookupIntrinsics() == null ) {
				Toast.makeText(FiducialSquareActivity.this, "Calibrate camera for better results!", Toast.LENGTH_LONG).show();
			}

			// the adjustment requires knowing what the camera's resolution is. The camera
			// must be initialized at this point
			paintTextVideo.setTextSize(30*cameraToDisplayDensity);
			paintTextBorder.setTextSize(30*cameraToDisplayDensity);
			paintTextBorder.setStrokeWidth(3*cameraToDisplayDensity);
			paintLine0.setStrokeWidth(4f*cameraToDisplayDensity);
			paintLine1.setStrokeWidth(4f*cameraToDisplayDensity);
			paintLine2.setStrokeWidth(4f*cameraToDisplayDensity);
			paintLine3.setStrokeWidth(4f*cameraToDisplayDensity);

			double fov[] = cameraNominalFov();
			intrinsic = MiscUtil.checkThenInventIntrinsic(app,imageWidth,imageHeight,fov[0],fov[1]);
			detector.setLensDistortion(LensDistortionOps.narrow(intrinsic),imageWidth,imageHeight);
		}

		@Override
		public void onDraw(Canvas canvas, Matrix imageToView) {
			synchronized (bitmapLock) {
				canvas.drawBitmap(bitmap,imageToView,null);
			}

			canvas.save();
			canvas.concat(imageToView);
			synchronized (listPose) {
				for ( int i = 0; i < listPose.size; i++ ) {
					double width = listWidths.get(i);
					long id = listIDs.get(i);
					drawCube(id, listPose.get(i), intrinsic, width, canvas);
				}
			}

			canvas.restore();
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

			synchronized (bitmapLock) {
				if (!showThreshold) {
					ConvertBitmap.boofToBitmap(input, bitmap, bitmapTmp);
				} else {
					GrayU8 binary;
					if (detector instanceof CalibrationFiducialDetector) {
						DetectorFiducialCalibration a = ((CalibrationFiducialDetector) detector).getCalibDetector();
						if (a instanceof CalibrationDetectorChessboard) {
							binary = ((CalibrationDetectorChessboard) a).getAlgorithm().getBinary();
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
			}

			// save the results for displaying in the UI thread
			synchronized (listPose) {
				listPose.reset();
				listWidths.reset();
				listIDs.reset();

				for (int i = 0; i < detector.totalFound(); i++) {
					detector.getFiducialToCamera(i, targetToCamera);
					listPose.grow().set(targetToCamera);
					listWidths.add(detector.getWidth(i));
					listIDs.add(detector.getId(i));
				}
			}
		}

		/**
		 * Draws a flat cube to show where the square fiducial is on the image
		 *
		 */
		public void drawCube( long number , Se3_F64 targetToCamera , CameraPinholeRadial intrinsic , double width ,
							  Canvas canvas )
		{
			double r = width/2.0;
			Point3D_F64 corners[] = new Point3D_F64[8];
			corners[0] = new Point3D_F64(-r,-r,0);
			corners[1] = new Point3D_F64( r,-r,0);
			corners[2] = new Point3D_F64( r, r,0);
			corners[3] = new Point3D_F64(-r, r,0);
			corners[4] = new Point3D_F64(-r,-r,r);
			corners[5] = new Point3D_F64( r,-r,r);
			corners[6] = new Point3D_F64( r, r,r);
			corners[7] = new Point3D_F64(-r, r,r);

			Point2D_F32 pixel[] = new Point2D_F32[8];
			Point2D_F64 p = new Point2D_F64();
			for (int i = 0; i < 8; i++) {
				Point3D_F64 c = corners[i];
				SePointOps_F64.transform(targetToCamera, c, c);
				PerspectiveOps.convertNormToPixel(intrinsic, c.x / c.z, c.y / c.z, p);
				pixel[i] = new Point2D_F32((float)p.x,(float)p.y);
			}

			Point3D_F64 centerPt = new Point3D_F64();

			SePointOps_F64.transform(targetToCamera, centerPt, centerPt);
			PerspectiveOps.convertNormToPixel(intrinsic,
					centerPt.x / centerPt.z, centerPt.y / centerPt.z, p);
			Point2D_F32 centerPixel  = new Point2D_F32((float)p.x,(float)p.y);

			// red
			drawLine(canvas,pixel[0],pixel[1],paintLine0);
			drawLine(canvas,pixel[1],pixel[2],paintLine0);
			drawLine(canvas,pixel[2],pixel[3],paintLine0);
			drawLine(canvas,pixel[3],pixel[0],paintLine0);

			// black
			drawLine(canvas,pixel[0],pixel[4],paintLine1);
			drawLine(canvas,pixel[1],pixel[5],paintLine1);
			drawLine(canvas,pixel[2],pixel[6],paintLine1);
			drawLine(canvas,pixel[3],pixel[7],paintLine1);

			drawLine(canvas,pixel[4],pixel[5],paintLine2);
			drawLine(canvas,pixel[5],pixel[6],paintLine2);
			drawLine(canvas,pixel[6],pixel[7],paintLine2);
			drawLine(canvas,pixel[7],pixel[4],paintLine3);

			String numberString = ""+number;

			paintTextVideo.getTextBounds(numberString,0,numberString.length(),bounds);

			int textLength = bounds.width();
			int textHeight = bounds.height();

			canvas.drawText(numberString, centerPixel.x-textLength/2,centerPixel.y+textHeight/2, paintTextBorder);
			canvas.drawText(numberString, centerPixel.x-textLength/2,centerPixel.y+textHeight/2, paintTextVideo);
		}

		private void renderDrawText( Canvas canvas ) {
			paintTextView.getTextBounds(textToDraw,0, textToDraw.length(),bounds);

			int textLength = bounds.width();
			int textHeight = bounds.height();

			int x0 = canvas.getWidth()/2 - textLength/2;
			int y0 = canvas.getHeight()/2 + textHeight/2;

			canvas.drawText(textToDraw, x0, y0, paintTextView);
		}

		private void drawLine( Canvas canvas , Point2D_F32 a , Point2D_F32 b , Paint color ) {
			canvas.drawLine(a.x,a.y,b.x,b.y,color);
		}
	}
}
