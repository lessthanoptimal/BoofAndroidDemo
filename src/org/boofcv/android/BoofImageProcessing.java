package org.boofcv.android;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.hardware.Camera;
import android.util.Log;
import android.view.View;
import boofcv.android.ConvertBitmap;
import boofcv.android.ConvertNV21;
import boofcv.struct.image.ImageUInt8;

/**
 * @author Peter Abeles
 */
public class BoofImageProcessing extends Thread implements BoofProcessing {

	Bitmap output;
	byte[] storage;
	ImageUInt8 gray;
	ImageUInt8 gray2;
	ImageUInt8 binary;
	volatile boolean requestStop = false;
	volatile boolean running = false;

	View view;
	Thread thread;

	public BoofImageProcessing( Camera camera ) {
		Camera.Size size = camera.getParameters().getPreviewSize();
		output = Bitmap.createBitmap(size.width,size.height,Bitmap.Config.ARGB_8888 );
		gray = new ImageUInt8(size.width,size.height);
		gray2 = new ImageUInt8(size.width,size.height);
		binary = new ImageUInt8(size.width,size.height);
		storage = ConvertBitmap.declareStorage(output, null);
	}

	@Override
	public void init(View view) {
		this.view = view;
		// start the thread for processing
		start();
	}

	@Override
	public void onDraw(Canvas canvas) {
		synchronized ( storage ) {
			int w = canvas.getWidth();
			int h = canvas.getHeight();

			// fill the window and center it
			double scaleX = w/(double)output.getWidth();
			double scaleY = h/(double)output.getHeight();

			float scale = (float)Math.min(scaleX,scaleY);
			canvas.translate((w-scale*output.getWidth())/2, (h-scale*output.getHeight())/2);
			canvas.scale(scale,scale);

			// draw the results
			canvas.drawBitmap(output, 0, 0, null);
		}
	}

	@Override
	public void convertPreview(byte[] bytes, Camera camera) {
		Camera.Size size = camera.getParameters().getPreviewSize();
		if( size.width != gray.width || size.height != gray.height ) {
			output = Bitmap.createBitmap(size.width,size.height,Bitmap.Config.ARGB_8888 );
			gray.reshape(size.width,size.height);
			gray2.reshape(size.width,size.height);
			binary.reshape(size.width,size.height);
			storage = ConvertBitmap.declareStorage(output,storage);
		}
		synchronized ( gray ) {
			ConvertNV21.nv21ToGray(bytes, gray.width, gray.height, gray);
		}
		// wake up the thread and tell it to do some processing
		thread.interrupt();
	}

	@Override
	public void stopProcessing() {
		if( thread == null )
			return;

		Log.d("stopProcessin()","ENTER");
		requestStop = true;
		while( running ) {
			// wake the thread up if needed
			thread.interrupt();
			try {
				Thread.sleep(10);
			} catch (InterruptedException e) {}
		}
		Log.d("stopProcessin()","EXIT");
	}

	@Override
	public void run() {
		thread = Thread.currentThread();
		running = true;
		while( !requestStop ) {
			synchronized ( thread ) {
				try {
					wait();
					if( requestStop )
						break;
				} catch (InterruptedException e) {}
			}

			// swap gray buffers so that convertPreview is modifying the copy which is not in use
			synchronized ( gray ) {
				ImageUInt8 tmp = gray;
				gray = gray2;
				gray2 = tmp;
			}

			synchronized ( storage ) {
				ConvertBitmap.grayToBitmap(gray2,output,storage);
			}
			view.postInvalidate();
		}
		running = false;
	}
}
