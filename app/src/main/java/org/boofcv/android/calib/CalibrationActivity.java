package org.boofcv.android.calib;

import android.app.Dialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Bundle;
import android.view.GestureDetector;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.boofcv.android.DemoMain;
import org.boofcv.android.R;
import org.boofcv.android.recognition.SelectCalibrationFiducial;
import org.boofcv.android.tracker.PointTrackerDisplayActivity;
import org.ddogleg.struct.FastQueue;

import java.util.ArrayList;
import java.util.List;

import boofcv.abst.fiducial.calib.CalibrationDetectorChessboard;
import boofcv.abst.fiducial.calib.CalibrationDetectorCircleAsymmGrid;
import boofcv.abst.fiducial.calib.CalibrationDetectorSquareGrid;
import boofcv.abst.fiducial.calib.CalibrationPatterns;
import boofcv.abst.fiducial.calib.ConfigChessboard;
import boofcv.abst.fiducial.calib.ConfigCircleAsymmetricGrid;
import boofcv.abst.fiducial.calib.ConfigSquareGrid;
import boofcv.abst.geo.calibration.DetectorFiducialCalibration;
import boofcv.alg.fiducial.calib.chess.DetectChessboardFiducial;
import boofcv.alg.fiducial.calib.circle.DetectAsymmetricCircleGrid;
import boofcv.alg.fiducial.calib.grid.DetectSquareGridFiducial;
import boofcv.alg.geo.calibration.CalibrationObservation;
import boofcv.android.ConvertBitmap;
import boofcv.android.VisualizeImageData;
import boofcv.android.gui.VideoRenderProcessing;
import boofcv.factory.fiducial.FactoryFiducialCalibration;
import boofcv.struct.geo.PointIndex2D_F64;
import boofcv.struct.image.GrayF32;
import boofcv.struct.image.ImageType;
import georegression.metric.UtilAngle;
import georegression.struct.point.Point2D_F64;
import georegression.struct.point.Point2D_I32;
import georegression.struct.shapes.EllipseRotated_F64;
import georegression.struct.shapes.Polygon2D_F64;

/**
 * Activity for collecting images of calibration targets. The user must first specify the type of target it is
 * searching for and click the screen to add the image.
 *
 * @author Peter Abeles
 */
public class CalibrationActivity extends PointTrackerDisplayActivity
{
	public static final int TARGET_DIALOG = 10;

	public static CalibrationPatterns targetType = CalibrationPatterns.CHESSBOARD;
	public static int numRows = 5;
	public static int numCols = 7;

	Paint paintPoint = new Paint();
	Paint paintFailed = new Paint();

	// Storage for calibration info
	List<CalibrationImageInfo> shots;

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
		paintPoint.setColor(Color.RED);
		paintPoint.setStyle(Paint.Style.FILL);

		paintFailed.setColor(Color.CYAN);
		paintFailed.setStyle(Paint.Style.FILL);
		paintFailed.setStrokeWidth(3f);
	}

	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		LayoutInflater inflater = getLayoutInflater();
		LinearLayout controls = (LinearLayout)inflater.inflate(R.layout.calibration_view,null);

		LinearLayout parent = getViewContent();
		parent.addView(controls);

		textCount = (TextView)controls.findViewById(R.id.text_total);

		shots = new ArrayList<CalibrationImageInfo>();

		FrameLayout iv = getViewPreview();
		mDetector = new GestureDetector(this, new MyGestureDetector(iv));
		iv.setOnTouchListener(new View.OnTouchListener() {
			@Override
			public boolean onTouch(View v, MotionEvent event) {
				mDetector.onTouchEvent(event);
				return true;
			}
		});

		showDialog(TARGET_DIALOG);
	}

	@Override
	protected void onResume() {
		super.onResume();  // Always call the superclass method first
		startVideoProcessing();

		if( DemoMain.preference.intrinsic != null ) {
			Toast.makeText(this, "Camera already calibrated", Toast.LENGTH_SHORT).show();
		}
	}

	/**
	 * Configures the detector, configures target description for calibration and starts the detector thread.
	 */
	private void startVideoProcessing() {
		DetectorFiducialCalibration detector;

		if( targetType == CalibrationPatterns.CHESSBOARD ) {
			ConfigChessboard config = new ConfigChessboard(numCols,numRows, 30);
			detector = FactoryFiducialCalibration.chessboard(config);
		} else if( targetType == CalibrationPatterns.SQUARE_GRID ) {
			ConfigSquareGrid config = new ConfigSquareGrid(numCols,numRows, 30 , 30);
			detector = FactoryFiducialCalibration.squareGrid(config);
		} else if( targetType == CalibrationPatterns.CIRCLE_ASYMMETRIC_GRID ){
			ConfigCircleAsymmetricGrid config = new ConfigCircleAsymmetricGrid(numCols,numRows, 1 , 6);
			detector = FactoryFiducialCalibration.circleAsymmGrid(config);
		} else {
			throw new RuntimeException("Unknown targetType "+targetType);
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

	@Override
	protected Dialog onCreateDialog(int id) {
		switch (id) {
			case TARGET_DIALOG:
				final SelectCalibrationFiducial dialog = new SelectCalibrationFiducial(numRows,numCols,targetType);

				dialog.create(this, new Runnable() {
					@Override
					public void run() {
						numCols = dialog.getGridColumns();
						numRows = dialog.getGridRows();
						targetType = dialog.getGridType();

						startVideoProcessing();
					}
				});
		}
		return super.onCreateDialog(id);
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
		View v;

		public MyGestureDetector(View v) {
			this.v = v;
		}

		@Override
		public boolean onDown(MotionEvent e) {
			captureRequested = true;
			return true;
		}
	}

	private class DetectTarget extends VideoRenderProcessing<GrayF32> {

		DetectorFiducialCalibration detector;

		FastQueue<Point2D_F64> pointsGui = new FastQueue<Point2D_F64>(Point2D_F64.class,true);

		List<List<Point2D_I32>> debugQuads = new ArrayList<>();
		List<EllipseRotated_F64> debugEllipses = new ArrayList<>();


		Bitmap bitmap;
		byte[] storage;

		protected DetectTarget( DetectorFiducialCalibration detector ) {
			super(ImageType.single(GrayF32.class));
			this.detector = detector;
		}

		@Override
		protected void declareImages(int width, int height) {
			super.declareImages(width, height);
			bitmap = Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888);
			storage = ConvertBitmap.declareStorage(bitmap, storage);
		}

		@Override
		protected void process(GrayF32 gray) {
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

			synchronized ( lockGui ) {
				ConvertBitmap.grayToBitmap(gray,bitmap,storage);
			}

			boolean detected = false;
			showDetectDebug = false;
			if( captureRequested ) {
				captureRequested = false;
				detected = collectMeasurement(gray);
			}

			// safely copy data into data structures used by GUI thread
			synchronized ( lockGui ) {
				ConvertBitmap.grayToBitmap(gray,bitmap,storage);
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
						VisualizeImageData.binaryToBitmap(alg.getBinary(), false, bitmap, storage);
						extractQuads(alg.getFindSeeds().getDetectorSquare().getFoundPolygons());
					} else if( detector instanceof CalibrationDetectorSquareGrid) {
						DetectSquareGridFiducial<GrayF32> alg = ((CalibrationDetectorSquareGrid) detector).getAlgorithm();
						VisualizeImageData.binaryToBitmap(alg.getBinary(), false ,bitmap, storage);
						extractQuads(alg.getDetectorSquare().getFoundPolygons());
					} else if( detector instanceof CalibrationDetectorCircleAsymmGrid) {
						DetectAsymmetricCircleGrid<GrayF32> alg = ((CalibrationDetectorCircleAsymmGrid) detector).getDetector();
						VisualizeImageData.binaryToBitmap(alg.getBinary(), false ,bitmap, storage);

						debugEllipses.clear();
						debugEllipses.addAll(alg.getEllipseDetector().getFoundEllipses().toList());
					}
				}
			}
		}

		protected void extractQuads( FastQueue<Polygon2D_F64> squares ) {
			debugQuads.clear();

			if( squares != null ) {
				for( Polygon2D_F64 b : squares.toList() ) {

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
		private boolean collectMeasurement(GrayF32 gray) {


			boolean success = detector.process(gray);

			// pause the display to provide feed back to the user
			timeResume = System.currentTimeMillis()+1500;

			if( success ) {
				shots.add( new CalibrationImageInfo(gray,detector.getDetectedPoints()));
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
			runOnUiThread(new Runnable() {
				public void run() {
					textCount.setText(""+size);
				}
			});
		}

		private boolean detectTarget(GrayF32 gray) {
			if( detector.process(gray) ) {
				return true;
			} else {
				showDetectDebug = true;
				return false;
			}
		}

		@Override
		protected void render(Canvas canvas, double imageToOutput) {
			// launch processing from here since you know data structures aren't being changed
			if( processRequested ) {
				processRequested = false;
				handleProcessRequest();
			} else {
				canvas.drawBitmap(bitmap,0,0,null);

				// draw shapes for debugging purposes
				for( List<Point2D_I32> l : debugQuads ) {
					for( int i = 1; i < l.size(); i++ ) {
						Point2D_I32 c0 = l.get(i-1);
						Point2D_I32 c1 = l.get(i);
						canvas.drawLine(c0.x,c0.y,c1.x,c1.y,paintFailed);
					}
					Point2D_I32 c0 = l.get(0);
					Point2D_I32 c1 = l.get(l.size()-1);
					canvas.drawLine(c0.x,c0.y,c1.x,c1.y,paintFailed);
				}

				for( EllipseRotated_F64 e : debugEllipses ) {

					float phi = (float) UtilAngle.radianToDegree(e.phi);

					float x0 = (float)(e.center.x - e.a);
					float y0 = (float)(e.center.y - e.b);
					float x1 = (float)(e.center.x + e.a);
					float y1 = (float)(e.center.y + e.b);

					canvas.rotate(phi, (float)e.center.x, (float)e.center.y);
//					r.set(cx-w,cy-h,cx+w+1,cy+h+1);
					canvas.drawOval(new RectF(x0,y0,x1,y1),paintFailed);
//					canvas.drawOval(r,paint);
					canvas.rotate(-phi, (float)e.center.x, (float)e.center.y);
				}

				// draw detected calibration points
				for( int i = 0; i < pointsGui.size(); i++ ) {
					Point2D_F64 p = pointsGui.get(i);
					canvas.drawCircle((float)p.x,(float)p.y,3,paintPoint);
				}
			}
		}
	}
}