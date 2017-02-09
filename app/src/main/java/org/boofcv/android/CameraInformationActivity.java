package org.boofcv.android;

import android.app.Activity;
import android.graphics.ImageFormat;
import android.hardware.Camera;
import android.os.Bundle;
import android.text.method.ScrollingMovementMethod;
import android.widget.TextView;
import android.widget.Toast;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.List;

import boofcv.io.calibration.CalibrationIO;
import boofcv.struct.calib.CameraPinholeRadial;

/**
 * Displays information on the camera(s) in a text view.
 *
 * @author Peter Abeles
 */
public class CameraInformationActivity extends Activity {

	TextView text;

	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		setContentView(R.layout.info_view);

		text = (TextView) findViewById(R.id.text_info);
		text.setMovementMethod(new ScrollingMovementMethod());

		int numCamera = Camera.getNumberOfCameras();
		write("Number of cameras: "+numCamera);
		write("-------- Intrinsic --------");
		for( int i = 0; i < numCamera; i++ ) {
			printIntrinsic(i);
		}
		write("-------- Capabilities --------");

		for( int i = 0; i < numCamera; i++ ) {
			write("------ Camera "+i);
			Camera c = Camera.open(i);
			Camera.CameraInfo info = DemoMain.specs.get(i).info;
			Camera.Parameters param = c.getParameters();
			String facing = info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT ? "Front" : "Back";
			write(" Facing: "+facing);
			write(" Orientation: "+info.orientation+" degrees");
			write(" Local Length: "+param.getFocalLength()+" (mm)");
			write(" Focus Mode: "+param.getFocusMode());
			write(" Horiz view angle: "+param.getHorizontalViewAngle());
			write(" * Preview Sizes");
			List<Camera.Size> supported = param.getSupportedPreviewSizes();
			for( Camera.Size size : supported ) {
				write("  "+s(size));
			}
			List<Integer> formats = param.getSupportedPreviewFormats();
			write(" * Preview Formats");
			for( Integer f : formats )
				write("   "+format(f));

			c.release();
		}
	}

	private String format( int value ) {
		switch( value ) {
			case ImageFormat.JPEG:
				return "JPEG";
			case ImageFormat.NV16:
				return "NV16";
			case ImageFormat.NV21:
				return "NV21";
			case ImageFormat.RGB_565:
				return "RGB_565";
			case ImageFormat.YUY2:
				return "YUY2";
			case ImageFormat.YV12:
				return "YV12";
			default:
				return "Unknown "+value;
		}
	}

	private String s( Camera.Size size ) {
		return size.width+" "+size.height;
	}

	private void printIntrinsic( int which ) {
		try {
			FileInputStream fos = openFileInput("cam"+which+".txt");
			Reader reader = new InputStreamReader(fos);
			CameraPinholeRadial intrinsic = CalibrationIO.load(reader);
			write("Found intrinsic for camera "+which);
			write(String.format("  Dimension %d %d",intrinsic.width,intrinsic.height));
			write(String.format("  fx = %6.1f fy = %6.1f",intrinsic.fx,intrinsic.fy));
			write(String.format("  cx = %6.1f cy = %6.1f",intrinsic.cx,intrinsic.cy));
			write(String.format("  skew = %8.3f",intrinsic.skew));
		} catch (RuntimeException e) {
			write("Intrinsic not known for camera "+which);
		} catch (IOException e) {
			write("IOException: "+e.getMessage());
			Toast toast = Toast.makeText(this, "Failed to load intrinsic parameters", 2000);
			toast.show();
		}
	}

	private void write( final String message ) {
		runOnUiThread(new Runnable() {
			public void run() {
				text.setText(text.getText() + message + "\n");
			}
		});
	}
}