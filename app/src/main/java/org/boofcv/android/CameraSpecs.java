package org.boofcv.android;

import android.util.Size;

import java.util.ArrayList;
import java.util.List;

/**
 * To speed things up, just collect information on the cameras once and store them in this data structure.
 *
 * @author Peter Abeles
 */
public class CameraSpecs {
	public String deviceId = "";
	public boolean facingBack = false;
	public List<Size> sizes = new ArrayList<>();
}
