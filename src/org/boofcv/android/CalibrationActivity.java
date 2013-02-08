package org.boofcv.android;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Bundle;
import android.view.GestureDetector;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.widget.*;
import boofcv.abst.calib.ConfigChessboard;
import boofcv.abst.calib.PlanarCalibrationDetector;
import boofcv.abst.calib.WrapPlanarChessTarget;
import boofcv.alg.feature.detect.chess.DetectChessCalibrationPoints;
import boofcv.alg.feature.detect.chess.DetectChessSquaresBinary;
import boofcv.alg.feature.detect.quadblob.QuadBlob;
import boofcv.alg.geo.calibration.PlanarCalibrationTarget;
import boofcv.android.ConvertBitmap;
import boofcv.factory.calib.FactoryPlanarCalibrationTarget;
import boofcv.struct.image.ImageFloat32;
import georegression.struct.point.Point2D_F64;
import georegression.struct.point.Point2D_I32;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Peter Abeles
 */
public class CalibrationActivity extends PointTrackerDisplayActivity
		implements CompoundButton.OnCheckedChangeListener
{

	public static int targetType = 0;
	public static int numRows = 3;
	public static int numCols = 4;

	Paint paintPoint = new Paint();
	Paint paintFailed = new Paint();

	// Storage for calibration info
	List<CalibrationImageInfo> shots;

	// user has requested that the next image be processed for the target
	boolean captureRequested = false;

	// displays the number of calibration images captured
	TextView textCount;

	// continuously detect the calibration target or only on user request
	boolean continuous;

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

		paintFailed.setColor(Color.BLUE);
		paintFailed.setStyle(Paint.Style.FILL);
		paintFailed.setStrokeWidth(1.5f);
	}

	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		LayoutInflater inflater = getLayoutInflater();
		LinearLayout controls = (LinearLayout)inflater.inflate(R.layout.calibration_view,null);

		LinearLayout parent = (LinearLayout)findViewById(R.id.camera_preview_parent);
		parent.addView(controls);

		CheckBox seek = (CheckBox)controls.findViewById(R.id.checkBox_continuous);
		seek.setOnCheckedChangeListener(this);

		textCount = (TextView)controls.findViewById(R.id.text_total);

		shots = new ArrayList<CalibrationImageInfo>();

		ConfigChessboard config = new ConfigChessboard(numCols,numRows);
//		config.nonmaxRadius = 3;
//		config.relativeSizeThreshold = 0.6;

		PlanarCalibrationDetector detector = FactoryPlanarCalibrationTarget.detectorChessboard(config);
		PlanarCalibrationTarget target = FactoryPlanarCalibrationTarget.gridChess(numCols, numRows, 30);

		setProcessing(new DetectTarget(detector));

		FrameLayout iv = (FrameLayout)findViewById(R.id.camera_preview);
		mDetector = new GestureDetector(this, new MyGestureDetector(iv));
		iv.setOnTouchListener(new View.OnTouchListener(){
			@Override
			public boolean onTouch(View v, MotionEvent event)
			{
				mDetector.onTouchEvent(event);
				return true;
			}});
	}

	public void pressedOK( View v ) {
		processRequested = true;
	}

	@Override
	public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
		continuous = b;
	}

	private void handleProcessRequest() {
		if( shots.size() < 3 ) {
			Toast toast = Toast.makeText(this, "Need at least three images.", 200);
			toast.show();
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

	private class DetectTarget extends BoofRenderProcessing<ImageFloat32> {

		PlanarCalibrationDetector detector;

		List<Point2D_F64> points;

		Bitmap bitmap;
		byte[] storage;

		protected DetectTarget( PlanarCalibrationDetector detector ) {
			super(ImageFloat32.class);
			this.detector = detector;
		}

		@Override
		protected void declareImages(int width, int height) {
			super.declareImages(width, height);
			bitmap = Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888);
			storage = ConvertBitmap.declareStorage(bitmap, storage);
		}

		@Override
		protected void process(ImageFloat32 gray) {
			if( timeResume > System.currentTimeMillis() )
				return;

			ConvertBitmap.grayToBitmap(gray,bitmap,storage);

			showDetectDebug = false;
			if( captureRequested ) {
				captureRequested = false;
				collectMeasurement(gray);

			} else if( continuous ) {
				detectTarget(gray);
			} else {
				points = null;
			}
		}

		/**
		 * Detect calibration targets in the image and save the results.  Pause the display so the
		 * user can see the results]
		 */
		private void collectMeasurement(ImageFloat32 gray) {
			// pause the display for 1 second
			timeResume = System.currentTimeMillis()+1000;

			if( detector.process(gray) ) {
				showDetectDebug = true;
				points = detector.getPoints();
				shots.add( new CalibrationImageInfo(gray,points));
				final int size = shots.size();
				runOnUiThread(new Runnable() {
					public void run() {
						textCount.setText(""+size);
					}
				});
			}  else {
				showDetectDebug = true;
				points = null;
			}
		}

		private void detectTarget(ImageFloat32 gray) {
			if( detector.process(gray) ) {
				points = detector.getPoints();
			} else {
				showDetectDebug = true;
				points = null;
			}
		}

		@Override
		protected void render(Canvas canvas, double imageToOutput) {
			canvas.drawBitmap(bitmap,0,0,null);

			if( points == null ) {
				if( showDetectDebug ) {
					if( detector instanceof WrapPlanarChessTarget ) {
						render(((WrapPlanarChessTarget)detector).getAlg(),canvas);
					}
				}
				return;
			}

			// draw dots at calibration points
			for( int i = 0; i < points.size(); i++ ) {
				Point2D_F64 p = points.get(i);
				canvas.drawCircle((float)p.x,(float)p.y,3,paintPoint);
			}
		}

		/**
		 * Draw debuging/visualization information specific to a chessboard target
		 */
		protected void render( DetectChessCalibrationPoints chess , Canvas canvas ) {

			if( chess.isFoundBound() ) {
				DetectChessSquaresBinary squares = chess.getFindBound();

				List<Point2D_F64> quad = squares.getBoundingQuad();

				Point2D_F64 p0 = quad.get(0);
				Point2D_F64 p1 = quad.get(1);
				Point2D_F64 p2 = quad.get(2);
				Point2D_F64 p3 = quad.get(3);

				canvas.drawLine((float)p0.x,(float)p0.y,(float)p1.x,(float)p1.y,paintFailed);
				canvas.drawLine((float)p1.x,(float)p1.y,(float)p2.x,(float)p2.y,paintFailed);
				canvas.drawLine((float)p2.x,(float)p2.y,(float)p3.x,(float)p3.y,paintFailed);
				canvas.drawLine((float)p3.x,(float)p3.y,(float)p0.x,(float)p0.y,paintFailed);
			}

			List<QuadBlob> quads = chess.getFindBound().getDetectBlobs().getDetected();
			if( quads != null ) {
				for( QuadBlob b : quads ) {
					if( b.corners.size() < 2 )
						continue;

					for( int i = 1; i < b.corners.size(); i++ ) {
						Point2D_I32 c0 = b.corners.get(i-1);
						Point2D_I32 c1 = b.corners.get(i);
						canvas.drawLine(c0.x,c0.y,c1.x,c1.y,paintFailed);
					}
					Point2D_I32 c0 = b.corners.get(0);
					Point2D_I32 c1 = b.corners.get(b.corners.size()-1);
					canvas.drawLine(c0.x,c0.y,c1.x,c1.y,paintFailed);
				}
			}

			// launch processing from here since you know data structures aren't being changed
			if( processRequested ) {
				processRequested = false;
				handleProcessRequest();
			}
		}
	}
}