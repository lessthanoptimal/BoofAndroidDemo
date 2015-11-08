package org.boofcv.android;

import boofcv.alg.geo.calibration.CalibrationObservation;
import boofcv.struct.image.ImageFloat32;

/**
 * Contains calibration information provide by an image
 *
 * @author Peter Abeles
 */
public class CalibrationImageInfo {
	ImageFloat32 image;
	CalibrationObservation calibPoints = new CalibrationObservation();

	public CalibrationImageInfo(ImageFloat32 image, CalibrationObservation observations) {
		this.image = image.clone();
		this.calibPoints.setTo(observations);
	}
}
