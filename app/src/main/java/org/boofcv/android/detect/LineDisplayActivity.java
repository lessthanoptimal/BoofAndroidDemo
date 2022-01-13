package org.boofcv.android.detect;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;

import org.boofcv.android.DemoCamera2Activity;
import org.boofcv.android.DemoProcessingAbstract;
import org.boofcv.android.R;
import org.ddogleg.struct.DogArray;

import java.util.List;

import boofcv.abst.feature.detect.line.DetectLine;
import boofcv.abst.feature.detect.line.DetectLineSegment;
import boofcv.abst.feature.detect.line.HoughGradient_to_DetectLine;
import boofcv.alg.feature.detect.line.LineImageOps;
import boofcv.alg.misc.ImageStatistics;
import boofcv.alg.misc.PixelMath;
import boofcv.android.ConvertBitmap;
import boofcv.factory.feature.detect.line.ConfigHoughGradient;
import boofcv.factory.feature.detect.line.ConfigLineRansac;
import boofcv.factory.feature.detect.line.FactoryDetectLine;
import boofcv.struct.image.GrayF32;
import boofcv.struct.image.GrayU8;
import boofcv.struct.image.ImageType;
import georegression.struct.line.LineParametric2D_F32;
import georegression.struct.line.LineSegment2D_F32;

/**
 * Displays detected lines.  User can adjust the number of lines it will display.  Default is set to three to
 * reduce false positives.
 *
 * @author Peter Abeles
 */
public class LineDisplayActivity extends DemoCamera2Activity
		implements AdapterView.OnItemSelectedListener {
	private static final String TAG = "LineActivity";

	Paint paint;

	EditText editLines;
	Spinner spinner;

	// which algorithm is processing the image
	int active = -1;
	// the number of lines its configured to detect
	int numLines = 3;

	// show hough transform in GUI
	boolean showTransform = false;

	public LineDisplayActivity() {
		super(Resolution.MEDIUM);
		super.changeResolutionOnSlow = true;
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

//		Log.d(TAG,"onCreate called");

		paint = new Paint();
		paint.setColor(Color.RED);
		paint.setStyle(Paint.Style.STROKE);

		LayoutInflater inflater = getLayoutInflater();
		LinearLayout controls = (LinearLayout)inflater.inflate(R.layout.detect_line_controls,null);

		spinner = controls.findViewById(R.id.spinner_algs);
		ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(this,
				R.array.line_features, android.R.layout.simple_spinner_item);
		adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		spinner.setAdapter(adapter);
		spinner.setOnItemSelectedListener(this);

		editLines = controls.findViewById(R.id.num_lines);
		editLines.setText("" + numLines);
		editLines.setOnEditorActionListener(
				(v, actionId, event) -> {
                    if ( actionId == EditorInfo.IME_ACTION_DONE ) {
                        checkUpdateLines();
                    }
                    return false; // pass on to other listeners.
                });
		// TODO This doesn't cover the back where the user dismisses the keyboard with the back button

		setControls(controls);

		activateTouchToShowTransform();
	}

	public void activateTouchToShowTransform() {
		displayView.setOnTouchListener((view, motionEvent) -> {
			if (motionEvent.getAction() == MotionEvent.ACTION_DOWN) {
				showTransform = true;
			} else if (motionEvent.getAction() == MotionEvent.ACTION_UP) {
				showTransform = false;
			}
			return true;
		});
	}

	@Override
	public void createNewProcessor() {
		setSelection( spinner.getSelectedItemPosition() );
	}

	@Override
	protected void onPause() {
		super.onPause();
		active = -1;
	}

	private void setSelection( int which ) {
//		Log.d(TAG,"Set selection. "+which+" "+active);
		active = which;

		createLineDetector();
	}

	private void createLineDetector() {
		DetectLine<GrayU8> detector = null;
		DetectLineSegment<GrayU8> detectorSegment = null;

		ConfigHoughGradient configGrad = new ConfigHoughGradient(numLines);

		//configGrad.thresholdEdge = 40; TODO
		configGrad.localMaxRadius = 5;
		configGrad.minCounts = 10;

		switch( active ) {
			case 0:
				detector = FactoryDetectLine.houghLinePolar(configGrad,null,GrayU8.class);
				break;

			case 1:
				detector = FactoryDetectLine.houghLineFoot(configGrad,null,GrayU8.class);
				break;

			case 2: {
				ConfigLineRansac configSeg = new ConfigLineRansac();
				configSeg.thresholdEdge = 50;
				detectorSegment = FactoryDetectLine.lineRansac(null, GrayU8.class);
			} break;

			default:
				throw new RuntimeException("Unknown selection");
		}

		Log.d(TAG,"setProcessing(stuff)");
		if( detector != null )
			setProcessing(new LineProcessing(detector));
		else {
			setProcessing(new LineProcessing(detectorSegment));
		}
	}

	private void checkUpdateLines() {
		try {
			int num = Integer.parseInt(editLines.getText().toString());
			if( numLines == num )
				return;

			if( num >= 0 && num <= 30  ) {
				numLines = num;
				createLineDetector();
				return;
			}
		} catch( RuntimeException e ) {

		}
		// undo the bad change
		editLines.setText(""+numLines);
	}

	@Override
	public void onItemSelected(AdapterView<?> adapterView, View view, int pos, long id) {
		setSelection( pos );
	}

	@Override
	public void onNothingSelected(AdapterView<?> adapterView) {}

	protected class LineProcessing extends DemoProcessingAbstract<GrayU8> {
		DetectLine<GrayU8> detector;
		DetectLineSegment<GrayU8> detectorSegment = null;

		DogArray<LineSegment2D_F32> lines = new DogArray<>(LineSegment2D_F32::new);

		GrayF32 transformLog = new GrayF32(1, 1);
		Bitmap transformBitmap = null;
        RectF dst = new RectF();

		public LineProcessing(DetectLine<GrayU8> detector) {
			super(ImageType.single(GrayU8.class));
			this.detector = detector;
		}

		public LineProcessing(DetectLineSegment<GrayU8> detectorSegment) {
			super(ImageType.single(GrayU8.class));
			this.detectorSegment = detectorSegment;
		}

		@Override
		public void initialize(int imageWidth, int imageHeight, int sensorOrientation) {
//			Log.d(TAG,"initialize "+imageWidth);
            paint.setStrokeWidth(5.0f*cameraToDisplayDensity);
			synchronized (lockGui) {
				transformBitmap = null;
				showTransform = false;
			}
		}

		@Override
		public void onDraw(Canvas canvas, Matrix imageToView) {
//			Log.d(TAG,"onDraw ");

			synchronized (lockGui) {

				if( showTransform && detector != null ) {
					if( transformBitmap != null ) {
						// TODO maintain transform image aspect ratio
//						float scale = Math.min(canvas.getWidth()/(float)transformBitmap.getWidth(),
//								canvas.getHeight()/(float)transformBitmap.getHeight());

						dst.left = 0;
						dst.right = canvas.getWidth();
						dst.top = 0;
						dst.bottom = canvas.getHeight();

                        canvas.drawBitmap(transformBitmap, null,dst, null);
                    }
				} else {
					canvas.concat(imageToView);
					for( LineSegment2D_F32 s : lines.toList() )  {
						canvas.drawLine(s.a.x,s.a.y,s.b.x,s.b.y,paint);
					}
				}
			}
		}

		@Override
		public void process(GrayU8 gray) {
//			Log.d(TAG,"process ");

			if( detector != null ) {
				List<LineParametric2D_F32> found = detector.detect(gray);

				synchronized ( lockGui ) {
					lines.reset();
					for( LineParametric2D_F32 p : found ) {
						LineSegment2D_F32 ls = LineImageOps.convert(p, gray.width,gray.height);
						lines.grow().setTo(ls.a,ls.b);
					}

					if( showTransform ) {
						HoughGradient_to_DetectLine alg = (HoughGradient_to_DetectLine) detector;
						PixelMath.log(alg.getHough().getTransform(), 1.0f,transformLog);
						if( transformBitmap == null ||
								transformBitmap.getWidth() != transformLog.getWidth() ||
								transformBitmap.getHeight() != transformLog.getHeight() ) {
							transformBitmap = Bitmap.createBitmap(transformLog.width,transformLog.height, Bitmap.Config.ARGB_8888);
						}
						float max = ImageStatistics.max(transformLog);
						PixelMath.multiply(transformLog,255.0f/max,transformLog);
						ConvertBitmap.grayToBitmap(transformLog,transformBitmap,null);
                    }
				}

			} else {
				List<LineSegment2D_F32> found = detectorSegment.detect(gray);
				synchronized ( lockGui ) {
					lines.reset();
					for( LineSegment2D_F32 p : found ) {
						lines.grow().setTo(p.a,p.b);
					}
				}
			}
		}

	}
}