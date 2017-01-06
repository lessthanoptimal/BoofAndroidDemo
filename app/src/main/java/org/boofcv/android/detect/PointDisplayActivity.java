package org.boofcv.android.detect;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.Spinner;

import org.boofcv.android.DemoVideoDisplayActivity;
import org.boofcv.android.R;

import boofcv.abst.feature.detect.extract.ConfigExtract;
import boofcv.abst.feature.detect.extract.NonMaxSuppression;
import boofcv.abst.feature.detect.intensity.GeneralFeatureIntensity;
import boofcv.alg.feature.detect.intensity.HessianBlobIntensity;
import boofcv.alg.feature.detect.interest.EasyGeneralFeatureDetector;
import boofcv.alg.feature.detect.interest.GeneralFeatureDetector;
import boofcv.android.ConvertBitmap;
import boofcv.android.gui.VideoRenderProcessing;
import boofcv.factory.feature.detect.extract.FactoryFeatureExtractor;
import boofcv.factory.feature.detect.intensity.FactoryIntensityPoint;
import boofcv.struct.QueueCorner;
import boofcv.struct.image.GrayS16;
import boofcv.struct.image.GrayU8;
import boofcv.struct.image.ImageType;
import georegression.struct.point.Point2D_I16;

/**
 * Displays detected point features.  Scale-space algorithms are excluded and have their own activity.  User
 * can select different algorithms.
 *
 * @author Peter Abeles
 */
public class PointDisplayActivity extends DemoVideoDisplayActivity
		implements AdapterView.OnItemSelectedListener  {

	Spinner spinner;

	Paint paintMax,paintMin;
	NonMaxSuppression nonmaxMax;
	NonMaxSuppression nonmaxMinMax;
	NonMaxSuppression nonmaxCandidate;

	int active = -1;

	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		paintMax = new Paint();
		paintMax.setColor(Color.RED);
		paintMax.setStyle(Paint.Style.FILL);

		paintMin = new Paint();
		paintMin.setColor(Color.BLUE);
		paintMin.setStyle(Paint.Style.FILL);

		LayoutInflater inflater = getLayoutInflater();
		LinearLayout controls = (LinearLayout)inflater.inflate(R.layout.detect_point_controls,null);

		LinearLayout parent = getViewContent();
		parent.addView(controls);

		spinner = (Spinner)controls.findViewById(R.id.spinner_algs);
		ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(this,
				R.array.point_features, android.R.layout.simple_spinner_item);
		adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		spinner.setAdapter(adapter);
		spinner.setOnItemSelectedListener(this);


		ConfigExtract configCorner = new ConfigExtract(2,20,3,true,false,true);
		ConfigExtract configBlob = new ConfigExtract(2,20,3,true,true,true);

		nonmaxMax = FactoryFeatureExtractor.nonmax(configCorner);
		nonmaxCandidate = FactoryFeatureExtractor.nonmaxCandidate(configCorner);
		nonmaxMinMax = FactoryFeatureExtractor.nonmax(configBlob);
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

		GeneralFeatureIntensity<GrayU8, GrayS16> intensity;
		NonMaxSuppression nonmax = nonmaxMax;

		switch( which ) {
			case 0:
				intensity = FactoryIntensityPoint.shiTomasi(2,false,GrayS16.class);
				break;

			case 1:
				intensity = FactoryIntensityPoint.harris(2, 0.04f, false, GrayS16.class);
				break;

			case 2:
				intensity = FactoryIntensityPoint.fast(25,9,GrayU8.class);
				nonmax = nonmaxCandidate;
				break;

			case 3:
				intensity = (GeneralFeatureIntensity)FactoryIntensityPoint.laplacian();
				nonmax = nonmaxMinMax;
				break;

			case 4:
				intensity = FactoryIntensityPoint.kitros(GrayS16.class);
				break;

			case 5:
				intensity = FactoryIntensityPoint.hessian(HessianBlobIntensity.Type.DETERMINANT,GrayS16.class);
				break;

			case 6:
				intensity = FactoryIntensityPoint.hessian(HessianBlobIntensity.Type.TRACE,GrayS16.class);
				nonmax = nonmaxMinMax;
				break;

			default:
				throw new RuntimeException("Unknown selection");
		}

		setProcessing(new PointProcessing(intensity,nonmax));
	}

	@Override
	public void onItemSelected(AdapterView<?> adapterView, View view, int pos, long id) {
		setSelection( pos );
	}

	@Override
	public void onNothingSelected(AdapterView<?> adapterView) {}

	protected class PointProcessing extends VideoRenderProcessing<GrayU8> {
		EasyGeneralFeatureDetector<GrayU8,GrayS16> detector;

		NonMaxSuppression nonmax;

		Bitmap bitmap;
		byte[] storage;

		// location of point features displayed inside of GUI
		QueueCorner maximumsGUI = new QueueCorner();
		QueueCorner minimumsGUI = new QueueCorner();


		public PointProcessing(GeneralFeatureIntensity<GrayU8, GrayS16> intensity,
							   NonMaxSuppression nonmax) {
			super(ImageType.single(GrayU8.class));
			GeneralFeatureDetector<GrayU8,GrayS16> general =
			new GeneralFeatureDetector<GrayU8, GrayS16>(intensity,nonmax);

			detector = new EasyGeneralFeatureDetector<GrayU8,GrayS16>(general,GrayU8.class,GrayS16.class);
			this.nonmax = nonmax;
		}

		@Override
		protected void declareImages(int width, int height) {
			super.declareImages(width, height);
			bitmap = Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888);
			storage = ConvertBitmap.declareStorage(bitmap,storage);
		}

		@Override
		protected void process(GrayU8 gray) {
			// adjust the non-max region based on image size
			nonmax.setSearchRadius( 3*gray.width/320 );
			detector.getDetector().setMaxFeatures( 200*gray.width/320 );
			detector.detect(gray,null);

			synchronized ( lockGui ) {
				ConvertBitmap.grayToBitmap(gray,bitmap,storage);

				maximumsGUI.reset();
				minimumsGUI.reset();

				QueueCorner maximums = detector.getMaximums();
				QueueCorner minimums = detector.getMinimums();

				for( int i = 0; i < maximums.size; i++ ) {
					Point2D_I16 p = maximums.get(i);
					maximumsGUI.grow().set(p);
				}
				for( int i = 0; i < minimums.size; i++ ) {
					Point2D_I16 p = minimums.get(i);
					minimumsGUI.grow().set(p);
				}
			}
		}

		@Override
		protected void render(Canvas canvas, double imageToOutput) {
			canvas.drawBitmap(bitmap,0,0,null);

			for( int i = 0; i < maximumsGUI.size; i++ ) {
				Point2D_I16 p = maximumsGUI.get(i);
				canvas.drawCircle(p.x,p.y,3,paintMax);
			}

			for( int i = 0; i < minimumsGUI.size; i++ ) {
				Point2D_I16 p = minimumsGUI.get(i);
				canvas.drawCircle(p.x,p.y,3,paintMin);
			}
		}
	}
}