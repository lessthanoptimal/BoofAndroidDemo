package org.boofcv.android.calib;

import boofcv.alg.geo.calibration.CalibrationObservation;
import boofcv.struct.image.GrayF32;

/**
 * Contains calibration information provide by an image
 *
 * @author Peter Abeles
 */
public class CalibrationImageInfo {
	GrayF32 image;
	CalibrationObservation calibPoints = new CalibrationObservation();

	public CalibrationImageInfo(GrayF32 image, CalibrationObservation observations) {
		this.image = image.clone();
		this.calibPoints.setTo(observations);
	}
}
