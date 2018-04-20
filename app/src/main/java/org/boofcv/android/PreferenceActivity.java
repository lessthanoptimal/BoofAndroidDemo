package org.boofcv.android;

import android.app.Activity;
import android.hardware.Camera;
import android.os.Bundle;
import android.util.Log;
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

	DemoPreference preference;
	List<CameraSpecs> specs;

	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		setContentView(R.layout.preference_activity);

		specs = DemoMain.specs;
		preference = DemoMain.preference;

		checkSpeed = (CheckBox) findViewById(R.id.checkbox_speed);
		checkReduce = (CheckBox) findViewById(R.id.checkbox_reduce);
		spinnerCamera = (Spinner) findViewById(R.id.spinner_camera);

		// finish setting up the GUI
		setupCameraSpinner();

		spinnerCamera.setSelection(preference.cameraId);
		checkSpeed.setChecked(preference.showSpeed);
		checkReduce.setChecked(preference.autoReduce);

		checkSpeed.setOnCheckedChangeListener(this);
		checkReduce.setOnCheckedChangeListener(this);
		spinnerCamera.setOnItemSelectedListener(this);
	}

	private void setupCameraSpinner() {
		// Find the total number of cameras available
		int numberOfCameras = Camera.getNumberOfCameras();

		// Find the ID of the default camera
		ArrayAdapter<CharSequence> adapter = new ArrayAdapter<CharSequence>(this, android.R.layout.simple_spinner_item);
		adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);

		Camera.CameraInfo cameraInfo = new Camera.CameraInfo();
		for (int i = 0; i < numberOfCameras; i++) {
			Camera.getCameraInfo(i, cameraInfo);
			if (cameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
				adapter.add("Front "+i);
			} else if (cameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_BACK) {
				adapter.add("Back "+i);
			} else {
				adapter.add("Unknown "+i);
			}
		}

		spinnerCamera.setAdapter(adapter);
		spinnerCamera.setSelection(preference.cameraId);
	}


	@Override
	public void onItemSelected(AdapterView<?> adapterView, View view, int pos, long id ) {
		CameraSpecs camera = specs.get( preference.cameraId );

		if( spinnerCamera == adapterView ) {
			Log.d("PreferenceActivity","onItemSelected camera");
//			Toast.makeText(this,"spinner camera",2).show();

			preference.cameraId = spinnerCamera.getSelectedItemPosition();
		} else {
//			Toast.makeText(this,"spinner unknown",2).show();
			Log.d("PreferenceActivity","onItemSelected unknown");
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