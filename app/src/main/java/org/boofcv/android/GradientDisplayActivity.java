package org.boofcv.android;

import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.Spinner;

import boofcv.abst.filter.derivative.ImageGradient;
import boofcv.android.VisualizeImageData;
import boofcv.android.gui.VideoImageProcessing;
import boofcv.factory.filter.derivative.FactoryDerivative;
import boofcv.struct.image.ImageSInt16;
import boofcv.struct.image.ImageType;
import boofcv.struct.image.ImageUInt8;

/**
 * Displays the gradient of a gray scale image.  The gradient is colorized so that x and y directions are visible.
 * User can select which gradient algorithm to use.
 *
 * @author Peter Abeles
 */
public class GradientDisplayActivity extends DemoVideoDisplayActivity
implements AdapterView.OnItemSelectedListener
{

	Spinner spinnerGradient;

	Class imageType = ImageUInt8.class;
	Class derivType = ImageSInt16.class;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		LayoutInflater inflater = getLayoutInflater();
		LinearLayout controls = (LinearLayout)inflater.inflate(R.layout.gradient_controls,null);

		LinearLayout parent = getViewContent();
		parent.addView(controls);

		spinnerGradient = (Spinner)controls.findViewById(R.id.spinner_gradient);
		ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(this,
				R.array.gradients, android.R.layout.simple_spinner_item);
		adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		spinnerGradient.setAdapter(adapter);
		spinnerGradient.setOnItemSelectedListener(this);
	}

	@Override
	protected void onResume() {
		super.onResume();
		startGradientProcess(spinnerGradient.getSelectedItemPosition());

	}

	@Override
	public void onItemSelected(AdapterView<?> adapterView, View view, int pos, long id) {
		startGradientProcess(pos);
	}

	private void startGradientProcess(int pos) {
		switch( pos ) {
			case 0:
				setProcessing(new GradientProcessing(FactoryDerivative.three(imageType, derivType)) );
				break;

			case 1:
				setProcessing(new GradientProcessing(FactoryDerivative.sobel(imageType, derivType)) );
				break;

			case 2:
				setProcessing(new GradientProcessing(FactoryDerivative.prewitt(imageType, derivType)) );
				break;

			default:
				throw new RuntimeException("Unknown gradient");
		}
	}

	@Override
	public void onNothingSelected(AdapterView<?> adapterView) {}

	protected class GradientProcessing extends VideoImageProcessing<ImageUInt8> {
		ImageSInt16 derivX;
		ImageSInt16 derivY;
		ImageGradient<ImageUInt8,ImageSInt16> gradient;

		public GradientProcessing(ImageGradient<ImageUInt8,ImageSInt16> gradient) {
			super(ImageType.single(ImageUInt8.class));
			this.gradient = gradient;
		}

		@Override
		protected void declareImages( int width , int height ) {
			super.declareImages(width, height);

			derivX = new ImageSInt16(width,height);
			derivY = new ImageSInt16(width,height);
		}

		@Override
		protected void process(ImageUInt8 input, Bitmap output, byte[] storage) {
			gradient.process(input,derivX,derivY);
			VisualizeImageData.colorizeGradient(derivX,derivY,-1,output,storage);
		}
	}
}