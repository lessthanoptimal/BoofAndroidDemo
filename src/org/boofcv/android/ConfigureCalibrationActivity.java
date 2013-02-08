package org.boofcv.android;

import android.app.Activity;
import android.content.Intent;
import android.hardware.Camera;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Spinner;

import java.util.List;

/**
 * @author Peter Abeles
 */
public class ConfigureCalibrationActivity extends Activity
		implements AdapterView.OnItemSelectedListener {

	Spinner spinnerCamera;
	Spinner spinnerTarget;
	EditText textRows;
	EditText textCols;

	List<CameraSpecs> specs;

	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		specs = DemoMain.specs;

		setContentView(R.layout.select_calibration);

		spinnerCamera = (Spinner) findViewById(R.id.spinner_camera);
		spinnerTarget = (Spinner) findViewById(R.id.spinner_type);
		textRows = (EditText) findViewById(R.id.text_rows);
		textCols = (EditText) findViewById(R.id.text_cols);

		textRows.setText(""+CalibrationActivity.numRows);
		textCols.setText(""+CalibrationActivity.numCols);

		setupCameraSpinner();
		setupTargetSpinner();

		spinnerCamera.setOnItemSelectedListener(this);
		spinnerTarget.setOnItemSelectedListener(this);
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
	}

	private void setupTargetSpinner() {
		ArrayAdapter<CharSequence> adapter = new ArrayAdapter<CharSequence>(this, android.R.layout.simple_spinner_item);
		adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		adapter.add("Chessboard");
		adapter.add("Square Grid");

		spinnerTarget.setAdapter(adapter);
	}

	@Override
	public void onItemSelected(AdapterView<?> adapterView, View view, int pos, long id ) {
		//To change body of implemented methods use File | Settings | File Templates.
	}

	@Override
	public void onNothingSelected(AdapterView<?> adapterView) {}

	public void pressedOK( View v ) {
		CalibrationActivity.numCols = Integer.parseInt(textCols.getText().toString());
		CalibrationActivity.numRows = Integer.parseInt(textRows.getText().toString());

		Intent intent = new Intent(this, CalibrationActivity.class);
		startActivity(intent);
	}


}