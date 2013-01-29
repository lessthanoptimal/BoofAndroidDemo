package org.boofcv.android;

import android.hardware.Camera;

import java.util.List;

/**
 * @author Peter Abeles
 */
public class UtilVarious {
	/** A safe way to get an instance of the Camera object. */
	public static Camera getCameraInstance(){
		Camera c = null;
		try {
			c = Camera.open(); // attempt to get a Camera instance
		}
		catch (Exception e){
			// Camera is not available (in use or does not exist)
		}
		return c; // returns null if camera is unavailable
	}

	/**
	 * From the list of image sizes, select the one which is closest to the specified size.
	 */
	public static Camera.Size closest( List<Camera.Size> sizes , int width , int height ) {
		Camera.Size best = null;
		int bestScore = Integer.MAX_VALUE;

		for( Camera.Size s : sizes ) {
			int dx = s.width-width;
			int dy = s.height-height;

			int score = dx*dx + dy*dy;
			if( score < bestScore ) {
				best = s;
				bestScore = score;
			}
		}

		return best;
	}
}
