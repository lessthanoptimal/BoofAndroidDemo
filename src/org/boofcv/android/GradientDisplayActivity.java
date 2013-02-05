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
import boofcv.abst.filter.derivative.ImageGradient;
import boofcv.android.VisualizeImageData;
import boofcv.factory.filter.derivative.FactoryDerivative;
import boofcv.struct.image.ImageSInt16;
import boofcv.struct.image.ImageUInt8;

/**
 * @author Peter Abeles
 */
public class GradientDisplayActivity extends VideoDisplayActivity
implements AdapterView.OnItemSelectedListener
{

	Class imageType = ImageUInt8.class;
	Class derivType = ImageSInt16.class;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		LayoutInflater inflater = getLayoutInflater();
		LinearLayout controls = (LinearLayout)inflater.inflate(R.layout.gradient_controls,null);

		LinearLayout parent = (LinearLayout)findViewById(R.id.camera_preview_parent);
		parent.addView(controls);

		Spinner spinnerGradient = (Spinner)controls.findViewById(R.id.spinner_gradient);
		ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(this,
				R.array.gradients, android.R.layout.simple_spinner_item);
		adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		spinnerGradient.setAdapter(adapter);
		spinnerGradient.setOnItemSelectedListener(this);
	}

	@Override
	public void onItemSelected(AdapterView<?> adapterView, View view, int pos, long id) {
		switch( pos ) {
			case 0:
				setProcessing(new GradientProcessing(FactoryDerivative.three(imageType,derivType)) );
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

	protected class GradientProcessing extends BoofImageProcessing {
		ImageSInt16 derivX;
		ImageSInt16 derivY;
		ImageGradient<ImageUInt8,ImageSInt16> gradient;

		public GradientProcessing(ImageGradient<ImageUInt8,ImageSInt16> gradient) {
			this.gradient = gradient;
		}

		@Override
		public void init(View view, Camera camera) {
			super.init(view, camera);
			Camera.Size size = camera.getParameters().getPreviewSize();

			derivX = new ImageSInt16(size.width,size.height);
			derivY = new ImageSInt16(size.width,size.height);
		}

		@Override
		protected void process(ImageUInt8 gray, Bitmap output, byte[] storage) {
			gradient.process(gray,derivX,derivY);
			VisualizeImageData.colorizeGradient(derivX,derivY,-1,output,storage);
		}
	}
}