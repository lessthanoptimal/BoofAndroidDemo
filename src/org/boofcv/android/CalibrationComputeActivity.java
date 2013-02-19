package org.boofcv.android;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.hardware.Camera;
import android.os.Bundle;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import boofcv.abst.calib.CalibrateMonoPlanar;
import boofcv.abst.calib.ImageResults;
import boofcv.alg.geo.calibration.CalibrationPlanarGridZhang99;
import boofcv.alg.geo.calibration.PlanarCalibrationTarget;
import boofcv.alg.geo.calibration.Zhang99Parameters;
import boofcv.android.BoofAndroidFiles;
import boofcv.struct.calib.IntrinsicParameters;
import georegression.struct.point.Point2D_F64;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;

/**
 * After images have been collected in the {@link CalibrationActivity}, this activity is brought up which computes
 * the calibration parameters and shows the user its progress.  After the parameters have been computed the user
 * then has the option to save or discard the results.
 *
 * @author Peter Abeles
 */
public class CalibrationComputeActivity extends Activity {

	// image information which is to be processed
	public static List<CalibrationImageInfo> images;
	public static PlanarCalibrationTarget target;
	public static IntrinsicParameters intrinsic;

	TextView text;
	Button buttonOK;
	Button buttonCancel;

	CalibrationPlanarGridZhang99 calibrationAlg;

	CalibrationThread thread;
	private volatile boolean threadRunning = false;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		setContentView(R.layout.calibration_compute_view);

		text = (TextView) findViewById(R.id.text_info);
		text.setMovementMethod(new ScrollingMovementMethod());

		buttonCancel = (Button) findViewById(R.id.button_discard);
		buttonOK = (Button) findViewById(R.id.button_accept);

		// start a new process
		calibrationAlg = new CalibrationPlanarGridZhang99(target,true,2);
		intrinsic = null;
		threadRunning = true;
		new CalibrationThread().start();
	}

	@Override
	public void onConfigurationChanged(Configuration newConfig) {
		super.onConfigurationChanged(newConfig);
	}

	@Override
	protected void onStop() {
		super.onStop();

		stopThread();
		calibrationAlg = null;
	}

	public void pressedDiscard( View v ) {
		intrinsic = null;
		finish();
	}

	public void pressedAccept( View v ) {
		// save the found parameters to a file
		String name = "cam"+DemoMain.preference.cameraId+".txt";
		try {
			// save to disk
			FileOutputStream fos = openFileOutput(name, Context.MODE_PRIVATE);
			Writer writer = new OutputStreamWriter(fos);
			BoofAndroidFiles.write(intrinsic,writer);
			fos.close();

			// let it know that it needs to reload intrinsic parameters
			DemoMain.changedPreferences = true;

			// switch back to the main menu
			Intent intent = new Intent(this, DemoMain.class);
			// make it so the back button won't take it back here
			intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
			startActivity(intent);
		} catch (IOException e) {
			Log.d("calibration","Saving intrinsic failed");
			Toast toast = Toast.makeText(this, "IOException when saving intrinsic!", 2000);
			toast.show();
		}
	}

	public void stopThread() {
		if( thread != null ) {
			thread.requestStop = true;
			while( threadRunning ) {
				Thread.yield();
			}
			thread = null;
		}
	}

	private void write( final String message ) {
		runOnUiThread(new Runnable() {
			public void run() {
				text.setText(text.getText() + message + "\n");
			}
		});
	}

	private void clearText() {
		runOnUiThread(new Runnable() {
			public void run() {
				text.setText("");
			}
		});
	}

	private class CalibrationThread extends Thread implements CalibrationPlanarGridZhang99.Listener
	{
		public volatile boolean requestStop = false;

		@Override
		public void run() {
			write("Processing images.  Could take a bit.");
			List<List<Point2D_F64>> points = new ArrayList<List<Point2D_F64>>();
			for( CalibrationImageInfo c : images ) {
				points.add( c.calibPoints );
			}
			calibrationAlg.setListener(this);
			calibrationAlg.process(points);

			try {
				Zhang99Parameters zhangParam = calibrationAlg.getOptimized();

				// need to open the camera to get its size
				Camera mCamera = Camera.open(DemoMain.preference.cameraId);
				Camera.Parameters param = mCamera.getParameters();
				Camera.Size sizePreview = param.getSupportedPreviewSizes().get(DemoMain.preference.preview);

				intrinsic = zhangParam.convertToIntrinsic();
				intrinsic.flipY = false;
				intrinsic.width = sizePreview.width;
				intrinsic.height = sizePreview.height;

				mCamera.release();

				clearText();
				write("Intrinsic Parameters");
				write(String.format("fx = %6.2f fy = %6.2f",intrinsic.fx,intrinsic.fy));
				write(String.format("cx = %6.2f cy = %6.2f",intrinsic.cx,intrinsic.cy));
				write(String.format("radial = [ %6.2e ][ %6.2e ]",intrinsic.radial[0],intrinsic.radial[1]));
				write("----------------------------");
				List<ImageResults> results = CalibrateMonoPlanar.computeErrors(points, zhangParam, target.points);
				double totalError = 0;
				for( int i = 0; i < results.size(); i++ ) {
					ImageResults r = results.get(i);
					write(String.format("[%3d] mean error = %7.3f",i,r.meanError));
					totalError += r.meanError;
				}
				write("Average error = "+(totalError/results.size()));

				// activate the buttons so the user can accept or reject the solution
				runOnUiThread(new Runnable() {
					public void run() {
						buttonCancel.setEnabled(true);
						buttonOK.setEnabled(true);
					}
				});
			} catch( RuntimeException e ) {
				// if a stop is requested a runtime exception is thrown
				write("Calibration thread stopped");
			}


			threadRunning = false;
		}

		@Override
		public boolean zhangUpdate(String taskName) {
			write(taskName);
			return !requestStop;
		}
	}

}
