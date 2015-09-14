package org.boofcv.android.fiducials;

import android.graphics.BitmapFactory;
import android.os.Bundle;

import java.util.List;

import boofcv.abst.fiducial.FiducialDetector;
import boofcv.abst.fiducial.SquareImage_to_FiducialDetector;
import boofcv.alg.filter.binary.BinaryImageOps;
import boofcv.alg.misc.PixelMath;
import boofcv.factory.fiducial.ConfigFiducialImage;
import boofcv.factory.fiducial.FactoryFiducial;
import boofcv.struct.image.ImageUInt8;

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

		BitmapFactory.Options options = new BitmapFactory.Options();
		options.inScaled = false;
	}

	@Override
	protected void onResume() {
		super.onResume();

		manager = new FiducialManager(this);
		manager.loadList();
		list = manager.copyList();
	}

	@Override
	protected FiducialDetector<ImageUInt8> createDetector() {

		SquareImage_to_FiducialDetector<ImageUInt8> detector;
		ConfigFiducialImage config = new ConfigFiducialImage();
		config.maxErrorFraction = 0.20;

		synchronized ( lock ) {
			if (robust) {
				detector = FactoryFiducial.squareImageRobust(config, 6, ImageUInt8.class);
			} else {
				detector = FactoryFiducial.squareImageFast(config, binaryThreshold, ImageUInt8.class);
			}
		}

		for (int i = 0; i < list.size(); i++) {
			ImageUInt8 binary = manager.loadBinaryImage(list.get(i).id);
			BinaryImageOps.invert(binary,binary);
			PixelMath.multiply(binary,255,0,255,binary);
			detector.addPatternImage(binary,125,list.get(i).sideLength);
		}

		return detector;
	}
}
