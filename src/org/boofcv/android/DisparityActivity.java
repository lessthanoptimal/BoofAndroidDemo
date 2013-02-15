package org.boofcv.android;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
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

	// indicate where the user touched the screen
	volatile int touchEventType = 0;
	volatile int touchX;
	volatile int touchY;

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
		setProcessing(new DisparityProcessing());
	}



	@Override
	public void onItemSelected(AdapterView<?> adapterView, View view, int pos, long id ) {
		if( pos == 0 ) {
			activeView = DView.ASSOCIATION;
		} else if( pos == 1 ) {
			touchY = -1;
			activeView = DView.RECTIFICATION;
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
				touchEventType = 1;
				touchX = (int)e.getX();
				touchY = (int)e.getY();
			} else if( activeView == DView.RECTIFICATION ) {
				touchY = (int)e.getY();
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

			touchEventType = (int)e1.getX() < v.getWidth()/2 ? 2 : 3;

			return true;
		}

		@Override
		public boolean onDoubleTapEvent(MotionEvent e)
		{
			if( activeView != DView.ASSOCIATION ) {
				return false;
			}

			touchEventType = 4;
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
		}

		@Override
		protected void declareImages(int width, int height) {
			super.declareImages(width, height);

			visualize.initializeImages( width, height );
			outputWidth = visualize.getOutputWidth();
			outputHeight = visualize.getOutputHeight();

			disparity.init(width,height);
		}

		@Override
		protected synchronized void process(ImageFloat32 gray) {

			int target = 0;

			// process GUI interactions
			synchronized ( lockGui ) {
				if( touchEventType == 1 ) {
					// first see if there are any features to select
					if( !visualize.setTouch(touchX,touchY) ) {
						// if not then it must be a capture image request
						target = touchX < view.getWidth()/2 ? 1 : 2;
					}
				} else if( touchEventType == 2 ) {
					visualize.setSource(null);
					visualize.forgetSelection();
				} else if( touchEventType == 3 ) {
					visualize.setDestination(null);
					visualize.forgetSelection();
				} else if( touchEventType == 4 ) {
					visualize.forgetSelection();
				}
			}
			touchEventType = 0;

			boolean computedFeatures = false;
			// compute image features for left or right depending on user selection
			if( target == 1 ) {
				disparity.setSource(gray);
				computedFeatures = true;
			} else if( target == 2 ) {
				disparity.setDestination(gray);
				computedFeatures = true;
			}

			synchronized ( lockGui ) {
				if( target == 1 ) {
					visualize.setSource(gray);
				} else if( target == 2 ) {
					visualize.setDestination(gray);
				}
			}

			if( computedFeatures && visualize.hasLeft && visualize.hasRight ) {
				if( disparity.process() ) {
					synchronized ( lockGui ) {
						visualize.setMatches(disparity.getInliersPixel());
						visualize.forgetSelection();
					}
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

				canvas.drawText("Calibrate Camera First", (canvas.getWidth() - textLength) / 2, canvas.getHeight() / 2, paint);
			} else if( activeView == DView.DISPARITY ) {
				// draw rectified image
				ConvertBitmap.grayToBitmap(disparity.rectifiedLeft, visualize.bitmapSrc, visualize.storage);

				// now draw the disparity as a colorized image
				ImageFloat32 d = disparity.getDisparity();

				int min = disparity.getDisparityAlg().getMinDisparity();
				int max = disparity.getDisparityAlg().getMaxDisparity();

				VisualizeImageData.disparity(d,min,max,0,visualize.bitmapDst,visualize.storage);

				int startX = d.getWidth() + AssociationVisualize.SEPARATION;
				canvas.drawBitmap(visualize.bitmapSrc,0,0,null);
				canvas.drawBitmap(visualize.bitmapDst,startX,0,null);
			} else if( activeView == DView.RECTIFICATION ) {
				ConvertBitmap.grayToBitmap(disparity.rectifiedLeft,visualize.bitmapSrc,visualize.storage);
				ConvertBitmap.grayToBitmap(disparity.rectifiedRight,visualize.bitmapDst,visualize.storage);

				int startX = disparity.rectifiedLeft.getWidth() + AssociationVisualize.SEPARATION;
				canvas.drawBitmap(visualize.bitmapSrc,0,0,null);
				canvas.drawBitmap(visualize.bitmapDst,startX,0,null);

				if( touchY >= 0 ) {
					canvas.restore();
					canvas.drawLine(0,touchY,canvas.getWidth(),touchY,visualize.paintPoint);
				}
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
		RECTIFICATION,
		DISPARITY
	}
}