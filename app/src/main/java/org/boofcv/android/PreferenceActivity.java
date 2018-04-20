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
	Spinner spinnerCamera;
	Spinner spinnerResolution;

	DemoPreference preference;
	List<CameraSpecs> specs;
	String[] cameras;

	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		setContentView(R.layout.preference_activity);

		specs = DemoMain.specs;
		preference = DemoMain.preference;

		checkSpeed = findViewById(R.id.checkbox_speed);
		checkReduce = findViewById(R.id.checkbox_reduce);
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
		setupResolutionSpinner(preference.resolution);

		checkSpeed.setOnCheckedChangeListener(this);
		checkReduce.setOnCheckedChangeListener(this);
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

		cameras = manager.getCameraIdList();
		for (int i = 0; i < cameras.length; i++ ) {
			String cameraId = cameras[i];
			CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);
			Integer facing = characteristics.get(CameraCharacteristics.LENS_FACING);

			adapter.add(CameraInformationActivity.facing(facing)+" "+cameraId);
		}

		spinnerCamera.setAdapter(adapter);
		spinnerCamera.setSelection(cameraNameToIndex(preference.cameraId));
	}

	private void setupResolutionSpinner( int selected ) {
		ArrayAdapter<CharSequence> adapter = new ArrayAdapter<CharSequence>(this, android.R.layout.simple_spinner_item);
		adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);

		CameraSpecs specs = DemoMain.defaultCameraSpecs();
		adapter.add("Automatic");
		for (int i = 0; i < specs.sizes.size(); i++ ) {
			Size s = specs.sizes.get(i);
			adapter.add(s.getWidth()+"x"+s.getHeight());
		}
		spinnerResolution.setAdapter(adapter);
		spinnerResolution.setSelection(selected);
	}

	private int cameraNameToIndex( String name ) {
		for (int i = 0; i < cameras.length; i++) {
			if( cameras[i].equals(name))
				return i;
		}
		throw new RuntimeException("Unknown camera "+name);
	}


	@Override
	public void onItemSelected(AdapterView<?> adapterView, View view, int pos, long id ) {
		if( spinnerCamera == adapterView ) {
			Log.d("PreferenceActivity","onItemSelected camera");
//			Toast.makeText(this,"spinner camera",2).show();
			preference.cameraId = cameras[pos];
			setupResolutionSpinner(preference.resolution);
		} else if( spinnerResolution == adapterView ) {
			preference.resolution = pos;
		}
		DemoMain.changedPreferences = true;
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
	}
}