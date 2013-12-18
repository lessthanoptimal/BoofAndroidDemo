package org.boofcv.android;

import android.app.Activity;
import android.content.Intent;
import android.hardware.Camera;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.ExpandableListView;
import android.widget.SimpleExpandableListAdapter;
import android.widget.Toast;
import boofcv.android.BoofAndroidFiles;

import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DemoMain extends Activity implements ExpandableListView.OnChildClickListener {

	// contains information on all the cameras.  less error prone and easier to deal with
	public static List<CameraSpecs> specs = new ArrayList<CameraSpecs>();
	// specifies which camera to use an image size
	public static DemoPreference preference;
	// If another activity modifies the demo preferences this needs to be set to true so that it knows to reload
	// camera parameters.
	public static boolean changedPreferences = false;

	List<Group> groups = new ArrayList<Group>();

	public DemoMain() {
		loadCameraSpecs();
	}

	/**
	 * Called when the activity is first created.
	 */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main);

		createGroups();

		ExpandableListView listView = (ExpandableListView) findViewById(R.id.DemoListView);

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
	}

	@Override
	protected void onResume() {
		super.onResume();
		if( preference == null ) {
			preference = new DemoPreference();
			setDefaultPreferences();
		} else if( changedPreferences ) {
			loadIntrinsic();
		}
	}


	private void createGroups() {
		Group ip = new Group("Image Processing");
		Group detect = new Group("Detection");
		Group assoc = new Group("Association");
		Group trackPoint = new Group("Point Tracking");
		Group trackObj = new Group("Object Tracking");
		Group sfm = new Group("Structure From Motion");

		ip.addChild("Blur",BlurDisplayActivity.class);
		ip.addChild("Gradient",GradientDisplayActivity.class);
		ip.addChild("Binary Ops",BinaryDisplayActivity.class);
		ip.addChild("Enhance",EnhanceDisplayActivity.class);
		ip.addChild("Transform",ImageTransformActivity.class);

		detect.addChild("Corner/Blob",PointDisplayActivity.class);
		detect.addChild("Scale Space",ScalePointDisplayActivity.class);
		detect.addChild("Lines",LineDisplayActivity.class);

		assoc.addChild("Two Pictures",AssociationActivity.class);

		trackPoint.addChild("KLT", KltDisplayActivity.class);
		trackPoint.addChild("Det-Desc-Assoc", DdaTrackerDisplayActivity.class);
		trackPoint.addChild("Combined", CombinedTrackerDisplayActivity.class);

		sfm.addChild("Calibration",CalibrationActivity.class);
		sfm.addChild("Stereo",DisparityActivity.class);
		sfm.addChild("Stabilization",StabilizeDisplayActivity.class);
		sfm.addChild("Mosaic",MosaicDisplayActivity.class);

		groups.add(ip);
		groups.add(detect);
		groups.add(assoc);
		groups.add(trackPoint);
		groups.add(trackObj);
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
		int numberOfCameras = Camera.getNumberOfCameras();
		for (int i = 0; i < numberOfCameras; i++) {
			CameraSpecs c = new CameraSpecs();
			specs.add(c);

			Camera.getCameraInfo(i, c.info);
			Camera camera = Camera.open(i);
			Camera.Parameters params = camera.getParameters();
			c.sizePreview.addAll(params.getSupportedPreviewSizes());
			c.sizePicture.addAll(params.getSupportedPictureSizes());
			camera.release();
		}
	}

	private void setDefaultPreferences() {
		preference.showFps = false;
		// select a front facing camera as the default
		for (int i = 0; i < specs.size(); i++) {
		    CameraSpecs c = specs.get(i);

			if( c.info.facing == Camera.CameraInfo.CAMERA_FACING_BACK ) {
				preference.cameraId = i;
				break;
			}
		}

		CameraSpecs camera = specs.get(preference.cameraId);
		preference.preview = UtilVarious.closest(camera.sizePreview,320,240);
		preference.picture = UtilVarious.closest(camera.sizePicture,640,480);

		// see if there are any intrinsic parameters to load
		loadIntrinsic();
	}

	private void loadIntrinsic() {
		preference.intrinsic = null;
		try {
			FileInputStream fos = openFileInput("cam"+preference.cameraId+".txt");
			Reader reader = new InputStreamReader(fos);
			preference.intrinsic = BoofAndroidFiles.readIntrinsic(reader);
		} catch (FileNotFoundException e) {

		} catch (IOException e) {
			Toast toast = Toast.makeText(this, "Failed to load intrinsic parameters", 2000);
			toast.show();
		}
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
}
