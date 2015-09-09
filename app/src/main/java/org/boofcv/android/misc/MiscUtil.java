package org.boofcv.android.misc;

import android.hardware.Camera;

import org.boofcv.android.CameraSpecs;
import org.boofcv.android.DemoMain;

import boofcv.struct.calib.IntrinsicParameters;

/**
 * @author Peter Abeles
 */
public class MiscUtil {
	/**
	 * Either loads the current intrinsic parameters or makes one up from camera information
	 * if it doesn't exist
	 */
	public static IntrinsicParameters checkThenInventIntrinsic() {

		IntrinsicParameters intrinsic;

		// make sure the camera is calibrated first
		if( DemoMain.preference.intrinsic == null ) {
			CameraSpecs specs = DemoMain.specs.get(DemoMain.preference.cameraId);

			Camera.Size size = specs.sizePreview.get( DemoMain.preference.preview);

			intrinsic = new IntrinsicParameters();

			intrinsic.width = size.width; intrinsic.height = size.height;
			intrinsic.cx = intrinsic.width/2;
			intrinsic.cy = intrinsic.height/2;
			intrinsic.fx = intrinsic.cx / Math.tan(specs.horizontalViewAngle/2.0f);
			intrinsic.fy = intrinsic.cy / Math.tan(specs.verticalViewAngle/2.0f);
		} else {
			intrinsic = DemoMain.preference.intrinsic;
		}

		return intrinsic;
	}
}
