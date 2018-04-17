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
import org.boofcv.android.DemoMain;
import org.boofcv.android.DemoProcessingAbstract;
import org.boofcv.android.R;

import boofcv.alg.distort.AdjustmentType;
import boofcv.alg.distort.ImageDistort;
import boofcv.alg.distort.LensDistortionOps;
import boofcv.alg.distort.PointToPixelTransform_F32;
import boofcv.alg.interpolate.InterpolatePixelS;
import boofcv.android.ConvertBitmap;
import boofcv.core.image.border.BorderType;
import boofcv.factory.distort.FactoryDistort;
import boofcv.factory.interpolate.FactoryInterpolation;
import boofcv.struct.calib.CameraPinhole;
import boofcv.struct.calib.CameraPinholeRadial;
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

	ImageDistort removeDistortion;

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

	@Override
	protected void onResume() {
		super.onResume();
		setProcessing();
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

	private void setProcessing() {
		Log.e("Undistort","Set Processing!");
		if( isColor ) {
			setProcessing(new UndistortProcessing(ImageType.pl(3,GrayU8.class)));
		} else {
			setProcessing(new UndistortProcessing(ImageType.single(GrayU8.class)));
		}
	}

	@Override
	public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
		if( DemoMain.preference.intrinsic == null ) {
			Toast toast = Toast.makeText(UndistortDisplayActivity.this,
					"You must first calibrate the camera!", Toast.LENGTH_LONG);
			toast.show();
		}

		if( toggleColor == compoundButton ) {
			isColor = b;
			setProcessing();
		}
	}

	protected class UndistortProcessing extends DemoProcessingAbstract {
		ImageBase undistorted;

		public UndistortProcessing( ImageType imageType ) {
			super(imageType);

			if (DemoMain.preference.intrinsic == null) {
				return;
			}
			CameraPinholeRadial intrinsic = DemoMain.preference.intrinsic;

			// define the transform.  Cache the results for quick rendering later on
			CameraPinhole desired = new CameraPinhole();
			desired.set(intrinsic);

			Point2Transform2_F32 fullView = LensDistortionOps.transformChangeModel_F32(AdjustmentType.FULL_VIEW,
					intrinsic,desired,false,null);
			InterpolatePixelS<GrayU8> interp = FactoryInterpolation.
					bilinearPixelS(GrayU8.class, BorderType.ZERO);
			// for some reason not caching is faster on a low end phone.  Maybe it has to do with CPU memory
			// cache misses when looking up a point?
			removeDistortion = FactoryDistort.distort(false,interp,imageType);
			removeDistortion.setModel(new PointToPixelTransform_F32(fullView));

		}

		@Override
		public void initialize(int imageWidth, int imageHeight) {
			undistorted = imageType.createImage(imageWidth,imageHeight);

			CameraPinholeRadial intrinsic = DemoMain.preference.intrinsic;

			if( intrinsic.width != imageWidth || intrinsic.height != imageHeight ) {
				UndistortDisplayActivity.this.runOnUiThread(()->{
					Toast toast = Toast.makeText(UndistortDisplayActivity.this,
							"Calibration doesn't match input image!", Toast.LENGTH_LONG);
					toast.show();
					UndistortDisplayActivity.this.finish();
				});
			}
		}

		@Override
		public void onDraw(Canvas canvas, Matrix imageToView) {
			if (DemoMain.preference.intrinsic == null) {
				Log.e("Undistort","No intrinsic!");
				Paint paint = new Paint();
				paint.setColor(Color.RED);
				paint.setTextSize(canvas.getWidth() / 10);
				int textLength = (int) paint.measureText("Calibrate Camera First");

				canvas.drawText("Calibrate Camera First", (canvas.getWidth() - textLength) / 2, canvas.getHeight() / 2, paint);
			} else {
				Log.e("Drawing bitmap","Has intrinsic!");
				drawBitmap(canvas,imageToView);
			}
		}

		@Override
		public void process(ImageBase input) {
			Log.e("Undistort","process called");
			removeDistortion.apply(input,undistorted);
			synchronized (bitmapLock) {
				ConvertBitmap.boofToBitmap(undistorted, bitmap, bitmapTmp);
			}
		}
	}
}