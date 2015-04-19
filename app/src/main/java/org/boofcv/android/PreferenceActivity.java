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

	CheckBox checkFPS;
	Spinner spinnerCamera;
	Spinner spinnerVideo;
	Spinner spinnerPicture;

	// size the user selected.  when cameras change it tries to select a similar size
	Camera.Size prefVideoSize;
	Camera.Size prefPictureSize;

	DemoPreference preference;
	List<CameraSpecs> specs;

	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		setContentView(R.layout.preference_activity);

		specs = DemoMain.specs;
		preference = DemoMain.preference;

		checkFPS = (CheckBox) findViewById(R.id.checkbox_FPS);
		spinnerCamera = (Spinner) findViewById(R.id.spinner_camera);
		spinnerVideo = (Spinner) findViewById(R.id.spinner_video_size);
		spinnerPicture = (Spinner) findViewById(R.id.spinner_picture_size);

		// remember the size the user selected
		CameraSpecs camera = specs.get( preference.cameraId );
		prefVideoSize = camera.sizePreview.get( preference.preview );
		prefPictureSize = camera.sizePicture.get( preference.picture );

		ArrayAdapter<CharSequence> adapter = new ArrayAdapter<CharSequence>(this, android.R.layout.simple_spinner_item);
		adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		spinnerVideo.setAdapter(adapter);
		adapter = new ArrayAdapter<CharSequence>(this, android.R.layout.simple_spinner_item);
		adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		spinnerPicture.setAdapter(adapter);

		// finish setting up the GUI
		setupCameraSpinner();
		setupSizeSpinners();

		spinnerCamera.setSelection(preference.cameraId);
		checkFPS.setChecked(preference.showFps);

		checkFPS.setOnCheckedChangeListener(this);
		spinnerCamera.setOnItemSelectedListener(this);
		spinnerVideo.setOnItemSelectedListener(this);
		spinnerPicture.setOnItemSelectedListener(this);
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

	private void setupSizeSpinners() {
		CameraSpecs camera = specs.get(preference.cameraId);

		ArrayAdapter<CharSequence> adapterVideo = (ArrayAdapter<CharSequence>)spinnerVideo.getAdapter();
		ArrayAdapter<CharSequence> adapterPicture = (ArrayAdapter<CharSequence>)spinnerPicture.getAdapter();

		addAll(camera.sizePreview,adapterVideo);
		addAll(camera.sizePicture,adapterPicture);

		// select a similar size to what it had selected before
		DemoPreference preference = DemoMain.preference;
		preference.preview = UtilVarious.closest(camera.sizePreview, prefVideoSize.width, prefVideoSize.height);
		preference.picture = UtilVarious.closest(camera.sizePicture, prefPictureSize.width, prefPictureSize.height);

		spinnerVideo.setSelection(preference.preview);
		spinnerPicture.setSelection(preference.picture );

		spinnerVideo.invalidate();
		spinnerPicture.invalidate();
	}

	private void addAll( List<Camera.Size> sizes , ArrayAdapter<CharSequence> adapter ) {

		adapter.clear();
		for( Camera.Size s : sizes ) {
			adapter.add(s.width+" x "+s.height);
		}
	}

	@Override
	public void onItemSelected(AdapterView<?> adapterView, View view, int pos, long id ) {
		CameraSpecs camera = specs.get( preference.cameraId );

		if( spinnerCamera == adapterView ) {
			Log.d("PreferenceActivity","onItemSelected camera");
//			Toast.makeText(this,"spinner camera",2).show();

			int selected = spinnerCamera.getSelectedItemPosition();
			if( selected != preference.cameraId ) {
				preference.cameraId = selected;
				setupSizeSpinners();
			}
		} else if( spinnerVideo == adapterView ) {
//			Toast.makeText(this,"spinner video",2).show();
			Log.d("PreferenceActivity","onItemSelected video");

			int selected = spinnerVideo.getSelectedItemPosition();
			if( selected != preference.preview ) {
				preference.preview = selected;
				prefVideoSize = camera.sizePreview.get(preference.preview);
			}
		} else if( spinnerPicture == adapterView ) {
//			Toast.makeText(this,"spinner picture",2).show();
			Log.d("PreferenceActivity","onItemSelected picture");
			int selected = spinnerPicture.getSelectedItemPosition();
			if( selected != preference.picture ) {
				preference.picture = selected;
				prefPictureSize = camera.sizePicture.get(preference.picture);
			}
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
		preference.showFps = b;
	}
}