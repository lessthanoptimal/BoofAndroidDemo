package org.boofcv.android;

import android.app.Activity;
import android.app.ProgressDialog;
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

	// Used to inform the user that its doing some calculations
	ProgressDialog progressDialog;
	protected final Object lockProgress = new Object();

	boolean hidePreview = true;

	public VideoDisplayActivity() {
	}

	public VideoDisplayActivity(boolean hidePreview) {
		this.hidePreview = hidePreview;
	}

	/**
	 * Changes the CV algorithm running.  Should only be called from a GUI thread.
	 */
	public void setProcessing( BoofProcessing processing ) {
		if( this.processing != null ) {
			// kill the old process
			this.processing.stopProcessing();
		}

		if (Looper.getMainLooper().getThread() != Thread.currentThread()) {
			throw new RuntimeException("Not called from a GUI thread. Bad stuff could happen");
		}

		this.processing = processing;
		// if the camera is null then it will be initialized when the camera is initialized
		if( processing != null && mCamera != null ) {
			processing.init(mDraw,mCamera);
		}
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		preference = DemoMain.preference;

		requestWindowFeature(Window.FEATURE_NO_TITLE);
		setContentView(R.layout.video);

		mDraw = new Visualization(this);

		// Create our Preview view and set it as the content of our activity.
		mPreview = new CameraPreview(this,this,hidePreview);

		FrameLayout preview = (FrameLayout) findViewById(R.id.camera_preview);

		preview.addView(mPreview);
		preview.addView(mDraw);
	}

	@Override
	protected void onResume() {
		super.onResume();

		if( mCamera != null )
			throw new RuntimeException("Bug, camera should not be initialized already");

		setUpAndConfigureCamera();
	}

	@Override
	protected void onPause() {
		super.onPause();

		hideProgressDialog();

		if( processing != null ) {
			BoofProcessing p = processing;
			processing = null;
			p.stopProcessing();
		}

		if (mCamera != null){
			mPreview.setCamera(null);
			mCamera.setPreviewCallback(null);
			mCamera.stopPreview();
			mCamera.release();
			mCamera = null;
		}
	}

	/**
	 * Sets up the camera if it is not already setup.
	 */
	private void setUpAndConfigureCamera() {
		// Open and configure the camera
		mCamera = Camera.open(preference.cameraId);

		Camera.Parameters param = mCamera.getParameters();
		Camera.Size sizePreview = param.getSupportedPreviewSizes().get(preference.preview);
		param.setPreviewSize(sizePreview.width,sizePreview.height);
		Camera.Size sizePicture = param.getSupportedPictureSizes().get(preference.picture);
		param.setPictureSize(sizePicture.width, sizePicture.height);
		mCamera.setParameters(param);

		// Create an instance of Camera
		mPreview.setCamera(mCamera);

		if( processing != null ) {
			processing.init(mDraw,mCamera);
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
			if( DemoMain.preference.showFps )
				canvas.drawText(String.format("FPS = %5.2f",meanFps), 50, 50, textPaint);
		}
	}

	/**
	 * Displays an indeterminate progress dialog.   If the dialog is already open this will change the message being
	 * displayed.  Function blocks until the dialog has been declared.
	 *
	 * @param message Text shown in dialog
	 */
	protected void setProgressMessage(final String message) {
		runOnUiThread(new Runnable() {
			public void run() {
				synchronized ( lockProgress ) {
					if( progressDialog != null ) {
						// a dialog is already open, change the message
						progressDialog.setMessage(message);
						return;
					}
					progressDialog = new ProgressDialog(VideoDisplayActivity.this);
					progressDialog.setMessage(message);
					progressDialog.setIndeterminate(true);
					progressDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
				}

				// don't show the dialog until 1 second has passed
				long showTime = System.currentTimeMillis()+1000;
				while( showTime > System.currentTimeMillis() ) {
					Thread.yield();
				}
				// if it hasn't been dismissed, show the dialog
				synchronized ( lockProgress ) {
					if( progressDialog != null )
						progressDialog.show();
				}
			}});

		// block until the GUI thread has been called
		while( progressDialog == null  ) {
			Thread.yield();
		}
	}

	/**
	 * Dismisses the progress dialog.  Can be called even if there is no progressDialog being shown.
	 */
	protected void hideProgressDialog() {
		// do nothing if the dialog is already being displayed
		synchronized ( lockProgress ) {
			if( progressDialog == null )
				return;
		}

		if (Looper.getMainLooper().getThread() == Thread.currentThread()) {
			// if inside the UI thread just dismiss the dialog and avoid a potential locking condition
			synchronized ( lockProgress ) {
				progressDialog.dismiss();
				progressDialog = null;
			}
		} else {
			runOnUiThread(new Runnable() {
				public void run() {
					synchronized ( lockProgress ) {
						progressDialog.dismiss();
						progressDialog = null;
					}
				}});

			// block until dialog has been dismissed
			while( progressDialog != null  ) {
				Thread.yield();
			}
		}
	}
}