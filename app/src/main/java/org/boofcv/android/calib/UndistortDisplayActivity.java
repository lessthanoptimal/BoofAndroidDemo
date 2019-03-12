package org.boofcv.android.calib;

import android.app.AlertDialog;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.Toast;
import android.widget.ToggleButton;

import org.boofcv.android.DemoBitmapCamera2Activity;
import org.boofcv.android.DemoProcessingAbstract;
import org.boofcv.android.R;

import boofcv.alg.distort.AdjustmentType;
import boofcv.alg.distort.ImageDistort;
import boofcv.alg.distort.LensDistortionOps_F32;
import boofcv.alg.distort.PointToPixelTransform_F32;
import boofcv.alg.interpolate.InterpolatePixelS;
import boofcv.android.ConvertBitmap;
import boofcv.factory.distort.FactoryDistort;
import boofcv.factory.interpolate.FactoryInterpolation;
import boofcv.struct.border.BorderType;
import boofcv.struct.calib.CameraPinhole;
import boofcv.struct.calib.CameraPinholeBrown;
import boofcv.struct.distort.Point2Transform2_F32;
import boofcv.struct.image.GrayU8;
import boofcv.struct.image.ImageBase;
import boofcv.struct.image.ImageType;

/**
 * After the camera has been calibrated the user can display a distortion free image
 *
 * @author Peter Abeles
 */
public class UndistortDisplayActivity extends DemoBitmapCamera2Activity
		implements CompoundButton.OnCheckedChangeListener
{

	ToggleButton toggleColor;

	boolean isColor = false;

	public UndistortDisplayActivity() {
		super(Resolution.MEDIUM);
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		LayoutInflater inflater = getLayoutInflater();
		LinearLayout controls = (LinearLayout)inflater.inflate(R.layout.undistort_controls,null);


		toggleColor = controls.findViewById(R.id.toggle_color);
		toggleColor.setOnCheckedChangeListener(this);
		toggleColor.setChecked(isColor);

		setControls(controls);
		activateTouchToShowInput();
	}
	
	public void pressedHelp( View view ) {
		AlertDialog alertDialog = new AlertDialog.Builder(this).create();
		alertDialog.setTitle("Undistort");
		alertDialog.setMessage("On most cellphones, lens distortion has already been removed. " +
				"That's why you hardly see any change. Touch screen to see original image.");
		alertDialog.setButton(AlertDialog.BUTTON_NEUTRAL, "OK",
				(dialog, which) -> dialog.dismiss());
		alertDialog.show();
	}

	@Override
	public void createNewProcessor() {
		Log.e("Undistort","Set Processing!");
		if( isColor ) {
			setProcessing(new UndistortProcessing(ImageType.pl(3,GrayU8.class)));
		} else {
			setProcessing(new UndistortProcessing(ImageType.single(GrayU8.class)));
		}
	}

	@Override
	public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
		if( app.preference.calibration.isEmpty() ) {
			Toast toast = Toast.makeText(UndistortDisplayActivity.this,
					"You must first calibrate the camera!", Toast.LENGTH_LONG);
			toast.show();
		}

		if( toggleColor == compoundButton ) {
			isColor = b;
			createNewProcessor();
		}
	}

	protected class UndistortProcessing extends DemoProcessingAbstract {
		ImageBase undistorted;
		ImageDistort removeDistortion;

		public UndistortProcessing( ImageType imageType ) {
			super(imageType);

			if (app.preference.calibration.isEmpty() ) {
				return;
			}
		}

		@Override
		public void initialize(int imageWidth, int imageHeight, int sensorOrientation) {
			undistorted = imageType.createImage(imageWidth,imageHeight);

			CameraPinholeBrown intrinsic = app.preference.lookup(
					imageWidth,imageHeight);

			if( intrinsic != null ) {
				// define the transform.  Cache the results for quick rendering later on
				CameraPinhole desired = new CameraPinhole();
				desired.set(intrinsic);

				Point2Transform2_F32 fullView = LensDistortionOps_F32.transformChangeModel(AdjustmentType.FULL_VIEW,
						intrinsic,desired,false,null);
				InterpolatePixelS<GrayU8> interp = FactoryInterpolation.
						bilinearPixelS(GrayU8.class, BorderType.ZERO);
				// for some reason not caching is faster on a low end phone.  Maybe it has to do with CPU memory
				// cache misses when looking up a point?
				removeDistortion = FactoryDistort.distort(false,interp,imageType);
				removeDistortion.setModel(new PointToPixelTransform_F32(fullView));
			}
		}

		@Override
		public void onDraw(Canvas canvas, Matrix imageToView) {
			if (removeDistortion == null) {
//				Log.e("Undistort","No intrinsic!");
				Paint paint = new Paint();
				paint.setColor(Color.RED);
				paint.setTextSize(canvas.getWidth() / 12);
				int textLength = (int) paint.measureText("Calibrate Camera First");

				canvas.drawText("Calibrate Camera First", (canvas.getWidth() - textLength) / 2, canvas.getHeight() / 2, paint);
			} else {
//				Log.e("Drawing bitmap","Has intrinsic!");
				drawBitmap(canvas,imageToView);
			}
		}

		@Override
		public void process(ImageBase input) {
			if( removeDistortion == null )
				return;

			removeDistortion.apply(input,undistorted);
			ConvertBitmap.boofToBitmap(undistorted, bitmap, bitmapTmp);
		}
	}
}