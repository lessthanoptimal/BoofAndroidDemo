package org.boofcv.android.calib;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Bundle;
import android.util.Log;
import android.view.GestureDetector;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.boofcv.android.DemoProcessingAbstract;
import org.boofcv.android.R;
import org.boofcv.android.recognition.ConfigAllCalibration;
import org.boofcv.android.recognition.SelectCalibrationFiducial;
import org.boofcv.android.tracker.PointTrackerDisplayActivity;
import org.ddogleg.struct.FastQueue;

import java.util.ArrayList;
import java.util.List;

import boofcv.abst.fiducial.calib.CalibrationDetectorChessboard;
import boofcv.abst.fiducial.calib.CalibrationDetectorCircleHexagonalGrid;
import boofcv.abst.fiducial.calib.CalibrationDetectorCircleRegularGrid;
import boofcv.abst.fiducial.calib.CalibrationDetectorSquareGrid;
import boofcv.abst.fiducial.calib.CalibrationPatterns;
import boofcv.abst.geo.calibration.DetectorFiducialCalibration;
import boofcv.alg.fiducial.calib.chess.DetectChessboardFiducial;
import boofcv.alg.fiducial.calib.circle.DetectCircleHexagonalGrid;
import boofcv.alg.fiducial.calib.circle.DetectCircleRegularGrid;
import boofcv.alg.fiducial.calib.grid.DetectSquareGridFiducial;
import boofcv.alg.geo.calibration.CalibrationObservation;
import boofcv.android.ConvertBitmap;
import boofcv.android.VisualizeImageData;
import boofcv.factory.fiducial.FactoryFiducialCalibration;
import boofcv.struct.geo.PointIndex2D_F64;
import boofcv.struct.image.GrayF32;
import boofcv.struct.image.GrayU8;
import georegression.metric.UtilAngle;
import georegression.struct.curve.EllipseRotated_F64;
import georegression.struct.point.Point2D_F64;
import georegression.struct.point.Point2D_I32;
import georegression.struct.shapes.Polygon2D_F64;

/**
 * Activity for collecting images of calibration targets. The user must first specify the type of target it is
 * searching for and click the screen to add the image.
 *
 * @author Peter Abeles
 */
public class CalibrationActivity extends PointTrackerDisplayActivity
{
	public static final String TAG = "CalibrationActivity";

	public static ConfigAllCalibration cc = new ConfigAllCalibration();
	Paint paintPoint = new Paint();
	Paint paintFailed = new Paint();

	// Storage for calibration info
	List<CalibrationObservation> shots;

	// user has requested that the next image be processed for the target
	boolean captureRequested = false;

	// user has requested that the most recent image be removed from data list
	boolean removeRequested = false;

	// displays the number of calibration images captured
	TextView textCount;

	// true if detect failed
	boolean showDetectDebug;

	// the user requests that the images be processed
	boolean processRequested = false;

	// pause the display so that it doesn't change until after this time
	long timeResume;

	// handles gestures
	GestureDetector mDetector;

	public CalibrationActivity() {
		super(Resolution.R640x480);

		// this activity wants control over what is shown
		super.bitmapMode = BitmapMode.NONE;

		paintPoint.setColor(Color.RED);
		paintPoint.setStyle(Paint.Style.FILL);

		paintFailed.setColor(Color.CYAN);
		paintFailed.setStyle(Paint.Style.STROKE);
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		LayoutInflater inflater = getLayoutInflater();
		LinearLayout controls = (LinearLayout)inflater.inflate(R.layout.calibration_view,null);

		textCount = controls.findViewById(R.id.text_total);

		shots = new ArrayList<>();

		setControls(controls);
		mDetector = new GestureDetector(this, new MyGestureDetector());
		displayView.setOnTouchListener((v, event) -> {
            mDetector.onTouchEvent(event);
            return true;
        });

		SelectCalibrationFiducial dialog = new SelectCalibrationFiducial(cc);
		dialog.show(this, this::createNewProcessor);
	}

	@Override
	protected void onResume() {
		super.onResume();

	}

	@Override
	protected void onCameraResolutionChange(int width, int height, int sensorOrientation) {
		super.onCameraResolutionChange(width, height,sensorOrientation);
		if (bitmap.getWidth() != width || bitmap.getHeight() != height)
			bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
		bitmapTmp = ConvertBitmap.declareStorage(bitmap, bitmapTmp);
		if( isCameraCalibrated() ) {
			runOnUiThread(()-> Toast.makeText(this, "Camera already calibrated", Toast.LENGTH_SHORT).show());
		}
	}

	/**
	 * Configures the detector, configures target description for calibration and starts the detector thread.
	 */
	@Override
	public void createNewProcessor() {
		DetectorFiducialCalibration detector;

		if( cc.targetType == CalibrationPatterns.CHESSBOARD ) {
			detector = FactoryFiducialCalibration.chessboard(cc.chessboard);
		} else if( cc.targetType == CalibrationPatterns.SQUARE_GRID ) {
			detector = FactoryFiducialCalibration.squareGrid(cc.squareGrid);
		} else if( cc.targetType == CalibrationPatterns.CIRCLE_HEXAGONAL ){
			detector = FactoryFiducialCalibration.circleHexagonalGrid(cc.hexagonal);
		} else if( cc.targetType == CalibrationPatterns.CIRCLE_GRID ){
			detector = FactoryFiducialCalibration.circleRegularGrid(cc.circleGrid);
		} else {
			throw new RuntimeException("Unknown targetType "+cc.targetType);
		}
		CalibrationComputeActivity.targetLayout = detector.getLayout();
		setProcessing(new DetectTarget(detector));
	}

	public void pressedOK( View view ) {
		processRequested = true;
	}

	public void pressedRemove( View view ) {
		removeRequested = true;
	}

	public void pressedHelp( View view ) {
		Intent intent = new Intent(this, CalibrationHelpActivity.class);
		startActivity(intent);
	}

	/**
	 * Checks to see if there are enough images and launches the activity for computing intrinsic parameters.
	 * Only call from a thread where 'shots' is not going to be modified
	 */
	private void handleProcessRequest() {
		if( shots.size() < 3 ) {
			Toast.makeText(this, "Need at least three images.", Toast.LENGTH_SHORT).show();
		} else {
			CalibrationComputeActivity.images = shots;
			Intent intent = new Intent(this, CalibrationComputeActivity.class);
			startActivity(intent);
		}
	}

	protected class MyGestureDetector extends GestureDetector.SimpleOnGestureListener
	{
		@Override
		public boolean onDown(MotionEvent e) {
			captureRequested = true;
			return true;
		}
	}

	private class DetectTarget extends DemoProcessingAbstract<GrayF32> {

		DetectorFiducialCalibration detector;

		FastQueue<Point2D_F64> pointsGui = new FastQueue<Point2D_F64>(Point2D_F64.class,true);

		final Object lockGUI = new Object();
		List<List<Point2D_I32>> debugQuads = new ArrayList<>();
		List<EllipseRotated_F64> debugEllipses = new ArrayList<>();
		float radius;

		protected DetectTarget( DetectorFiducialCalibration detector ) {
			super(GrayF32.class);
			this.detector = detector;
		}

		@Override
		public void initialize(int imageWidth, int imageHeight, int sensorOrientation) {
			float density = cameraToDisplayDensity;
			paintFailed.setStrokeWidth(7f*density);
			radius = 6*density;
		}

		@Override
		public void onDraw(Canvas canvas, Matrix imageToView) {
			canvas.drawBitmap(bitmap, imageToView, null);

			if( processRequested ) {
				processRequested = false;
				handleProcessRequest();
				return;
			}

			synchronized (lockGUI) {
				canvas.concat(imageToView);

				// draw shapes for debugging purposes
				for (List<Point2D_I32> l : debugQuads) {
					for (int i = 1; i < l.size(); i++) {
						Point2D_I32 c0 = l.get(i - 1);
						Point2D_I32 c1 = l.get(i);
						canvas.drawLine(c0.x, c0.y, c1.x, c1.y, paintFailed);
					}
					Point2D_I32 c0 = l.get(0);
					Point2D_I32 c1 = l.get(l.size() - 1);
					canvas.drawLine(c0.x, c0.y, c1.x, c1.y, paintFailed);
				}

				for (EllipseRotated_F64 e : debugEllipses) {

					float phi = (float) UtilAngle.radianToDegree(e.phi);

					float x0 = (float) (e.center.x - e.a);
					float y0 = (float) (e.center.y - e.b);
					float x1 = (float) (e.center.x + e.a);
					float y1 = (float) (e.center.y + e.b);

					canvas.save();
					canvas.rotate(phi, (float) e.center.x, (float) e.center.y);
//					r.set(cx-w,cy-h,cx+w+1,cy+h+1);
					canvas.drawOval(new RectF(x0, y0, x1, y1), paintFailed);
					canvas.restore();
				}

				// draw detected calibration points
				for (int i = 0; i < pointsGui.size(); i++) {
					Point2D_F64 p = pointsGui.get(i);
					canvas.drawCircle((float) p.x, (float) p.y, radius, paintPoint);
				}
			}
		}

		@Override
		public void process(GrayF32 input) {
			// User requested that the most recently processed image be removed
			if( removeRequested ) {
				removeRequested = false;
				if( shots.size() > 0 )  {
					shots.remove( shots.size()-1 );
					updateShotCountInUiThread();
				}
			}

			if( timeResume > System.currentTimeMillis() )
				return;


			GrayU8 binary = null;
			boolean detected = false;
			showDetectDebug = false;
			if( captureRequested ) {
				captureRequested = false;
				long before = System.currentTimeMillis();
				detected = collectMeasurement(input);
				long after = System.currentTimeMillis();
				Log.i(TAG,"detection time "+(after-before)+" (ms)");
			}

			// safely copy data into data structures used by GUI thread
			synchronized ( lockGUI ) {
				pointsGui.reset();
				debugQuads.clear();
				debugEllipses.clear();
				if( detected ) {
					CalibrationObservation found = detector.getDetectedPoints();
					for( PointIndex2D_F64 p : found.points )
						pointsGui.grow().set(p);
				} else if( showDetectDebug ) {
					// show binary image to aid in debugging and detected rectangles
					if( detector instanceof CalibrationDetectorChessboard) {
						DetectChessboardFiducial<GrayF32> alg = ((CalibrationDetectorChessboard) detector).getAlgorithm();
						extractQuads(alg.getFindSeeds().getDetectorSquare().getPolygons(null,null));
						binary = alg.getBinary();
					} else if( detector instanceof CalibrationDetectorSquareGrid) {
						DetectSquareGridFiducial<GrayF32> alg = ((CalibrationDetectorSquareGrid) detector).getAlgorithm();
						extractQuads(alg.getDetectorSquare().getPolygons(null,null));
						binary = alg.getBinary();
					} else if( detector instanceof CalibrationDetectorCircleHexagonalGrid) {
						DetectCircleHexagonalGrid<GrayF32> alg = ((CalibrationDetectorCircleHexagonalGrid) detector).getDetector();
						debugEllipses.clear();
						debugEllipses.addAll(alg.getEllipseDetector().getFoundEllipses(null));
						binary = alg.getBinary();
					} else if( detector instanceof CalibrationDetectorCircleRegularGrid) {
						DetectCircleRegularGrid<GrayF32> alg = ((CalibrationDetectorCircleRegularGrid) detector).getDetector();
						debugEllipses.clear();
						debugEllipses.addAll(alg.getEllipseDetector().getFoundEllipses(null));
						binary = alg.getBinary();
					}
				}
			}

			if( binary != null )
				VisualizeImageData.binaryToBitmap(binary,false,bitmap,bitmapTmp);
			else
				ConvertBitmap.grayToBitmap(input, bitmap, bitmapTmp);
		}

		protected void extractQuads( List<Polygon2D_F64> squares ) {
			debugQuads.clear();

			if( squares != null ) {
				for( Polygon2D_F64 b : squares ) {

					List<Point2D_I32> l = new ArrayList<Point2D_I32>();
					for( int i = 0; i < b.size(); i++ ) {
						Point2D_F64 c = b.get(i);
						l.add( new Point2D_I32((int)c.x,(int)c.y) );
					}
					debugQuads.add(l);
				}
			}
		}

		/**
		 * Detect calibration targets in the image and save the results.  Pause the display so the
		 * user can see the results]
		 */
		private boolean collectMeasurement(GrayF32 gray)
		{
			boolean success = detector.process(gray);

			// pause the display to provide feed back to the user
			timeResume = System.currentTimeMillis()+1500;

			if( success ) {
				shots.add(detector.getDetectedPoints());
				updateShotCountInUiThread();
				return true;
			}  else {
				showDetectDebug = true;
				return false;
			}
		}

		/**
		 * Call when the number of shots needs to be updated from outside an UI thread
		 */
		private void updateShotCountInUiThread() {
			final int size = shots.size();
			runOnUiThread(() -> textCount.setText(""+size));
		}
	}
}