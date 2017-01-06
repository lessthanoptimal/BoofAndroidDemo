package org.boofcv.android.detect;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
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

import org.boofcv.android.DemoVideoDisplayActivity;
import org.boofcv.android.R;
import org.ddogleg.struct.FastQueue;

import boofcv.abst.feature.detect.interest.ConfigFastHessian;
import boofcv.abst.feature.detect.interest.ConfigSiftDetector;
import boofcv.abst.feature.detect.interest.InterestPointDetector;
import boofcv.android.ConvertBitmap;
import boofcv.android.gui.VideoRenderProcessing;
import boofcv.factory.feature.detect.interest.FactoryInterestPoint;
import boofcv.struct.feature.ScalePoint;
import boofcv.struct.image.GrayU8;
import boofcv.struct.image.ImageType;
import georegression.struct.point.Point2D_F64;

/**
 * @author Peter Abeles
 */
public class ScalePointDisplayActivity extends DemoVideoDisplayActivity
		implements AdapterView.OnItemSelectedListener  {

	Spinner spinner;

	Paint paintMax;

	int active = -1;

	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		DisplayMetrics dm = getResources().getDisplayMetrics();

		paintMax = new Paint();
		paintMax.setColor(Color.RED);
		paintMax.setStyle(Paint.Style.STROKE);
		float strokeWidth = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 0.5f, dm);
		paintMax.setStrokeWidth(strokeWidth);

		LayoutInflater inflater = getLayoutInflater();
		LinearLayout controls = (LinearLayout)inflater.inflate(R.layout.detect_point_controls,null);

		LinearLayout parent = getViewContent();
		parent.addView(controls);

		spinner = (Spinner)controls.findViewById(R.id.spinner_algs);
		ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(this,
				R.array.scale_features, android.R.layout.simple_spinner_item);
		adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		spinner.setAdapter(adapter);
		spinner.setOnItemSelectedListener(this);
	}

	@Override
	protected void onResume() {
		super.onResume();
		setSelection( spinner.getSelectedItemPosition() );
	}

	@Override
	protected void onPause() {
		super.onPause();
		active = -1;
	}

	private void setSelection( int which ) {
		if( which == active )
			return;
		active = which;

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

	protected class PointProcessing extends VideoRenderProcessing<GrayU8> {
		InterestPointDetector<GrayU8> detector;

		Bitmap bitmap;
		byte[] storage;

		FastQueue<ScalePoint> foundGUI = new FastQueue<ScalePoint>(ScalePoint.class,true);

		public PointProcessing(InterestPointDetector<GrayU8> detector) {
			super(ImageType.single(GrayU8.class));
			this.detector = detector;
		}

		@Override
		protected void declareImages(int width, int height) {
			super.declareImages(width, height);
			bitmap = Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888);
			storage = ConvertBitmap.declareStorage(bitmap,storage);
		}

		@Override
		protected void process(GrayU8 gray) {
			detector.detect(gray);
			synchronized ( lockGui ) {
				ConvertBitmap.grayToBitmap(gray,bitmap,storage);

				foundGUI.reset();
				int N = detector.getNumberOfFeatures();
				for( int i = 0; i < N; i++ ) {
					Point2D_F64 p = detector.getLocation(i);
					double radius = detector.getRadius(i);
					foundGUI.grow().set(p.x, p.y, radius);
				}
			}
		}

		@Override
		protected void render(Canvas canvas, double imageToOutput) {
			canvas.drawBitmap(bitmap,0,0,null);

			for( int i = 0; i < foundGUI.size(); i++ ) {
				ScalePoint p = foundGUI.get(i);
				canvas.drawCircle((float)p.x,(float)p.y,(float)p.scale,paintMax);
			}
		}
	}
}