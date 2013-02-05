package org.boofcv.android;

import android.app.Activity;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.hardware.Camera;
import android.os.Bundle;
import android.os.Looper;
import android.view.SurfaceView;
import android.view.Window;
import android.widget.FrameLayout;

/**
 * Activity for displaying video results.
 *
 * @author Peter Abeles
 */
public class VideoDisplayActivity extends Activity implements Camera.PreviewCallback {

	protected Camera mCamera;
	private Visualization mDraw;
	private CameraPreview mPreview;
	protected BoofProcessing processing;

	public static DemoPreference preference;

	boolean hidePreview = true;

	public VideoDisplayActivity() {
	}

	public VideoDisplayActivity(boolean hidePreview) {
		this.hidePreview = hidePreview;
	}

	/**
	 * Changes the CV algorithm running.  Should only be called from a GUI thread
	 */
	public void setProcessing(  BoofProcessing processing ) {
		if( this.processing != null ) {
			// kill the old process
			this.processing.stopProcessing();
		}

		if (Looper.getMainLooper().getThread() != Thread.currentThread()) {
			throw new RuntimeException("Not called from a GUI thread. Bad stuff could happen");
		}

		this.processing = processing;
		if( processing != null ) {
			processing.init(mDraw,mCamera);
		}
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		preference = DemoMain.preference;

		requestWindowFeature(Window.FEATURE_NO_TITLE);
		setContentView(R.layout.video);

		// Create an instance of Camera
		mCamera = Camera.open(preference.cameraId);

		Camera.Parameters param = mCamera.getParameters();
		Camera.Size sizePreview = param.getSupportedPreviewSizes().get(preference.preview);
		param.setPreviewSize(sizePreview.width,sizePreview.height);
		Camera.Size sizePicture = param.getSupportedPictureSizes().get(preference.picture);
		param.setPictureSize(sizePicture.width, sizePicture.height);
		mCamera.setParameters(param);

		mDraw = new Visualization(this);

		// Create our Preview view and set it as the content of our activity.
		mPreview = new CameraPreview(this,this,hidePreview);
		mPreview.setCamera(mCamera);


		FrameLayout preview = (FrameLayout) findViewById(R.id.camera_preview);

		preview.addView(mPreview);
		preview.addView(mDraw);
	}

	@Override
	protected void onPause() {
		super.onPause();

		if( processing != null )
			processing.stopProcessing();

		if (mCamera != null){
			mPreview.setCamera(null);
			mCamera.setPreviewCallback(null);
			mCamera.stopPreview();
			mCamera.release();
			mCamera = null;
		}
	}

	@Override
	public void onPreviewFrame(byte[] bytes, Camera camera) {
		if( processing != null )
			processing.convertPreview(bytes,camera);
	}

	/**
	 * Draws on top of the video stream for visualizing results from vision algorithms
	 */
	private class Visualization extends SurfaceView {

		private Paint textPaint = new Paint();

		double history[] = new double[10];
		int historyNum = 0;

		Activity activity;

		long previous = 0;

		public Visualization(Activity context ) {
			super(context);
			this.activity = context;

			// Create out paint to use for drawing
			textPaint.setARGB(255, 200, 0, 0);
			textPaint.setTextSize(60);
			// This call is necessary, or else the
			// draw method will not be called.
			setWillNotDraw(false);
		}

		@Override
		protected void onDraw(Canvas canvas){

			canvas.save();
			if( processing != null )
				processing.onDraw(canvas);

			// Draw how fast it is running
			long current = System.currentTimeMillis();
			long elapsed = current - previous;
			previous = current;
			history[historyNum++] = 1000.0/elapsed;
			historyNum %= history.length;

			double meanFps = 0;
			for( int i = 0; i < history.length; i++ ) {
				meanFps += history[i];
			}
			meanFps /= history.length;

			canvas.restore();
			canvas.drawText(String.format("FPS = %5.2f",meanFps), 50, 50, textPaint);
		}
	}

}