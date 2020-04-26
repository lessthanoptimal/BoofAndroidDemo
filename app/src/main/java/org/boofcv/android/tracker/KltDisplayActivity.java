package org.boofcv.android.tracker;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.widget.LinearLayout;
import android.widget.SeekBar;

import org.boofcv.android.R;

import boofcv.abst.feature.detect.interest.ConfigPointDetector;
import boofcv.abst.feature.detect.interest.PointDetectorTypes;
import boofcv.abst.tracker.PointTracker;
import boofcv.alg.tracker.klt.ConfigPKlt;
import boofcv.factory.tracker.FactoryPointTracker;
import boofcv.struct.image.GrayS16;
import boofcv.struct.image.GrayU8;
import boofcv.struct.pyramid.ConfigDiscreteLevels;

/**
 * Displays KLT tracks.
 *
 * @author Peter Abeles
 */
public class KltDisplayActivity extends PointTrackerDisplayActivity {

	private int maxFeatures=120;

	public KltDisplayActivity() {
		super(Resolution.LOW);
		super.changeResolutionOnSlow = true;
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		LayoutInflater inflater = getLayoutInflater();
		LinearLayout controls = (LinearLayout)inflater.inflate(R.layout.klt_controls,null);

		SeekBar seek = controls.findViewById(R.id.slider_tracks);
		seek.setProgress(maxFeatures);

		seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
			@Override
			public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
				maxFeatures = progress+1;
				createNewProcessor();
			}

			@Override
			public void onStartTrackingTouch(SeekBar seekBar) {}

			@Override
			public void onStopTrackingTouch(SeekBar seekBar) {}
		});


		setControls(controls);
	}

	@Override
	public void createNewProcessor() {
		ConfigPointDetector configDet = new ConfigPointDetector();
		configDet.type = PointDetectorTypes.SHI_TOMASI;
		configDet.shiTomasi.radius = 3;
		configDet.general.maxFeatures = maxFeatures;
		configDet.general.threshold = Math.max(20,45-(maxFeatures/20));
		configDet.general.radius = 4;

		ConfigPKlt configKlt = new ConfigPKlt();
		configKlt.pyramidLevels = ConfigDiscreteLevels.levels(3);
		configKlt.templateRadius = 3;

		PointTracker<GrayU8> tracker =
				FactoryPointTracker.klt(configKlt,configDet,GrayU8.class, GrayS16.class);

		Log.i("KLT","maxFeatures = "+maxFeatures);
		setProcessing(new PointProcessing(tracker));
	}

	@Override
	protected void onResume() {
		super.onResume();
	}
}