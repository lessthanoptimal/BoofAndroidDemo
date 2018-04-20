package org.boofcv.android;

import android.hardware.Camera;
import android.os.Bundle;

import boofcv.android.camera.VideoDisplayActivity;

/**
 * Activity for displaying video results.
 *
 * @author Peter Abeles
 */
public class DemoVideoDisplayActivity extends VideoDisplayActivity {

	public static DemoPreference preference;

	public DemoVideoDisplayActivity() {
	}

	public DemoVideoDisplayActivity(boolean hidePreview) {
		super(hidePreview);
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		preference = DemoMain.preference;
		setShowFPS(preference.showSpeed);
	}

	@Override
	protected Camera openConfigureCamera( Camera.CameraInfo info ) {
		Camera mCamera = Camera.open(preference.cameraId);
		Camera.getCameraInfo(preference.cameraId,info);

		Camera.Parameters param = mCamera.getParameters();
		param.setPreviewSize(320,240);
		param.setPictureSize(320,240);
		mCamera.setParameters(param);

		return mCamera;
	}
}