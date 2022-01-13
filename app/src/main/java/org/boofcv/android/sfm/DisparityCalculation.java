package org.boofcv.android.sfm;

import android.util.Log;

import org.ddogleg.fitting.modelset.ModelMatcher;
import org.ddogleg.struct.FastAccess;
import org.ddogleg.struct.DogArray;
import org.ejml.data.DMatrixRMaj;
import org.ejml.data.FMatrixRMaj;
import org.ejml.ops.ConvertMatrixData;

import java.util.ArrayList;
import java.util.List;

import boofcv.abst.disparity.StereoDisparity;
import boofcv.abst.feature.associate.AssociateDescription;
import boofcv.abst.feature.detdesc.DetectDescribePoint;
import boofcv.alg.descriptor.UtilFeature;
import boofcv.alg.distort.ImageDistort;
import boofcv.alg.geo.PerspectiveOps;
import boofcv.alg.geo.RectifyDistortImageOps;
import boofcv.alg.geo.RectifyImageOps;
import boofcv.alg.geo.rectify.RectifyCalibrated;
import boofcv.alg.geo.robust.ModelMatcherMultiview;
import boofcv.core.image.GConvertImage;
import boofcv.factory.distort.LensDistortionFactory;
import boofcv.factory.geo.ConfigEssential;
import boofcv.factory.geo.ConfigRansac;
import boofcv.factory.geo.EnumEssential;
import boofcv.factory.geo.FactoryMultiViewRobust;
import boofcv.struct.border.BorderType;
import boofcv.struct.calib.CameraPinholeBrown;
import boofcv.struct.distort.Point2Transform2_F64;
import boofcv.struct.feature.AssociatedIndex;
import boofcv.struct.feature.TupleDesc;
import boofcv.struct.geo.AssociatedPair;
import boofcv.struct.image.GrayF32;
import boofcv.struct.image.GrayU8;
import boofcv.struct.image.ImageDimension;
import boofcv.struct.image.ImageGray;
import boofcv.struct.image.ImageType;
import georegression.struct.point.Point2D_F64;
import georegression.struct.se.Se3_F64;

/**
 * Computes the disparity image from two views.  Features are first associated between the two images, image motion
 * found, rectification and dense stereo calculation.
 *
 * @author Peter Abeles
 */
public class DisparityCalculation<Desc extends TupleDesc<Desc>> {

	DetectDescribePoint<GrayU8,Desc> detDesc;
	AssociateDescription<Desc> associate;
	CameraPinholeBrown intrinsic;

	StereoDisparity<?, GrayF32> disparityAlg;

	DogArray<Desc> listSrc;
	DogArray<Desc> listDst;
	DogArray<Point2D_F64> locationSrc = new DogArray<>(Point2D_F64::new);
	DogArray<Point2D_F64> locationDst = new DogArray<>(Point2D_F64::new);

	List<AssociatedPair> inliersPixel;

	RectifyCalibrated rectifyAlg = RectifyImageOps.createCalibrated();

	// mask that indicates which pixels are inside the image and which ones are outside
	GrayU8 rectMask = new GrayU8(1,1);

	GrayU8 distortedLeft;
	GrayU8 distortedRight;
	GrayU8 rectifiedLeft;
	GrayU8 rectifiedRight;

	// the new rectification matricies after they have been adjusted
	DMatrixRMaj rectifiedK = new DMatrixRMaj(3,3);
	DMatrixRMaj rectifiedR = new DMatrixRMaj(3,3);

	// estimated camera motion
	Se3_F64 leftToRight;


	// has the disparity been computed
	boolean computedDisparity = false;

	public DisparityCalculation(DetectDescribePoint<GrayU8, Desc> detDesc,
								AssociateDescription<Desc> associate ,
								CameraPinholeBrown intrinsic ) {
		this.detDesc = detDesc;
		this.associate = associate;
		this.intrinsic = intrinsic;

		listSrc = UtilFeature.createArray(detDesc, 10);
		listDst = UtilFeature.createArray(detDesc, 10);
	}

	public void setDisparityAlg(StereoDisparity<?, GrayF32> disparityAlg) {
		this.disparityAlg = disparityAlg;
	}

	public void init( int width , int height ) {
		distortedLeft = new GrayU8(width,height);
		distortedRight = new GrayU8(width,height);
		rectifiedLeft = new GrayU8(width,height);
		rectifiedRight = new GrayU8(width,height);
	}

	public void setSource( GrayU8 image ) {
		distortedLeft.setTo(image);
		detDesc.detect(image);
		describeImage(listSrc, locationSrc);
		associate.setSource(listSrc);
	}

	public void setDestination( GrayU8 image ) {
		distortedRight.setTo(image);
		detDesc.detect(image);
		describeImage(listDst, locationDst);
		associate.setDestination(listDst);

	}

	private void describeImage(DogArray<Desc> listDesc, DogArray<Point2D_F64> listLoc) {
		listDesc.reset();
		listLoc.reset();
		int N = detDesc.getNumberOfFeatures();
		for( int i = 0; i < N; i++ ) {
			listLoc.grow().setTo(detDesc.getLocation(i));
			listDesc.grow().setTo(detDesc.getDescription(i));
		}
	}

	/**
	 * Associates image features, computes camera motion, and rectifies images.
	 *
	 * @return true it was able to rectify the input images or false if not
	 */
	public boolean rectifyImage() {
		computedDisparity = false;

		associate.associate();
		List<AssociatedPair> pairs = convertToNormalizedCoordinates();

		leftToRight = estimateCameraMotion(pairs);

		if( leftToRight == null ) {
			Log.e("disparity","estimate motion failed");
			Log.e("disparity","  left.size = "+locationSrc.size());
			Log.e("disparity","  right.size = "+locationDst.size());
			Log.e("disparity", "  associated size = " + associate.getMatches().size());
			Log.e("disparity","  pairs.size = "+pairs.size());
			return false;
		}

		rectifyImages(leftToRight);

		return true;
	}

	/**
	 * Computes the disparity between the two rectified images
	 */
	public GrayF32 computeDisparity() {
		if( !rectifiedLeft.getImageType().isSameType(disparityAlg.getInputType())) {
			// Need to copy the rectified image if it's not U8
			ImageGray tmpLeft = (ImageGray)disparityAlg.getInputType().createImage(rectifiedLeft.width,rectifiedRight.height);
			ImageGray tmpRight = (ImageGray)disparityAlg.getInputType().createImage(rectifiedLeft.width,rectifiedRight.height);

			GConvertImage.convert(rectifiedLeft,tmpLeft);
			GConvertImage.convert(rectifiedRight,tmpRight);

			((StereoDisparity)disparityAlg).process(tmpLeft, tmpRight);
		} else {
			// sorry for the type-casting. hack to get around generics short coming
			((StereoDisparity)disparityAlg).process(rectifiedLeft, rectifiedRight);
		}

		// Remove pixels in the rectified image which are not mapped to a pixel in the source image
        GrayF32 disparity = disparityAlg.getDisparity();
		RectifyImageOps.applyMask(disparity,rectMask,0);
		computedDisparity = true;
		return disparity;
	}

	/**
	 * Convert a set of associated point features from pixel coordinates into normalized image coordinates.
	 */
	public List<AssociatedPair> convertToNormalizedCoordinates() {

		Point2Transform2_F64 tran = LensDistortionFactory.narrow(intrinsic).undistort_F64(true,false);

		List<AssociatedPair> calibratedFeatures = new ArrayList<>();

		FastAccess<AssociatedIndex> matches = associate.getMatches();
		for( AssociatedIndex a : matches.toList() ) {
			Point2D_F64 p1 = locationSrc.get( a.src );
			Point2D_F64 p2 = locationDst.get( a.dst );

			AssociatedPair c = new AssociatedPair();

			tran.compute(p1.x, p1.y, c.p1);
			tran.compute(p2.x, p2.y, c.p2);

			calibratedFeatures.add(c);
		}

		return calibratedFeatures;
	}

	/**
	 * Estimates image motion up to a scale factor
	 * @param matchedNorm List of associated features in normalized image coordinates
	 */
	public volatile int numInside = 0;
	public Se3_F64 estimateCameraMotion( List<AssociatedPair> matchedNorm )
	{
		numInside++;
		ConfigEssential configEssential = new ConfigEssential();
		configEssential.which = EnumEssential.NISTER_5;
		configEssential.numResolve = 5;

		ConfigRansac configRansac = new ConfigRansac();
		configRansac.iterations = 400;
		configRansac.inlierThreshold = 0.15;
		ModelMatcherMultiview<Se3_F64, AssociatedPair> epipolarMotion =
				FactoryMultiViewRobust.baselineRansac(configEssential,configRansac);
		epipolarMotion.setIntrinsic(0,intrinsic);
		epipolarMotion.setIntrinsic(1,intrinsic);

		if (!epipolarMotion.process(matchedNorm)) {
			numInside--;
			return null;
		}

		createInliersList(epipolarMotion);

		numInside--;
		return epipolarMotion.getModelParameters();
	}

	/**
	 * Save a list of inliers in pixel coordinates
	 */
	private void createInliersList( ModelMatcher<Se3_F64, AssociatedPair> epipolarMotion ) {
		inliersPixel = new ArrayList<>();

		FastAccess<AssociatedIndex> matches = associate.getMatches();

		int N = epipolarMotion.getMatchSet().size();
		for( int i = 0; i < N; i++ ) {

			AssociatedIndex a = matches.get( epipolarMotion.getInputIndex(i));

			Point2D_F64 p1 = locationSrc.get( a.src );
			Point2D_F64 p2 = locationDst.get( a.dst );

			inliersPixel.add( new AssociatedPair(p1,p2));
		}
	}

	/**
	 * Remove lens distortion and rectify stereo images
	 *
	 * @param leftToRight    Camera motion from left to right
	 */
	public void rectifyImages(Se3_F64 leftToRight)
	{
		// original camera calibration matrices
		DMatrixRMaj K = PerspectiveOps.pinholeToMatrix(intrinsic, (DMatrixRMaj)null);

		rectifyAlg.process(K, new Se3_F64(), K, leftToRight);

		// rectification matrix for each image
		DMatrixRMaj rect1 = rectifyAlg.getUndistToRectPixels1();
		DMatrixRMaj rect2 = rectifyAlg.getUndistToRectPixels2();

		// save calibration matrices
		rectifiedK.setTo(rectifyAlg.getCalibrationMatrix());
		rectifiedR.setTo(rectifyAlg.getRectifiedRotation());

		// Adjust the rectification to make the view area more useful
		ImageDimension rectShape = new ImageDimension();
		RectifyImageOps.fullViewLeft(intrinsic, rect1, rect2, rectifiedK, rectShape);

		rectMask.reshape(rectShape.width,rectShape.height);
		rectifiedLeft.reshape(rectShape.width,rectShape.height);
		rectifiedRight.reshape(rectShape.width,rectShape.height);

		// undistorted and rectify images
		FMatrixRMaj rect1_f = new FMatrixRMaj(3,3);
		FMatrixRMaj rect2_f = new FMatrixRMaj(3,3);

		ConvertMatrixData.convert(rect1,rect1_f);
		ConvertMatrixData.convert(rect2,rect2_f);

		ImageDistort<GrayU8,GrayU8> distortLeft =
				RectifyDistortImageOps.rectifyImage(intrinsic, rect1_f, BorderType.EXTENDED, ImageType.single(GrayU8.class));
		ImageDistort<GrayU8,GrayU8> distortRight =
				RectifyDistortImageOps.rectifyImage(intrinsic, rect2_f, BorderType.EXTENDED, ImageType.single(GrayU8.class));

		distortLeft.apply(distortedLeft, rectifiedLeft, rectMask);
		distortRight.apply(distortedRight, rectifiedRight);
	}

	public List<AssociatedPair> getInliersPixel() {
		return inliersPixel;
	}

	public GrayF32 getDisparity() {
		return disparityAlg.getDisparity();
	}

	public GrayU8 getDisparityMask() {
		return rectMask;
	}

	public StereoDisparity<?, GrayF32> getDisparityAlg() {
		return disparityAlg;
	}

	public boolean isDisparityAvailable() {
		return computedDisparity;
	}

	public RectifyCalibrated getRectifyAlg() {
		return rectifyAlg;
	}
}
