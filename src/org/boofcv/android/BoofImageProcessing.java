package org.boofcv.android;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.hardware.Camera;
import android.util.Log;
import android.view.View;
import boofcv.abst.filter.blur.BlurFilter;
import boofcv.abst.filter.blur.MedianImageFilter;
import boofcv.abst.filter.derivative.ImageGradient;
import boofcv.android.ConvertBitmap;
import boofcv.android.ConvertNV21;
import boofcv.android.VisualizeImageData;
import boofcv.factory.filter.blur.FactoryBlurFilter;
import boofcv.factory.filter.derivative.FactoryDerivative;
import boofcv.struct.image.ImageSInt16;
import boofcv.struct.image.ImageUInt8;

/**
 * @author Peter Abeles
 */
public abstract class BoofImageProcessing extends BoofRenderProcessing {

	Bitmap output;
	byte[] storage;

	@Override
	protected void declareImages( int width , int height ) {
		super.declareImages(width,height);
		output = Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888 );
		storage = ConvertBitmap.declareStorage(output,storage);
	}

	@Override
	protected void resizeImages( int width , int height ) {
		super.resizeImages(width,height);
		output = Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888 );
		storage = ConvertBitmap.declareStorage(output,storage);
	}

	@Override
	protected void process(ImageUInt8 gray) {
		process(gray,output,storage);
	}

	@Override
	protected void render(Canvas canvas, double imageToOutput) {
		canvas.drawBitmap(output,0,0,null);
	}

	protected abstract void process( ImageUInt8 gray , Bitmap output , byte[] storage );

}
