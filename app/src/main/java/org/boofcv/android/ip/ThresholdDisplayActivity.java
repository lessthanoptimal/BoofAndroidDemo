package org.boofcv.android.ip;

import android.graphics.Canvas;
import android.graphics.Matrix;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.ToggleButton;

import org.boofcv.android.DemoBitmapCamera2Activity;
import org.boofcv.android.DemoProcessingAbstract;
import org.boofcv.android.R;

import boofcv.abst.filter.binary.InputToBinary;
import boofcv.android.VisualizeImageData;
import boofcv.factory.filter.binary.FactoryThresholdBinary;
import boofcv.struct.ConfigLength;
import boofcv.struct.image.GrayU8;

/**
 * Automatic thresholding
 *
 * @author Peter Abeles
 */
public class ThresholdDisplayActivity extends DemoBitmapCamera2Activity
{

	Spinner spinnerView;

	final Object lock = new Object();
	boolean changed = false;
	boolean down;
	boolean localBlock = true;
	int width;
	int selectedAlg;

	public ThresholdDisplayActivity() {
		super(Resolution.MEDIUM);
		super.changeResolutionOnSlow = true;
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		LayoutInflater inflater = getLayoutInflater();
		LinearLayout controls = (LinearLayout)inflater.inflate(R.layout.thresholding_controls,null);

		spinnerView = controls.findViewById(R.id.spinner_algs);
		ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(this,
				R.array.thresholding, android.R.layout.simple_spinner_item);
		adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		spinnerView.setAdapter(adapter);

		ToggleButton toggle = controls.findViewById(R.id.toggle_threshold);
		final SeekBar seek = controls.findViewById(R.id.slider_width);

		ToggleButton toggleLocal = controls.findViewById(R.id.toggle_local_block);

		changed = true;
		selectedAlg = spinnerView.getSelectedItemPosition();
		adjustSeekEnabled(seek);
		down = toggle.isChecked();
		width = seek.getProgress();
		localBlock = toggleLocal.isChecked();

		seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
			@Override
			public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
				synchronized (lock) {
					changed = true;
					width = progress;
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
					adjustLocalBlockEnabled(toggleLocal);
				}
			}

			@Override
			public void onNothingSelected(AdapterView<?> parent) {}
		});

		toggle.setOnCheckedChangeListener((buttonView, isChecked) -> {
            synchronized (lock) {
                changed = true;
                down = isChecked;
            }
        });

		toggleLocal.setOnCheckedChangeListener((buttonView, isChecked) -> {
			synchronized (lock) {
				changed = true;
				localBlock = isChecked;
			}
		});

		setControls(controls);
		activateTouchToShowInput();
	}

	private void adjustSeekEnabled(SeekBar seek) {
		seek.setEnabled(selectedAlg > 1);
	}

	private void adjustLocalBlockEnabled(ToggleButton toggle) {
		toggle.setEnabled(selectedAlg >= 6);
	}

	@Override
	public void createNewProcessor() {
		setProcessing(new ThresholdingProcessing());
	}

	private InputToBinary<GrayU8> createFilter() {

		int width = this.width + 3;

		int blockWidth = width*2+4;

		switch (selectedAlg) {
			case 0:
				return FactoryThresholdBinary.globalOtsu(0,255,1.0,down,GrayU8.class);

			case 1:
				return FactoryThresholdBinary.globalEntropy(0, 255, 1.0,down, GrayU8.class);

			case 2:
				return FactoryThresholdBinary.localMean(ConfigLength.fixed(width),0.95,down,GrayU8.class);

			case 3:
				return FactoryThresholdBinary.localGaussian(ConfigLength.fixed(width),0.95,down,GrayU8.class);

			case 4:
				return FactoryThresholdBinary.localSauvola(ConfigLength.fixed(width),down,0.3f,GrayU8.class);

			case 5:
				return FactoryThresholdBinary.localNick(ConfigLength.fixed(width),down,-0.2f,GrayU8.class);

			case 6:
				return FactoryThresholdBinary.blockMean(ConfigLength.fixed(blockWidth),0.95,down,localBlock,GrayU8.class);

			case 7:
				return FactoryThresholdBinary.blockMinMax(ConfigLength.fixed(blockWidth),0.95,down,localBlock,2,GrayU8.class);

			case 8:
				return FactoryThresholdBinary.blockOtsu(ConfigLength.fixed(blockWidth),0.95,down,localBlock,true,0.0,GrayU8.class);
		}

		throw new RuntimeException("Unknown selection "+selectedAlg);
	}

	protected class ThresholdingProcessing extends DemoProcessingAbstract<GrayU8> {
		GrayU8 binary;
		InputToBinary<GrayU8> filter;

		public ThresholdingProcessing() {
			super(GrayU8.class);
		}

		@Override
		public void initialize(int imageWidth, int imageHeight, int sensorOrientation) {
			binary = new GrayU8(imageWidth,imageHeight);
		}

		@Override
		public void onDraw(Canvas canvas, Matrix imageToView) {
			canvas.drawBitmap(bitmap, imageToView, null);
		}

		@Override
		public void process(GrayU8 input) {
			synchronized (lock) {
				if (changed) {
					changed = false;
					filter = createFilter();
				}
			}

			if (filter != null) {
				filter.process(input, binary);
				VisualizeImageData.binaryToBitmap(binary, false, bitmap, bitmapTmp);
			}
		}
	}
}