package org.boofcv.android.sfm;

import android.app.ProgressDialog;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.os.Bundle;
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
import org.ddogleg.struct.DogArray_I8;
import org.ejml.data.DMatrixRMaj;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;

import boofcv.abst.feature.associate.AssociateDescription;
import boofcv.abst.feature.associate.ScoreAssociation;
import boofcv.abst.feature.detdesc.DetectDescribePoint;
import boofcv.alg.cloud.PointCloudReader;
import boofcv.alg.cloud.PointCloudWriter;
import boofcv.alg.geo.RectifyImageOps;
import boofcv.alg.misc.ImageMiscOps;
import boofcv.alg.misc.ImageStatistics;
import boofcv.alg.misc.PixelMath;
import boofcv.android.ConvertBitmap;
import boofcv.android.VisualizeImageData;
import boofcv.core.image.ConvertImage;
import boofcv.factory.feature.associate.ConfigAssociateGreedy;
import boofcv.factory.feature.associate.FactoryAssociation;
import boofcv.factory.feature.detdesc.FactoryDetectDescribe;
import boofcv.io.calibration.CalibrationIO;
import boofcv.io.points.PointCloudIO;
import boofcv.struct.calib.CameraPinholeBrown;
import boofcv.struct.calib.StereoParameters;
import boofcv.struct.distort.Point2Transform2_F64;
import boofcv.struct.feature.TupleDesc_F64;
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
	public final static String STEREO_DIRECTORY = "stereo";

	Spinner spinnerView;
	Button buttonSave;
	Button buttonLoad;
	Button buttonConfigure;
	StereoDisparityDialog dialogDisparity = new StereoDisparityDialog();

	// indicate where the user touched the screen
	volatile int touchEventType = 0;
	volatile int touchX;
	volatile int touchY;
	volatile boolean reset = false;
	volatile boolean disparityChanged = false;

	private GestureDetector mDetector;

	DView activeView = DView.ASSOCIATION;

	// Used to display the found disparity as a 3D point cloud
	PointCloudSurfaceView cloudView;
	ViewGroup layoutViews;

	// Shows status of saving to disk. If dialog is not null then that is also a request to save
	ProgressDialog saveDialog;
	File savePath;
	// Path where of saved data that should be loaded. If not null then it will be loaded
	// in the next cycle
	File loadPath=null;

	public DisparityActivity() {
		super(Resolution.R640x480);
		super.bitmapMode = BitmapMode.NONE;

		// Select a reasonable default and when the user reconfigures disparity, recompute it
		dialogDisparity.selectedType = StereoDisparityDialog.DisparityType.BLOCK5;
		dialogDisparity.listenerOK = ()->disparityChanged=true;
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		LayoutInflater inflater = getLayoutInflater();
		LinearLayout controls = (LinearLayout)inflater.inflate(R.layout.disparity_controls,null);

		spinnerView = controls.findViewById(R.id.spinner_view);
		buttonConfigure = controls.findViewById(R.id.button_configure);
		buttonSave = controls.findViewById(R.id.button_save);
		buttonLoad = controls.findViewById(R.id.button_load);

		// disable save button until disparity has been computed
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

		setControls(controls);

		mDetector = new GestureDetector(this, new MyGestureDetector(displayView));
		displayView.setOnTouchListener((v, event) -> {
			mDetector.onTouchEvent(event);
			return true;
		});

		cloudView = new PointCloudSurfaceView(this);
	}

	@Override
	protected void startCamera( ViewGroup layout, TextureView view ) {
		super.startCamera(layout,view);
		this.layoutViews = layout;
	}

	private void showCloud3D( boolean visible ) {
		if( visible ) {
			layoutViews.addView(cloudView,layoutViews.getChildCount(),
					new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
		} else {
			layoutViews.removeView(cloudView);
		}
	}

	@Override
	protected void onResume() {
		super.onResume();
		changeView(DView.ASSOCIATION,false);
	}

	@Override
	public void createNewProcessor() {
		setProcessing(new DisparityProcessing());
	}

	@Override
	public void onItemSelected(AdapterView<?> adapterView, View view, int pos, long id ) {
		if( adapterView == spinnerView ) {
			if (pos == 0) {
				activeView = DView.ASSOCIATION;
			} else if (pos == 1) {
				touchY = -1;
				activeView = DView.RECTIFICATION;
			} else if (pos == 2) {
				activeView = DView.DISPARITY;
			} else {
				activeView = DView.CLOUD3D;
			}
			showCloud3D(activeView == DView.CLOUD3D);
		}
	}

	@Override
	public void onNothingSelected(AdapterView<?> adapterView) {}

	public void resetPressed( View view ) {
		reset = true;
	}

	public void configurePressed( View view ) {
		// TODO After the BoofCV Activity has been updated switch this over to AndroidX API
		dialogDisparity.show(getFragmentManager(), "Disparity Dialog");
	}

	/**
	 * Opens a dialog showing the status of saving to disk and creates a request to save to disk.
	 */
	public void savePressed( View view ) {
		buttonSave.setEnabled(false);

		if( saveDialog != null ) {
			Log.e(TAG,"Save pressed while save was already in progress!");
			return;
		}

		savePath = new File(getExternalDirectory(this),STEREO_DIRECTORY+"/"+System.currentTimeMillis());
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

	public void loadPressed( View view ) {
		selectDirectoryDialog(STEREO_DIRECTORY,(selected)->this.loadPath=selected);
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

		@Override
		public boolean onFling( MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
		    // don't do anything on fling. It's too easy to accidentially discard when tapping
            // on points
			return false;
		}

		@Override
		public boolean onDoubleTapEvent(MotionEvent e)
		{
			if( activeView != DView.ASSOCIATION ) {
				return false;
			}

			touchEventType = 2;
			return true;
		}
	}

	private void changeView( DView view , boolean saveEnabled ) {
//        Log.i(TAG,"changeView() to "+view);

        // immediately change the selected void to avoid flickering
		this.activeView = view;
		runOnUiThread(() -> {buttonSave.setEnabled(saveEnabled);spinnerView.setSelection(view.ordinal());});
	}

	protected class DisparityProcessing extends DemoProcessingAbstract<InterleavedU8> {

		DisparityCalculation<TupleDesc_F64> disparity;
        AssociationVisualize<GrayU8> visualize;

		InterleavedU8 colorLeft = new InterleavedU8(1,1,1);
		GrayU8 gray = new GrayU8(1,1);

		CameraPinholeBrown intrinsic;

		//------------- Owned by lockGui
		final GrayF32 disparityImage = new GrayF32(1,1);

		public DisparityProcessing() {
			super(InterleavedU8.class,3);
//			Log.i(TAG,"NEW DisparityProcessing() hash "+hashCode());
		}

		@Override
		public void initialize(int imageWidth, int imageHeight, int sensorOrientation) {
//            Log.i(TAG,"ENTER DisparityProcessing.initialize() hash = "+hashCode());
			if( !isCameraCalibrated() )
				return;

			intrinsic = lookupIntrinsics();

			DetectDescribePoint<GrayU8, TupleDesc_F64> detDesc =
					FactoryDetectDescribe.surfStable(null,null,null,GrayU8.class);

			ConfigAssociateGreedy configAssoc = new ConfigAssociateGreedy();
			configAssoc.scoreRatioThreshold = 0.75;
			configAssoc.forwardsBackwards = true;

			ScoreAssociation<TupleDesc_F64> score = FactoryAssociation.defaultScore(TupleDesc_F64.class);
			AssociateDescription<TupleDesc_F64> associate = FactoryAssociation.greedy(configAssoc,score);

			disparityChanged = true;
			disparity = new DisparityCalculation<>(detDesc, associate, intrinsic);
            disparity.init(imageWidth,imageHeight);

            visualize = new AssociationVisualize<>(DisparityActivity.this);
            visualize.initializeImages( imageWidth, imageHeight , ImageType.SB_U8);
		}

		@Override
		public void onDraw(Canvas canvas, Matrix imageToView) {
//		    Log.i(TAG, "onDraw() view = "+activeView+" hash "+hashCode());

			if( intrinsic == null ) {
				Paint paint = new Paint();
				paint.setColor(Color.RED);
				paint.setTextSize(40*displayMetrics.density);
				int textLength = (int)paint.measureText("Calibrate Camera First");

				canvas.drawText("Calibrate Camera First", (canvas.getWidth() - textLength) / 2, canvas.getHeight() / 2, paint);
				return;
			}

			synchronized (lockGui) {
				if (activeView == DView.DISPARITY) {
					if (disparity.isDisparityAvailable()) {
						visualize.render(displayView, canvas, true, true);
					}
				} else if (activeView == DView.RECTIFICATION) {
					canvas.save();
					visualize.render(displayView, canvas, true, true);

					if (touchY >= 0) {
						canvas.restore();
						Paint paint = new Paint();
						paint.setColor(Color.RED);
						paint.setStyle(Paint.Style.STROKE);
						paint.setStrokeWidth(4 * displayMetrics.density);
						canvas.drawLine(0, touchY, canvas.getWidth(), touchY, paint);
					}
				} else if (activeView == DView.ASSOCIATION) {
					visualize.render(displayView, canvas);
				}
			}
		}

		@Override
		public void process(InterleavedU8 color) {
//            Log.i(TAG,"ENTER process(color) hash "+hashCode());
			if( intrinsic == null )
				return;

			// Handle save request here since the processing thread controls all the relevant
			// data structure and can block for as long as it needs without issue
			if (checkIfThenSave()) return;
			if (checkIfThenLoad()) return;

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
						visualize.setSource(null);
						visualize.setDestination(null);
						changeView(DView.ASSOCIATION,false);
					}
				}
				if( touchEventType == 1 ) {
					// first see if there are any features to select
					if( !visualize.setTouch(touchX,touchY) ) {
						// if not then it must be a capture image request
						target = touchX < viewWidth/2 ? 1 : 2;
					}
				} else if( touchEventType == 2 ) {
					visualize.forgetSelection();
				}
				touchEventType = 0;
			}

			// Handle the user selecting an image and compute features

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
			if( activeView == DView.ASSOCIATION ) {
				synchronized (lockGui) {
//                Log.i(TAG,"target = "+target+" left "+visualize.hasLeft+" right "+visualize.hasRight+" hash "+hashCode());
					if (target == 1) {
						visualize.setSource(gray);
					} else if (target == 2) {
						visualize.setDestination(gray);
					} else if (!(visualize.hasLeft && visualize.hasRight)) {
						// only show a preview if it has not selected the two images
						visualize.setPreview(gray);
					}
				}
			}

			// Handle a request to update the disparity algorithm and recompute disparity
			boolean disparityChangedThisFrame = disparityChanged;
			if (disparityChanged) {
				disparityChanged = false;
				disparity.setDisparityAlg(dialogDisparity.createDisparity());
				disparity.filterDisparity =
						dialogDisparity.filterType==StereoDisparityDialog.FilterType.SPECKLE;
			}

			// If the two images are selected and it has a disparity algorithm compute the disparity
			if( disparity.disparityAlg != null && visualize.hasLeft && visualize.hasRight ) {
				// If computedFeatures is true here that means the user just selected the last image
				// and we can now compute the disparity
				if( computedFeatures ) {
//                    Log.i(TAG,"recomputing disparity starting at rectification");

                    // rectify the images and compute the disparity
					setProgressMessage("Rectifying", false);
					boolean success = disparity.rectifyImage();
					if( success ) {
						setProgressMessage("Disparity", false);
						disparity.computeDisparity();
						synchronized (lockGui) {
							disparityImage.setTo(disparity.getDisparity());
							updateVisualizedDisparity(false);
						}
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
				} else if (disparityChangedThisFrame) {
//                    Log.i(TAG,"recomputing disparity");

                    // The user has requested that a new disparity algorithm be used to recompute
					// the disparity
					setProgressMessage("Disparity", false);
					disparity.computeDisparity();
					synchronized (lockGui) {
						disparityImage.setTo(disparity.getDisparity());
						updateVisualizedDisparity(true);
					}
					runOnUiThread(() -> buttonSave.setEnabled(true));
				}
			}

			hideProgressDialog();

			synchronized (lockGui) {
				updateVisualizationImages();
			}

//            Log.i(TAG,"EXIT process(color)");
        }

		private boolean checkIfThenSave() {
			ProgressDialog saveDialog = DisparityActivity.this.saveDialog;
			if (saveDialog==null)
				return false;

			try {
				saveStereoToDisk(saveDialog);
			} catch( Exception e ) {
				e.printStackTrace(System.err);
				Log.e(TAG,e.getMessage());
				runOnUiThread(()-> Toast.makeText(DisparityActivity.this,
						"Save failed! "+e.getMessage(),Toast.LENGTH_LONG).show());
			} finally {
				closeSaveDialog(saveDialog);
			}
			return true;
		}

		private boolean checkIfThenLoad() {
			if (loadPath==null)
				return false;

			try {
				loadStereoData(loadPath);
			} catch( Exception e ) {
				e.printStackTrace(System.err);
				Log.e(TAG,e.getMessage());
				runOnUiThread(()-> Toast.makeText(DisparityActivity.this,
						"Load failed! "+e.getMessage(),Toast.LENGTH_LONG).show());
			} finally {
				loadPath = null;
			}
			return true;
		}

		/**
		 * Updates the bitmap images that are rendered as a pair in several viewing modes.
		 *
		 * NOTE: This is only called when lockGui is locked
		 */
		void updateVisualizationImages() {
			int disparityRange = dialogDisparity.disparityRange;
			switch( activeView ) {
				case DISPARITY:
					visualize.bitmapSrc = ConvertBitmap.checkDeclare(disparity.rectifiedLeft,visualize.bitmapSrc);
					ConvertBitmap.grayToBitmap(disparity.rectifiedLeft, visualize.bitmapSrc, visualize.storage);

					if( disparityImage != null ) {
						visualize.bitmapDst = ConvertBitmap.checkDeclare(disparityImage,visualize.bitmapDst);
						VisualizeImageData.disparity(disparityImage,disparityRange,0,
								visualize.bitmapDst,visualize.storage);
					}
					break;

				case RECTIFICATION:
					visualize.bitmapSrc = ConvertBitmap.checkDeclare(disparity.rectifiedLeft, visualize.bitmapSrc);
					visualize.bitmapDst = ConvertBitmap.checkDeclare(disparity.rectifiedRight, visualize.bitmapDst);
					ConvertBitmap.grayToBitmap(disparity.rectifiedLeft, visualize.bitmapSrc, visualize.storage);
					ConvertBitmap.grayToBitmap(disparity.rectifiedRight, visualize.bitmapDst, visualize.storage);
					break;

				case ASSOCIATION:
					visualize.bitmapSrc = ConvertBitmap.checkDeclare(visualize.graySrc, visualize.bitmapSrc);
					visualize.bitmapDst = ConvertBitmap.checkDeclare(visualize.grayDst, visualize.bitmapDst);
					ConvertBitmap.grayToBitmap(visualize.graySrc, visualize.bitmapSrc, visualize.storage);
					ConvertBitmap.grayToBitmap(visualize.grayDst, visualize.bitmapDst, visualize.storage);
					break;
			}
		}

		private void updateVisualizedDisparity( boolean changedDisparity ) {
			int disparityMin = disparity.getDisparityAlg().getDisparityMin();
			int disparityRange = disparity.getDisparityAlg().getDisparityRange();
			visualize.setMatches(disparity.getInliersPixel());
			visualize.forgetSelection();

			InterleavedU8 colorLeft = this.colorLeft;
			GrayF32 disparityImage = this.disparityImage;

			runOnUiThread(() -> {
				DMatrixRMaj rectified1 = disparity.getRectifyAlg().getUndistToRectPixels1();
				DMatrixRMaj rectifiedK = disparity.rectifiedK;
				DMatrixRMaj rectifiedR = disparity.rectifiedR;
				Point2Transform2_F64 rectifiedToColor =
						RectifyImageOps.transformRectToPixel(intrinsic,rectified1);

				cloudView.getRenderer().getCloud().disparityToCloud(
						colorLeft, disparityImage,
						disparityMin,disparityRange,
						rectifiedToColor,rectifiedK,rectifiedR);
				if( !changedDisparity )
					changeView(DView.DISPARITY,true);
			});
		}

		private void saveStereoToDisk( ProgressDialog saveDialog ) throws IOException {
			// Save configuration. More should be added here
			PrintStream out = new PrintStream(new File(savePath, "settings.txt"));
			out.println("Algorithm "+dialogDisparity.selectedType.name());
			out.println("disparityMin "+dialogDisparity.disparityMin);
			out.println("disparityRange "+dialogDisparity.disparityRange);
			out.close();

			runOnUiThread(() -> saveDialog.setProgress(1));

			Bitmap bitmap = Bitmap.createBitmap(
					disparity.rectifiedLeft.width,
					disparity.rectifiedLeft.height,
					Bitmap.Config.ARGB_8888);
			DogArray_I8 storage = new DogArray_I8();

			ConvertBitmap.boofToBitmap(disparity.rectifiedLeft,bitmap,storage);
			bitmap.compress(Bitmap.CompressFormat.PNG, 100, new FileOutputStream(new File(savePath,"rectified_left.png")));
			runOnUiThread(() -> saveDialog.setProgress(2));

			ConvertBitmap.boofToBitmap(disparity.rectifiedRight,bitmap,storage);
			bitmap.compress(Bitmap.CompressFormat.PNG, 100, new FileOutputStream(new File(savePath,"rectified_right.png")));
			runOnUiThread(() -> saveDialog.setProgress(3));

			VisualizeImageData.binaryToBitmap(disparity.rectMask,false,bitmap,storage);
			bitmap.compress(Bitmap.CompressFormat.PNG, 100, new FileOutputStream(new File(savePath,"rectified_mask.png")));
			runOnUiThread(() -> saveDialog.setProgress(4));

			// Save disparity as 8-bit image. This tosses out sub-pixel but there currently
			// isn't a good way to save those extra bits of resolution. Update this in
			// the future
			synchronized (lockGui) {
				ConvertBitmap.boofToBitmap(disparityImage, bitmap, storage);
			}
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
			calib.right_to_left = disparity.leftToRight.invert(null);
			CalibrationIO.save(calib,new File(savePath,"stereo_calibration.yaml"));
			runOnUiThread(() -> saveDialog.setProgress(7));

			// Save the point cloud
			PointCloud3D cloud = cloudView.getRenderer().getCloud();
			int totalPoints = cloud.points.size/3;
			OutputStream output = new FileOutputStream(new File(savePath, "point_cloud.ply"));
			PointCloudIO.save3D(PointCloudIO.Format.PLY,
					PointCloudReader.wrap3FRGB(cloud.points.data,cloud.colors.data,0,totalPoints),
					true,output);
			output.close();
			runOnUiThread(() -> saveDialog.setProgress(8));
		}

		void loadStereoData( File directory ) throws IOException {
			Log.i(TAG,"Loading "+directory.getPath());
			DogArray_I8 storage = new DogArray_I8();
			ConvertBitmap.bitmapToBoof(BitmapFactory.decodeFile(
					new File(directory,"rectified_left.png").getPath()),disparity.rectifiedLeft,storage);
			ConvertBitmap.bitmapToBoof(BitmapFactory.decodeFile(
					new File(directory,"rectified_right.png").getPath()),disparity.rectifiedRight,storage);
			ConvertBitmap.bitmapToBoof(BitmapFactory.decodeFile(
					new File(directory,"rectified_mask.png").getPath()),disparity.rectMask,storage);
			// convert it into a binary image with values 0 and 1
			PixelMath.lambda1(disparity.rectMask,v-> (v==0)?(byte)0:(byte)1,disparity.rectMask);
			Log.i(TAG,"  Loading color_left");
			ConvertBitmap.bitmapToBoof(BitmapFactory.decodeFile(
					new File(directory,"color_left.png").getPath()),colorLeft,storage);
			synchronized (lockGui) {
				// precision has been lost but it should look the same
				ConvertBitmap.bitmapToBoof(BitmapFactory.decodeFile(
						new File(directory, "disparityU8.png").getPath()), disparityImage, storage);
			}

			Log.i(TAG,"  ma vlaue "+ ImageStatistics.max(disparityImage)+" width "+disparityImage.width);

			Log.i(TAG,"  Loading stereo_calibration");
			StereoParameters calib = CalibrationIO.load(new File(directory,"stereo_calibration.yaml"));
			intrinsic.setTo(calib.left);
			calib.right_to_left.invert(disparity.leftToRight);

			// load the cloud
			Log.i(TAG,"  Loading point cloud");
			PointCloud3D cloud = cloudView.getRenderer().getCloud();
			PointCloudIO.load(PointCloudIO.Format.PLY,
					new FileInputStream(new File(directory, "point_cloud.ply")),
					new PointCloudWriter() {
						int total = 0;
						@Override
						public void init(int estimatedSize) {
							cloud.declarePoints(estimatedSize);
						}

						@Override
						public void add(double x, double y, double z) {
							cloud.setPoint(total++,x,y,z,0xFFFF0000);
						}

						@Override
						public void add(double x, double y, double z, int rgb) {
							cloud.setPoint(total++,x,y,z,rgb);
						}
					}
			);
			// Finish rendering the cloud and switch to the 3D view
			runOnUiThread(()->{
				// tell the point cloud that it can be displayed
				cloud.finalizePoints();
				// change the view to 3D cloud and disable the save button
				changeView(DView.CLOUD3D,false);
			});
		}
	}

	enum DView {
		ASSOCIATION,
		RECTIFICATION,
		DISPARITY,
		CLOUD3D
	}
}