package org.boofcv.android;

import android.app.Activity;
import android.graphics.ImageFormat;
import android.hardware.Camera;
import android.os.Bundle;
import android.text.method.ScrollingMovementMethod;
import android.widget.TextView;

import java.util.List;

/**
 * Displays camera info in a text view
 *
 * @author Peter Abeles
 */
public class CameraInfoActivity extends Activity {

	TextView view;

	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.camera_info_activity);

		view = (TextView) findViewById(R.id.CameraInfoText);
		view.setMovementMethod(new ScrollingMovementMethod());

		Camera camera = UtilVarious.getCameraInstance();

		Camera.Parameters param = camera.getParameters();

		println("Focal Length "+param.getFocalLength()+" (mm)");
		println("Image Size "+toString(param.getPictureSize()));
		println("Preview Size "+toString(param.getPreviewSize()));
		println("Focus Mode "+param.getFocusMode());
		println("Horiz view angle "+param.getHorizontalViewAngle());
		println("Picture format "+param.getPictureFormat());
		println("Preview format "+param.getPreviewFormat());

		List<Camera.Size> sizes = param.getSupportedPictureSizes();
		println("------ Allowed Picture Sizes ------ ");
		for( Camera.Size s : sizes )
			println("   "+toString(s));
		sizes = param.getSupportedPreviewSizes();
		println("------ Allowed Preview Sizes ------ ");
		for( Camera.Size s : sizes )
			println("   "+toString(s));
		List<Integer> formats = param.getSupportedPictureFormats();
		println("------ Allowed Picture Formats ------ ");
		for( Integer f : formats )
			println("   "+format(f));
		formats = param.getSupportedPreviewFormats();
		println("------ Allowed Preview Formats ------ ");
		for( Integer f : formats )
			println("   "+format(f));

		camera.release();
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

	private String toString( Camera.Size size ) {
		return size.width+" "+size.height;
	}

	private void println( String text ) {
		view.setText( view.getText() + text + "\n");
	}
}