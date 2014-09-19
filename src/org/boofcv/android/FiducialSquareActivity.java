package org.boofcv.android;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.*;
import boofcv.abst.fiducial.FiducialDetector;
import boofcv.alg.geo.PerspectiveOps;
import boofcv.android.ConvertBitmap;
import boofcv.android.gui.VideoImageProcessing;
import boofcv.core.image.ConvertImage;
import boofcv.factory.fiducial.ConfigFiducialBinary;
import boofcv.factory.fiducial.FactoryFiducial;
import boofcv.struct.calib.IntrinsicParameters;
import boofcv.struct.image.ImageBase;
import boofcv.struct.image.ImageType;
import boofcv.struct.image.ImageUInt8;
import boofcv.struct.image.MultiSpectral;
import georegression.metric.UtilAngle;
import georegression.struct.point.Point2D_F64;
import georegression.struct.point.Point2D_I32;
import georegression.struct.point.Point3D_F64;
import georegression.struct.se.Se3_F64;
import georegression.transform.se.SePointOps_F64;

/**
 * Base class for square fiducials
 *
 * @author Peter Abeles
 */
public abstract class FiducialSquareActivity extends DemoVideoDisplayActivity
{
	final Object lock = new Object();
	volatile boolean changed = true;
	volatile boolean robust = true;
	volatile int binaryThreshold = 100;

	Se3_F64 targetToCamera = new Se3_F64();
	IntrinsicParameters intrinsic;

	Class help;

	FiducialSquareActivity(Class help) {
		this.help = help;
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		LayoutInflater inflater = getLayoutInflater();
		LinearLayout controls = (LinearLayout)inflater.inflate(R.layout.fiducial_controls,null);

		LinearLayout parent = getViewContent();
		parent.addView(controls);

		ToggleButton toggle = (ToggleButton)controls.findViewById(R.id.toggle_robust);
		final SeekBar seek = (SeekBar)controls.findViewById(R.id.slider_threshold);

		robust = toggle.isChecked();
		binaryThreshold = seek.getProgress();

		seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
			@Override
			public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
				synchronized (lock) {
					changed = true;
					binaryThreshold = progress;
				}
			}

			@Override
			public void onStartTrackingTouch(SeekBar seekBar) {
			}

			@Override
			public void onStopTrackingTouch(SeekBar seekBar) {
			}
		});
		toggle.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
			@Override
			public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
				synchronized (lock) {
					changed = true;
					robust = isChecked;
					if( robust ) {
						seek.setEnabled(false);
					} else {
						seek.setEnabled(true);
					}
				}
			}
		});
	}

	public void pressedHelp( View view ) {
		Intent intent = new Intent(this, help );
		startActivity(intent);
	}

	@Override
	protected void onResume() {
		super.onResume();
		startDetector();
		if( DemoMain.preference.intrinsic == null ) {
			Toast.makeText(FiducialSquareActivity.this, "Calibrate camera for better results!", Toast.LENGTH_LONG).show();
		}
	}

	private void startDetector() {
		setProcessing(new FiducialProcessor() );
	}

	protected abstract FiducialDetector<ImageUInt8> createDetector();

	protected class FiducialProcessor<T extends ImageBase> extends VideoImageProcessing<MultiSpectral<ImageUInt8>>
	{
		T input;

		FiducialDetector<T> detector;

		Paint paintSelected = new Paint();
		Paint paintLine0 = new Paint();
		Paint paintLine1 = new Paint();
		Paint paintLine2 = new Paint();
		Paint paintLine3 = new Paint();
		Paint paintLine4 = new Paint();
		Paint paintLine5 = new Paint();
		private Paint textPaint = new Paint();

		protected FiducialProcessor() {
			super(ImageType.ms(3,ImageUInt8.class));

			paintSelected.setColor(Color.argb(0xFF / 2, 0xFF, 0, 0));

			paintLine0.setColor(Color.RED);
			paintLine0.setStrokeWidth(3f);
			paintLine1.setColor(Color.BLACK);
			paintLine1.setStrokeWidth(3f);
			paintLine2.setColor(Color.BLUE);
			paintLine2.setStrokeWidth(3f);
			paintLine3.setColor(Color.GREEN);
			paintLine3.setStrokeWidth(3f);
			paintLine4.setColor(Color.MAGENTA);
			paintLine4.setStrokeWidth(3f);
			paintLine5.setColor(Color.YELLOW);
			paintLine5.setStrokeWidth(3f);

			// Create out paint to use for drawing
			textPaint.setARGB(255, 200, 0, 0);
			textPaint.setTextSize(30);

		}

		@Override
		protected void declareImages(int width, int height) {
			super.declareImages(width, height);

			// make sure the camera is calibrated first
			if( DemoMain.preference.intrinsic == null ) {
				intrinsic = new IntrinsicParameters();
				intrinsic.width = width; intrinsic.height = height;
				intrinsic.cx = intrinsic.width/2;
				intrinsic.cy = intrinsic.height/2;
				intrinsic.fx = intrinsic.cx/Math.tan(UtilAngle.degreeToRadian(30)); // assume 60 degree FOV
				intrinsic.fy = intrinsic.cx/Math.tan(UtilAngle.degreeToRadian(30));
				intrinsic.flipY = false;
			} else {
				intrinsic = DemoMain.preference.intrinsic;
			}
		}

		@Override
		protected void process(MultiSpectral<ImageUInt8> color, Bitmap output, byte[] storage)
		{
			if( changed && intrinsic != null ) {
				detector = (FiducialDetector)createDetector();
				detector.setIntrinsic(intrinsic);
				if( input == null || input.getImageType() != detector.getInputType() ) {
					input = detector.getInputType().createImage(1, 1);
				}
			}
			ConvertBitmap.multiToBitmap(color, output, storage);

			if( detector == null  ) {
				return;
			}

			ImageType inputType = detector.getInputType();
			if( inputType.getFamily() == ImageType.Family.SINGLE_BAND ) {
				input.reshape(color.width,color.height);
				ConvertImage.average(color, (ImageUInt8) input);
			} else {
				input = (T)color;
			}

			detector.detect(input);

			Canvas canvas = new Canvas(output);

			for (int i = 0; i < detector.totalFound(); i++) {
				detector.getFiducialToWorld(i,targetToCamera);

				drawCube(detector.getId(i),targetToCamera,intrinsic,0.1,canvas);
			}
		}

		/**
		 * Draws a flat cube to show where the square fiducial is on the image
		 *
		 */
		public void drawCube( int number , Se3_F64 targetToCamera , IntrinsicParameters intrinsic , double width ,
							  Canvas canvas )
		{
			double r = width/2.0;
			Point3D_F64 corners[] = new Point3D_F64[8];
			corners[0] = new Point3D_F64(-r,-r,0);
			corners[1] = new Point3D_F64( r,-r,0);
			corners[2] = new Point3D_F64( r, r,0);
			corners[3] = new Point3D_F64(-r, r,0);
			corners[4] = new Point3D_F64(-r,-r,r);
			corners[5] = new Point3D_F64( r,-r,r);
			corners[6] = new Point3D_F64( r, r,r);
			corners[7] = new Point3D_F64(-r, r,r);

			Point2D_I32 pixel[] = new Point2D_I32[8];
			Point2D_F64 p = new Point2D_F64();
			for (int i = 0; i < 8; i++) {
				Point3D_F64 c = corners[i];
				SePointOps_F64.transform(targetToCamera, c, c);
				PerspectiveOps.convertNormToPixel(intrinsic, c.x / c.z, c.y / c.z, p);
				pixel[i] = new Point2D_I32((int)(p.x+0.5),(int)(p.y+0.5));
			}

			// red
			drawLine(canvas,pixel[0],pixel[1],paintLine0);
			drawLine(canvas,pixel[1],pixel[2],paintLine0);
			drawLine(canvas,pixel[2],pixel[3],paintLine0);
			drawLine(canvas,pixel[3],pixel[0],paintLine0);

			// black
			drawLine(canvas,pixel[0],pixel[4],paintLine1);
			drawLine(canvas,pixel[1],pixel[5],paintLine1);
			drawLine(canvas,pixel[2],pixel[6],paintLine1);
			drawLine(canvas,pixel[3],pixel[7],paintLine1);

			drawLine(canvas,pixel[4],pixel[5],paintLine2);
			drawLine(canvas,pixel[5],pixel[6],paintLine3);
			drawLine(canvas,pixel[6],pixel[7],paintLine4);
			drawLine(canvas,pixel[7],pixel[4],paintLine5);

			String numberString = ""+number;
			int textLength = (int)textPaint.measureText(numberString);
			canvas.drawText(numberString, pixel[7].x-textLength/2,pixel[7].y, textPaint);
		}

		private void drawLine( Canvas canvas , Point2D_I32 a , Point2D_I32 b , Paint color ) {
			canvas.drawLine((int)a.x,(int)a.y,(int)b.x,(int)b.y,color);
		}

	}
}
