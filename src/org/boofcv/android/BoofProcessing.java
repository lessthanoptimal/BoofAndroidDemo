package org.boofcv.android;

import android.graphics.Canvas;
import android.hardware.Camera;
import android.view.View;

/**
 * @author Peter Abeles
 */
public interface BoofProcessing{

	public void init( View view );

	/**
	 * Called inside the GUI thread
	 *
	 * @param canvas
	 */
	public void onDraw(Canvas canvas);


	/**
	 * Called inside the preview thread.  Should be as fast as possible.  All expensive computations should be pushed
	 * into their own thread.
	 *
	 * @param bytes
	 * @param camera
	 */
	public void convertPreview( byte[] bytes, Camera camera );

	/**
	 * Blocks until all processing has stopped
	 */
	public void stopProcessing();

}
