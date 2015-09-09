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
		synchronized ( lock ) {
			if (robust) {
				detector = FactoryFiducial.squareBinaryRobust(new ConfigFiducialBinary(0.1), 4, ImageUInt8.class);
			} else {
				detector = FactoryFiducial.squareBinaryFast(new ConfigFiducialBinary(0.1), binaryThreshold, ImageUInt8.class);
			}
		}

		return detector;
	}

}
