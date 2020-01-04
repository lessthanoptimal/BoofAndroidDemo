package org.boofcv.android.sfm;

import android.app.ActivityManager;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.pm.ConfigurationInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;
import android.view.GestureDetector;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.Toast;

import org.boofcv.android.DemoCamera2Activity;
import org.boofcv.android.DemoProcessingAbstract;
import org.boofcv.android.R;
import org.boofcv.android.assoc.AssociationVisualize;
import org.boofcv.android.visalize.PointCloud3D;
import org.boofcv.android.visalize.PointCloudSurfaceView;
import org.ddogleg.struct.GrowQueue_I8;
import org.ejml.data.DMatrixRMaj;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;

import boofcv.abst.feature.associate.AssociateDescription;
import boofcv.abst.feature.associate.ScoreAssociation;
import boofcv.abst.feature.detdesc.DetectDescribePoint;
import boofcv.abst.feature.disparity.StereoDisparity;
import boofcv.alg.geo.RectifyImageOps;
import boofcv.alg.misc.ImageMiscOps;
import boofcv.android.ConvertBitmap;
import boofcv.android.VisualizeImageData;
import boofcv.core.image.ConvertImage;
import boofcv.factory.feature.associate.FactoryAssociation;
import boofcv.factory.feature.detdesc.FactoryDetectDescribe;
import boofcv.factory.feature.disparity.ConfigDisparityBM;
import boofcv.factory.feature.disparity.ConfigDisparityBMBest5;
import boofcv.factory.feature.disparity.ConfigDisparitySGM;
import boofcv.factory.feature.disparity.DisparityError;
import boofcv.factory.feature.disparity.DisparitySgmError;
import boofcv.factory.feature.disparity.FactoryStereoDisparity;
import boofcv.io.calibration.CalibrationIO;
import boofcv.io.points.PointCloudIO;
import boofcv.io.points.PointCloudReader;
import boofcv.struct.calib.CameraPinholeBrown;
import boofcv.struct.calib.StereoParameters;
import boofcv.struct.distort.Point2Transform2_F64;
import boofcv.struct.feature.BrightFeature;
import boofcv.struct.image.GrayF32;
import boofcv.struct.image.GrayU8;
import boofcv.struct.image.ImageType;
import boofcv.struct.image.InterleavedU8;

import static org.boofcv.android.DemoMain.getExternalDirectory;

/**
 * Computes the stereo disparity between two images captured by the camera.  The user selects the images and which
 * algorithm to process them using.
 *
 * @author Peter Abeles
 */
public class DisparityActivity extends DemoCamera2Activity
		implements AdapterView.OnItemSelectedListener
{
	public final static String TAG = "DisparityActivity";

	Spinner spinnerView;
	Spinner spinnerAlgs;
	Spinner spinnerError;
	Button buttonSave;

	AssociationVisualize<GrayU8> visualize;

	// indicate where the user touched the screen
	volatile int touchEventType = 0;
	volatile int touchX;
	volatile int touchY;
	volatile boolean reset = false;

	private GestureDetector mDetector;

	// Which error models are selected for each disparity algorithm family
	private DisparityError errorBM = DisparityError.CENSUS;
	private DisparitySgmError errorSGM = DisparitySgmError.CENSUS;

	// used to notify processor that the disparity algorithms need to be changed
	volatile int changeDisparityAlg = -1;
	volatile int selectedDisparityAlg = 0;

	DView activeView = DView.ASSOCIATION;

	boolean supportedGL=false;

	PointCloudSurfaceView cloudView;
	ViewGroup layoutViews;

	// Shows status of saving to disk
	ProgressDialog saveDialog;
	File savePath;

	public DisparityActivity() {
		super(Resolution.R640x480);
		super.bitmapMode = BitmapMode.NONE;
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		supportedGL = hasGLES20();

		visualize = new AssociationVisualize<>(this);

		LayoutInflater inflater = getLayoutInflater();
		LinearLayout controls = (LinearLayout)inflater.inflate(R.layout.disparity_controls,null);

		spinnerView = controls.findViewById(R.id.spinner_view);
		// disable save button until disparity has been computed
		buttonSave = controls.findViewById(R.id.button_save);
		buttonSave.setEnabled(false);

		ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(this,
				R.array.disparity_views, android.R.layout.simple_spinner_item);
		// if it doesn't support 3D rendering remove the cloud option
		if( !hasGLES20() ) {
			List<CharSequence> list = new ArrayList<>();
			for( int i = 0; i < adapter.getCount()-1; i++ ) {
				list.add(adapter.getItem(i));
			}
			adapter = new ArrayAdapter<>(this,android.R.layout.simple_spinner_item,list);
		}
		adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		spinnerView.setAdapter(adapter);
		spinnerView.setOnItemSelectedListener(this);

		spinnerAlgs = controls.findViewById(R.id.spinner_algs);
		adapter = ArrayAdapter.createFromResource(this,
				R.array.disparity_algs, android.R.layout.simple_spinner_item);
		adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		spinnerAlgs.setAdapter(adapter);
		spinnerAlgs.setOnItemSelectedListener(this);

		spinnerError = controls.findViewById(R.id.spinner_error);
		setupErrorSpinner();
		spinnerError.setOnItemSelectedListener(this);

		setControls(controls);

		mDetector = new GestureDetector(this, new MyGestureDetector(displayView));
		displayView.setOnTouchListener((v, event) -> {
			mDetector.onTouchEvent(event);
			return true;
		});
	}

	@Override
	protected void startCamera(@NonNull ViewGroup layout, @Nullable TextureView view ) {
		super.startCamera(layout,view);
		this.layoutViews = layout;
		cloudView = new PointCloudSurfaceView(this);
	}

	private void showCloud3D( boolean visible ) {
		if( visible ) {
			layoutViews.addView(cloudView,layoutViews.getChildCount(),
					new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
		} else {
			layoutViews.removeView(cloudView);
		}
	}

	private boolean hasGLES20() {
		ActivityManager am = (ActivityManager)
				getSystemService(Context.ACTIVITY_SERVICE);
		ConfigurationInfo info = am.getDeviceConfigurationInfo();
		return info.reqGlEsVersion >= 0x20000;
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
			} else if( pos == 2 ) {
				activeView = DView.DISPARITY;
			} else {
				activeView = DView.CLOUD3D;
			}
			showCloud3D(activeView == DView.CLOUD3D);
		} else if( adapterView == spinnerAlgs ) {
			changeDisparityAlg = pos;

			// update the list of possible error models
			if( changeDisparityAlg != selectedDisparityAlg ) {
				setupErrorSpinner();
			}

		} else if( adapterView == spinnerError ) {
			// Changing disparity can trigger this event to happen. If there is no actual change in
			// error abort
			if( isBlockMatch(selectedDisparityAlg)) {
				DisparityError selected = DisparityError.values()[spinnerError.getSelectedItemPosition()];
				if( selected == errorBM )
					return;
				this.errorBM = selected;
			} else {
				DisparitySgmError selected = DisparitySgmError.values()[spinnerError.getSelectedItemPosition()];
				if( selected == errorSGM )
					return;
				this.errorSGM = selected;
			}
			changeDisparityAlg = selectedDisparityAlg;
		}
	}

	private void setupErrorSpinner() {
		int res = isBlockMatch(changeDisparityAlg) ?
				R.array.disparity_bm_errors : R.array.disparity_sgm_errors;
		int ordinal = isBlockMatch(changeDisparityAlg) ?
				errorBM.ordinal() : errorSGM.ordinal();

		ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(this,
				res, android.R.layout.simple_spinner_item);
		adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		spinnerError.setAdapter(adapter);
		spinnerError.setSelection(ordinal,false);
	}

	private boolean isBlockMatch( int algIndex ) {
		return algIndex < 2;
	}

	@Override
	public void onNothingSelected(AdapterView<?> adapterView) {}

	public void resetPressed( View view ) {
		reset = true;
	}

	/**
	 * Opens a dialog showing the status of saving to disk and creates a request to save to disk.
	 * @param view
	 */
	public void savePressed( View view ) {
		buttonSave.setEnabled(false);

		if( saveDialog != null ) {
			Log.e(TAG,"Save proessed while save is in progress!");
			return;
		}

		savePath = new File(getExternalDirectory(this),"stereo/"+System.currentTimeMillis());
		if( !savePath.exists() ) {
			if( !savePath.mkdirs() ) {
				Log.d(TAG,"Failed to create output directory");
				Log.d(TAG,savePath.getAbsolutePath());
				// request permission when it is not granted.
				Toast toast = Toast.makeText(this, "Failed to create output directory", Toast.LENGTH_LONG);
				toast.show();
				return;
			}
		}

		ProgressDialog saveDialog = new ProgressDialog(this);
		saveDialog.setMax(8);
		saveDialog.setMessage(savePath.getPath());
		saveDialog.setTitle("Saving Stereo Information");
		saveDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
		// Make it so the user can't dismiss the dialog and has to wait for it complete
		// otherwise it might be in an unstable state
		saveDialog.setCancelable(false);
		saveDialog.setOnCancelListener(dialog -> {
			buttonSave.setEnabled(true);
		});
		saveDialog.show();
		this.saveDialog = saveDialog;
	}

	private void closeSaveDialog(ProgressDialog saveDialog) {
		// If writing a file fails and crashes the app then this can be null
		if( saveDialog != null ) {
			runOnUiThread(()->saveDialog.setCancelable(true));
		}
		this.saveDialog = null;
		savePath = null;
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
			if( !isCameraCalibrated() ) {
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


	protected class DisparityProcessing extends DemoProcessingAbstract<InterleavedU8> {

		DisparityCalculation<BrightFeature> disparity;

		InterleavedU8 colorLeft = new InterleavedU8(1,1,1);
		GrayU8 gray = new GrayU8(1,1);
		GrayF32 disparityImage;
		int disparityMin, disparityRange;
		CameraPinholeBrown intrinsic;

		public DisparityProcessing() {
			super(InterleavedU8.class,3);
		}

		private StereoDisparity<?, GrayF32> createDisparity( int whichAlg ) {

			// Don't set to zero to avoid points at infinity when rending 3D
			int disparityMin = 5;
			int disparityRange = 120;

			switch( whichAlg ) {
				case 0: {
					ConfigDisparityBM config = new ConfigDisparityBM();
					config.disparityMin = disparityMin;
					config.disparityRange = disparityRange;
					config.errorType = errorBM;
					config.subpixel = true;
					Class inputType = errorBM.isCorrelation() ? GrayF32.class : GrayU8.class;
					return FactoryStereoDisparity.blockMatch(config,inputType,GrayF32.class);
				}
				case 1: {
					ConfigDisparityBMBest5 config = new ConfigDisparityBMBest5();
					config.disparityMin = disparityMin;
					config.disparityRange = disparityRange;
					config.errorType = errorBM;
					config.subpixel = true;
					Class inputType = errorBM.isCorrelation() ? GrayF32.class : GrayU8.class;
					return FactoryStereoDisparity.blockMatchBest5(config,inputType,GrayF32.class);
				}
				case 2: {
					ConfigDisparitySGM config = new ConfigDisparitySGM();
					config.disparityMin = disparityMin;
					config.disparityRange = disparityRange;
					config.errorType = errorSGM;
					config.useBlocks = true;
					config.subpixel = true;
					return FactoryStereoDisparity.sgm(config,GrayU8.class,GrayF32.class);
				}

				default:
					throw new RuntimeException("Unknown algorithm "+changeDisparityAlg);
			}
		}

		@Override
		public void initialize(int imageWidth, int imageHeight, int sensorOrientation) {
			if( !isCameraCalibrated() )
				return;

			intrinsic = lookupIntrinsics();

			DetectDescribePoint<GrayU8, BrightFeature> detDesc =
					FactoryDetectDescribe.surfFast(null,null,null,GrayU8.class);

			ScoreAssociation<BrightFeature> score = FactoryAssociation.defaultScore(BrightFeature.class);
			AssociateDescription<BrightFeature> associate =
					FactoryAssociation.greedy(score,Double.MAX_VALUE,true);

			disparity = new DisparityCalculation<>(detDesc, associate, intrinsic);
			visualize.initializeImages( imageWidth, imageHeight , ImageType.SB_U8);
			disparity.init(imageWidth,imageHeight);
		}

		@Override
		public void onDraw(Canvas canvas, Matrix imageToView) {
			// TODO show a 3D view of the disparity too. Tap to toggle to it?
			if( intrinsic == null ) {
				Paint paint = new Paint();
				paint.setColor(Color.RED);
				paint.setTextSize(40*displayMetrics.density);
				int textLength = (int)paint.measureText("Calibrate Camera First");

				canvas.drawText("Calibrate Camera First", (canvas.getWidth() - textLength) / 2, canvas.getHeight() / 2, paint);
			} else if( activeView == DView.DISPARITY ) {
				// draw rectified image
				visualize.bitmapSrc = ConvertBitmap.checkDeclare(disparity.rectifiedLeft,visualize.bitmapSrc);
				ConvertBitmap.grayToBitmap(disparity.rectifiedLeft, visualize.bitmapSrc, visualize.storage);

				if( disparity.isDisparityAvailable() ) {
					visualize.bitmapDst = ConvertBitmap.checkDeclare(disparityImage,visualize.bitmapDst);
					VisualizeImageData.disparity(disparityImage,disparityRange,0,
							visualize.bitmapDst,visualize.storage);

					visualize.render(displayView,canvas,true,true);
				}
			} else if( activeView == DView.RECTIFICATION ) {
				canvas.save();

				visualize.bitmapSrc = ConvertBitmap.checkDeclare(disparity.rectifiedLeft,visualize.bitmapSrc);
				visualize.bitmapDst = ConvertBitmap.checkDeclare(disparity.rectifiedRight,visualize.bitmapDst);
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
				visualize.bitmapSrc = ConvertBitmap.checkDeclare(visualize.graySrc,visualize.bitmapSrc);
				visualize.bitmapDst = ConvertBitmap.checkDeclare(visualize.grayDst,visualize.bitmapDst);
				ConvertBitmap.grayToBitmap(visualize.graySrc,visualize.bitmapSrc,visualize.storage);
				ConvertBitmap.grayToBitmap(visualize.grayDst,visualize.bitmapDst,visualize.storage);

				visualize.render(displayView,canvas);
			}
		}

		@Override
		public void process(InterleavedU8 color) {

			if( intrinsic == null )
				return;

			// Handle save request here since the processing thread controls all the relevant
			// data structure and can block for as long as it needs without issue
			ProgressDialog saveDialog = DisparityActivity.this.saveDialog;
			if( saveDialog != null ) {
				saveStereoToDisk(saveDialog);
				return;
			}

			ConvertImage.average(color,gray);

			int target = 0;

			// process GUI interactions
			synchronized ( lockGui ) {
				if( reset ) {
					// the user hit the reset button
					reset = false;
					if( activeView == DView.CLOUD3D ) {
						// set the 3D camera back to its original location
						runOnUiThread(() -> cloudView.getRenderer().setCameraToHome());
					} else {
						// clear results and start over
						disparityImage = null;
						visualize.setSource(null);
						visualize.setDestination(null);
						runOnUiThread(() -> {buttonSave.setEnabled(false);spinnerView.setSelection(0);});
					}
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

			// Handle the user selecting an image and compute features
			touchEventType = 0;

			boolean computedFeatures = false;
			// compute image features for left or right depending on user selection
			if( target == 1 ) {
				// save the color image for computing the point cloud
				colorLeft.setTo(color);
				setProgressMessage("Detecting Features Left", false);
				disparity.setSource(gray);
				computedFeatures = true;
			} else if( target == 2 ) {
				setProgressMessage("Detecting Features Right", false);
				disparity.setDestination(gray);
				computedFeatures = true;
			}

			// Show the selected image or a preview of what the camera is showing
			synchronized ( lockGui ) {
				if( target == 1 ) {
					visualize.setSource(gray);
				} else if( target == 2 ) {
					visualize.setDestination(gray);
				} else if( !(visualize.hasLeft && visualize.hasRight) ){
					// only show a preview if it has not selected the two images
					visualize.setPreview(gray);
				}
			}

			// Handle a request to update the disparity algorithm and recompute disparity
			int changeDisparityAlg = DisparityActivity.this.changeDisparityAlg;
			if( changeDisparityAlg != -1 ) {
				DisparityActivity.this.changeDisparityAlg = -1;
				selectedDisparityAlg = changeDisparityAlg;
				disparityImage = null;
				disparity.setDisparityAlg(createDisparity(changeDisparityAlg));
			}

			// If the two images are selected and it has a disparity algorithm compute the disparity
			if( disparity.disparityAlg != null && visualize.hasLeft && visualize.hasRight ) {
				// If computedFeatures is true here that means the user just selected the last image
				// and we can now compute the disparity
				if( computedFeatures ) {
					// rectify the images and compute the disparity
					setProgressMessage("Rectifying", false);
					boolean success = disparity.rectifyImage();
					if( success ) {
						setProgressMessage("Disparity", false);
						disparityImage = disparity.computeDisparity();
						synchronized (lockGui) {
							updateVisualizedDisparity(disparityImage);
						}
						runOnUiThread(() -> buttonSave.setEnabled(true));
					} else {
						synchronized ( lockGui ) {
							ImageMiscOps.fill(disparityImage,0);
						}
						runOnUiThread(() -> {
							buttonSave.setEnabled(false);
							Toast.makeText(DisparityActivity.this,
									"Disparity computation failed!", Toast.LENGTH_SHORT).show();
						});
					}
				} else if( changeDisparityAlg != -1 ) {
					// The user has requested that a new disparity algorithm be used to recompute
					// the disprarity
					setProgressMessage("Disparity", false);
					disparityImage = disparity.computeDisparity();
					synchronized (lockGui) {
						updateVisualizedDisparity(disparityImage);
					}
					runOnUiThread(() -> buttonSave.setEnabled(true));
				}
			}
			if( changeDisparityAlg != -1 ) {
				Log.i("DISPARITY","process(gray) exit");
			}

			hideProgressDialog();
		}

		private void updateVisualizedDisparity(GrayF32 computedDisparity) {
			disparityMin = disparity.getDisparityAlg().getDisparityMin();
			disparityRange = disparity.getDisparityAlg().getDisparityRange();
			disparityImage.setTo(computedDisparity);
			visualize.setMatches(disparity.getInliersPixel());
			visualize.forgetSelection();

			runOnUiThread(() -> {
				DMatrixRMaj rectified1 = disparity.getRectifyAlg().getRect1();
				DMatrixRMaj rectifiedK = disparity.rectifiedK;
				DMatrixRMaj rectifiedR = disparity.rectifiedR;
				Point2Transform2_F64 rectifiedToColor =
						RectifyImageOps.transformRectToPixel(intrinsic,rectified1);

				cloudView.getRenderer().getCloud().disparityToCloud(
						colorLeft, computedDisparity,
						disparityMin,disparityRange,
						rectifiedToColor,rectifiedK,rectifiedR);
				spinnerView.setSelection(2); // switch to disparity view
			});
		}

		private void saveStereoToDisk(ProgressDialog saveDialog) {
			try {
				// Save configuration. More should be added here
				PrintStream out = new PrintStream(new File(savePath, "settings.txt"));
				out.println("disparityMin "+disparityMin);
				out.println("disparityRange "+disparityRange);
				out.close();

				runOnUiThread(() -> saveDialog.setProgress(1));

				Bitmap bitmap = Bitmap.createBitmap(
						disparity.rectifiedLeft.width,
						disparity.rectifiedLeft.height,
						Bitmap.Config.ARGB_8888);
				GrowQueue_I8 storage = new GrowQueue_I8();

				ConvertBitmap.boofToBitmap(disparity.rectifiedLeft,bitmap,storage);
				bitmap.compress(Bitmap.CompressFormat.PNG, 100, new FileOutputStream(new File(savePath,"rectified_left.png")));
				runOnUiThread(() -> saveDialog.setProgress(2));

				ConvertBitmap.boofToBitmap(disparity.rectifiedRight,bitmap,storage);
				bitmap.compress(Bitmap.CompressFormat.PNG, 100, new FileOutputStream(new File(savePath,"rectified_right.png")));
				runOnUiThread(() -> saveDialog.setProgress(3));

				ConvertBitmap.boofToBitmap(disparity.rectMask,bitmap,storage);
				bitmap.compress(Bitmap.CompressFormat.PNG, 100, new FileOutputStream(new File(savePath,"rectified_mask.png")));
				runOnUiThread(() -> saveDialog.setProgress(4));

				// Save disparity as 8-bit image. This tosses out sub-pixel but there currently
				// isn't a good way to save those extra bits of resolution. Update this in
				// the future
				ConvertBitmap.boofToBitmap(disparityImage,bitmap,storage);
				bitmap.compress(Bitmap.CompressFormat.PNG, 100, new FileOutputStream(new File(savePath,"disparityU8.png")));
				runOnUiThread(() -> saveDialog.setProgress(5));

				bitmap = Bitmap.createBitmap( colorLeft.width, colorLeft.height,Bitmap.Config.ARGB_8888);
				ConvertBitmap.boofToBitmap(colorLeft,bitmap,storage);
				bitmap.compress(Bitmap.CompressFormat.PNG, 100, new FileOutputStream(new File(savePath,"color_left.png")));
				runOnUiThread(() -> saveDialog.setProgress(6));

				// free memory. Is this necessary?
				bitmap = null;
				storage = null;

				// Save stereo intrinsic and extrinsic parameters
				StereoParameters calib = new StereoParameters();
				calib.left = intrinsic;
				calib.right = intrinsic;
				calib.rightToLeft = disparity.leftToRight.invert(null);
				CalibrationIO.save(calib,new File(savePath,"stereo_calibration.yaml"));
				runOnUiThread(() -> saveDialog.setProgress(7));

				// Save the point cloud
				PointCloud3D cloud = cloudView.getRenderer().getCloud();
				int totalPoints = cloud.points.size/3;
				FileWriter writer = new FileWriter(new File(savePath, "point_cloud.ply"));
				PointCloudIO.save3DRgb(PointCloudIO.Format.PLY_ASCII,
						PointCloudReader.wrap3FRGB(cloud.points.data,cloud.colors.data,0,totalPoints),writer);
				writer.close();
				runOnUiThread(() -> saveDialog.setProgress(8));
			} catch( Exception e ) {
				e.printStackTrace(System.err);
				Log.e(TAG,e.getMessage());
				runOnUiThread(()->Toast.makeText(DisparityActivity.this,
						"Save failed! "+e.getMessage(),Toast.LENGTH_LONG).show());
			} finally {
				closeSaveDialog(saveDialog);
			}
		}
	}

	enum DView {
		ASSOCIATION,
		RECTIFICATION,
		DISPARITY,
		CLOUD3D
	}
}