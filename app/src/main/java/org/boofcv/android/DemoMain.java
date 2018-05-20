package org.boofcv.android;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Size;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.ExpandableListView;
import android.widget.SimpleExpandableListAdapter;

import org.boofcv.android.assoc.AssociationActivity;
import org.boofcv.android.calib.CalibrationActivity;
import org.boofcv.android.calib.UndistortDisplayActivity;
import org.boofcv.android.detect.CannyEdgeActivity;
import org.boofcv.android.detect.ContourShapeFittingActivity;
import org.boofcv.android.detect.DetectBlackEllipseActivity;
import org.boofcv.android.detect.DetectBlackPolygonActivity;
import org.boofcv.android.detect.LineDisplayActivity;
import org.boofcv.android.detect.PointDisplayActivity;
import org.boofcv.android.detect.ScalePointDisplayActivity;
import org.boofcv.android.ip.BinaryOpsDisplayActivity;
import org.boofcv.android.ip.BlurDisplayActivity;
import org.boofcv.android.ip.EnhanceDisplayActivity;
import org.boofcv.android.ip.EquirectangularToPinholeActivity;
import org.boofcv.android.ip.GradientDisplayActivity;
import org.boofcv.android.ip.ImageTransformActivity;
import org.boofcv.android.ip.ThresholdDisplayActivity;
import org.boofcv.android.recognition.FiducialCalibrationActivity;
import org.boofcv.android.recognition.FiducialSquareBinaryActivity;
import org.boofcv.android.recognition.FiducialSquareImageActivity;
import org.boofcv.android.recognition.ImageClassificationActivity;
import org.boofcv.android.recognition.QrCodeDetectActivity;
import org.boofcv.android.segmentation.ColorHistogramSegmentationActivity;
import org.boofcv.android.segmentation.SuperpixelDisplayActivity;
import org.boofcv.android.sfm.DisparityActivity;
import org.boofcv.android.sfm.MosaicDisplayActivity;
import org.boofcv.android.sfm.StabilizeDisplayActivity;
import org.boofcv.android.tracker.KltDisplayActivity;
import org.boofcv.android.tracker.ObjectTrackerActivity;
import org.boofcv.android.tracker.StaticBackgroundMotionActivity;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import boofcv.io.calibration.CalibrationIO;
import boofcv.struct.calib.CameraPinholeRadial;

public class DemoMain extends AppCompatActivity implements ExpandableListView.OnChildClickListener {

	public static final String TAG = "DemoMain";

	List<Group> groups = new ArrayList<>();

	boolean waitingCameraPermissions = true;

	DemoApplication app;

	/**
	 * Called when the activity is first created.
	 */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main);

		app = (DemoApplication)getApplication();
		if( app == null )
			throw new RuntimeException("App is null!");

		try {
            loadCameraSpecs();
        } catch( NoClassDefFoundError e ) {
		    // Some people like trying to run this app on really old versions of android and
            // seem to enjoy crashing and reporting the errors.
		    e.printStackTrace();
            abortDialog("Camera2 API Required");
            return;
        }
		createGroups();

		ExpandableListView listView = findViewById(R.id.DemoListView);

		SimpleExpandableListAdapter expListAdapter =
				new SimpleExpandableListAdapter(
						this,
						createGroupList(),              // Creating group List.
						R.layout.group_row,             // Group item layout XML.
						new String[] { "Group Item" },  // the key of group item.
						new int[] { R.id.row_name },    // ID of each group item.-Data under the key goes into this TextView.
						createChildList(),              // childData describes second-level entries.
						R.layout.child_row,             // Layout for sub-level entries(second level).
						new String[] {"Sub Item"},      // Keys in childData maps to display.
						new int[] { R.id.grp_child}     // Data under the keys above go into these TextViews.
				);

		listView.setAdapter(expListAdapter);
		listView.setOnChildClickListener(this);

		Toolbar toolbar = findViewById(R.id.my_toolbar);
		setSupportActionBar(toolbar);
	}

	@Override
	protected void onResume() {
		super.onResume();
		if( !waitingCameraPermissions && app.changedPreferences ) {
			loadIntrinsics(this,app.preference.cameraId, app.preference.calibration,null);
		}
	}

	public void pressedWebsite( View view ) {
		Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("http://boofcv.org"));
		startActivity(browserIntent);
	}

	private void createGroups() {
		Group ip = new Group("Image Processing");
		Group segment = new Group("Segmentation");
		Group detect = new Group("Detection");
		Group assoc = new Group("Association");
		Group tracker = new Group("Tracking");
		Group calib = new Group("Calibration");
		Group sfm = new Group("Structure From Motion");
		Group recognition = new Group("Recognition");

		ip.addChild("Blur",BlurDisplayActivity.class);
		ip.addChild("Gradient",GradientDisplayActivity.class);
		ip.addChild("Binary Ops",BinaryOpsDisplayActivity.class);
		ip.addChild("Enhance",EnhanceDisplayActivity.class);
		ip.addChild("Transform",ImageTransformActivity.class);
		ip.addChild("Equirectangular",EquirectangularToPinholeActivity.class);

		segment.addChild("Superpixel",SuperpixelDisplayActivity.class);
		segment.addChild("Color Histogram",ColorHistogramSegmentationActivity.class);
		segment.addChild("Binarization",ThresholdDisplayActivity.class);

		detect.addChild("Corner/Blob",PointDisplayActivity.class);
		detect.addChild("Scale Space",ScalePointDisplayActivity.class);
		detect.addChild("Lines",LineDisplayActivity.class);
		detect.addChild("Canny Edge",CannyEdgeActivity.class);
		detect.addChild("Contour Shapes",ContourShapeFittingActivity.class);
		detect.addChild("Black Polygon",DetectBlackPolygonActivity.class);
		detect.addChild("Black Ellipse",DetectBlackEllipseActivity.class);

		assoc.addChild("Two Pictures",AssociationActivity.class);

		tracker.addChild("Object Tracking", ObjectTrackerActivity.class);
		tracker.addChild("KLT Pyramid", KltDisplayActivity.class);
		tracker.addChild("Motion Detection", StaticBackgroundMotionActivity.class);

		recognition.addChild("QR Code", QrCodeDetectActivity.class);
		recognition.addChild("Image Classification", ImageClassificationActivity.class);
		recognition.addChild("Square Binary",FiducialSquareBinaryActivity.class);
		recognition.addChild("Square Image",FiducialSquareImageActivity.class);
		recognition.addChild("Calibration Targets",FiducialCalibrationActivity.class);

		calib.addChild("Calibrate",CalibrationActivity.class);
		calib.addChild("Undistort",UndistortDisplayActivity.class);

		sfm.addChild("Stereo",DisparityActivity.class);
		sfm.addChild("Stabilization",StabilizeDisplayActivity.class);
		sfm.addChild("Mosaic",MosaicDisplayActivity.class);

		groups.add(ip);
		groups.add(segment);
		groups.add(detect);
		groups.add(assoc);
		groups.add(tracker);
		groups.add(recognition);
		groups.add(calib);
		groups.add(sfm);
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.main_menu, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		// Handle item selection
		switch (item.getItemId()) {
			case R.id.preferences: {
				Intent intent = new Intent(this, PreferenceActivity.class);
				startActivity(intent);
				return true;
			}
			case R.id.info: {
				Intent intent = new Intent(this, CameraInformationActivity.class);
				startActivity(intent);
				return true;
			}
			case R.id.about:
				Intent intent = new Intent(this, AboutActivity.class);
				startActivity(intent);
				return true;
			default:
				return super.onOptionsItemSelected(item);
		}
	}

	private void loadCameraSpecs() {
		int permissionCheck = ContextCompat.checkSelfPermission(this,
				Manifest.permission.CAMERA);

		if( permissionCheck != android.content.pm.PackageManager.PERMISSION_GRANTED) {
			ActivityCompat.requestPermissions(this,
					new String[]{Manifest.permission.CAMERA},
					0);
		} else {
			waitingCameraPermissions = false;

            CameraManager manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
            if( manager == null )
                throw new RuntimeException("No cameras?!");
            try {
                String[] cameras = manager.getCameraIdList();

                for ( String cameraId : cameras ) {
                    CameraSpecs c = new CameraSpecs();
					app.specs.add(c);
                    c.deviceId = cameraId;
                    CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);
                    Integer facing = characteristics.get(CameraCharacteristics.LENS_FACING);
                    c.facingBack = facing != null && facing==CameraCharacteristics.LENS_FACING_BACK;
                    StreamConfigurationMap map = characteristics.
                            get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                    if (map == null) {
                        continue;
                    }
                    Size[] sizes = map.getOutputSizes(ImageFormat.YUV_420_888);
                    if( sizes == null )
                        continue;
                    c.sizes.addAll(Arrays.asList(sizes));
                }
            } catch (CameraAccessException e) {
                throw new RuntimeException("No camera access??? Wasn't it just granted?");
            }

            // Now that it can read the camera set the default settings
            setDefaultPreferences();
		}
	}

	@Override
	public void onRequestPermissionsResult(int requestCode,
										   String permissions[], int[] grantResults) {
		switch (requestCode) {
			case 0: {
				// If request is cancelled, the result arrays are empty.
				if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
					loadCameraSpecs();
					setDefaultPreferences();
				} else {
					dialogNoCameraPermission();
				}
				return;
			}
		}
	}

	private void setDefaultPreferences() {
		app.preference.showSpeed = false;
		app.preference.autoReduce = true;

		// There are no cameras.  This is possible due to the hardware camera setting being set to false
		// which was a work around a bad design decision where front facing cameras wouldn't be accepted as hardware
		// which is an issue on tablets with only front facing cameras
		if( app.specs.size() == 0 ) {
			dialogNoCamera();
		}
		// select a front facing camera as the default
		for (int i = 0; i < app.specs.size(); i++) {
		    CameraSpecs c = app.specs.get(i);

            app.preference.cameraId = c.deviceId;
            if( c.facingBack) {
				break;
			}
		}

		if( !app.specs.isEmpty() ) {
			loadIntrinsics(this, app.preference.cameraId, app.preference.calibration,null);
		}
	}

	private void dialogNoCamera() {
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setMessage("Your device has no cameras!")
				.setCancelable(false)
				.setPositiveButton("OK", new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int id) {
						System.exit(0);
					}
				});
		AlertDialog alert = builder.create();
		alert.show();
	}

	private void dialogNoCameraPermission() {
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setMessage("Denied access to the camera! Exiting.")
				.setCancelable(false)
				.setPositiveButton("OK", new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int id) {
						System.exit(0);
					}
				});
		AlertDialog alert = builder.create();
		alert.show();
	}

	public static void loadIntrinsics(Activity activity,
									  String cameraId,
									  List<CameraPinholeRadial> intrinsics,
									  List<File> locations ) {
		intrinsics.clear();
		if( locations != null )
			locations.clear();

		File directory = new File(getExternalDirectory(activity),"calibration");
		if( !directory.exists() )
			return;
		File files[] = directory.listFiles();
		if( files == null )
			return;
		String prefix = "camera"+cameraId;
		for( File f : files ) {
			if( !f.getName().startsWith(prefix))
				continue;
			try {
				FileInputStream fos = new FileInputStream(f);
				Reader reader = new InputStreamReader(fos);
				CameraPinholeRadial intrinsic = CalibrationIO.load(reader);
				intrinsics.add(intrinsic);
				if( locations != null ) {
					locations.add(f);
				}
			} catch( RuntimeException | FileNotFoundException ignore ) {}
		}
	}

	public static File getExternalDirectory( Activity activity ) {
		// if possible use a public directory. If that fails use a private one
//		if(Objects.equals(Environment.getExternalStorageState(), Environment.MEDIA_MOUNTED)) {
//			File dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS);
//			if( !dir.exists() )
//				dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
//			return new File(dir,"org.boofcv.android");
//		} else {
			return activity.getExternalFilesDir(null);
//		}
	}

	/* Creating the Hashmap for the row */
	@SuppressWarnings("unchecked")
	private List<Map<String,String>> createGroupList() {
		List<Map<String,String>> result = new ArrayList<Map<String,String>>();
		for( Group g : groups ) {
			Map<String,String> m = new HashMap<String,String>();
			m.put("Group Item",g.name);
			result.add(m);
		}

		return result;
	}

	/* creatin the HashMap for the children */
	@SuppressWarnings("unchecked")
	private List<List<Map<String,String>>> createChildList() {

		List<List<Map<String,String>>> result = new ArrayList<List<Map<String,String>>>();
		for( Group g : groups ) {
			List<Map<String,String>> secList = new ArrayList<Map<String,String>>();
			for( String c : g.children ) {
				Map<String,String> child = new HashMap<String,String>();
				child.put( "Sub Item", c);
				secList.add( child );
			}
			result.add( secList );
		}

		return result;
	}
	public void  onContentChanged  () {
		System.out.println("onContentChanged");
		super.onContentChanged();
	}

	/**
	 * Switch to a different activity when the user selects a child from the menu
	 */
	public boolean onChildClick( ExpandableListView parent, View v, int groupPosition,int childPosition,long id) {

		Group g = groups.get(groupPosition);

		Class<Activity> action = g.actions.get(childPosition);
		if( action != null ) {
			Intent intent = new Intent(this, action);
			startActivity(intent);
		}

		return true;
	}


	private static class Group {
		String name;
		List<String> children = new ArrayList<String>();
		List<Class<Activity>> actions = new ArrayList<Class<Activity>>();

		private Group(String name) {
			this.name = name;
		}

		public void addChild( String name , Class action ) {
			children.add(name);
			actions.add(action);
		}
	}

	public static CameraSpecs defaultCameraSpecs( DemoApplication app ) {
		for(int i = 0; i < app.specs.size(); i++ ) {
			CameraSpecs s = app.specs.get(i);
			if( s.deviceId.equals(app.preference.cameraId))
				return s;
		}
		throw new RuntimeException("Can't find default camera");
	}

    /**
     * Displays a warning dialog and then exits the activity
     */
    private void abortDialog( String message ) {
        AlertDialog alertDialog = new AlertDialog.Builder(this).create();
        alertDialog.setTitle("Fatal error");
        alertDialog.setMessage(message);
        alertDialog.setButton(AlertDialog.BUTTON_NEUTRAL, "OK",
                (dialog, which) -> {
                    dialog.dismiss();
                    DemoMain.this.finish();
                });
        alertDialog.show();
    }
}
