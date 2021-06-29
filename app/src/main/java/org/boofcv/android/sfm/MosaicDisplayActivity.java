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
import org.ddogleg.struct.DogArray;

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
import georegression.struct.shapes.Quadrilateral_F64;
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
		super.bitmapMode = BitmapMode.NONE;
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

		Quadrilateral_F64 corners = new Quadrilateral_F64();
		Point2D_F64 distPt = new Point2D_F64();

		DogArray<Point2D_F64> inliersGui = new DogArray<>(Point2D_F64::new);
		DogArray<Point2D_F64> outliersGui = new DogArray<>(Point2D_F64::new);

		float radius;

		public PointProcessing( StitchingFromMotion2D<GrayU8,Affine2D_F64> alg  ) {
			super(ImageType.single(GrayU8.class));
			this.alg = alg;
		}

		@Override
		public void initialize(int imageWidth, int imageHeight, int sensorOrientation) {

			outputWidth = imageWidth*2;
			outputHeight = imageHeight*2;
			bitmap = Bitmap.createBitmap(outputWidth, outputHeight, Bitmap.Config.ARGB_8888);

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
			canvas.drawBitmap(bitmap,imageToView,null);

			if( !showFeatures )
				return;

			canvas.concat(imageToView);
			synchronized (lockGui) {
				Point2D_F64 p0 = corners.a;
				Point2D_F64 p1 = corners.b;
				Point2D_F64 p2 = corners.c;
				Point2D_F64 p3 = corners.d;

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

				ConvertBitmap.grayToBitmap(stitched,bitmap,bitmapTmp);

				synchronized ( lockGui ) {

					ImageMotion2D<?,?> motion = alg.getMotion();
					if( showFeatures && (motion instanceof AccessPointTracks) ) {
						AccessPointTracks access = (AccessPointTracks)motion;

						alg.getWorldToCurr(imageToDistorted);
						imageToDistorted.invert(distortedToImage);
						inliersGui.reset();outliersGui.reset();
						int N = access.getTotalTracks();
						Point2D_F64 pixel = new Point2D_F64();
						for( int i = 0; i < N; i++ ) {
							access.getTrackPixel(i, pixel);
							HomographyPointOps_F64.transform(distortedToImage,pixel,distPt);

							if( access.isTrackInlier(i) ) {
								inliersGui.grow().setTo(distPt.x,distPt.y);
							} else {
								outliersGui.grow().setTo(distPt.x,distPt.y);
							}
						}
					}

					alg.getImageCorners(gray.width,gray.height,corners);
				}

				boolean inside = true;
				inside &= BoofMiscOps.isInside(stitched,corners.a.x,corners.b.y,5);
				inside &= BoofMiscOps.isInside(stitched,corners.b.x,corners.c.y,5);
				inside &= BoofMiscOps.isInside(stitched,corners.c.x,corners.d.y,5);
				inside &= BoofMiscOps.isInside(stitched,corners.d.x,corners.a.y,5);
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