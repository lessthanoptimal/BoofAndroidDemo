package org.boofcv.android;

import android.graphics.Bitmap;
import android.hardware.Camera;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.Spinner;
import boofcv.abst.filter.blur.BlurFilter;
import boofcv.android.ConvertBitmap;
import boofcv.factory.filter.blur.FactoryBlurFilter;
import boofcv.struct.image.ImageUInt8;

/**
 * Blurs the input video image using different algorithms.
 *
 * @author Peter Abeles
 */
public class BlurDisplayActivity extends VideoDisplayActivity
		implements AdapterView.OnItemSelectedListener
{

	Spinner spinnerView;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		LayoutInflater inflater = getLayoutInflater();
		LinearLayout controls = (LinearLayout)inflater.inflate(R.layout.select_algorithm,null);

		LinearLayout parent = (LinearLayout)findViewById(R.id.camera_preview_parent);
		parent.addView(controls);

		spinnerView = (Spinner)controls.findViewById(R.id.spinner_algs);
		ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(this,
				R.array.blurs, android.R.layout.simple_spinner_item);
		adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		spinnerView.setAdapter(adapter);
		spinnerView.setOnItemSelectedListener(this);

		setProcessing(new BlurProcessing(FactoryBlurFilter.mean(ImageUInt8.class,2)) );
	}

	@Override
	public void onItemSelected(AdapterView<?> adapterView, View view, int pos, long id ) {
		switch (pos) {
			case 0:
				setProcessing(new BlurProcessing(FactoryBlurFilter.mean(ImageUInt8.class,2)) );
				break;

			case 1:
				setProcessing(new BlurProcessing(FactoryBlurFilter.gaussian(ImageUInt8.class,-1,2)) );
				break;

			case 2:
				setProcessing(new BlurProcessing(FactoryBlurFilter.median(ImageUInt8.class,2)) );
				break;
		}
	}

	@Override
	public void onNothingSelected(AdapterView<?> adapterView) {}

	protected class BlurProcessing extends BoofImageProcessing {
		ImageUInt8 blurred;
		BlurFilter<ImageUInt8> filter;

		public BlurProcessing(BlurFilter<ImageUInt8> filter) {
			this.filter = filter;
		}

		@Override
		public void init(View view, Camera camera) {
			super.init(view, camera);
			Camera.Size size = camera.getParameters().getPreviewSize();

			blurred = new ImageUInt8(size.width,size.height);
		}

		@Override
		protected void process(ImageUInt8 gray, Bitmap output, byte[] storage) {
			filter.process(gray,blurred);
			ConvertBitmap.grayToBitmap(blurred,output,storage);
		}
	}
}