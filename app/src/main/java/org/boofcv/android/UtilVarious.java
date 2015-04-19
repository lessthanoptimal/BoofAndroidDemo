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
	public static int closest( List<Camera.Size> sizes , int width , int height ) {
		int best = -1;
		int bestScore = Integer.MAX_VALUE;

		for( int i = 0; i < sizes.size(); i++ ) {
			Camera.Size s = sizes.get(i);

			int dx = s.width-width;
			int dy = s.height-height;

			int score = dx*dx + dy*dy;
			if( score < bestScore ) {
				best = i;
				bestScore = score;
			}
		}

		return best;
	}

	public static Camera.Size closestS( List<Camera.Size> sizes , int width , int height ) {
		return sizes.get( closest(sizes,width,height));
	}
}
