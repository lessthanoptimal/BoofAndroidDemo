package org.boofcv.android.misc;

import android.hardware.Camera;

import org.boofcv.android.CameraSpecs;
import org.boofcv.android.DemoMain;

import boofcv.struct.calib.CameraPinholeRadial;
import georegression.metric.UtilAngle;

/**
 * @author Peter Abeles
 */
public class MiscUtil {
	/**
	 * Either loads the current intrinsic parameters or makes one up from camera information
	 * if it doesn't exist
	 */
	public static CameraPinholeRadial checkThenInventIntrinsic() {

		CameraPinholeRadial intrinsic;

		// make sure the camera is calibrated first
		if( DemoMain.preference.intrinsic == null ) {
			CameraSpecs specs = DemoMain.specs.get(DemoMain.preference.cameraId);

			Camera.Size size = specs.sizePreview.get( DemoMain.preference.preview);

			intrinsic = new CameraPinholeRadial();

			double hfov = UtilAngle.degreeToRadian(specs.horizontalViewAngle);
			double vfov = UtilAngle.degreeToRadian(specs.verticalViewAngle);

			intrinsic.width = size.width; intrinsic.height = size.height;
			intrinsic.cx = intrinsic.width/2;
			intrinsic.cy = intrinsic.height/2;
			intrinsic.fx = intrinsic.cx / Math.tan(hfov/2.0f);
			intrinsic.fy = intrinsic.cy / Math.tan(vfov/2.0f);
		} else {
			intrinsic = DemoMain.preference.intrinsic;
		}

		return intrinsic;
	}
}
