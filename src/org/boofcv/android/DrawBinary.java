package org.boofcv.android;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.hardware.Camera;
import android.util.Log;
import android.view.SurfaceView;
import boofcv.alg.filter.binary.BinaryImageOps;
import boofcv.alg.filter.binary.ThresholdImageOps;
import boofcv.android.ConvertBitmap;
import boofcv.android.ConvertNV21;
import boofcv.android.VisualizeImageData;
import boofcv.struct.image.ImageUInt8;

/**
 * @author Peter Abeles
 */
public class DrawBinary extends SurfaceView implements Camera.PreviewCallback {
	public final static String TAG = "DrawBinary";

	private Paint textPaint = new Paint();

	BoofProcessing processing;
	Activity activity;

	long previous = 0;

	public DrawBinary(Activity context , BoofProcessing processing ) {
		super(context);
		this.activity = context;
		this.processing = processing;

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
		processing.onDraw(canvas);

		// Draw how fast it is running
		long current = System.currentTimeMillis();
		long elapsed = current - previous;
		previous = current;
		double fps = 1000.0/elapsed;
		canvas.restore();
		canvas.drawText(String.format("FPS = %5.2f",fps), 50, 50, textPaint);
	}

	@Override
	public void onPreviewFrame(byte[] bytes, Camera camera) {
		processing.convertPreview(bytes,camera);
	}
}
