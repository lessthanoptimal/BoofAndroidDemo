package org.boofcv.android.ip;

import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.ToggleButton;

import org.boofcv.android.DemoVideoDisplayActivity;
import org.boofcv.android.R;

import boofcv.abst.filter.binary.InputToBinary;
import boofcv.android.VisualizeImageData;
import boofcv.android.gui.VideoImageProcessing;
import boofcv.factory.filter.binary.FactoryThresholdBinary;
import boofcv.struct.image.GrayU8;
import boofcv.struct.image.ImageType;

/**
 * Automatic thresholding
 *
 * @author Peter Abeles
 */
public class ThresholdDisplayActivity extends DemoVideoDisplayActivity
{

	Spinner spinnerView;

	final Object lock = new Object();
	boolean changed = false;
	boolean down;
	int radius;
	int selectedAlg;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		LayoutInflater inflater = getLayoutInflater();
		LinearLayout controls = (LinearLayout)inflater.inflate(R.layout.thresholding_controls,null);

		LinearLayout parent = getViewContent();
		parent.addView(controls);

		spinnerView = (Spinner)controls.findViewById(R.id.spinner_algs);
		ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(this,
				R.array.thresholding, android.R.layout.simple_spinner_item);
		adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		spinnerView.setAdapter(adapter);

		ToggleButton toggle = (ToggleButton)controls.findViewById(R.id.toggle_threshold);
		final SeekBar seek = (SeekBar)controls.findViewById(R.id.slider_radius);

		changed = true;
		selectedAlg = spinnerView.getSelectedItemPosition();
		adjustSeekEnabled(seek);
		down = toggle.isChecked();
		radius = seek.getProgress();

		seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
			@Override
			public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
				synchronized (lock) {
					changed = true;
					radius = progress;
				}
			}

			@Override
			public void onStartTrackingTouch(SeekBar seekBar) {}

			@Override
			public void onStopTrackingTouch(SeekBar seekBar) {}
		});

		spinnerView.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
			@Override
			public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
				synchronized (lock) {
					changed = true;
					selectedAlg = position;
					adjustSeekEnabled(seek);
				}
			}

			@Override
			public void onNothingSelected(AdapterView<?> parent) {}
		});

		toggle.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
			@Override
			public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
				synchronized (lock) {
					changed = true;
					down = isChecked;
				}
			}
		});
	}

	private void adjustSeekEnabled(SeekBar seek) {
		if( selectedAlg <= 1 ) {
			seek.setEnabled(false);
		} else {
			seek.setEnabled(true);
		}
	}

	@Override
	protected void onResume() {
		super.onResume();
		setProcessing(new ThresholdingProcessing());
	}

	private InputToBinary<GrayU8> createFilter() {

		int radius = this.radius + 1;

		switch (selectedAlg) {
			case 0:
				return FactoryThresholdBinary.globalOtsu(0,255,down,GrayU8.class);

			case 1:
				return FactoryThresholdBinary.globalEntropy(0, 255, down, GrayU8.class);

			case 2:
				return FactoryThresholdBinary.localSquare(radius,0.95,down,GrayU8.class);

			case 3:
				return FactoryThresholdBinary.localGaussian(radius,0.95,down,GrayU8.class);

			case 4:
				return FactoryThresholdBinary.localSauvola(radius,0.3f,down,GrayU8.class);
		}

		throw new RuntimeException("Unknown selection "+selectedAlg);
	}

	protected class ThresholdingProcessing extends VideoImageProcessing<GrayU8> {
		GrayU8 binary;
		InputToBinary<GrayU8> filter;

		public ThresholdingProcessing() {
			super(ImageType.single(GrayU8.class));
		}

		@Override
		protected void declareImages( int width , int height ) {
			super.declareImages(width, height);

			binary = new GrayU8(width,height);
		}

		@Override
		protected void process(GrayU8 input, Bitmap output, byte[] storage) {

			synchronized ( lock ) {
				if (changed) {
					changed = false;
					filter = createFilter();
				}
			}

			if( filter != null ) {
				filter.process(input, binary);
			}
			VisualizeImageData.binaryToBitmap(binary,false,  output, storage);
		}
	}
}