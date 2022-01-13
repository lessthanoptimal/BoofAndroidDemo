package org.boofcv.android.detect;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.Spinner;

import org.boofcv.android.DemoCamera2Activity;
import org.boofcv.android.DemoProcessingAbstract;
import org.boofcv.android.R;
import org.ddogleg.struct.DogArray;

import java.util.ArrayList;
import java.util.List;

import boofcv.abst.feature.detect.extract.ConfigExtract;
import boofcv.abst.feature.detect.extract.NonMaxSuppression;
import boofcv.abst.feature.detect.intensity.GeneralFeatureIntensity;
import boofcv.abst.feature.detect.interest.ConfigFastCorner;
import boofcv.abst.feature.detect.interest.GeneralToInterestPoint;
import boofcv.abst.feature.detect.interest.InterestPointDetector;
import boofcv.alg.feature.detect.intensity.HessianBlobIntensity;
import boofcv.alg.feature.detect.interest.EasyGeneralFeatureDetector;
import boofcv.alg.feature.detect.interest.GeneralFeatureDetector;
import boofcv.alg.feature.detect.selector.FeatureSelectLimit;
import boofcv.alg.feature.detect.selector.FeatureSelectNBest;
import boofcv.factory.feature.detect.extract.FactoryFeatureExtractor;
import boofcv.factory.feature.detect.intensity.FactoryIntensityPoint;
import boofcv.factory.feature.detect.interest.FactoryInterestPoint;
import boofcv.struct.QueueCorner;
import boofcv.struct.image.GrayS16;
import boofcv.struct.image.GrayU8;
import boofcv.struct.image.ImageType;
import georegression.struct.point.Point2D_F64;
import georegression.struct.point.Point2D_I16;

/**
 * Displays detected point features.  Scale-space algorithms are excluded and have their own activity.  User
 * can select different algorithms.
 *
 * @author Peter Abeles
 */
public class PointDisplayActivity extends DemoCamera2Activity
		implements AdapterView.OnItemSelectedListener  {

	Spinner spinner;
	SeekBar seekRadius;
	CheckBox checkWeighted;

	Paint paintMax,paintMin;
	NonMaxSuppression nonmaxMax;
	NonMaxSuppression nonmaxMinMax;
	NonMaxSuppression nonmaxCandidate;

	public PointDisplayActivity() {
		super(Resolution.MEDIUM);
		super.changeResolutionOnSlow = true;
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		paintMax = new Paint();
		paintMax.setColor(Color.RED);
		paintMax.setStyle(Paint.Style.FILL);

		paintMin = new Paint();
		paintMin.setColor(Color.BLUE);
		paintMin.setStyle(Paint.Style.FILL);

		LayoutInflater inflater = getLayoutInflater();
		LinearLayout controls = (LinearLayout)inflater.inflate(R.layout.detect_point_controls,null);

		spinner = controls.findViewById(R.id.spinner_algs);
		ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(this,
				R.array.point_features, android.R.layout.simple_spinner_item);
		adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		spinner.setAdapter(adapter);
		spinner.setOnItemSelectedListener(this);

		seekRadius = controls.findViewById(R.id.slider_radius);
		seekRadius.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
			@Override
			public void onProgressChanged(SeekBar seekBar, int i, boolean b) {
				createNewProcessor();
			}
			@Override public void onStartTrackingTouch(SeekBar seekBar) {}
			@Override public void onStopTrackingTouch(SeekBar seekBar) {}
		});

		checkWeighted = controls.findViewById(R.id.check_weighted);
		checkWeighted.setOnCheckedChangeListener((compoundButton, b) -> createNewProcessor());

		ConfigExtract configCorner = new ConfigExtract(2,20,3,true,false,true);
		ConfigExtract configBlob = new ConfigExtract(2,20,3,true,true,true);

		nonmaxMax = FactoryFeatureExtractor.nonmax(configCorner);
		nonmaxCandidate = FactoryFeatureExtractor.nonmaxCandidate(configCorner);
		nonmaxMinMax = FactoryFeatureExtractor.nonmax(configBlob);

		setControls(controls);
	}

	@Override
	public void createNewProcessor() {
		 setSelection( spinner.getSelectedItemPosition() );
	}

	@Override
	protected void onPause() {
		super.onPause();
	}

	private void setSelection( int which ) {
		InterestPointDetector<GrayU8> detector=null;
		GeneralFeatureIntensity<GrayU8, GrayS16> intensity=null;
		NonMaxSuppression nonmax = nonmaxMax;


		int featureRadius = seekRadius.getProgress()+1;
		boolean weighted = checkWeighted.isChecked();

		int pixelTol = 10 + (int)(200*(featureRadius/(double)seekRadius.getMax()));
		ConfigFastCorner configFast = new ConfigFastCorner(pixelTol,9);
		// configFast.maxFeatures = 0.02; TODO

		boolean enableRadius=false;
		boolean enabledWeighted=false;

		switch( which ) {
			case 0:
				enableRadius = true;
				enabledWeighted = true;
				intensity = FactoryIntensityPoint.shiTomasi(featureRadius,weighted,GrayS16.class);
				break;

			case 1:
				enableRadius = true;
				enabledWeighted = true;
				intensity = FactoryIntensityPoint.harris(featureRadius, 0.04f, weighted, GrayS16.class);
				break;

			case 2:
				enableRadius = true;
				// less strict requirement since it can prune features with non-max
				intensity = FactoryIntensityPoint.fast(
						configFast.pixelTol,configFast.minContinuous,GrayU8.class);
				nonmax = nonmaxMinMax;
				break;

			case 3:
				intensity = (GeneralFeatureIntensity)FactoryIntensityPoint.laplacian(GrayU8.class);
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

		checkWeighted.setEnabled(enabledWeighted);
		seekRadius.setEnabled(enableRadius);

		if( intensity != null )
			setProcessing(new PointProcessing(intensity, nonmaxMinMax, nonmax));
		else
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

		NonMaxSuppression nonmax;

		// location of point features displayed inside of GUI
		List<Point2D_F64> maximumsGUI = new  ArrayList<>();
		List<Point2D_F64> minimumsGUI = new ArrayList<>();

		float radius;

		public PointProcessing(GeneralFeatureIntensity<GrayU8, GrayS16> intensity,
							   NonMaxSuppression nonmin, NonMaxSuppression nonmax) {
			super(ImageType.single(GrayU8.class));

			FeatureSelectNBest selectLimit = new FeatureSelectNBest();
			GeneralFeatureDetector<GrayU8, GrayS16> general =
					new GeneralFeatureDetector<GrayU8, GrayS16>(intensity, nonmin, nonmax, selectLimit);

			detector = FactoryInterestPoint.wrapPoint(general, 1, GrayU8.class, GrayS16.class);

			// detector = new GeneralToPointDetector<>(general, GrayU8.class, GrayS16.class);
			this.nonmax = nonmax;
		}

		public PointProcessing(InterestPointDetector<GrayU8> detector ) {
			super(ImageType.single(GrayU8.class));
			this.detector = detector;
		}

		@Override
		public void initialize(int imageWidth, int imageHeight, int sensorOrientation) {
			radius = 3 * cameraToDisplayDensity;

			if( nonmax != null ) {
				// adjust the non-max region based on image size
				nonmax.setSearchRadius(3 * imageWidth / 320);
				EasyGeneralFeatureDetector easy = (EasyGeneralFeatureDetector)detector;
				int totalSets = easy.getDetector().isDetectMaximums() ? 1 : 0;
				totalSets += easy.getDetector().isDetectMinimums() ? 1 : 0;
				easy.getDetector().setFeatureLimit(200 * imageWidth / (320*totalSets) );
			}
		}

		@Override
		public void onDraw(Canvas canvas, Matrix imageToView) {

			canvas.concat(imageToView);
			synchronized (lockGui) {
				for (int i = 0; i < maximumsGUI.size(); i++) {
					Point2D_F64 p = maximumsGUI.get(i);
					canvas.drawCircle((float)p.x, (float)p.y, radius, paintMax);
				}

				for (int i = 0; i < minimumsGUI.size(); i++) {
					Point2D_F64 p = minimumsGUI.get(i);
					canvas.drawCircle((float)p.x, (float)p.y, radius, paintMin);
				}
			}
		}

		@Override
		public void process(GrayU8 gray) {
			detector.detect(gray);

			synchronized (lockGui) {
				maximumsGUI.clear();
				minimumsGUI.clear();

				if( detector.getNumberOfSets() == 1 ) {
					int set = detector.getSet(0); // TODO

					maximumsGUI.add(detector.getLocation(0));
				} else {
					minimumsGUI.add(detector.getLocation(0));
					maximumsGUI.add(detector.getLocation(1));
				}
			}
		}
	}
}