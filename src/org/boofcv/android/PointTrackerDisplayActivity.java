package org.boofcv.android;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import boofcv.abst.feature.tracker.PointTrack;
import boofcv.abst.feature.tracker.PointTracker;
import boofcv.android.ConvertBitmap;
import boofcv.struct.image.ImageUInt8;
import georegression.struct.point.Point2D_F64;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Peter Abeles
 */
public class PointTrackerDisplayActivity extends VideoDisplayActivity {

	Paint paintLine = new Paint();
	Paint paintRed = new Paint();
	Paint paintBlue = new Paint();


	public PointTrackerDisplayActivity() {
		paintLine.setColor(Color.RED);
		paintLine.setStrokeWidth(1.5f);
		paintRed.setColor(Color.MAGENTA);
		paintRed.setStyle(Paint.Style.FILL);
		paintBlue.setColor(Color.BLUE);
		paintBlue.setStyle(Paint.Style.FILL);
	}

	protected class PointProcessing extends BoofRenderProcessing {
		PointTracker<ImageUInt8> tracker;

		long tick;

		Bitmap bitmap;
		byte[] storage;

		List<PointTrack> active = new ArrayList<PointTrack>();
		List<PointTrack> spawned = new ArrayList<PointTrack>();
		List<PointTrack> inactive = new ArrayList<PointTrack>();

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

			// drop tracks which are no longer being used
			inactive.clear();
			tracker.getInactiveTracks(inactive);
			for( int i = 0; i < inactive.size(); i++ ) {
				PointTrack t = inactive.get(i);
				TrackInfo info = t.getCookie();
				if( tick - info.lastActive > 2 ) {
					tracker.dropTrack(t);
				}
			}

			active.clear();
			tracker.getActiveTracks(active);
			for( int i = 0; i < active.size(); i++ ) {
				PointTrack t = active.get(i);
				TrackInfo info = t.getCookie();
				info.lastActive = tick;
			}

			spawned.clear();
			if( active.size() < 50 )  {
				tracker.spawnTracks();

				// update the track's initial position
				for( int i = 0; i < active.size(); i++ ) {
					PointTrack t = active.get(i);
					TrackInfo info = t.getCookie();
					info.spawn.set(t);
				}

				tracker.getNewTracks(spawned);
				for( int i = 0; i < spawned.size(); i++ ) {
					PointTrack t = spawned.get(i);
					if( t.cookie == null ) {
						t.cookie = new TrackInfo();
					}
					TrackInfo info = t.getCookie();
					info.lastActive = tick;
					info.spawn.set(t);
				}
			}

			ConvertBitmap.grayToBitmap(gray,bitmap,storage);

			tick++;
		}

		@Override
		protected void render(Canvas canvas, double imageToOutput) {
			canvas.drawBitmap(bitmap,0,0,null);

			for( int i = 0; i < active.size(); i++ ) {
				PointTrack t = active.get(i);
				TrackInfo info = t.getCookie();
				Point2D_F64 s = info.spawn;
				Point2D_F64 p = active.get(i);
				canvas.drawLine((int)s.x,(int)s.y,(int)p.x,(int)p.y,paintLine);
				canvas.drawCircle((int)p.x,(int)p.y,2f, paintRed);
			}

			for( int i = 0; i < spawned.size(); i++ ) {
				Point2D_F64 p = spawned.get(i);
				canvas.drawCircle((int)p.x,(int)p.y,3, paintBlue);
			}
		}
	}

	private static class TrackInfo {
		long lastActive;
		Point2D_F64 spawn = new Point2D_F64();
	}
}