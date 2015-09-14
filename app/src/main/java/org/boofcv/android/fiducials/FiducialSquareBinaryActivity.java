package org.boofcv.android.fiducials;

import boofcv.abst.fiducial.FiducialDetector;
import boofcv.factory.fiducial.ConfigFiducialBinary;
import boofcv.factory.fiducial.FactoryFiducial;
import boofcv.struct.image.ImageUInt8;

/**
 * Detects and shows square binary fiducials
 *
 * @author Peter Abeles
 */
public class FiducialSquareBinaryActivity extends FiducialSquareActivity
{

	public FiducialSquareBinaryActivity() {
		super(FiducialSquareBinaryHelpActivity.class);
	}

	@Override
	protected FiducialDetector<ImageUInt8> createDetector() {

		FiducialDetector<ImageUInt8> detector;
		ConfigFiducialBinary config = new ConfigFiducialBinary(0.1);
		config.ambiguousThreshold = 0.75;

		synchronized ( lock ) {
			if (robust) {
				detector = FactoryFiducial.squareBinaryRobust(config, 6, ImageUInt8.class);
			} else {
				detector = FactoryFiducial.squareBinaryFast(config, binaryThreshold, ImageUInt8.class);
			}
		}

		return detector;
	}

}
