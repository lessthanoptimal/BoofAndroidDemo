package org.boofcv.android;

import android.graphics.Bitmap;
import android.hardware.Camera;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.*;
import boofcv.alg.enhance.EnhanceImageOps;
import boofcv.alg.misc.ImageStatistics;
import boofcv.android.ConvertBitmap;
import boofcv.core.image.ConvertImage;
import boofcv.struct.image.ImageUInt8;
import boofcv.struct.image.MultiSpectral;

/**
 * Blurs the input video image using different algorithms.
 *
 * @author Peter Abeles
 */
public class EnhanceDisplayActivity extends VideoDisplayActivity
		implements AdapterView.OnItemSelectedListener, CompoundButton.OnCheckedChangeListener
{

	Spinner spinnerView;
	CheckBox checkColor;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		LayoutInflater inflater = getLayoutInflater();
		LinearLayout controls = (LinearLayout)inflater.inflate(R.layout.enhance_controls,null);

		LinearLayout parent = (LinearLayout)findViewById(R.id.camera_preview_parent);
		parent.addView(controls);

		spinnerView = (Spinner)controls.findViewById(R.id.spinner_enhance);
		ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(this,
				R.array.enhance, android.R.layout.simple_spinner_item);
		adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		spinnerView.setAdapter(adapter);
		spinnerView.setOnItemSelectedListener(this);

		checkColor = (CheckBox)controls.findViewById(R.id.check_color);
		checkColor.setOnCheckedChangeListener(this);
	}

	@Override
	protected void onResume() {
		super.onResume();
		startBlurProcess(spinnerView.getSelectedItemPosition(),checkColor.isChecked());
	}

	@Override
	public void onItemSelected(AdapterView<?> adapterView, View view, int pos, long id ) {
		startBlurProcess(pos,checkColor.isChecked());
	}

	private void startBlurProcess(int pos, boolean color ) {
		switch (pos) {
			case 0:
				if( color )
					setProcessing(new HistogramGlobalProcessingColor() );
				else
					setProcessing(new HistogramGlobalProcessing() );
				break;

			case 1:
				if( color )
					setProcessing(new HistogramLocalProcessingColor() );
				else
					setProcessing(new HistogramLocalProcessing() );
				break;

			case 2:
				if( color )
					setProcessing(new SharpenProcessingColor(4) );
				else
					setProcessing(new SharpenProcessing(4) );
				break;

			case 3:
				if( color )
					setProcessing(new SharpenProcessingColor(8) );
				else
					setProcessing(new SharpenProcessing(8) );
				break;

			case 4:
				if( color )
					setProcessing(new NoneProcessingColor() );
				else
					setProcessing(new NoneProcessing() );
				break;
		}
	}

	@Override
	public void onNothingSelected(AdapterView<?> adapterView) {}

	@Override
	public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
		startBlurProcess(spinnerView.getSelectedItemPosition(),isChecked);
	}

	protected abstract class EnhanceProcessing extends BoofImageProcessing {
		ImageUInt8 enhanced;

		@Override
		public void init(View view, Camera camera) {
			super.init(view, camera);
			Camera.Size size = camera.getParameters().getPreviewSize();

			enhanced = new ImageUInt8(size.width,size.height);
		}
	}

	protected abstract class EnhanceProcessingColor extends BoofMsImageProcessing {
		MultiSpectral<ImageUInt8> enhanced;
		ImageUInt8 gray;

		@Override
		public void init(View view, Camera camera) {
			super.init(view, camera);
			Camera.Size size = camera.getParameters().getPreviewSize();

			gray = new ImageUInt8(size.width,size.height);
			enhanced = new MultiSpectral<ImageUInt8>(ImageUInt8.class,size.width,size.height,3);
		}
	}

	protected class HistogramGlobalProcessing extends EnhanceProcessing {
		int histogram[] = new int[256];
		int transform[] = new int[256];


		@Override
		protected void process(ImageUInt8 gray, Bitmap output, byte[] storage) {
			ImageStatistics.histogram(gray, histogram);
			EnhanceImageOps.equalize(histogram, transform);
			EnhanceImageOps.applyTransform(gray, transform, enhanced);
			ConvertBitmap.grayToBitmap(enhanced,output,storage);
		}
	}

	protected class HistogramGlobalProcessingColor extends EnhanceProcessingColor {
		int histogram[] = new int[256];
		int transform[] = new int[256];

		@Override
		protected void process(MultiSpectral<ImageUInt8> color, Bitmap output, byte[] storage) {
			ConvertImage.average(color,gray);
			ImageStatistics.histogram(gray, histogram);
			EnhanceImageOps.equalize(histogram, transform);
			for( int i = 0; i < 3; i++ )
				EnhanceImageOps.applyTransform(color.getBand(i), transform, enhanced.getBand(i));
			ConvertBitmap.multiToBitmap(enhanced,output,storage);
		}
	}

	protected class HistogramLocalProcessing extends EnhanceProcessing {
		int histogram[] = new int[256];
		int transform[] = new int[256];

		@Override
		protected void process(ImageUInt8 gray, Bitmap output, byte[] storage) {
			EnhanceImageOps.equalizeLocal(gray, 50, enhanced, histogram, transform);
			ConvertBitmap.grayToBitmap(enhanced,output,storage);
		}
	}

	protected class HistogramLocalProcessingColor extends EnhanceProcessingColor {
		int histogram[] = new int[256];
		int transform[] = new int[256];

		@Override
		protected void process(MultiSpectral<ImageUInt8> color, Bitmap output, byte[] storage) {
			for( int i = 0; i < 3; i++ )
				EnhanceImageOps.equalizeLocal(color.getBand(i), 50, enhanced.getBand(i), histogram, transform);
			ConvertBitmap.multiToBitmap(enhanced,output,storage);
		}
	}

	protected class SharpenProcessing extends EnhanceProcessing {

		int which;

		public SharpenProcessing( int which ) {
			this.which = which;
		}

		@Override
		protected void process(ImageUInt8 gray, Bitmap output, byte[] storage) {
			if( which == 4 )
				EnhanceImageOps.sharpen4(gray, enhanced);
			else
				EnhanceImageOps.sharpen8(gray, enhanced);
			ConvertBitmap.grayToBitmap(enhanced,output,storage);
		}
	}

	protected class SharpenProcessingColor extends EnhanceProcessingColor {

		int which;

		public SharpenProcessingColor( int which ) {
			this.which = which;
		}

		@Override
		protected void process( MultiSpectral<ImageUInt8> color, Bitmap output, byte[] storage) {
			for( int i = 0; i < 3; i++ ) {
				if( which == 4 )
					EnhanceImageOps.sharpen4(color.getBand(i), enhanced.getBand(i));
				else
					EnhanceImageOps.sharpen8(color.getBand(i), enhanced.getBand(i));
			}
			ConvertBitmap.multiToBitmap(enhanced, output, storage);
		}
	}

	protected class NoneProcessing extends EnhanceProcessing {

		@Override
		protected void process(ImageUInt8 gray, Bitmap output, byte[] storage) {
			ConvertBitmap.grayToBitmap(gray,output,storage);
		}
	}

	protected class NoneProcessingColor extends EnhanceProcessingColor {

		@Override
		protected void process(MultiSpectral<ImageUInt8> color, Bitmap output, byte[] storage) {
			ConvertBitmap.multiToBitmap(color, output, storage);
		}
	}
}