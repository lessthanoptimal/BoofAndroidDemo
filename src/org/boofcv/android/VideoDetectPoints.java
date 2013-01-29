package org.boofcv.android;

import android.app.Activity;
import android.hardware.Camera;
import android.os.Bundle;
import android.util.Log;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.FrameLayout;

/**
 * @author Peter Abeles
 */
public class VideoDetectPoints extends Activity {
	private final String TAG = "VideoDetectPoints";

	private Camera mCamera;
	private DrawBinary mDraw;
	private CameraPreview2 mPreview;
	BoofImageProcessing processing;


	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		Log.d(TAG, "onCreate ENTER");

		requestWindowFeature(Window.FEATURE_NO_TITLE);
		setContentView(R.layout.video);

		// Create an instance of Camera
		mCamera = UtilVarious.getCameraInstance();

		Camera.Parameters param = mCamera.getParameters();
		Camera.Size sizePreview = UtilVarious.closest(param.getSupportedPreviewSizes(),320,240);
		param.setPreviewSize(sizePreview.width,sizePreview.height);
		Camera.Size sizePicture = UtilVarious.closest(param.getSupportedPictureSizes(),640,480);
		param.setPictureSize(sizePicture.width, sizePicture.height);
		mCamera.setParameters(param);

		processing = new BoofImageProcessing(mCamera);
		mDraw = new DrawBinary(this,processing);
		processing.init(mDraw);

		// Create our Preview view and set it as the content of our activity.
		mPreview = new CameraPreview2(this,mDraw);
		mPreview.setCamera(mCamera);
		FrameLayout preview = (FrameLayout) findViewById(R.id.camera_preview);

		preview.addView(mPreview);
		preview.addView(mDraw);

		Log.d(TAG, "onCreate EXIT");
	}

	@Override
	protected void onPause() {
		super.onPause();
		Log.d(TAG, "onPause ENTER");

		processing.stopProcessing();

		Log.d(TAG, "onPause");
		if (mCamera != null){
			mPreview.setCamera(null);
			mCamera.setPreviewCallback(null);
			mCamera.stopPreview();
			mCamera.release();
			mCamera = null;
		}

		Log.d(TAG, "onPause EXIT");
	}


}