package org.boofcv.android.recognition;

import android.os.Bundle;

import java.util.List;

import boofcv.abst.fiducial.FiducialDetector;
import boofcv.abst.fiducial.SquareImage_to_FiducialDetector;
import boofcv.alg.filter.binary.BinaryImageOps;
import boofcv.alg.misc.PixelMath;
import boofcv.factory.fiducial.ConfigFiducialImage;
import boofcv.factory.fiducial.FactoryFiducial;
import boofcv.factory.filter.binary.ConfigThreshold;
import boofcv.factory.filter.binary.ThresholdType;
import boofcv.struct.image.GrayU8;

/**
 * Detects and shows square binary fiducials
 *
 * @author Peter Abeles
 */
public class FiducialSquareImageActivity extends FiducialSquareActivity
{
	FiducialManager manager;
	List<FiducialManager.Info> list;

	public FiducialSquareImageActivity() {
		super(FiducialSquareImageHelpActivity.class);
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
	}

	@Override
	protected void onResume() {
		super.onResume();

		manager = new FiducialManager(this);
		manager.loadList();
		list = manager.copyList();

		if( list.size() == 0 ) {
			drawText = "ADD FIDUCIALS!";
		}
	}

	@Override
	protected FiducialDetector<GrayU8> createDetector() {

		SquareImage_to_FiducialDetector<GrayU8> detector;
		ConfigFiducialImage config = new ConfigFiducialImage();

		synchronized ( lock ) {
			ConfigThreshold configThreshold;
			if (robust) {
				configThreshold = ConfigThreshold.local(ThresholdType.LOCAL_SQUARE, 6);
			} else {
				configThreshold = ConfigThreshold.fixed(binaryThreshold);
			}
			detector = FactoryFiducial.squareImage(config, configThreshold, GrayU8.class);
		}

		for (int i = 0; i < list.size(); i++) {
			GrayU8 binary = manager.loadBinaryImage(list.get(i).id);
			BinaryImageOps.invert(binary,binary);
			PixelMath.multiply(binary,255,0,255,binary);
			detector.addPatternImage(binary,125,list.get(i).sideLength);
		}

		return detector;
	}
}
