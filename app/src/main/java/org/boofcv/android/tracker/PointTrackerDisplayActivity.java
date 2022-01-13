package org.boofcv.android.tracker;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;

import org.boofcv.android.DemoCamera2Activity;
import org.boofcv.android.DemoProcessingAbstract;
import org.ddogleg.struct.DogArray;

import java.util.ArrayList;
import java.util.List;

import boofcv.abst.tracker.PointTrack;
import boofcv.abst.tracker.PointTracker;
import boofcv.struct.image.GrayU8;
import georegression.struct.point.Point2D_F64;

/**
 * Base tracks for point tracker display activities.
 *
 * @author Peter Abeles
 */
public abstract class PointTrackerDisplayActivity extends DemoCamera2Activity {

	Paint paintLine = new Paint();
	Paint paintRed = new Paint();
	Paint paintBlue = new Paint();

	public PointTrackerDisplayActivity(Resolution resolution) {
		super(resolution);

		paintLine.setColor(Color.RED);
		paintLine.setStyle(Paint.Style.STROKE);
		paintRed.setColor(Color.MAGENTA);
		paintRed.setStyle(Paint.Style.FILL);
		paintBlue.setColor(Color.BLUE);
		paintBlue.setStyle(Paint.Style.FILL);
	}

	protected class PointProcessing extends DemoProcessingAbstract<GrayU8> {
		PointTracker<GrayU8> tracker;

		long tick;

		final Object lockTracks = new Object();

		List<PointTrack> active = new ArrayList<>();
		List<PointTrack> spawned = new ArrayList<>();
		List<PointTrack> inactive = new ArrayList<>();

		// storage for data structures that are displayed in the GUI
		DogArray<Point2D_F64> trackSrc = new DogArray<>(Point2D_F64::new);
		DogArray<Point2D_F64> trackDst = new DogArray<>(Point2D_F64::new);
		DogArray<Point2D_F64> trackSpawn = new DogArray<>(Point2D_F64::new);

		float circleRadius;

		public PointProcessing( PointTracker<GrayU8> tracker ) {
			super(GrayU8.class);
			this.tracker = tracker;
		}

		@Override
		public void initialize(int imageWidth, int imageHeight, int sensorOrientation) {
			paintLine.setStrokeWidth(3*cameraToDisplayDensity);
			circleRadius = 2*cameraToDisplayDensity;
		}

		@Override
		public void onDraw(Canvas canvas, Matrix imageToView) {

			canvas.concat(imageToView);
			synchronized (lockTracks) {
				for (int i = 0; i < trackSrc.size(); i++) {
					Point2D_F64 s = trackSrc.get(i);
					Point2D_F64 p = trackDst.get(i);
					canvas.drawLine((float) s.x, (float) s.y, (float) p.x, (float) p.y, paintLine);
					canvas.drawCircle((float) p.x, (float) p.y, circleRadius, paintRed);
				}

				for (int i = 0; i < trackSpawn.size(); i++) {
					Point2D_F64 p = trackSpawn.get(i);
					canvas.drawCircle((int) p.x, (int) p.y, circleRadius*1.5f, paintBlue);
				}
			}
		}

		@Override
		public void process(GrayU8 input) {
			if( tracker == null )
				return;

			tracker.process(input);

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
					info.spawn.setTo(t.pixel);
				}

				tracker.getNewTracks(spawned);
				for( int i = 0; i < spawned.size(); i++ ) {
					PointTrack t = spawned.get(i);
					if( t.cookie == null ) {
						t.cookie = new TrackInfo();
					}
					TrackInfo info = t.getCookie();
					info.lastActive = tick;
					info.spawn.setTo(t.pixel);
				}
			}

			synchronized ( lockTracks ) {
				trackSrc.reset();
				trackDst.reset();
				trackSpawn.reset();

				for( int i = 0; i < active.size(); i++ ) {
					PointTrack t = active.get(i);
					TrackInfo info = t.getCookie();
					Point2D_F64 s = info.spawn;
					Point2D_F64 p = active.get(i).pixel;

					trackSrc.grow().setTo(s);
					trackDst.grow().setTo(p);
				}

				for( int i = 0; i < spawned.size(); i++ ) {
					Point2D_F64 p = spawned.get(i).pixel;
					trackSpawn.grow().setTo(p);
				}
			}

			tick++;
		}

	}

	private static class TrackInfo {
		long lastActive;
		Point2D_F64 spawn = new Point2D_F64();
	}
}