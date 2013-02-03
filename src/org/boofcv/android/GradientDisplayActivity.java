package org.boofcv.android;

import android.graphics.Bitmap;
import android.hardware.Camera;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import boofcv.abst.filter.blur.BlurFilter;
import boofcv.abst.filter.derivative.ImageGradient;
import boofcv.android.VisualizeImageData;
import boofcv.factory.filter.blur.FactoryBlurFilter;
import boofcv.factory.filter.derivative.FactoryDerivative;
import boofcv.struct.image.ImageSInt16;
import boofcv.struct.image.ImageUInt8;

/**
 * @author Peter Abeles
 */
public class GradientDisplayActivity extends VideoDisplayActivity  {

	Class imageType = ImageUInt8.class;
	Class derivType = ImageSInt16.class;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		setProcessing(new GradientProcessing(FactoryDerivative.three(imageType, derivType)) );
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.gradient_menu, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item)
	{
		switch (item.getItemId()) {
			case R.id.gradient_three:
				setProcessing(new GradientProcessing(FactoryDerivative.three(imageType,derivType)) );
				return true;
			case R.id.gradient_sobel:
				setProcessing(new GradientProcessing(FactoryDerivative.sobel(imageType, derivType)) );
				return true;
			case R.id.gradient_prewitt:
				setProcessing(new GradientProcessing(FactoryDerivative.prewitt(imageType, derivType)) );
				return true;
			default:
				return super.onOptionsItemSelected(item);
		}
	}

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