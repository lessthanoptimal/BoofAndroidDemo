package org.boofcv.android;

import boofcv.struct.image.ImageFloat32;
import georegression.struct.point.Point2D_F64;

import java.util.ArrayList;
import java.util.List;

/**
 * Contains calibration information provide by an image
 *
 * @author Peter Abeles
 */
public class CalibrationImageInfo {
	ImageFloat32 image;
	List<Point2D_F64> calibPoints;

	public CalibrationImageInfo(ImageFloat32 image, List<Point2D_F64> calibPoints) {
		this.image = image.clone();
		this.calibPoints = new ArrayList<Point2D_F64>();
		for( Point2D_F64 p : calibPoints ) {
			this.calibPoints.add(p.copy());
		}
	}
}
