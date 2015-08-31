package org.boofcv.android;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;

import boofcv.abst.fiducial.FiducialDetector;
import boofcv.abst.fiducial.SquareImage_to_FiducialDetector;
import boofcv.android.ConvertBitmap;
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
	ImageUInt8 target;

	public FiducialSquareImageActivity() {
		super(FiducialSquareImageHelpActivity.class);
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		BitmapFactory.Options options = new BitmapFactory.Options();
		options.inScaled = false;
		Bitmap input = BitmapFactory.decodeResource(getResources(), R.drawable.dog, options);

		target = new ImageUInt8(input.getWidth(),input.getHeight());

		ConvertBitmap.bitmapToGray(input, target, null);
	}

	@Override
	protected FiducialDetector<ImageUInt8> createDetector() {

		SquareImage_to_FiducialDetector<ImageUInt8> detector;
		synchronized ( lock ) {
			if (robust) {
				detector = FactoryFiducial.squareImageRobust(new ConfigFiducialImage(), 4, ImageUInt8.class);
			} else {
				detector = FactoryFiducial.squareImageFast(new ConfigFiducialImage(), binaryThreshold, ImageUInt8.class);
			}
		}
		detector.addPatternImage(target,125,0.1);

		return detector;
	}
}
