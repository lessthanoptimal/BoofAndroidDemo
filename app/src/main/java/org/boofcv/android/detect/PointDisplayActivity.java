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

import boofcv.abst.feature.detect.extract.ConfigExtract;
import boofcv.abst.feature.detect.extract.NonMaxSuppression;
import boofcv.abst.feature.detect.interest.ConfigPointDetector;
import boofcv.abst.feature.detect.interest.PointDetectorTypes;
import boofcv.alg.feature.detect.interest.EasyGeneralFeatureDetector;
import boofcv.alg.feature.detect.interest.GeneralFeatureDetector;
import boofcv.factory.feature.detect.extract.FactoryFeatureExtractor;
import boofcv.factory.feature.detect.interest.FactoryDetectPoint;
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
		ConfigPointDetector config = new ConfigPointDetector();
		config.general.detectMaximums = true;
		config.general.detectMinimums = false;

		int featureRadius = seekRadius.getProgress()+1;
		boolean weighted = checkWeighted.isChecked();

		boolean enabledWeighted=false;

		switch( which ) {
			case 0:
				enabledWeighted = true;
				config.shiTomasi.weighted = weighted;
				config.shiTomasi.radius = featureRadius;
				config.type = PointDetectorTypes.SHI_TOMASI;
				break;

			case 1:
				enabledWeighted = true;
				config.harris.weighted = weighted;
				config.harris.radius = featureRadius;
				config.type = PointDetectorTypes.HARRIS;
				break;

			case 2:
				// less strict requirement since it can prune features with non-max
				config.fast.minContinuous = 9;
				config.fast.pixelTol= 10 + (int)(200*(featureRadius/(double)seekRadius.getMax()));
				config.type = PointDetectorTypes.FAST;
				config.general.detectMinimums = true;
				break;

			case 3:
				config.type = PointDetectorTypes.LAPLACIAN;
				config.general.detectMinimums = true;
				break;

			case 4:
				config.type = PointDetectorTypes.KIT_ROS;
				break;

			case 5:
				config.type = PointDetectorTypes.DETERMINANT;
				break;

			default:
				throw new RuntimeException("Unknown selection");
		}

		config.general.radius = featureRadius;
		checkWeighted.setEnabled(enabledWeighted);

		GeneralFeatureDetector<GrayU8,GrayS16> general = FactoryDetectPoint.create(config,GrayU8.class, GrayS16.class);

		EasyGeneralFeatureDetector<GrayU8,GrayS16> easy = new EasyGeneralFeatureDetector<>(general,GrayU8.class, GrayS16.class);

		setProcessing(new PointProcessing(easy));
	}

	@Override
	public void onItemSelected(AdapterView<?> adapterView, View view, int pos, long id) {
		setSelection( pos );
	}

	@Override
	public void onNothingSelected(AdapterView<?> adapterView) {}

	protected class PointProcessing extends DemoProcessingAbstract<GrayU8> {
		EasyGeneralFeatureDetector<GrayU8,GrayS16> detector;

		NonMaxSuppression nonmax;

		// location of point features displayed inside of GUI
		QueueCorner maximumsGUI = new QueueCorner();
		QueueCorner minimumsGUI = new QueueCorner();

		float radius;

		public PointProcessing(EasyGeneralFeatureDetector<GrayU8,GrayS16> detector) {
			super(ImageType.single(GrayU8.class));
			this.detector = detector;
		}

		@Override
		public void initialize(int imageWidth, int imageHeight, int sensorOrientation) {
			radius = 3 * cameraToDisplayDensity;

			if( nonmax != null ) {
				// adjust the non-max region based on image size
				nonmax.setSearchRadius(3 * imageWidth / 320);
				int totalSets = detector.getDetector().isDetectMaximums() ? 1 : 0;
				totalSets += detector.getDetector().isDetectMinimums() ? 1 : 0;
				detector.getDetector().setFeatureLimit(200 * imageWidth / (320*totalSets) );
			}
		}

		@Override
		public void onDraw(Canvas canvas, Matrix imageToView) {

			canvas.concat(imageToView);
			synchronized (lockGui) {
				for (int i = 0; i < maximumsGUI.size; i++) {
					Point2D_I16 p = maximumsGUI.get(i);
					canvas.drawCircle(p.x, p.y, radius, paintMax);
				}

				for (int i = 0; i < minimumsGUI.size; i++) {
					Point2D_I16 p = minimumsGUI.get(i);
					canvas.drawCircle(p.x, p.y, radius, paintMin);
				}
			}
		}

		@Override
		public void process(GrayU8 gray) {
			detector.detect(gray, null);

			synchronized (lockGui) {
				maximumsGUI.reset();
				minimumsGUI.reset();

				if (detector.getDetector().isDetectMaximums())
					maximumsGUI.appendAll(detector.getMaximums());

				if (detector.getDetector().isDetectMinimums())
					minimumsGUI.appendAll(detector.getMinimums());
			}
		}
	}
}