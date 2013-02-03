package org.boofcv.android;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Bundle;
import boofcv.abst.feature.detect.interest.ConfigGeneralDetector;
import boofcv.abst.feature.tracker.PointTrack;
import boofcv.abst.feature.tracker.PointTracker;
import boofcv.android.ConvertBitmap;
import boofcv.factory.feature.tracker.FactoryPointTracker;
import boofcv.struct.image.ImageSInt16;
import boofcv.struct.image.ImageUInt8;
import georegression.struct.point.Point2D_F64;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Peter Abeles
 */
public class KltDisplayActivity extends VideoDisplayActivity {

	Paint paint;

	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		paint = new Paint();
		paint.setColor(Color.RED);
		paint.setStyle(Paint.Style.FILL);

		ConfigGeneralDetector config = new ConfigGeneralDetector();
		config.maxFeatures = 100;
		config.threshold = 40;
		config.radius = 3;

		PointTracker<ImageUInt8> tracker =
				FactoryPointTracker.klt(new int[]{1,2,4},config,3,ImageUInt8.class, ImageSInt16.class);

		setProcessing(new PointProcessing(tracker));
	}

	protected class PointProcessing extends BoofRenderProcessing {
		PointTracker<ImageUInt8> tracker;

		Bitmap bitmap;
		byte[] storage;

		List<PointTrack> active = new ArrayList<PointTrack>();

		public PointProcessing( PointTracker<ImageUInt8> tracker ) {
			this.tracker = tracker;
		}

		@Override
		protected void declareImages(int width, int height) {
			super.declareImages(width, height);
			bitmap = Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888);
			storage = ConvertBitmap.declareStorage(bitmap, storage);
		}

		@Override
		protected void process(ImageUInt8 gray) {
			tracker.process(gray);

			active.clear();
			tracker.getActiveTracks(active);

			if( active.size() < 50 )
				tracker.spawnTracks();

			ConvertBitmap.grayToBitmap(gray,bitmap,storage);
		}

		@Override
		protected void render(Canvas canvas, double imageToOutput) {
			canvas.drawBitmap(bitmap,0,0,null);

			active.clear();
			tracker.getActiveTracks(active);

			for( int i = 0; i < active.size(); i++ ) {
				Point2D_F64 p = active.get(i);
				canvas.drawCircle((int)p.x,(int)p.y,3,paint);
			}
		}
	}
}