package org.boofcv.android.ip;

import android.graphics.Canvas;
import android.graphics.Matrix;
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

import org.boofcv.android.DemoFilterCamera2Activity;
import org.boofcv.android.DemoProcessing;
import org.boofcv.android.R;

import boofcv.alg.filter.binary.BinaryImageOps;
import boofcv.alg.filter.binary.GThresholdImageOps;
import boofcv.android.VisualizeImageData;
import boofcv.struct.image.GrayU8;
import boofcv.struct.image.ImageType;

/**
 * Converts camera image into a binary image and lets the user control the threshold/filters.
 *
 * @author Peter Abeles
 */
public class BinaryDisplayActivity extends DemoFilterCamera2Activity
		implements SeekBar.OnSeekBarChangeListener ,
		CompoundButton.OnCheckedChangeListener,
		AdapterView.OnItemSelectedListener {

	boolean down;
	double threshold;
	int action;

	public BinaryDisplayActivity() {
		super(Resolution.MEDIUM);
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		LayoutInflater inflater = getLayoutInflater();
		LinearLayout controls = (LinearLayout)inflater.inflate(R.layout.binary_controls,null);

		SeekBar seek = controls.findViewById(R.id.slider_threshold);
		seek.setOnSeekBarChangeListener(this);

		ToggleButton toggle = controls.findViewById(R.id.toggle_threshold);
		toggle.setOnCheckedChangeListener(this);

		Spinner spinner = controls.findViewById(R.id.spinner_binary_ops);
		ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(this,
				R.array.binary_filters, android.R.layout.simple_spinner_item);
		adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		spinner.setAdapter(adapter);
		spinner.setOnItemSelectedListener(this);

		down = toggle.isChecked();
		threshold = seek.getProgress();
		action = spinner.getSelectedItemPosition();

		setControls(controls);
	}

	@Override
	protected void onResume() {
		super.onResume();
		setProcessing(new ThresholdProcessing() );
	}

	@Override
	public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
		threshold = progress;
	}

	@Override
	public void onStartTrackingTouch(SeekBar seekBar) {}

	@Override
	public void onStopTrackingTouch(SeekBar seekBar) {}

	@Override
	public void onCheckedChanged(CompoundButton compoundButton, boolean isChecked) {
		down = isChecked;
	}

	@Override
	public void onItemSelected(AdapterView<?> adapterView, View view, int pos, long id ) {
		action = pos;
	}

	@Override
	public void onNothingSelected(AdapterView<?> adapterView) {}

	protected void convertToOutput(GrayU8 binary ) {
		synchronized (bitmapLock) {
			VisualizeImageData.binaryToBitmap(binary, false,bitmap, bitmapTmp);
		}
	}

	protected class ThresholdProcessing implements DemoProcessing<GrayU8> {
		GrayU8 binary;
		GrayU8 afterOps;

		@Override
		public void initialize(int imageWidth, int imageHeight) {
			binary = new GrayU8(imageWidth,imageHeight);
			afterOps = new GrayU8(imageWidth,imageHeight);
		}

		@Override
		public void onDraw(Canvas canvas, Matrix imageToView) {
			drawBitmap(canvas,imageToView);
		}

		@Override
		public void process(GrayU8 input) {
			GThresholdImageOps.threshold(input,binary,threshold, down);

			switch( action ) {
				case 1:
					BinaryImageOps.dilate4(binary,1,afterOps);
					break;

				case 2:
					BinaryImageOps.dilate8(binary, 1, afterOps);
					break;

				case 3:
					BinaryImageOps.erode4(binary, 1, afterOps);
					break;

				case 4:
					BinaryImageOps.erode8(binary, 1, afterOps);
					break;

				case 5:
					BinaryImageOps.edge4(binary, afterOps);
					break;

				case 6:
					BinaryImageOps.edge8(binary, afterOps);
					break;

				case 7:
					BinaryImageOps.removePointNoise(binary, afterOps);
					break;

				case 8:
					BinaryImageOps.thin(binary,50,afterOps);
					break;

				default:
					afterOps.setTo(binary);
			}

			convertToOutput(afterOps);
		}

		@Override
		public void stop() {

		}

		@Override
		public boolean isThreadSafe() {
			return false;
		}

		@Override
		public ImageType<GrayU8> getImageType() {
			return ImageType.single(GrayU8.class);
		}
	}
}