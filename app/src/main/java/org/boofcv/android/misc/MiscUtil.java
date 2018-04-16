package org.boofcv.android.misc;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.hardware.Camera;

import org.boofcv.android.CameraSpecs;
import org.boofcv.android.DemoMain;

import boofcv.struct.calib.CameraPinholeRadial;
import georegression.metric.UtilAngle;
import georegression.struct.point.Point2D_F64;
import georegression.struct.shapes.Polygon2D_F64;

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

	public static CameraPinholeRadial checkThenInventIntrinsic( int width , int height ,
																double hfov , double vfov) {

		CameraPinholeRadial intrinsic;

		// make sure the camera is calibrated first
		if( DemoMain.preference.intrinsic == null ) {
			intrinsic = new CameraPinholeRadial();

			intrinsic.width = width; intrinsic.height = height;
			intrinsic.cx = intrinsic.width/2;
			intrinsic.cy = intrinsic.height/2;
			intrinsic.fx = intrinsic.cx / Math.tan(hfov/2.0f);
			intrinsic.fy = intrinsic.cy / Math.tan(vfov/2.0f);
		} else {
			intrinsic = DemoMain.preference.intrinsic;
			if( intrinsic.width != width || intrinsic.height != height ) {
				throw new RuntimeException("Intrinsics doesn't match current resolution");
			}
		}

		return intrinsic;
	}

	public static void renderPolygon(Polygon2D_F64 s, Path path , Canvas canvas , Paint paint ) {
		path.reset();
		for (int j = 0; j < s.size(); j++) {
			Point2D_F64 p = s.get(j);
			if (j == 0)
				path.moveTo((float) p.x, (float) p.y);
			else
				path.lineTo((float) p.x, (float) p.y);
		}
		Point2D_F64 p = s.get(0);
		path.lineTo((float) p.x, (float) p.y);
		path.close();
		canvas.drawPath(path, paint);
	}
}
