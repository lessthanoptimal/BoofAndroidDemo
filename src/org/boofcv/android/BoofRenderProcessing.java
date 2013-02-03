package org.boofcv.android;

import android.graphics.*;
import android.hardware.Camera;
import android.util.Log;
import android.view.View;
import boofcv.android.ConvertBitmap;
import boofcv.android.ConvertNV21;
import boofcv.struct.image.ImageUInt8;

/**
 * @author Peter Abeles
 */
public abstract class BoofRenderProcessing extends Thread implements BoofProcessing {

	ImageUInt8 gray;
	ImageUInt8 gray2;

	volatile boolean requestStop = false;
	volatile boolean running = false;

	// size of the area being down for output.  defaults to image size
	int outputWidth;
	int outputHeight;

	View view;
	Thread thread;

	Object lockGui = new Object();
	Object lockConvert = new Object();

	// It is possible for this class to have been inserted between process() and render() operations
	// this variable is used to make sure it won't try to render before processing
	boolean hasProcessedImage = false;

	@Override
	public void init(View view, Camera camera ) {
		synchronized (lockGui) {
			this.view = view;

			Camera.Size size = camera.getParameters().getPreviewSize();
			outputWidth = size.width;
			outputHeight = size.height;
			declareImages(size.width,size.height);
		}

		// start the thread for processing
		start();
	}

	@Override
	public void onDraw(Canvas canvas) {
		synchronized (lockGui) {
			// the process class could have been swapped
			if( !hasProcessedImage || gray == null )
				return;

			int w = canvas.getWidth();
			int h = canvas.getHeight();

			// fill the window and center it
			double scaleX = w/(double)outputWidth;
			double scaleY = h/(double)outputHeight;

			float scale = (float)Math.min(scaleX,scaleY);
			canvas.translate((w-scale*outputWidth)/2, (h-scale*outputHeight)/2);
			canvas.scale(scale,scale);

			render(canvas, scale);
		}
	}

	@Override
	public void convertPreview(byte[] bytes, Camera camera) {
		if( thread == null )
			return;

		synchronized ( lockConvert ) {
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
			synchronized ( lockConvert ) {
				ImageUInt8 tmp = gray;
				gray = gray2;
				gray2 = tmp;
			}

			synchronized (lockGui) {
				process(gray2);
				hasProcessedImage = true;
			}

			view.postInvalidate();
		}
		running = false;
	}

	/**
	 * Image processing should be done here.  process and render will not be called at the same time, but won't
	 * be called from the same threads.
	 */
	protected abstract void process( ImageUInt8 gray );

	/**
	 * Visualize results here.
	 */
	protected abstract void render(  Canvas canvas , double imageToOutput );

	protected void declareImages( int width , int height ) {
		gray = new ImageUInt8(width,height);
		gray2 = new ImageUInt8(width,height);
	}
}
