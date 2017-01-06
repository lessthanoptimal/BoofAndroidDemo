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

import boofcv.alg.filter.binary.BinaryImageOps;
import boofcv.alg.filter.binary.GThresholdImageOps;
import boofcv.android.VisualizeImageData;
import boofcv.android.gui.VideoImageProcessing;
import boofcv.struct.image.GrayU8;
import boofcv.struct.image.ImageType;

/**
 * Converts camera image into a binary image and lets the user control the threshold/filters.
 *
 * @author Peter Abeles
 */
public class BinaryDisplayActivity extends DemoVideoDisplayActivity
		implements SeekBar.OnSeekBarChangeListener ,
		CompoundButton.OnCheckedChangeListener,
		AdapterView.OnItemSelectedListener {

	boolean down;
	double threshold;
	int action;

	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		LayoutInflater inflater = getLayoutInflater();
		LinearLayout controls = (LinearLayout)inflater.inflate(R.layout.binary_controls,null);

		LinearLayout parent = getViewContent();
		parent.addView(controls);

		SeekBar seek = (SeekBar)controls.findViewById(R.id.slider_threshold);
		seek.setOnSeekBarChangeListener(this);

		ToggleButton toggle = (ToggleButton)controls.findViewById(R.id.toggle_threshold);
		toggle.setOnCheckedChangeListener(this);

		Spinner spinner = (Spinner)controls.findViewById(R.id.spinner_binary_ops);
		ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(this,
				R.array.binary_filters, android.R.layout.simple_spinner_item);
		adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		spinner.setAdapter(adapter);
		spinner.setOnItemSelectedListener(this);

		down = toggle.isChecked();
		threshold = seek.getProgress();
		action = spinner.getSelectedItemPosition();
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

	protected class ThresholdProcessing extends VideoImageProcessing<GrayU8> {
		GrayU8 binary;
		GrayU8 afterOps;

		protected ThresholdProcessing() {
			super(ImageType.single(GrayU8.class));
		}

		@Override
		protected void declareImages( int width , int height ) {
			super.declareImages(width, height);

			binary = new GrayU8(width,height);
			afterOps = new GrayU8(width,height);
		}

		@Override
		protected void process(GrayU8 input, Bitmap output, byte[] storage) {
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

			VisualizeImageData.binaryToBitmap(afterOps, false, output, storage);
		}
	}
}