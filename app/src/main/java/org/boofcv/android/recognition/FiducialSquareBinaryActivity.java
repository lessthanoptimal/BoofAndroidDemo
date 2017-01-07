package org.boofcv.android.recognition;

import boofcv.abst.fiducial.FiducialDetector;
import boofcv.factory.fiducial.ConfigFiducialBinary;
import boofcv.factory.fiducial.FactoryFiducial;
import boofcv.factory.filter.binary.ConfigThreshold;
import boofcv.factory.filter.binary.ThresholdType;
import boofcv.struct.image.GrayU8;

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
	protected FiducialDetector<GrayU8> createDetector() {

		FiducialDetector<GrayU8> detector;
		ConfigFiducialBinary config = new ConfigFiducialBinary(0.1);

		synchronized ( lock ) {
			ConfigThreshold configThreshold;
			if (robust) {
				configThreshold = ConfigThreshold.local(ThresholdType.LOCAL_SQUARE, 6);
			} else {
				configThreshold = ConfigThreshold.fixed(binaryThreshold);
			}
			detector = FactoryFiducial.squareBinary(config, configThreshold, GrayU8.class);
		}

		return detector;
	}

}
