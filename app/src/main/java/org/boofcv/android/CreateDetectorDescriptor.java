package org.boofcv.android;

import boofcv.abst.feature.describe.ConfigBrief;
import boofcv.abst.feature.describe.DescribeRegionPoint;
import boofcv.abst.feature.detdesc.ConfigCompleteSift;
import boofcv.abst.feature.detdesc.DetectDescribePoint;
import boofcv.abst.feature.detect.interest.ConfigFast;
import boofcv.abst.feature.detect.interest.ConfigFastHessian;
import boofcv.abst.feature.detect.interest.ConfigGeneralDetector;
import boofcv.abst.feature.detect.interest.ConfigSiftDetector;
import boofcv.abst.feature.detect.interest.InterestPointDetector;
import boofcv.abst.feature.orientation.OrientationImage;
import boofcv.abst.feature.orientation.OrientationIntegral;
import boofcv.alg.feature.detect.interest.GeneralFeatureDetector;
import boofcv.alg.filter.derivative.GImageDerivativeOps;
import boofcv.alg.transform.ii.GIntegralImageOps;
import boofcv.factory.feature.describe.FactoryDescribeRegionPoint;
import boofcv.factory.feature.detdesc.FactoryDetectDescribe;
import boofcv.factory.feature.detect.interest.FactoryDetectPoint;
import boofcv.factory.feature.detect.interest.FactoryInterestPoint;
import boofcv.factory.feature.orientation.FactoryOrientation;
import boofcv.factory.feature.orientation.FactoryOrientationAlgs;

/**
 * Class which is intended to make it easier to create instances of DetectDescribePoint.  It will automatically
 * check to see if there is a specialized version of some algorithm.  If not it will declare its components individually
 * then combine them.
 *
 * @author Peter Abeles
 */
public class CreateDetectorDescriptor {

	public static final int DETECT_FH = 0;
	public static final int DETECT_SIFT = 1;
	public static final int DETECT_SHITOMASI = 2;
	public static final int DETECT_HARRIS = 3;
	public static final int DETECT_FAST = 4;

	public static final int DESC_SURF = 0;
	public static final int DESC_SIFT = 1;
	public static final int DESC_BRIEF = 2;
	public static final int DESC_NCC = 3;

	public static DetectDescribePoint create( int detect , int describe , Class imageType ) {

		if( detect == DETECT_FH && describe == DESC_SURF ) {
			return FactoryDetectDescribe.surfFast(confDetectFH(), null, null, imageType);
		} else if( detect == DETECT_SIFT && describe == DESC_SIFT ) {
			return FactoryDetectDescribe.sift( confSift() );
		} else {
			boolean ss = isScaleSpace(detect);

			InterestPointDetector detector = createDetector(detect,imageType);
			DescribeRegionPoint descriptor = createDescriptor(describe,ss,imageType);
			OrientationImage ori = createOrientation(detect,imageType);

			return FactoryDetectDescribe.fuseTogether(detector,ori,descriptor);
		}
	}

	private static OrientationImage createOrientation(int detect, Class imageType) {
		// OK this is a bit lazy...
		if( isScaleSpace(detect)) {
			Class integralType = GIntegralImageOps.getIntegralType(imageType);
			OrientationIntegral orientationII = FactoryOrientationAlgs.sliding_ii(null, integralType);
			return FactoryOrientation.convertImage(orientationII, imageType);
		} else {
			return null;
		}
	}

	public static boolean isScaleSpace( int detector ) {
		return detector == DETECT_FH || detector == DETECT_SIFT;
	}

	public static InterestPointDetector createDetector( int detect , Class imageType ) {
		Class derivType = GImageDerivativeOps.getDerivativeType(imageType);
		GeneralFeatureDetector general;

		switch( detect ) {
			case DETECT_FH:
				return FactoryInterestPoint.fastHessian(confDetectFH());

			case DETECT_SIFT:
				return FactoryInterestPoint.sift(null,confDetectSift(),imageType);

			case DETECT_SHITOMASI:
				general = FactoryDetectPoint.createShiTomasi(confCorner(),false,derivType);
				break;

			case DETECT_HARRIS:
				general = FactoryDetectPoint.createHarris(confCorner(), false, derivType);
				break;

			case DETECT_FAST:
				general = FactoryDetectPoint.createFast(new ConfigFast(20,9),new ConfigGeneralDetector(150,3,20), imageType);
				break;

			default:
				throw new RuntimeException("Unknown detector");

		}

		return FactoryInterestPoint.wrapPoint(general,1.0,imageType,derivType);
	}

	public static DescribeRegionPoint createDescriptor( int describe , boolean scaleSpace , Class imageType ) {
		switch( describe ) {
			case DESC_SURF:
				return FactoryDescribeRegionPoint.surfFast(null, imageType);

			case DESC_SIFT:
				return FactoryDescribeRegionPoint.sift(null,null,imageType);

			case DESC_BRIEF:
				return FactoryDescribeRegionPoint.brief(new ConfigBrief(!scaleSpace),imageType);

			case DESC_NCC:
				return FactoryDescribeRegionPoint.pixelNCC(9,9,imageType);

			default:
				throw new RuntimeException("Unknown descriptor");
		}


	}

	private static ConfigGeneralDetector confCorner() {
		ConfigGeneralDetector conf = new ConfigGeneralDetector();
		conf.radius = 3;
		conf.threshold = 20;
		conf.maxFeatures = 150;
		return conf;
	}

	private static  ConfigFastHessian confDetectFH() {
		ConfigFastHessian conf = new ConfigFastHessian();
		conf.initialSampleSize = 2;
		conf.extractRadius = 2;
		conf.maxFeaturesPerScale = 120;
		return conf;
	}

	private static ConfigCompleteSift confSift() {
		ConfigCompleteSift config = new ConfigCompleteSift();
		config.detector = confDetectSift();
		return config;
	}

	private static  ConfigSiftDetector confDetectSift() {
		ConfigSiftDetector conf = new ConfigSiftDetector();
		conf.extract.radius = 3;
		conf.extract.threshold = 2;
		conf.maxFeaturesPerScale = 120;
		return conf;
	}

}
