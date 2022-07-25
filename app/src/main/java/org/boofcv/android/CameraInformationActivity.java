package org.boofcv.android;

import android.app.Activity;
import android.content.Context;
import android.graphics.ImageFormat;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.os.Bundle;
import android.text.method.ScrollingMovementMethod;
import android.util.Size;
import android.widget.TextView;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import boofcv.android.BoofAndroidUtils;
import boofcv.android.camera2.CameraID;
import boofcv.struct.calib.CameraPinholeBrown;

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

		text = findViewById(R.id.text_info);
		text.setMovementMethod(new ScrollingMovementMethod());

		try {
			cameraInfo();
		} catch (CameraAccessException e) {
			text.setText(e.getLocalizedMessage());
		}
	}

	private void cameraInfo() throws CameraAccessException {
		CameraManager manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
		if( manager == null ) {
			write( "manager is null");
			return;
		}

		List<CameraID> cameras = BoofAndroidUtils.getAllCameras(manager);

		write("Number of cameras: "+cameras.size());
		write("-------- Intrinsic --------");
		for( CameraID cameraId : cameras ) {
			printIntrinsic(cameraId.id);
		}

		for( CameraID cameraId : cameras ) {
			CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId.id);
			Integer facing = characteristics.get(CameraCharacteristics.LENS_FACING);
			float[] focalLengths = characteristics.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS);

			write("------ Camera = "+cameraId.id);
			write("orientation  = " +characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION));
			write("facing       = " +facing(facing));
			write("logical      = " + cameraId.isLogical());

			StreamConfigurationMap map = characteristics.
					get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
			if (map == null) {
				continue;
			}
			Size[] sizes = map.getOutputSizes(ImageFormat.YUV_420_888);
			write(" * Video Resolutions for YUV 420_888");
			Size sizeMax = sizes[0];
			Size sizeMin = sizes[0];
			for (int i = 1; i < sizes.length; i++) {
				Size s = sizes[i];
				int area = s.getWidth()*s.getHeight();
				if (area < sizeMin.getWidth()*sizeMin.getHeight()) {
					sizeMin = s;
				} else if (area > sizeMax.getWidth()*sizeMax.getHeight()) {
					sizeMax = s;
				}
			}
			write("  "+s(sizeMin)+" -> "+s(sizeMax));

			write(" * Image Formats");
			int[] formats = map.getOutputFormats();
			for( int format : formats ) {
				write("  "+format(format));
			}

			if( focalLengths != null ) {
				write(" * Focal Lengths");
				for( float f : focalLengths ) {
					write("  "+f);
				}
			}

		}
	}

	public static String facing(Integer value) {
		if( value == null )
			return "null";
		if( CameraCharacteristics.LENS_FACING_FRONT == value )
			return "front";
		else if( CameraCharacteristics.LENS_FACING_BACK == value )
			return "back";
		else if( CameraCharacteristics.LENS_FACING_EXTERNAL == value )
			return "external";
		else
			return "unknown";
	}

	private String format( int value ) {
		switch( value ) {
			case ImageFormat.DEPTH16:
				return "DEPTH16";
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
			case ImageFormat.YUV_420_888:
				return "YUV_420_888";
			case ImageFormat.YUV_422_888:
				return "YUV_422_888";
			case ImageFormat.YUV_444_888:
				return "YUV_444_888";
			case ImageFormat.FLEX_RGB_888:
				return "FLEX_RGB_888";
			case ImageFormat.FLEX_RGBA_8888:
				return "FLEX_RGB_888";
			case ImageFormat.PRIVATE:
				return "PRIVATE";
			case ImageFormat.RAW_PRIVATE:
				return "RAW_PRIVATE";
			case ImageFormat.RAW_SENSOR:
				return "RAW_SENSOR";
			case ImageFormat.RAW10:
				return "RAW10";
			case ImageFormat.RAW12:
				return "RAW12";
			default:
				return "Unknown "+value;
		}
	}

	private String s( Size size ) {
		return size.getWidth()+" "+size.getHeight();
	}

	private void printIntrinsic( String which ) {
		File directory = new File(getExternalFilesDir(null),"calibration");
		if( !directory.exists() ) {
			write("No cameras have been calibrated. No Directory");
			return;
		}
		List<CameraPinholeBrown> calibration = new ArrayList<>();
		List<File> locations = new ArrayList<>();
		DemoMain.loadIntrinsics(this,which,calibration,locations);

		for( int i = 0; i < calibration.size(); i++ ) {
			CameraPinholeBrown intrinsic = calibration.get(i);
			write("Found intrinsic for camera " + which);
			write(String.format("  Dimension %d %d", intrinsic.width, intrinsic.height));
			write(String.format("  fx = %6.1f fy = %6.1f", intrinsic.fx, intrinsic.fy));
			write(String.format("  cx = %6.1f cy = %6.1f", intrinsic.cx, intrinsic.cy));
			write(String.format("  skew = %8.3f", intrinsic.skew));
			write("  Path: " + locations.get(i).getAbsolutePath());
		}

		if( calibration.isEmpty() ) {
			write("Camera "+which+" has not been calibrated");
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