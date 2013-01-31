package org.boofcv.android;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.hardware.Camera;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import boofcv.abst.filter.FilterImageInterface;
import boofcv.abst.filter.blur.BlurFilter;
import boofcv.android.ConvertBitmap;
import boofcv.factory.filter.blur.FactoryBlurFilter;
import boofcv.struct.image.ImageUInt8;

/**
 * @author Peter Abeles
 */
public class BlurDisplayActivity extends VideoDisplayActivity  {

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		setProcessing(new BlurProcessing(FactoryBlurFilter.mean(ImageUInt8.class,2)) );
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.blur_menu, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		// Handle item selection
		switch (item.getItemId()) {
			case R.id.blur_mean:
				setProcessing(new BlurProcessing(FactoryBlurFilter.mean(ImageUInt8.class,2)) );
				return true;
			case R.id.blur_gaussian:
				setProcessing(new BlurProcessing(FactoryBlurFilter.gaussian(ImageUInt8.class,-1,2)) );
				return true;
			case R.id.blur_median:
				setProcessing(new BlurProcessing(FactoryBlurFilter.median(ImageUInt8.class,2)) );
				return true;
			default:
				return super.onOptionsItemSelected(item);
		}
	}

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

		@Override
		protected void resizeImages(int width, int height) {
			super.resizeImages(width, height);

			blurred.reshape(width,height);
		}
	}
}