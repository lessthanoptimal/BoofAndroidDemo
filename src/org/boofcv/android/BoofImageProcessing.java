package org.boofcv.android;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import boofcv.android.ConvertBitmap;
import boofcv.struct.image.ImageUInt8;

/**
 * @author Peter Abeles
 */
public abstract class BoofImageProcessing extends BoofRenderProcessing<ImageUInt8> {

	Bitmap output;
	byte[] storage;

	protected BoofImageProcessing() {
		super(ImageUInt8.class);
	}

	@Override
	protected void declareImages( int width , int height ) {
		super.declareImages(width,height);
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
