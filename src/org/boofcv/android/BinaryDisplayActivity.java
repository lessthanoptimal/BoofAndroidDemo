package org.boofcv.android;

import android.graphics.Bitmap;
import android.hardware.Camera;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.*;
import boofcv.alg.filter.binary.BinaryImageOps;
import boofcv.alg.filter.binary.GThresholdImageOps;
import boofcv.android.VisualizeImageData;
import boofcv.struct.image.ImageUInt8;

/**
 * Converts camera image into a binary image and lets the user control the threshold/filters.
 *
 * @author Peter Abeles
 */
public class BinaryDisplayActivity extends VideoDisplayActivity
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

		LinearLayout parent = (LinearLayout)findViewById(R.id.camera_preview_parent);
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

	protected class ThresholdProcessing extends BoofImageProcessing {
		ImageUInt8 binary;
		ImageUInt8 afterOps;

		@Override
		public void init(View view, Camera camera) {
			super.init(view, camera);
			Camera.Size size = camera.getParameters().getPreviewSize();

			binary = new ImageUInt8(size.width,size.height);
			afterOps = new ImageUInt8(size.width,size.height);
		}

		@Override
		protected void process(ImageUInt8 gray, Bitmap output, byte[] storage) {
			GThresholdImageOps.threshold(gray,binary,threshold, down);

			switch( action ) {
				case 1:
					BinaryImageOps.dilate4(binary,afterOps);
					break;

				case 2:
					BinaryImageOps.dilate8(binary,afterOps);
					break;

				case 3:
					BinaryImageOps.erode4(binary,afterOps);
					break;

				case 4:
					BinaryImageOps.erode8(binary,afterOps);
					break;

				case 5:
					BinaryImageOps.edge4(binary,afterOps);
					break;

				case 6:
					BinaryImageOps.edge8(binary,afterOps);
					break;

				case 7:
					BinaryImageOps.removePointNoise(binary,afterOps);
					break;

				default:
					afterOps.setTo(binary);
			}

			VisualizeImageData.binaryToBitmap(afterOps, output, storage);
		}
	}
}