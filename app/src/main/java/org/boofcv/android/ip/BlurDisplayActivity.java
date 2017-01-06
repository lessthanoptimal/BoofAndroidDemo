package org.boofcv.android.ip;

import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.Spinner;

import org.boofcv.android.DemoVideoDisplayActivity;
import org.boofcv.android.R;

import boofcv.abst.filter.blur.BlurFilter;
import boofcv.android.ConvertBitmap;
import boofcv.android.gui.VideoImageProcessing;
import boofcv.factory.filter.blur.FactoryBlurFilter;
import boofcv.struct.image.GrayU8;
import boofcv.struct.image.ImageType;

/**
 * Blurs the input video image using different algorithms.
 *
 * @author Peter Abeles
 */
public class BlurDisplayActivity extends DemoVideoDisplayActivity
		implements AdapterView.OnItemSelectedListener
{

	Spinner spinnerView;

	// amount of blur applied to the image
	int radius;

	BlurProcessing processing;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		LayoutInflater inflater = getLayoutInflater();
		LinearLayout controls = (LinearLayout)inflater.inflate(R.layout.blur_controls,null);

		LinearLayout parent = getViewContent();
		parent.addView(controls);

		spinnerView = (Spinner)controls.findViewById(R.id.spinner_algs);
		ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(this,
				R.array.blurs, android.R.layout.simple_spinner_item);
		adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		spinnerView.setAdapter(adapter);
		spinnerView.setOnItemSelectedListener(this);

		SeekBar seek = (SeekBar)controls.findViewById(R.id.slider_radius);
		radius = seek.getProgress();

		seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
			@Override
			public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
				radius = progress;
				if( radius > 0 )
					processing.setRadius(radius);
			}

			@Override
			public void onStartTrackingTouch(SeekBar seekBar) {}

			@Override
			public void onStopTrackingTouch(SeekBar seekBar) {}
		});
	}

	@Override
	protected void onResume() {
		super.onResume();
		startBlurProcess(spinnerView.getSelectedItemPosition());
	}

	@Override
	public void onItemSelected(AdapterView<?> adapterView, View view, int pos, long id ) {
		startBlurProcess(pos);
	}

	private void startBlurProcess(int pos) {
		// not sure what these do if the radius is set to 0
		int radius = Math.max(1,this.radius);
		switch (pos) {
			case 0:
				processing = new BlurProcessing(FactoryBlurFilter.mean(GrayU8.class, radius));
				break;

			case 1:
				processing = new BlurProcessing(FactoryBlurFilter.gaussian(GrayU8.class,-1,radius));
				break;

			case 2:
				processing = new BlurProcessing(FactoryBlurFilter.median(GrayU8.class,radius));
				break;
		}

		setProcessing(processing);
	}

	@Override
	public void onNothingSelected(AdapterView<?> adapterView) {}

	protected class BlurProcessing extends VideoImageProcessing<GrayU8> {
		GrayU8 blurred;
		final BlurFilter<GrayU8> filter;

		public BlurProcessing(BlurFilter<GrayU8> filter) {
			super(ImageType.single(GrayU8.class));
			this.filter = filter;
		}

		@Override
		protected void declareImages( int width , int height ) {
			super.declareImages(width, height);

			blurred = new GrayU8(width,height);
		}

		@Override
		protected void process(GrayU8 input, Bitmap output, byte[] storage) {
			if( radius > 0 ) {
				synchronized ( filter ) {
					filter.process(input, blurred);
				}
				ConvertBitmap.grayToBitmap(blurred, output, storage);
			} else {
				ConvertBitmap.grayToBitmap(input, output, storage);
			}
		}

		public void setRadius( int radius ) {
			synchronized ( filter ) {
				filter.setRadius(radius);
			}
		}
	}
}