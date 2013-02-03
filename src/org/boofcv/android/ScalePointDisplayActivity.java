package org.boofcv.android;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.*;
import boofcv.abst.feature.detect.extract.ConfigExtract;
import boofcv.abst.feature.detect.extract.NonMaxSuppression;
import boofcv.abst.feature.detect.intensity.GeneralFeatureIntensity;
import boofcv.abst.feature.detect.interest.ConfigFastHessian;
import boofcv.abst.feature.detect.interest.ConfigSiftDetector;
import boofcv.abst.feature.detect.interest.InterestPointDetector;
import boofcv.alg.feature.detect.intensity.HessianBlobIntensity;
import boofcv.alg.feature.detect.interest.EasyGeneralFeatureDetector;
import boofcv.alg.feature.detect.interest.FeatureLaplacePyramid;
import boofcv.alg.feature.detect.interest.GeneralFeatureDetector;
import boofcv.android.ConvertBitmap;
import boofcv.core.image.ConvertImage;
import boofcv.factory.feature.detect.extract.FactoryFeatureExtractor;
import boofcv.factory.feature.detect.intensity.FactoryIntensityPoint;
import boofcv.factory.feature.detect.interest.FactoryInterestPoint;
import boofcv.factory.feature.detect.interest.FactoryInterestPointAlgs;
import boofcv.struct.QueueCorner;
import boofcv.struct.image.ImageFloat32;
import boofcv.struct.image.ImageSInt16;
import boofcv.struct.image.ImageUInt8;
import georegression.struct.point.Point2D_F64;
import georegression.struct.point.Point2D_I16;

/**
 * @author Peter Abeles
 */
public class ScalePointDisplayActivity extends VideoDisplayActivity
		implements SeekBar.OnSeekBarChangeListener , AdapterView.OnItemSelectedListener  {

	Paint paintMax;

	int active = -1;

	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		paintMax = new Paint();
		paintMax.setColor(Color.RED);
		paintMax.setStyle(Paint.Style.STROKE);

		LayoutInflater inflater = getLayoutInflater();
		LinearLayout controls = (LinearLayout)inflater.inflate(R.layout.detect_point_controls,null);

		LinearLayout parent = (LinearLayout)findViewById(R.id.camera_preview_parent);
		parent.addView(controls);

		SeekBar seek = (SeekBar)controls.findViewById(R.id.slider_threshold);
		seek.setOnSeekBarChangeListener(this);

		Spinner spinner = (Spinner)controls.findViewById(R.id.spinner_algs);
		ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(this,
				R.array.scale_features, android.R.layout.simple_spinner_item);
		adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		spinner.setAdapter(adapter);
		spinner.setOnItemSelectedListener(this);

		setSelection( spinner.getSelectedItemPosition() );
	}

	private void setSelection( int which ) {
		if( which == active )
			return;
		active = which;

		InterestPointDetector<ImageUInt8> detector;

		switch( which ) {
			case 0:
				detector = FactoryInterestPoint.fastHessian(new ConfigFastHessian(10,3,100,2,9,4,4));
				break;

			case 1:
				ConfigSiftDetector configSift = new ConfigSiftDetector(3,10,-1,5);
				InterestPointDetector<ImageFloat32> detectorF =
						FactoryInterestPoint.siftDetector(null,configSift);
				detector = new ConvertDetector(detectorF);
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

	@Override
	public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser ) {
		//To change body of implemented methods use File | Settings | File Templates.
	}

	@Override
	public void onStartTrackingTouch(SeekBar seekBar) {}

	@Override
	public void onStopTrackingTouch(SeekBar seekBar) {}

	protected class PointProcessing extends BoofRenderProcessing {
		InterestPointDetector<ImageUInt8> detector;

		Bitmap bitmap;
		byte[] storage;

		public PointProcessing(InterestPointDetector<ImageUInt8> detector) {
			this.detector = detector;
		}

		@Override
		protected void declareImages(int width, int height) {
			super.declareImages(width, height);
			bitmap = Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888);
			storage = ConvertBitmap.declareStorage(bitmap,storage);
		}

		@Override
		protected void process(ImageUInt8 gray) {
			detector.detect(gray);
			ConvertBitmap.grayToBitmap(gray,bitmap,storage);
		}

		@Override
		protected void render(Canvas canvas, double imageToOutput) {
			canvas.drawBitmap(bitmap,0,0,null);

			int N = detector.getNumberOfFeatures();
			for( int i = 0; i < N; i++ ) {
				Point2D_F64 p = detector.getLocation(i);
				double ory = detector.getOrientation(i);
				int r = (int)(detector.getScale(i)*3.0);
				canvas.drawCircle((int)p.x,(int)p.y,r,paintMax);
			}
		}
	}

	private static class ConvertDetector implements InterestPointDetector<ImageUInt8> {

		ImageFloat32 storage = new ImageFloat32(1,1);
		InterestPointDetector<ImageFloat32> detector;

		private ConvertDetector(InterestPointDetector<ImageFloat32> detector) {
			this.detector = detector;
		}

		@Override
		public void detect(ImageUInt8 input) {
			storage.reshape(input.width, input.height);
			ConvertImage.convert(input,storage);
			detector.detect(storage);
		}

		@Override
		public boolean hasScale() {
			return detector.hasScale();
		}

		@Override
		public boolean hasOrientation() {
			return detector.hasOrientation();
		}

		@Override
		public int getNumberOfFeatures() {
			return detector.getNumberOfFeatures();
		}

		@Override
		public Point2D_F64 getLocation(int featureIndex) {
			return detector.getLocation(featureIndex);
		}

		@Override
		public double getScale(int featureIndex) {
			return detector.getScale(featureIndex);
		}

		@Override
		public double getOrientation(int featureIndex) {
			return detector.getOrientation(featureIndex);
		}
	}
}