package org.boofcv.android;

import android.app.Activity;
import android.content.Context;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.os.Bundle;
import android.util.Log;
import android.util.Size;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.Spinner;

import java.util.List;

import boofcv.android.camera2.CameraID;
import boofcv.android.camera2.SimpleCamera2Activity;

/**
 * Lets the user configure the camera size.
 *
 * @author Peter Abeles
 */
public class PreferenceActivity extends Activity
		implements AdapterView.OnItemSelectedListener , CompoundButton.OnCheckedChangeListener
{

	CheckBox checkSpeed;
	CheckBox checkReduce;
	CheckBox checkConcurrent;
	Spinner spinnerCamera;
	Spinner spinnerResolution;

	DemoPreference preference;
	List<CameraSpecs> specs;
	List<CameraID> cameras;

	DemoApplication app;

	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		app = (DemoApplication)getApplication();

		setContentView(R.layout.preference_activity);

		specs = app.specs;
		preference = app.preference;

		checkSpeed = findViewById(R.id.checkbox_speed);
		checkReduce = findViewById(R.id.checkbox_reduce);
		checkConcurrent = findViewById(R.id.checkbox_concurrent);
		spinnerCamera = findViewById(R.id.spinner_camera);
		spinnerResolution = findViewById(R.id.spinner_resolution);

		// finish setting up the GUI
		try {
			setupCameraSpinner();
		} catch (CameraAccessException e) {
			e.printStackTrace();
		}

		spinnerCamera.setSelection(cameraNameToIndex(preference.cameraId));
		checkSpeed.setChecked(preference.showSpeed);
		checkReduce.setChecked(preference.autoReduce);
		checkConcurrent.setChecked(preference.useConcurrent);
		setupResolutionSpinner(preference.resolution);

		// it will crash if turned on
		if( android.os.Build.VERSION.SDK_INT < 24 ) {
			checkConcurrent.setEnabled(false);
		}

		checkSpeed.setOnCheckedChangeListener(this);
		checkReduce.setOnCheckedChangeListener(this);
		checkConcurrent.setOnCheckedChangeListener(this);
		spinnerCamera.setOnItemSelectedListener(this);
		spinnerResolution.setOnItemSelectedListener(this);
	}

	private void setupCameraSpinner() throws CameraAccessException {
		CameraManager manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
		if( manager == null )
			return;
		// Find the ID of the default camera
		ArrayAdapter<CharSequence> adapter = new ArrayAdapter<CharSequence>(this, android.R.layout.simple_spinner_item);
		adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);

		cameras = SimpleCamera2Activity.getAllCameras(manager);
		for (int i = 0; i < cameras.size(); i++ ) {
			CameraID camera = cameras.get(i);
			CameraCharacteristics characteristics = manager.getCameraCharacteristics(camera.id);
			Integer facing = characteristics.get(CameraCharacteristics.LENS_FACING);

			// Name it so you can tell if it's multi-sensor camera
			String name = camera.isLogical() ? camera.id : camera.logical+":"+camera.id;

			// Also show focal length so you can figure out the type of camera
			String focal = "";
			float[] focalLengths = characteristics.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS);
			if (focalLengths != null && focalLengths.length >= 1) {
				focal = String.format("%3.1f", focalLengths[0]);
			}

			adapter.add(name+" "+CameraInformationActivity.facing(facing)+" f:"+focal);
		}

		spinnerCamera.setAdapter(adapter);
		spinnerCamera.setSelection(cameraNameToIndex(preference.cameraId));
	}

	private void setupResolutionSpinner( int selected ) {
		ArrayAdapter<CharSequence> adapter = new ArrayAdapter<CharSequence>(this, android.R.layout.simple_spinner_item);
		adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);

		CameraSpecs specs = DemoMain.defaultCameraSpecs(app);
		adapter.add("Automatic");
		for (int i = 0; i < specs.sizes.size(); i++ ) {
			Size s = specs.sizes.get(i);
			adapter.add(s.getWidth()+"x"+s.getHeight());
		}
		spinnerResolution.setAdapter(adapter);
		spinnerResolution.setSelection(selected);
	}

	private int cameraNameToIndex( String name ) {
		for (int i = 0; i < cameras.size(); i++) {
			if( cameras.get(i).id.equals(name))
				return i;
		}
		throw new RuntimeException("Unknown camera "+name);
	}


	@Override
	public void onItemSelected(AdapterView<?> adapterView, View view, int pos, long id ) {
		if( spinnerCamera == adapterView ) {
			Log.d("PreferenceActivity","onItemSelected camera");
//			Toast.makeText(this,"spinner camera",2).show();
			preference.cameraId = cameras.get(pos).id;
			setupResolutionSpinner(preference.resolution);
		} else if( spinnerResolution == adapterView ) {
			preference.resolution = pos;
		}
		app.changedPreferences = true;
	}

	@Override
	public void onNothingSelected(AdapterView<?> adapterView) {
	}

	@Override
	public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
		if( compoundButton == checkSpeed)
			preference.showSpeed = b;
		else if( compoundButton == checkReduce )
			preference.autoReduce = b;
		else if( compoundButton == checkConcurrent )
			preference.useConcurrent = b;
	}
}