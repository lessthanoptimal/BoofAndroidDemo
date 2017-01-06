package org.boofcv.android.sfm;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.LinearLayout;

import org.boofcv.android.DemoVideoDisplayActivity;
import org.boofcv.android.R;
import org.ddogleg.struct.FastQueue;

import java.util.List;

import boofcv.abst.feature.detect.interest.ConfigGeneralDetector;
import boofcv.abst.feature.tracker.PointTracker;
import boofcv.abst.sfm.AccessPointTracks;
import boofcv.abst.sfm.d2.ImageMotion2D;
import boofcv.alg.sfm.d2.StitchingFromMotion2D;
import boofcv.android.ConvertBitmap;
import boofcv.android.gui.VideoRenderProcessing;
import boofcv.factory.feature.tracker.FactoryPointTracker;
import boofcv.factory.sfm.FactoryMotion2D;
import boofcv.struct.image.GrayS16;
import boofcv.struct.image.GrayU8;
import boofcv.struct.image.ImageType;
import georegression.struct.affine.Affine2D_F64;
import georegression.struct.homography.Homography2D_F64;
import georegression.struct.point.Point2D_F64;
import georegression.transform.homography.HomographyPointOps_F64;

/**
 * Displays image stabilization.
 *
 * @author Peter Abeles
 */
public class StabilizeDisplayActivity extends DemoVideoDisplayActivity
implements CompoundButton.OnCheckedChangeListener
{

	Paint paintInlier;
	Paint paintOutlier;

	boolean showFeatures;
	boolean resetRequested;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		paintInlier = new Paint();
		paintInlier.setColor(Color.RED);
		paintInlier.setStyle(Paint.Style.FILL);
		paintOutlier = new Paint();
		paintOutlier.setColor(Color.BLUE);
		paintOutlier.setStyle(Paint.Style.FILL);

		resetRequested = false;

		LayoutInflater inflater = getLayoutInflater();
		LinearLayout controls = (LinearLayout)inflater.inflate(R.layout.stabilization_controls,null);

		LinearLayout parent = getViewContent();
		parent.addView(controls);

		CheckBox seek = (CheckBox)controls.findViewById(R.id.check_features);
		seek.setOnCheckedChangeListener(this);


		StitchingFromMotion2D<GrayU8,Affine2D_F64> distortAlg = createStabilization();

		setProcessing(new PointProcessing(distortAlg));
	}

	@Override
	protected void onResume() {
		super.onResume();
		StitchingFromMotion2D<GrayU8,Affine2D_F64> distortAlg = createStabilization();
		setProcessing(new PointProcessing(distortAlg));
	}

	public void resetPressed( View view ) {
		resetRequested = true;
	}

	private StitchingFromMotion2D<GrayU8,Affine2D_F64> createStabilization() {

		ConfigGeneralDetector config = new ConfigGeneralDetector();
		config.maxFeatures = 150;
		config.threshold = 40;
		config.radius = 3;

		PointTracker<GrayU8> tracker = FactoryPointTracker.
				klt(new int[]{1, 2,4}, config, 3, GrayU8.class, GrayS16.class);

		ImageMotion2D<GrayU8,Affine2D_F64> motion = FactoryMotion2D.createMotion2D(100, 1.5, 2, 40,
				0.5, 0.6, false, tracker, new Affine2D_F64());

		return FactoryMotion2D.createVideoStitch(0.2,motion, ImageType.single(GrayU8.class));
	}

	@Override
	public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
		showFeatures = b;
	}

	protected class PointProcessing extends VideoRenderProcessing<GrayU8> {
		StitchingFromMotion2D<GrayU8,Affine2D_F64> alg;
		Homography2D_F64 imageToDistorted = new Homography2D_F64();
		Homography2D_F64 distortedToImage = new Homography2D_F64();

		Bitmap bitmap;
		byte[] storage;

		StitchingFromMotion2D.Corners corners = new StitchingFromMotion2D.Corners();
		Point2D_F64 distPt = new Point2D_F64();

		FastQueue<Point2D_F64> inliersGui = new FastQueue<Point2D_F64>(Point2D_F64.class,true);
		FastQueue<Point2D_F64> outliersGui = new FastQueue<Point2D_F64>(Point2D_F64.class,true);

		public PointProcessing( StitchingFromMotion2D<GrayU8,Affine2D_F64> alg  ) {
			super(ImageType.single(GrayU8.class));
			this.alg = alg;
		}

		@Override
		protected void declareImages(int width, int height) {
			super.declareImages(width, height);
			alg.configure(width,height,null);
			bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
			storage = ConvertBitmap.declareStorage(bitmap, storage);
		}

		@Override
		protected void process(GrayU8 gray) {
			if( !resetRequested && alg.process(gray) ) {
				synchronized ( lockGui ) {
					alg.getImageCorners(gray.width,gray.height,corners);
					ConvertBitmap.grayToBitmap(alg.getStitchedImage(),bitmap,storage);


					ImageMotion2D<?,?> motion = alg.getMotion();
					if( showFeatures && (motion instanceof AccessPointTracks) ) {
						AccessPointTracks access = (AccessPointTracks)motion;

						alg.getWorldToCurr(imageToDistorted);
						imageToDistorted.invert(distortedToImage);
						inliersGui.reset();outliersGui.reset();
						List<Point2D_F64> points = access.getAllTracks();
						for( int i = 0; i < points.size(); i++ ) {
							HomographyPointOps_F64.transform(distortedToImage,points.get(i),distPt);

							if( access.isInlier(i) ) {
								inliersGui.grow().set(distPt.x,distPt.y);
							} else {
								outliersGui.grow().set(distPt.x,distPt.y);
							}
						}
					}
				}
			} else {
				resetRequested = false;
				alg.reset();
			}
		}

		@Override
		protected void render(Canvas canvas, double imageToOutput) {
			canvas.drawBitmap(bitmap,0,0,null);

			Point2D_F64 p0 = corners.p0;
			Point2D_F64 p1 = corners.p1;
			Point2D_F64 p2 = corners.p2;
			Point2D_F64 p3 = corners.p3;

			canvas.drawLine((int)p0.x,(int)p0.y,(int)p1.x,(int)p1.y, paintInlier);
			canvas.drawLine((int)p1.x,(int)p1.y,(int)p2.x,(int)p2.y, paintInlier);
			canvas.drawLine((int)p2.x,(int)p2.y,(int)p3.x,(int)p3.y, paintInlier);
			canvas.drawLine((int)p3.x,(int)p3.y,(int)p0.x,(int)p0.y, paintInlier);

			for( int i = 0; i < inliersGui.size; i++ ) {
				Point2D_F64 p = inliersGui.get(i);
				canvas.drawCircle((float)p.x,(float)p.y,3,paintInlier);
			}
			for( int i = 0; i < outliersGui.size; i++ ) {
				Point2D_F64 p = outliersGui.get(i);
				canvas.drawCircle((float)p.x,(float)p.y,3,paintOutlier);
			}
		}
	}
}