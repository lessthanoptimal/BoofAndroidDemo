package org.boofcv.android.ip;

import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.Spinner;

import org.boofcv.android.DemoVideoDisplayActivity;
import org.boofcv.android.R;

import boofcv.abst.filter.derivative.ImageGradient;
import boofcv.android.VisualizeImageData;
import boofcv.android.gui.VideoImageProcessing;
import boofcv.factory.filter.derivative.FactoryDerivative;
import boofcv.struct.image.GrayS16;
import boofcv.struct.image.GrayU8;
import boofcv.struct.image.ImageType;

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

	Class imageType = GrayU8.class;
	Class derivType = GrayS16.class;

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

			case 3:
				setProcessing(new GradientProcessing(FactoryDerivative.two0(imageType, derivType)) );
				break;

			case 4:
				setProcessing(new GradientProcessing(FactoryDerivative.two1(imageType, derivType)) );
				break;

			default:
				throw new RuntimeException("Unknown gradient");
		}
	}

	@Override
	public void onNothingSelected(AdapterView<?> adapterView) {}

	protected class GradientProcessing extends VideoImageProcessing<GrayU8> {
		GrayS16 derivX;
		GrayS16 derivY;
		ImageGradient<GrayU8,GrayS16> gradient;

		public GradientProcessing(ImageGradient<GrayU8,GrayS16> gradient) {
			super(ImageType.single(GrayU8.class));
			this.gradient = gradient;
		}

		@Override
		protected void declareImages( int width , int height ) {
			super.declareImages(width, height);

			derivX = new GrayS16(width,height);
			derivY = new GrayS16(width,height);
		}

		@Override
		protected void process(GrayU8 input, Bitmap output, byte[] storage) {
			gradient.process(input,derivX,derivY);
			VisualizeImageData.colorizeGradient(derivX,derivY,-1,output,storage);
		}
	}
}