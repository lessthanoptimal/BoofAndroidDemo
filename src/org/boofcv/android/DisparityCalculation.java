package org.boofcv.android;

import android.util.Log;
import boofcv.abst.feature.associate.AssociateDescription;
import boofcv.abst.feature.detdesc.DetectDescribePoint;
import boofcv.abst.feature.disparity.StereoDisparity;
import boofcv.abst.geo.Estimate1ofEpipolar;
import boofcv.abst.geo.TriangulateTwoViewsCalibrated;
import boofcv.alg.distort.ImageDistort;
import boofcv.alg.distort.LensDistortionOps;
import boofcv.alg.feature.UtilFeature;
import boofcv.alg.filter.derivative.LaplacianEdge;
import boofcv.alg.geo.PerspectiveOps;
import boofcv.alg.geo.RectifyImageOps;
import boofcv.alg.geo.rectify.RectifyCalibrated;
import boofcv.alg.misc.ImageMiscOps;
import boofcv.alg.sfm.robust.DistanceSe3SymmetricSq;
import boofcv.alg.sfm.robust.Se3FromEssentialGenerator;
import boofcv.factory.geo.EnumEpipolar;
import boofcv.factory.geo.FactoryMultiView;
import boofcv.factory.geo.FactoryTriangulate;
import boofcv.struct.FastQueue;
import boofcv.struct.calib.IntrinsicParameters;
import boofcv.struct.distort.PointTransform_F64;
import boofcv.struct.feature.AssociatedIndex;
import boofcv.struct.feature.TupleDesc;
import boofcv.struct.geo.AssociatedPair;
import boofcv.struct.image.ImageFloat32;
import georegression.struct.point.Point2D_F64;
import georegression.struct.se.Se3_F64;
import org.ddogleg.fitting.modelset.DistanceFromModel;
import org.ddogleg.fitting.modelset.ModelGenerator;
import org.ddogleg.fitting.modelset.ModelMatcher;
import org.ddogleg.fitting.modelset.ransac.Ransac;
import org.ejml.data.DenseMatrix64F;

import java.util.ArrayList;
import java.util.List;

/**
 * Computes the disparity image from two views.  Features are first associated between the two images, image motion
 * found, rectification and dense stereo calculation.
 *
 * @author Peter Abeles
 */
public class DisparityCalculation<Desc extends TupleDesc> {

	DetectDescribePoint<ImageFloat32,Desc> detDesc;
	AssociateDescription<Desc> associate;
	IntrinsicParameters intrinsic;

	StereoDisparity<ImageFloat32, ImageFloat32> disparityAlg;

	FastQueue<Desc> listSrc;
	FastQueue<Desc> listDst;
	FastQueue<Point2D_F64> locationSrc = new FastQueue<Point2D_F64>(Point2D_F64.class,true);
	FastQueue<Point2D_F64> locationDst = new FastQueue<Point2D_F64>(Point2D_F64.class,true);

	List<AssociatedPair> inliersPixel;

	boolean directionLeftToRight;

	ImageFloat32 distortedLeft;
	ImageFloat32 distortedRight;
	ImageFloat32 rectifiedLeft;
	ImageFloat32 rectifiedRight;

	// Laplacian that has been applied to rectified images
	ImageFloat32 edgeLeft;
	ImageFloat32 edgeRight;

	// has the disparity been computed
	boolean computedDisparity = false;

	public DisparityCalculation(DetectDescribePoint<ImageFloat32, Desc> detDesc,
								AssociateDescription<Desc> associate ,
								IntrinsicParameters intrinsic ) {
		this.detDesc = detDesc;
		this.associate = associate;
		this.intrinsic = intrinsic;

		listSrc = UtilFeature.createQueue(detDesc, 10);
		listDst = UtilFeature.createQueue(detDesc,10);
	}

	public void setDisparityAlg(StereoDisparity<ImageFloat32, ImageFloat32> disparityAlg) {
		this.disparityAlg = disparityAlg;
	}

	public void init( int width , int height ) {
		distortedLeft = new ImageFloat32(width,height);
		distortedRight = new ImageFloat32(width,height);
		rectifiedLeft = new ImageFloat32(width,height);
		rectifiedRight = new ImageFloat32(width,height);
		edgeLeft = new ImageFloat32(width,height);
		edgeRight = new ImageFloat32(width,height);
	}

	public void setSource( ImageFloat32 image ) {
		distortedLeft.setTo(image);
		detDesc.detect(image);
		describeImage(listSrc, locationSrc);
		associate.setSource(listSrc);
	}

	public void setDestination( ImageFloat32 image ) {
		distortedRight.setTo(image);
		detDesc.detect(image);
		describeImage(listDst, locationDst);
		associate.setDestination(listDst);

	}

	private void describeImage(FastQueue<Desc> listDesc, FastQueue<Point2D_F64> listLoc) {
		listDesc.reset();
		listLoc.reset();
		int N = detDesc.getNumberOfFeatures();
		for( int i = 0; i < N; i++ ) {
			listLoc.grow().set(detDesc.getLocation(i));
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

		Se3_F64 leftToRight = estimateCameraMotion(pairs);

		if( leftToRight == null ) {
			Log.e("disparity","estimate motion failed");
			Log.e("disparity","  left.size = "+locationSrc.size());
			Log.e("disparity","  right.size = "+locationDst.size());
			Log.e("disparity", "  associated size = " + associate.getMatches().size());
			Log.e("disparity","  pairs.size = "+pairs.size());

			return false;
		} else if( leftToRight.getT().x > 0 ) {
			// the user took a picture from right to left instead of left to right
			// so now everything needs to be swapped
			leftToRight = leftToRight.invert(null);
			ImageFloat32 tmp = distortedLeft;
			distortedLeft = distortedRight;
			distortedRight = tmp;
			tmp = edgeLeft;
			edgeLeft = edgeRight;
			edgeRight = tmp;
			directionLeftToRight = false;
		} else {
			directionLeftToRight = true;
		}

		DenseMatrix64F rectifiedK = new DenseMatrix64F(3,3);
		rectifyImages(leftToRight,rectifiedK);

		return true;
	}

	/**
	 * Computes the disparity between the two rectified images
	 */
	public void computeDisparity() {
		if( disparityAlg == null )
			return;

		disparityAlg.process(edgeLeft, edgeRight);
		computedDisparity = true;
	}

	/**
	 * Convert a set of associated point features from pixel coordinates into normalized image coordinates.
	 */
	public List<AssociatedPair> convertToNormalizedCoordinates() {

		PointTransform_F64 tran = LensDistortionOps.transformRadialToNorm_F64(intrinsic);

		List<AssociatedPair> calibratedFeatures = new ArrayList<AssociatedPair>();

		FastQueue<AssociatedIndex> matches = associate.getMatches();
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
	public Se3_F64 estimateCameraMotion( List<AssociatedPair> matchedNorm )
	{
		Estimate1ofEpipolar essentialAlg = FactoryMultiView.computeFundamental_1(EnumEpipolar.ESSENTIAL_5_NISTER, 5);
		TriangulateTwoViewsCalibrated triangulate = FactoryTriangulate.twoGeometric();
		ModelGenerator<Se3_F64, AssociatedPair> generateEpipolarMotion =
				new Se3FromEssentialGenerator(essentialAlg, triangulate);

		DistanceFromModel<Se3_F64, AssociatedPair> distanceSe3 =
				new DistanceSe3SymmetricSq(triangulate,
						intrinsic.fx, intrinsic.fy, intrinsic.skew,
						intrinsic.fx, intrinsic.fy, intrinsic.skew);

		// 1/2 a pixel tolerance for RANSAC inliers
		double ransacTOL = 0.5 * 0.5 * 2.0;

		ModelMatcher<Se3_F64, AssociatedPair> epipolarMotion =
				new Ransac<Se3_F64, AssociatedPair>(2323, generateEpipolarMotion, distanceSe3,
						300, ransacTOL);

		if (!epipolarMotion.process(matchedNorm))
			return null;

		createInliersList(epipolarMotion);

		return epipolarMotion.getModel();
	}

	/**
	 * Save a list of inliers in pixel coordinates
	 */
	private void createInliersList( ModelMatcher<Se3_F64, AssociatedPair> epipolarMotion ) {
		inliersPixel = new ArrayList<AssociatedPair>();

		FastQueue<AssociatedIndex> matches = associate.getMatches();

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
	 * @param rectifiedK     Output camera calibration matrix for rectified camera
	 */
	public void rectifyImages(Se3_F64 leftToRight,
							  DenseMatrix64F rectifiedK) {
		RectifyCalibrated rectifyAlg = RectifyImageOps.createCalibrated();

		// original camera calibration matrices
		DenseMatrix64F K = PerspectiveOps.calibrationMatrix(intrinsic, null);

		rectifyAlg.process(K, new Se3_F64(), K, leftToRight);

		// rectification matrix for each image
		DenseMatrix64F rect1 = rectifyAlg.getRect1();
		DenseMatrix64F rect2 = rectifyAlg.getRect2();

		// New calibration matrix,
		rectifiedK.set(rectifyAlg.getCalibrationMatrix());

		// Adjust the rectification to make the view area more useful
//		RectifyImageOps.allInsideLeft(intrinsic, rect1, rect2, rectifiedK);

		// undistorted and rectify images
		ImageDistort<ImageFloat32> distortLeft =
				RectifyImageOps.rectifyImage(intrinsic, rect1, ImageFloat32.class);
		ImageDistort<ImageFloat32> distortRight =
				RectifyImageOps.rectifyImage(intrinsic, rect2, ImageFloat32.class);

		// Apply the Laplacian for some lighting invariance
		ImageMiscOps.fill(rectifiedLeft,0);
		distortLeft.apply(distortedLeft, rectifiedLeft);
		LaplacianEdge.process(rectifiedLeft,edgeLeft);

		ImageMiscOps.fill(rectifiedRight, 0);
		distortRight.apply(distortedRight, rectifiedRight);
		LaplacianEdge.process(rectifiedRight,edgeRight);
	}

	public List<AssociatedPair> getInliersPixel() {
		return inliersPixel;
	}

	public ImageFloat32 getDisparity() {
		return disparityAlg.getDisparity();
	}

	public StereoDisparity<ImageFloat32, ImageFloat32> getDisparityAlg() {
		return disparityAlg;
	}

	public boolean isDisparityAvailable() {
		return computedDisparity;
	}

	public boolean isDirectionLeftToRight() {
		return directionLeftToRight;
	}
}
