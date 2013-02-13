package org.boofcv.android;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.hardware.Camera;
import android.os.Bundle;
import android.view.GestureDetector;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.widget.*;
import boofcv.abst.feature.associate.AssociateDescription;
import boofcv.abst.feature.associate.ScoreAssociation;
import boofcv.abst.feature.detdesc.DetectDescribePoint;
import boofcv.android.ConvertBitmap;
import boofcv.android.VisualizeImageData;
import boofcv.factory.feature.associate.FactoryAssociation;
import boofcv.factory.feature.detdesc.FactoryDetectDescribe;
import boofcv.struct.feature.SurfFeature;
import boofcv.struct.image.ImageFloat32;

/**
 * @author Peter Abeles
 */
public class DisparityActivity extends VideoDisplayActivity
		implements AdapterView.OnItemSelectedListener
{
	Spinner spinnerView;

	AssociationVisualize visualize;

	int target = 0;

	private GestureDetector mDetector;

	DView activeView = DView.ASSOCIATION;

	public DisparityActivity() {
		visualize = new AssociationVisualize(this);
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		LayoutInflater inflater = getLayoutInflater();
		LinearLayout controls = (LinearLayout)inflater.inflate(R.layout.select_algorithm,null);

		LinearLayout parent = (LinearLayout)findViewById(R.id.camera_preview_parent);
		parent.addView(controls);

		spinnerView = (Spinner)controls.findViewById(R.id.spinner_algs);
		ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(this,
				R.array.disparity_views, android.R.layout.simple_spinner_item);
		adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		spinnerView.setAdapter(adapter);
		spinnerView.setOnItemSelectedListener(this);

		FrameLayout iv = (FrameLayout)findViewById(R.id.camera_preview);
		mDetector = new GestureDetector(this, new MyGestureDetector(iv));
		iv.setOnTouchListener(new View.OnTouchListener(){
			@Override
			public boolean onTouch(View v, MotionEvent event)
			{
				mDetector.onTouchEvent(event);
				return true;
			}});
	}

	@Override
	protected void onResume() {
		super.onResume();
		Camera.Size size = mCamera.getParameters().getPreviewSize();
		visualize.initializeImages( size.width, size.height );

		setProcessing(new DisparityProcessing());
	}



	@Override
	public void onItemSelected(AdapterView<?> adapterView, View view, int pos, long id ) {
		if( pos == 0 ) {
			activeView = DView.ASSOCIATION;
		} else {
			activeView = DView.DISPARITY;
		}
	}

	@Override
	public void onNothingSelected(AdapterView<?> adapterView) {}

	protected class MyGestureDetector extends GestureDetector.SimpleOnGestureListener
	{
		View v;

		public MyGestureDetector(View v) {
			this.v = v;
		}

		@Override
		public boolean onDown(MotionEvent e) {

			// make sure the camera is calibrated first
			if( DemoMain.preference.intrinsic == null ) {
				Toast toast = Toast.makeText(DisparityActivity.this, "You must first calibrate the camera!", 2000);
				toast.show();
				return false;
			}

			if( activeView == DView.ASSOCIATION ) {
				if( !visualize.setTouch((int)e.getX(),(int)e.getY()))
				{
					// select an image to capture
					int half = v.getWidth()/2;

					if( e.getX() < half ) {
						target = 1;
					} else {
						target = 2;
					}
				}
			}

			return true;
		}

		/**
		 * If the user flings an image discard the results in the image
		 */
		@Override
		public boolean onFling( MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
			if( activeView != DView.ASSOCIATION ) {
				return false;
			}

			int half = v.getWidth()/2;

			if( e1.getX() < half ) {
				visualize.setSource(null);
			} else {
				visualize.setDestination(null);
			}
			visualize.forgetSelection();

			return true;
		}

		@Override
		public boolean onDoubleTapEvent(MotionEvent e)
		{
			if( activeView != DView.ASSOCIATION ) {
				return false;
			}

			// clear selection of individual match
			visualize.forgetSelection();
			return true;
		}
	}


	protected class DisparityProcessing extends BoofRenderProcessing<ImageFloat32> {

		DisparityCalculation<SurfFeature> disparity;

		public DisparityProcessing() {
			super(ImageFloat32.class);

			DetectDescribePoint<ImageFloat32, SurfFeature> detDesc =
					FactoryDetectDescribe.surfFast(null,null,null,ImageFloat32.class);

			ScoreAssociation<SurfFeature> score = FactoryAssociation.defaultScore(SurfFeature.class);
			AssociateDescription<SurfFeature> associate =
					FactoryAssociation.greedy(score,Double.MAX_VALUE,true);

			disparity = new DisparityCalculation<SurfFeature>(detDesc,associate,DemoMain.preference.intrinsic);

			// clear any user selected points
			visualize.forgetSelection();
		}

		@Override
		protected void declareImages(int width, int height) {
			super.declareImages(width, height);

			outputWidth = visualize.getOutputWidth();
			outputHeight = visualize.getOutputHeight();

			disparity.init(width,height);
		}

		@Override
		protected synchronized void process(ImageFloat32 gray) {
			if( target == 0 )
				return;

			if( target == 1 ) {
				visualize.setSource(gray);
				disparity.setSource(gray);
			} else if( target == 2 ) {
				visualize.setDestination(gray);
				disparity.setDestination(gray);
			}

			if( visualize.hasLeft && visualize.hasRight ) {
				if( disparity.process() ) {
					visualize.setMatches(disparity.getInliersPixel());
				} else {
					runOnUiThread(new Runnable() {
						public void run() {
							Toast toast = Toast.makeText(DisparityActivity.this, "Disparity computation failed!", 2000);
							toast.show();
						}});
				}
			}

			target = 0;
		}

		@Override
		protected synchronized void render(Canvas canvas, double imageToOutput) {
			if( DemoMain.preference.intrinsic == null ) {
				canvas.restore();
				Paint paint = new Paint();
				paint.setColor(Color.RED);
				paint.setTextSize(60);
				int textLength = (int)paint.measureText("Calibrate Camera First");

				canvas.drawText("Calibrate Camera First", (canvas.getWidth() + textLength) / 2, canvas.getHeight() / 2, paint);
			} else if( activeView == DView.DISPARITY ) {
				// draw rectified image
				ConvertBitmap.grayToBitmap(disparity.rectifiedLeft,visualize.bitmapSrc,visualize.storage);

				// now draw the disparity as a colorized image
				ImageFloat32 d = disparity.getDisparity();

				int min = disparity.getDisparityAlg().getMinDisparity();
				int max = disparity.getDisparityAlg().getMaxDisparity();

				VisualizeImageData.disparity(d,min,max,0,visualize.bitmapDst,visualize.storage);

				int startX = d.getWidth() + AssociationVisualize.SEPARATION;
				canvas.drawBitmap(visualize.bitmapSrc,0,0,null);
				canvas.drawBitmap(visualize.bitmapDst,startX,0,null);
			} else {
				// bit of a hack to reduce memory usage
				ConvertBitmap.grayToBitmap(visualize.graySrc,visualize.bitmapSrc,visualize.storage);
				ConvertBitmap.grayToBitmap(visualize.grayDst,visualize.bitmapDst,visualize.storage);

				visualize.render(canvas,tranX,tranY,scale);
			}
		}
	}

	enum DView {
		ASSOCIATION,
		DISPARITY
	}
}