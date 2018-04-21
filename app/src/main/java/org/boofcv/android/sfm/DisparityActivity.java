package org.boofcv.android.sfm;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.os.Bundle;
import android.view.GestureDetector;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.Toast;

import org.boofcv.android.DemoCamera2Activity;
import org.boofcv.android.DemoProcessingAbstract;
import org.boofcv.android.R;
import org.boofcv.android.assoc.AssociationVisualize;

import boofcv.abst.feature.associate.AssociateDescription;
import boofcv.abst.feature.associate.ScoreAssociation;
import boofcv.abst.feature.detdesc.DetectDescribePoint;
import boofcv.abst.feature.disparity.StereoDisparity;
import boofcv.alg.misc.ImageMiscOps;
import boofcv.android.ConvertBitmap;
import boofcv.android.VisualizeImageData;
import boofcv.factory.feature.associate.FactoryAssociation;
import boofcv.factory.feature.detdesc.FactoryDetectDescribe;
import boofcv.factory.feature.disparity.DisparityAlgorithms;
import boofcv.factory.feature.disparity.FactoryStereoDisparity;
import boofcv.struct.calib.CameraPinholeRadial;
import boofcv.struct.feature.BrightFeature;
import boofcv.struct.image.GrayF32;

/**
 * Computes the stereo disparity between two images captured by the camera.  The user selects the images and which
 * algorithm to process them using.
 *
 * @author Peter Abeles
 */
public class DisparityActivity extends DemoCamera2Activity
		implements AdapterView.OnItemSelectedListener
{
	Spinner spinnerView;
	Spinner spinnerAlgs;

	AssociationVisualize visualize;

	// indicate where the user touched the screen
	volatile int touchEventType = 0;
	volatile int touchX;
	volatile int touchY;
	volatile boolean reset = false;

	private GestureDetector mDetector;

	// used to notify processor that the disparity algorithms need to be changed
	int changeDisparityAlg = -1;

	DView activeView = DView.ASSOCIATION;

	public DisparityActivity() {
		super(Resolution.R640x480);
		super.showBitmap = false;
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		visualize = new AssociationVisualize(this);

		LayoutInflater inflater = getLayoutInflater();
		LinearLayout controls = (LinearLayout)inflater.inflate(R.layout.disparity_controls,null);

		spinnerView = controls.findViewById(R.id.spinner_view);
		ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(this,
				R.array.disparity_views, android.R.layout.simple_spinner_item);
		adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		spinnerView.setAdapter(adapter);
		spinnerView.setOnItemSelectedListener(this);

		spinnerAlgs = controls.findViewById(R.id.spinner_algs);
		adapter = ArrayAdapter.createFromResource(this,
				R.array.disparity_algs, android.R.layout.simple_spinner_item);
		adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		spinnerAlgs.setAdapter(adapter);
		spinnerAlgs.setOnItemSelectedListener(this);

		setControls(controls);

		mDetector = new GestureDetector(this, new MyGestureDetector(displayView));
		displayView.setOnTouchListener((v, event) -> {
            mDetector.onTouchEvent(event);
            return true;
        });
	}

	@Override
	protected void onResume() {
		super.onResume();
		visualize.setSource(null);
		visualize.setDestination(null);
		changeDisparityAlg = spinnerAlgs.getSelectedItemPosition();
	}

	@Override
	public void createNewProcessor() {
		setProcessing(new DisparityProcessing());
	}

	@Override
	public void onItemSelected(AdapterView<?> adapterView, View view, int pos, long id ) {
		if( adapterView == spinnerView ) {
			if( pos == 0 ) {
				activeView = DView.ASSOCIATION;
			} else if( pos == 1 ) {
				touchY = -1;
				activeView = DView.RECTIFICATION;
			} else {
				activeView = DView.DISPARITY;
			}
		} else if( adapterView == spinnerAlgs ) {
			changeDisparityAlg = pos;
		}
	}

	@Override
	public void onNothingSelected(AdapterView<?> adapterView) {}

	public void resetPressed( View view ) {
		reset = true;
	}

	protected class MyGestureDetector extends GestureDetector.SimpleOnGestureListener
	{
		View v;

		public MyGestureDetector(View v) {
			this.v = v;
		}

		@Override
		public boolean onDown(MotionEvent e) {

			// make sure the camera is calibrated first
			if( lookupIntrinsics() == null ) {
				Toast.makeText(DisparityActivity.this, "You must first calibrate the camera!", Toast.LENGTH_SHORT).show();
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


	protected class DisparityProcessing extends DemoProcessingAbstract<GrayF32> {

		DisparityCalculation<BrightFeature> disparity;

		GrayF32 disparityImage;
		int disparityMin,disparityMax;
		CameraPinholeRadial intrinsic;

		public DisparityProcessing() {
			super(GrayF32.class);
		}

		private StereoDisparity<GrayF32, GrayF32> createDisparity() {

			DisparityAlgorithms which;
			switch( changeDisparityAlg ) {
				case 0:
					which = DisparityAlgorithms.RECT;
					break;

				case 1:
					which = DisparityAlgorithms.RECT_FIVE;
					break;

				default:
					throw new RuntimeException("Unknown algorithm "+changeDisparityAlg);
			}


			return FactoryStereoDisparity.regionSubpixelWta(which,
					5, 40, 5, 5, 100, 1, 0.1, GrayF32.class);
		}

		@Override
		public void initialize(int imageWidth, int imageHeight) {
			intrinsic = lookupIntrinsics();

			DetectDescribePoint<GrayF32, BrightFeature> detDesc =
					FactoryDetectDescribe.surfFast(null,null,null,GrayF32.class);

			ScoreAssociation<BrightFeature> score = FactoryAssociation.defaultScore(BrightFeature.class);
			AssociateDescription<BrightFeature> associate =
					FactoryAssociation.greedy(score,Double.MAX_VALUE,true);

			disparity = new DisparityCalculation<>(detDesc, associate, intrinsic);
			disparityImage = new GrayF32(imageWidth,imageHeight);
			visualize.initializeImages( imageWidth, imageHeight );
			disparity.init(imageWidth,imageHeight);
		}

		@Override
		public void onDraw(Canvas canvas, Matrix imageToView) {
			if( intrinsic == null ) {
				Paint paint = new Paint();
				paint.setColor(Color.RED);
				paint.setTextSize(40*displayMetrics.density);
				int textLength = (int)paint.measureText("Calibrate Camera First");

				canvas.drawText("Calibrate Camera First", (canvas.getWidth() - textLength) / 2, canvas.getHeight() / 2, paint);
			} else if( activeView == DView.DISPARITY ) {
				// draw rectified image
				ConvertBitmap.grayToBitmap(disparity.rectifiedLeft, visualize.bitmapSrc, visualize.storage);

				if( disparity.isDisparityAvailable() ) {
					VisualizeImageData.disparity(disparityImage,disparityMin,disparityMax,0,
							visualize.bitmapDst,visualize.storage);

					visualize.render(displayView,canvas,true,true);
				}
			} else if( activeView == DView.RECTIFICATION ) {
				canvas.save();
				ConvertBitmap.grayToBitmap(disparity.rectifiedLeft,visualize.bitmapSrc,visualize.storage);
				ConvertBitmap.grayToBitmap(disparity.rectifiedRight,visualize.bitmapDst,visualize.storage);

				visualize.render(displayView,canvas,true,true);

				if( touchY >= 0 ) {
					canvas.restore();
					Paint paint = new Paint();
					paint.setColor(Color.RED);
					paint.setStyle(Paint.Style.STROKE);
					paint.setStrokeWidth(4*displayMetrics.density);
					canvas.drawLine(0,touchY,canvas.getWidth(),touchY,paint);
				}
			} else {
				// bit of a hack to reduce memory usage
				ConvertBitmap.grayToBitmap(visualize.graySrc,visualize.bitmapSrc,visualize.storage);
				ConvertBitmap.grayToBitmap(visualize.grayDst,visualize.bitmapDst,visualize.storage);

				visualize.render(displayView,canvas);
			}
		}

		@Override
		public void process(GrayF32 gray) {

			int target = 0;

			// process GUI interactions
			synchronized ( lockGui ) {
				if( reset ) {
					reset = false;
					visualize.setSource(null);
					visualize.setDestination(null);
					runOnUiThread(new Runnable() {
						@Override
						public void run() {
							spinnerView.setSelection(0);
						}
					});

				}
				if( touchEventType == 1 ) {
					// first see if there are any features to select
					if( !visualize.setTouch(touchX,touchY) ) {
						// if not then it must be a capture image request
						target = touchX < viewWidth/2 ? 1 : 2;
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
				setProgressMessage("Detecting Features Left", false);
				disparity.setSource(gray);
				computedFeatures = true;
			} else if( target == 2 ) {
				setProgressMessage("Detecting Features Right", false);
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

			if( changeDisparityAlg != -1 ) {
				disparity.setDisparityAlg(createDisparity());
			}

			if( disparity.disparityAlg != null ) {
				if( computedFeatures && visualize.hasLeft && visualize.hasRight ) {
					// rectify the images and compute the disparity
					setProgressMessage("Rectifying", false);
					boolean success = disparity.rectifyImage();
					if( success ) {
						setProgressMessage("Disparity", false);
						disparity.computeDisparity();
						synchronized ( lockGui ) {
							disparityMin = disparity.getDisparityAlg().getMinDisparity();
							disparityMax = disparity.getDisparityAlg().getMaxDisparity();
							disparityImage.setTo(disparity.getDisparity());
							visualize.setMatches(disparity.getInliersPixel());
							visualize.forgetSelection();

							runOnUiThread(() -> {
                                spinnerView.setSelection(2); // switch to disparity view
                            });
						}
					} else {
						synchronized ( lockGui ) {
							ImageMiscOps.fill(disparityImage,0);
						}
						runOnUiThread(() -> Toast.makeText(DisparityActivity.this,
								"Disparity computation failed!", Toast.LENGTH_SHORT).show());
					}
				} else if( changeDisparityAlg != -1 && visualize.hasLeft && visualize.hasRight ) {
					// recycle the rectified image but compute the disparity using the new algorithm
					setProgressMessage("Disparity", false);
					disparity.computeDisparity();

					synchronized ( lockGui ) {
						disparityMin = disparity.getDisparityAlg().getMinDisparity();
						disparityMax = disparity.getDisparityAlg().getMaxDisparity();
						disparityImage.setTo(disparity.getDisparity());
					}
				}
			}

			hideProgressDialog();
			changeDisparityAlg = -1;
		}
	}

	enum DView {
		ASSOCIATION,
		RECTIFICATION,
		DISPARITY
	}
}