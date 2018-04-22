package org.boofcv.android.sfm;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.ToggleButton;

import org.boofcv.android.DemoCamera2Activity;
import org.boofcv.android.DemoProcessingAbstract;
import org.boofcv.android.R;
import org.ddogleg.struct.FastQueue;

import java.util.List;

import boofcv.abst.sfm.AccessPointTracks;
import boofcv.abst.sfm.d2.ImageMotion2D;
import boofcv.alg.sfm.d2.StitchingFromMotion2D;
import boofcv.android.ConvertBitmap;
import boofcv.misc.BoofMiscOps;
import boofcv.struct.image.GrayU8;
import boofcv.struct.image.ImageType;
import georegression.struct.affine.Affine2D_F64;
import georegression.struct.homography.Homography2D_F64;
import georegression.struct.point.Point2D_F64;
import georegression.transform.homography.HomographyPointOps_F64;

import static org.boofcv.android.sfm.StabilizeDisplayActivity.createStabilization;

/**
 * Displays an image mosaic created from the video stream.
 *
 * @author Peter Abeles
 */
public class MosaicDisplayActivity extends DemoCamera2Activity
implements CompoundButton.OnCheckedChangeListener
{

	Paint paintBorder=new Paint();
	Paint paintInlier=new Paint();
	Paint paintOutlier=new Paint();

	boolean showFeatures;
	boolean resetRequested;
	boolean paused = false;

	int outputWidth,outputHeight;

	Spinner spinnerView;

	public MosaicDisplayActivity() {
		super(Resolution.R320x240);
		super.showBitmap = false;
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		paintInlier.setColor(Color.GREEN);
		paintInlier.setStyle(Paint.Style.FILL);
		paintOutlier.setColor(Color.RED);
		paintOutlier.setStyle(Paint.Style.FILL);
		paintBorder.setColor(Color.CYAN);
		paintBorder.setStyle(Paint.Style.STROKE);

		resetRequested = false;

		LayoutInflater inflater = getLayoutInflater();
		LinearLayout controls = (LinearLayout)inflater.inflate(R.layout.mosaic_controls,null);

		spinnerView = controls.findViewById(R.id.spinner_algs);
		ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(this,
				R.array.trackers_stabilize, android.R.layout.simple_spinner_item);
		adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		spinnerView.setAdapter(adapter);
		spinnerView.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
			@Override
			public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
				createNewProcessor();
			}

			@Override
			public void onNothingSelected(AdapterView<?> adapterView) {}
		});

		CheckBox seek = controls.findViewById(R.id.check_features);
		seek.setOnCheckedChangeListener(this);

		setControls(controls);
		// set the background color so the mosaic's boundary can be seen easier
		displayView.setBackgroundColor(Color.DKGRAY);
	}

	@Override
	public void createNewProcessor() {
		StitchingFromMotion2D<GrayU8,Affine2D_F64> distortAlg =
				createStabilization(spinnerView.getSelectedItemPosition());
		setProcessing(new PointProcessing(distortAlg));
	}

	public void resetPressed(View view ) {
		resetRequested = true;
	}

	public void pausedPressed( View view ) {
		paused = !((ToggleButton)view).isChecked();
	}

	@Override
	public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
		showFeatures = b;
	}

	protected class PointProcessing extends DemoProcessingAbstract<GrayU8> {
		StitchingFromMotion2D<GrayU8,Affine2D_F64> alg;
		Homography2D_F64 imageToDistorted = new Homography2D_F64();
		Homography2D_F64 distortedToImage = new Homography2D_F64();

		StitchingFromMotion2D.Corners corners = new StitchingFromMotion2D.Corners();
		Point2D_F64 distPt = new Point2D_F64();

		FastQueue<Point2D_F64> inliersGui = new FastQueue<Point2D_F64>(Point2D_F64.class,true);
		FastQueue<Point2D_F64> outliersGui = new FastQueue<Point2D_F64>(Point2D_F64.class,true);

		float radius;

		public PointProcessing( StitchingFromMotion2D<GrayU8,Affine2D_F64> alg  ) {
			super(ImageType.single(GrayU8.class));
			this.alg = alg;
		}

		@Override
		public void initialize(int imageWidth, int imageHeight, int sensorOrientation) {

			outputWidth = imageWidth*2;
			outputHeight = imageHeight*2;
			synchronized (bitmapLock) {
				bitmap = Bitmap.createBitmap(outputWidth, outputHeight, Bitmap.Config.ARGB_8888);
				bitmapTmp = ConvertBitmap.declareStorage(bitmap,bitmapTmp);
			}

			int rotation = getWindowManager().getDefaultDisplay().getRotation();
			videoToDisplayMatrix(outputWidth, outputHeight,sensorOrientation,
					viewWidth,viewHeight,rotation*90, stretchToFill,imageToView);

			int tx = outputWidth/2 - imageWidth/4;
			int ty = outputHeight/2 - imageHeight/4;

			Affine2D_F64 init = new Affine2D_F64(0.5,0,0,0.5,tx,ty);
			init = init.invert(null);

			alg.configure(outputWidth,outputHeight,init);

			float density = cameraToDisplayDensity;
			radius = 3*density;
			paintBorder.setStrokeWidth(8*density);

		}

		@Override
		public void onDraw(Canvas canvas, Matrix imageToView) {
			synchronized (bitmapLock) {
				canvas.drawBitmap(bitmap,imageToView,null);
			}

			if( !showFeatures )
				return;

			canvas.setMatrix(imageToView);
			synchronized (lockGui) {
				Point2D_F64 p0 = corners.p0;
				Point2D_F64 p1 = corners.p1;
				Point2D_F64 p2 = corners.p2;
				Point2D_F64 p3 = corners.p3;

				canvas.drawLine((int) p0.x, (int) p0.y, (int) p1.x, (int) p1.y, paintBorder);
				canvas.drawLine((int) p1.x, (int) p1.y, (int) p2.x, (int) p2.y, paintBorder);
				canvas.drawLine((int) p2.x, (int) p2.y, (int) p3.x, (int) p3.y, paintBorder);
				canvas.drawLine((int) p3.x, (int) p3.y, (int) p0.x, (int) p0.y, paintBorder);

				for (int i = 0; i < inliersGui.size; i++) {
					Point2D_F64 p = inliersGui.get(i);
					canvas.drawCircle((float) p.x, (float) p.y, radius, paintInlier);
				}
				for (int i = 0; i < outliersGui.size; i++) {
					Point2D_F64 p = outliersGui.get(i);
					canvas.drawCircle((float) p.x, (float) p.y, radius, paintOutlier);
				}
			}
		}

		@Override
		public void process(GrayU8 gray) {
			if(paused)
				return;

			if( !resetRequested && alg.process(gray) ) {
				GrayU8 stitched = alg.getStitchedImage();

				synchronized (bitmapLock) {
					ConvertBitmap.grayToBitmap(stitched,bitmap,bitmapTmp);
				}

				synchronized ( lockGui ) {

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

					alg.getImageCorners(gray.width,gray.height,corners);
				}

				boolean inside = true;
				inside &= BoofMiscOps.checkInside(stitched,corners.p0.x,corners.p0.y,5);
				inside &= BoofMiscOps.checkInside(stitched,corners.p1.x,corners.p1.y,5);
				inside &= BoofMiscOps.checkInside(stitched,corners.p2.x,corners.p2.y,5);
				inside &= BoofMiscOps.checkInside(stitched,corners.p3.x,corners.p3.y,5);
				if( !inside ) {
					alg.setOriginToCurrent();
				}

			} else {
				resetRequested = false;
				alg.reset();
			}
		}

	}
}