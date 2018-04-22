package org.boofcv.android.detect;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.Spinner;

import org.boofcv.android.DemoCamera2Activity;
import org.boofcv.android.DemoProcessingAbstract;
import org.boofcv.android.R;
import org.ddogleg.struct.FastQueue;

import boofcv.abst.feature.detect.interest.ConfigFastHessian;
import boofcv.abst.feature.detect.interest.ConfigSiftDetector;
import boofcv.abst.feature.detect.interest.InterestPointDetector;
import boofcv.factory.feature.detect.interest.FactoryInterestPoint;
import boofcv.struct.feature.ScalePoint;
import boofcv.struct.image.GrayU8;
import boofcv.struct.image.ImageType;
import georegression.struct.point.Point2D_F64;

/**
 * @author Peter Abeles
 */
public class ScalePointDisplayActivity extends DemoCamera2Activity
		implements AdapterView.OnItemSelectedListener  {

	Spinner spinner;

	Paint paintMax;

	public ScalePointDisplayActivity() {
		super(Resolution.MEDIUM);
		super.changeResolutionOnSlow = true;
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		DisplayMetrics dm = getResources().getDisplayMetrics();

		paintMax = new Paint();
		paintMax.setColor(Color.RED);
		paintMax.setStyle(Paint.Style.STROKE);
		float strokeWidth = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 0.5f, dm);
		paintMax.setStrokeWidth(strokeWidth);

		LayoutInflater inflater = getLayoutInflater();
		LinearLayout controls = (LinearLayout)inflater.inflate(R.layout.select_algorithm,null);

		spinner = controls.findViewById(R.id.spinner_algs);
		ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(this,
				R.array.scale_features, android.R.layout.simple_spinner_item);
		adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		spinner.setAdapter(adapter);
		spinner.setOnItemSelectedListener(this);

		setControls(controls);
	}

	@Override
	public void createNewProcessor() {
		setSelection( spinner.getSelectedItemPosition() );	}

	@Override
	protected void onPause() {
		super.onPause();
	}

	private void setSelection( int which ) {
		InterestPointDetector<GrayU8> detector;

		switch( which ) {
			case 0:
				detector = FactoryInterestPoint.fastHessian(new ConfigFastHessian(10,3,100,2,9,4,4));
				break;

			case 1:
				ConfigSiftDetector configSift = new ConfigSiftDetector(200);
				detector = FactoryInterestPoint.sift(null, configSift, GrayU8.class);
				break;

			default:
				throw new RuntimeException("Unknown selection");
		}

		setProcessing(new PointProcessing(detector));
	}

	@Override
	public void onItemSelected(AdapterView<?> adapterView, View view, int pos, long id) {
		setSelection( pos );
	}

	@Override
	public void onNothingSelected(AdapterView<?> adapterView) {}

	protected class PointProcessing extends DemoProcessingAbstract<GrayU8> {
		InterestPointDetector<GrayU8> detector;

		float density;

		FastQueue<ScalePoint> foundGUI = new FastQueue<ScalePoint>(ScalePoint.class,true);

		public PointProcessing(InterestPointDetector<GrayU8> detector) {
			super(ImageType.single(GrayU8.class));
			this.detector = detector;
		}

		@Override
		public void initialize(int imageWidth, int imageHeight, int sensorOrientation) {
			density = cameraToDisplayDensity;
		}

		@Override
		public void onDraw(Canvas canvas, Matrix imageToView) {
			canvas.concat(imageToView);
			synchronized (lockGui) {
				for (int i = 0; i < foundGUI.size(); i++) {
					ScalePoint p = foundGUI.get(i);
					float radius = (float)(p.scale*density);
					canvas.drawCircle((float) p.x, (float) p.y, radius, paintMax);
				}
			}
		}

		@Override
		public void process(GrayU8 gray) {
			detector.detect(gray);
			synchronized ( lockGui ) {
				foundGUI.reset();
				int N = detector.getNumberOfFeatures();
				for( int i = 0; i < N; i++ ) {
					Point2D_F64 p = detector.getLocation(i);
					double radius = detector.getRadius(i);
					foundGUI.grow().set(p.x, p.y, radius);
				}
			}
		}
	}
}