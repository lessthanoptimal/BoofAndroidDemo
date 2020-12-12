package org.boofcv.android.tracker;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.ToggleButton;

import org.boofcv.android.R;

import boofcv.abst.feature.detect.interest.ConfigPointDetector;
import boofcv.abst.feature.detect.interest.PointDetectorTypes;
import boofcv.abst.tracker.PointTracker;
import boofcv.alg.tracker.klt.ConfigPKlt;
import boofcv.factory.feature.detect.selector.ConfigSelectLimit;
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

	private int maxFeatures=100;

	public KltDisplayActivity() {
		super(Resolution.LOW);
		super.changeResolutionOnSlow = true;
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		LayoutInflater inflater = getLayoutInflater();
		LinearLayout controls = (LinearLayout)inflater.inflate(R.layout.klt_controls,null);

		ToggleButton toggle = controls.findViewById(R.id.toggle_dots);
		toggle.setOnCheckedChangeListener((view,value)-> renderDots=value);

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
		Log.i("KLT","MaxFeatures="+maxFeatures);
		ConfigPointDetector configDet = new ConfigPointDetector();
		configDet.type = PointDetectorTypes.SHI_TOMASI;
		configDet.shiTomasi.radius = 3;
		configDet.general.maxFeatures = 10+maxFeatures;
		configDet.general.threshold = 0.05f;
		configDet.general.radius = 4;
		configDet.general.selector = ConfigSelectLimit.selectUniform(10.0);

		ConfigPKlt configKlt = new ConfigPKlt();
		configKlt.pyramidLevels = ConfigDiscreteLevels.minSize(40);
		configKlt.templateRadius = 3;
		configKlt.maximumTracks.setFixed(configDet.general.maxFeatures);
		configKlt.toleranceFB = 2;

		respawnThreshold = Math.max(1,(int)(configKlt.maximumTracks.length/4));

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