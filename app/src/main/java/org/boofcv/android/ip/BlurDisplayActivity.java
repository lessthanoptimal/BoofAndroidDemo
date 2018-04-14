package org.boofcv.android.ip;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.Spinner;

import org.boofcv.android.DemoCamera2Activity;
import org.boofcv.android.DemoProcessing;
import org.boofcv.android.R;

import boofcv.abst.filter.blur.BlurFilter;
import boofcv.android.ConvertBitmap;
import boofcv.factory.filter.blur.FactoryBlurFilter;
import boofcv.struct.image.GrayU8;
import boofcv.struct.image.ImageType;

/**
 * Blurs the input video image using different algorithms.
 *
 * @author Peter Abeles
 */
public class BlurDisplayActivity extends DemoCamera2Activity
		implements AdapterView.OnItemSelectedListener
{
	private static final String TAG = "BlurActivity";
	Spinner spinnerView;

	// amount of blur applied to the image
	int radius;

	public BlurDisplayActivity() {
		super(Resolution.MEDIUM);

		// this class will handle all manipulation of the bitmap image since the blurred
		// image is shown and not the input
		super.showBitmap = false;
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		setContentView(R.layout.standard_camera2);

		LinearLayout parent = findViewById(R.id.root_layout);

		LayoutInflater inflater = getLayoutInflater();
		LinearLayout controls = (LinearLayout)inflater.inflate(R.layout.blur_controls,null);
		parent.addView(controls);

		spinnerView = controls.findViewById(R.id.spinner_algs);
		ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(this,
				R.array.blurs, android.R.layout.simple_spinner_item);
		adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		spinnerView.setAdapter(adapter);
		spinnerView.setOnItemSelectedListener(this);

		SeekBar seek = controls.findViewById(R.id.slider_width);
		radius = seek.getProgress();

		seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
			@Override
			public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
				radius = progress;
				synchronized (lockProcessor) {
					if( processor == null) {
						Log.e(TAG,"onProgressChanged() and processing is NULL");
						return;
					}
					if (radius > 0)
						((BlurProcessing)processor).setRadius(radius);
				}
			}

			@Override
			public void onStartTrackingTouch(SeekBar seekBar) {}

			@Override
			public void onStopTrackingTouch(SeekBar seekBar) {}
		});

		FrameLayout surfaceLayout = findViewById(R.id.camera_frame_layout);
//		TextureView texture = findViewById(R.id.texture);
		startCamera(surfaceLayout,null);
	}

	@Override
	protected void onCameraResolutionChange(int width, int height) {
		super.onCameraResolutionChange(width, height);
		synchronized (bitmapLock) {
			if (bitmap.getWidth() != width || bitmap.getHeight() != height)
				bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
			convertTmp = ConvertBitmap.declareStorage(bitmap, convertTmp);
		}
	}

	@Override
	protected void onResume() {
		super.onResume();
		startBlurProcess(spinnerView.getSelectedItemPosition());
	}

	@Override
	public void onItemSelected(AdapterView<?> adapterView, View view, int pos, long id ) {
		startBlurProcess(pos);
	}

	private void startBlurProcess(int pos) {
		// not sure what these do if the radius is set to 0
		int radius = Math.max(1,this.radius);
		BlurProcessing processing;
		switch (pos) {
			case 0:
				processing = new BlurProcessing(FactoryBlurFilter.mean(GrayU8.class, radius));
				break;

			case 1:
				processing = new BlurProcessing(FactoryBlurFilter.gaussian(GrayU8.class,-1,radius));
				break;

			case 2:
				processing = new BlurProcessing(FactoryBlurFilter.median(GrayU8.class,radius));
				break;

			default:
				throw new RuntimeException("Unknown");
		}
		processing.setRadius(radius);
		setProcessor(processing);
	}

	@Override
	public void onNothingSelected(AdapterView<?> adapterView) {}

	protected void renderBlurred( GrayU8 blurred ) {
		synchronized (bitmapLock) {
			ConvertBitmap.grayToBitmap(blurred, bitmap, convertTmp);
		}
	}

	protected void drawBitmap(Canvas canvas, Matrix imageToView) {
		synchronized (bitmapLock) {
			canvas.drawBitmap(bitmap, imageToView, null);
		}
	}

	protected class BlurProcessing implements DemoProcessing<GrayU8> {
		GrayU8 blurred;
		final BlurFilter<GrayU8> filter;

		public BlurProcessing(BlurFilter<GrayU8> filter) {
			this.filter = filter;
		}

		public void setRadius( int radius ) {
			synchronized ( filter ) {
				filter.setRadius(radius);
			}
		}

		@Override
		public void initialize(int imageWidth, int imageHeight) {
			blurred = new GrayU8(imageWidth,imageHeight);
		}

		@Override
		public void onDraw(Canvas canvas, Matrix imageToView) {
			drawBitmap(canvas,imageToView);
		}

		@Override
		public void process(GrayU8 input) {
			if( radius > 0 ) {
				synchronized ( filter ) {
					filter.process(input, blurred);
				}
				renderBlurred(blurred);
			} else {
				renderBlurred(input);
			}
		}

		@Override
		public void stop() {

		}

		@Override
		public boolean isThreadSafe() {
			return false;
		}

		@Override
		public ImageType<GrayU8> getImageType() {
			return filter.getInputType();
		}
	}
}