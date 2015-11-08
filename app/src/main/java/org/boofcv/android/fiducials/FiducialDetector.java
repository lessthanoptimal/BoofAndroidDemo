package org.boofcv.android.fiducials;

import org.ddogleg.struct.FastQueue;

import java.util.ArrayList;
import java.util.List;

import boofcv.abst.filter.binary.InputToBinary;
import boofcv.alg.fiducial.square.BaseDetectFiducialSquare;
import boofcv.alg.fiducial.square.FoundFiducial;
import boofcv.factory.filter.binary.FactoryThresholdBinary;
import boofcv.factory.shape.ConfigPolygonDetector;
import boofcv.factory.shape.FactoryShapeDetector;
import boofcv.struct.image.ImageFloat32;
import boofcv.struct.image.ImageUInt8;
import georegression.struct.shapes.Quadrilateral_F64;

/**
 * Detects fiducials inside of an image.  Used to return the pattern inside.
 *
 * @author Peter Abeles
 */
public class FiducialDetector extends BaseDetectFiducialSquare<ImageUInt8> {

	private static final String TAG = "FiducialDetector";

	// Width of black border (units = pixels)
	private final static int w=32;
	private final static int squareLength=w*4; // this must be a multiple of 16

	private InputToBinary<ImageFloat32> threshold = FactoryThresholdBinary.globalOtsu(0,255,true,ImageFloat32.class);
	private ImageFloat32 grayNoBorder = new ImageFloat32();

	// All the images inside which it found
	private FastQueue<ImageUInt8> foundBinary;

	public FiducialDetector() {
		super(FactoryThresholdBinary.globalOtsu(0, 255, true, ImageUInt8.class),FactoryShapeDetector.polygon(
				new ConfigPolygonDetector(false, 4,4), ImageUInt8.class),
				0.25, squareLength + squareLength, ImageUInt8.class);

		foundBinary = new FastQueue<ImageUInt8>(ImageUInt8.class,true) {
			@Override
			protected ImageUInt8 createInstance() {
				return new ImageUInt8(squareLength,squareLength);
			}
		};
	}

	@Override
	public void process(ImageUInt8 gray) {
		foundBinary.reset();
		super.process(gray);
	}

	/**
	 * Returns a list of all detected fiducials
	 */
	public List<Detected> getDetected() {
		FastQueue<FoundFiducial> found = getFound();
		if( found.size() <= 0 )
			return new ArrayList<>();

		// select the square with the largest area
		List<Detected> detections = new ArrayList<>();
		for (int i = 0; i < found.size(); i++) {
			FoundFiducial f = found.get(i);

			Detected detected = new Detected();
			detected.binary = foundBinary.get(i);
			detected.location = f.location;

			detections.add( detected );
		}

		return detections;
	}

	@Override
	protected boolean processSquare(ImageFloat32 gray, Result result) {
		ImageUInt8 binary = foundBinary.grow();
		int off = (gray.width-binary.width)/2;
		gray.subimage(off, off, gray.width - off, gray.width - off, grayNoBorder);

		threshold.process(grayNoBorder, binary);
		return true;
	}

	public static class Detected {
		public ImageUInt8 binary;
		public Quadrilateral_F64 location;
	}
}
