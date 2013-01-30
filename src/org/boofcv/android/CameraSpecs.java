package org.boofcv.android;

import android.hardware.Camera;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Peter Abeles
 */
public class CameraSpecs {
	Camera.CameraInfo info = new Camera.CameraInfo();
	List<Camera.Size> sizePreview = new ArrayList<Camera.Size>();
	List<Camera.Size> sizePicture = new ArrayList<Camera.Size>();
}
