package org.boofcv.android.calib;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.Toast;
import android.widget.ToggleButton;

import org.boofcv.android.DemoMain;
import org.boofcv.android.DemoVideoDisplayActivity;
import org.boofcv.android.R;

import boofcv.alg.distort.AdjustmentType;
import boofcv.alg.distort.ImageDistort;
import boofcv.alg.distort.LensDistortionOps;
import boofcv.alg.distort.PointToPixelTransform_F32;
import boofcv.alg.interpolate.InterpolatePixelS;
import boofcv.android.ConvertBitmap;
import boofcv.android.gui.VideoImageProcessing;
import boofcv.core.image.ConvertImage;
import boofcv.core.image.border.BorderType;
import boofcv.factory.distort.FactoryDistort;
import boofcv.factory.interpolate.FactoryInterpolation;
import boofcv.struct.distort.Point2Transform2_F32;
import boofcv.struct.image.GrayU8;
import boofcv.struct.image.ImageType;
import boofcv.struct.image.Planar;

/**
 * After the camera has been calibrated the user can display a distortion free image
 *
 * @author Peter Abeles
 */
public class UndistortDisplayActivity extends DemoVideoDisplayActivity
		implements CompoundButton.OnCheckedChangeListener
{

	ToggleButton toggleDistort;
	ToggleButton toggleColor;

	boolean isDistorted = false;
	boolean isColor = false;

	ImageDistort<GrayU8,GrayU8> removeDistortion;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		LayoutInflater inflater = getLayoutInflater();
		LinearLayout controls = (LinearLayout)inflater.inflate(R.layout.undistort_controls,null);

		LinearLayout parent = getViewContent();
		parent.addView(controls);

		toggleDistort = (ToggleButton)controls.findViewById(R.id.toggle_distort);
		toggleDistort.setOnCheckedChangeListener(this);
		toggleDistort.setChecked(isDistorted);

		toggleColor = (ToggleButton)controls.findViewById(R.id.toggle_color);
		toggleColor.setOnCheckedChangeListener(this);
		toggleColor.setChecked(isColor);

		if( DemoMain.preference.intrinsic != null ) {
			// define the transform.  Cache the results for quick rendering later on
			Point2Transform2_F32 fullView = LensDistortionOps.transform_F32(AdjustmentType.FULL_VIEW,
					DemoMain.preference.intrinsic, null, false);
			InterpolatePixelS<GrayU8> interp = FactoryInterpolation.
					bilinearPixelS(GrayU8.class, BorderType.ZERO);
			// for some reason not caching is faster on a low end phone.  Maybe it has to do with CPU memory
			// cache misses when looking up a point?
			removeDistortion = FactoryDistort.distortSB(false,interp,GrayU8.class);
			removeDistortion.setModel(new PointToPixelTransform_F32(fullView));
		}
	}

	@Override
	protected void onResume() {
		super.onResume();
		setProcessing(new UndistortProcessing());
	}

	@Override
	public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
		if( DemoMain.preference.intrinsic == null ) {
			Toast toast = Toast.makeText(UndistortDisplayActivity.this,
					"You must first calibrate the camera!", Toast.LENGTH_LONG);
			toast.show();
		}
		if( toggleDistort == compoundButton ) {
			isDistorted = b;
		} else if( toggleColor == compoundButton ) {
			isColor = b;
		}
	}

	protected class UndistortProcessing extends VideoImageProcessing<Planar<GrayU8>> {
		Planar<GrayU8> undistorted;

		public UndistortProcessing() {
			super(ImageType.pl(3,GrayU8.class));
		}

		@Override
		protected void declareImages( int width , int height ) {
			super.declareImages(width, height);

			undistorted = new Planar<GrayU8>(GrayU8.class,width,height,3);
		}

		@Override
		protected void process(Planar<GrayU8> input, Bitmap output, byte[] storage) {
			if( DemoMain.preference.intrinsic == null ) {
				Canvas canvas = new Canvas(output);
				Paint paint = new Paint();
				paint.setColor(Color.RED);
				paint.setTextSize(output.getWidth()/10);
				int textLength = (int)paint.measureText("Calibrate Camera First");

				canvas.drawText("Calibrate Camera First", (canvas.getWidth() - textLength) / 2, canvas.getHeight() / 2, paint);
			} else if( isDistorted ) {
				if( isColor )
					ConvertBitmap.multiToBitmap(input,output,storage);
				else {
					ConvertImage.average(input,undistorted.getBand(0));
					ConvertBitmap.grayToBitmap(undistorted.getBand(0),output,storage);
				}
			} else {
				if( isColor ) {
					for( int i = 0; i < input.getNumBands(); i++ ) {
						removeDistortion.apply(input.getBand(i),undistorted.getBand(i));
					}

					ConvertBitmap.multiToBitmap(undistorted,output,storage);
				} else {
					ConvertImage.average(input,undistorted.getBand(0));
					removeDistortion.apply(undistorted.getBand(0),undistorted.getBand(1));
					ConvertBitmap.grayToBitmap(undistorted.getBand(1),output,storage);
				}
			}
		}
	}
}