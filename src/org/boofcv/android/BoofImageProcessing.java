package org.boofcv.android;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import boofcv.android.ConvertBitmap;
import boofcv.struct.image.ImageUInt8;

/**
 * Visualizes a process where the output is simply a rendered Bitmap image.
 *
 * @author Peter Abeles
 */
public abstract class BoofImageProcessing extends BoofRenderProcessing<ImageUInt8> {

	// output image which is modified by processing thread
	Bitmap output;
	// output image which is displayed by the GUI
	Bitmap outputGUI;
	// storage used during image convert
	byte[] storage;

	protected BoofImageProcessing() {
		super(ImageUInt8.class);
	}

	@Override
	protected void declareImages( int width , int height ) {
		super.declareImages(width,height);
		output = Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888 );
		outputGUI = Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888 );
		storage = ConvertBitmap.declareStorage(output,storage);
	}

	@Override
	protected void process(ImageUInt8 gray) {
		process(gray,output,storage);
		synchronized ( lockGui ) {
			Bitmap tmp = output;
			output = outputGUI;
			outputGUI = tmp;
		}
	}

	@Override
	protected void render(Canvas canvas, double imageToOutput) {
		canvas.drawBitmap(outputGUI,0,0,null);
	}

	protected abstract void process( ImageUInt8 gray , Bitmap output , byte[] storage );

}
