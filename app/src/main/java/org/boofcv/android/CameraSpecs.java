package org.boofcv.android;

import android.hardware.Camera;

import java.util.ArrayList;
import java.util.List;

/**
 * To speed things up, just collect information on the cameras once and store them in this data structure.
 *
 * @author Peter Abeles
 */
public class CameraSpecs {
	public Camera.CameraInfo info = new Camera.CameraInfo();
	public List<Camera.Size> sizePreview = new ArrayList<Camera.Size>();
	public List<Camera.Size> sizePicture = new ArrayList<Camera.Size>();
	public float horizontalViewAngle;
	public float verticalViewAngle;
}
