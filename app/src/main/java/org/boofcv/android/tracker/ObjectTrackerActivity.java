package org.boofcv.android.tracker;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.Toast;

import org.boofcv.android.DemoCamera2Activity;
import org.boofcv.android.DemoProcessingAbstract;
import org.boofcv.android.R;

import boofcv.abst.tracker.ConfigComaniciu2003;
import boofcv.abst.tracker.ConfigTld;
import boofcv.abst.tracker.MeanShiftLikelihoodType;
import boofcv.abst.tracker.TrackerObjectQuad;
import boofcv.alg.tracker.sfot.SfotConfig;
import boofcv.factory.tracker.FactoryTrackerObjectQuad;
import boofcv.struct.image.GrayU8;
import boofcv.struct.image.ImageBase;
import boofcv.struct.image.ImageType;
import georegression.struct.point.Point2D_F64;
import georegression.struct.point.Point2D_I32;
import georegression.struct.shapes.Quadrilateral_F64;

/**
 * Allow the user to select an object in the image and then track it
 *
 * @author Peter Abeles
 */
public class ObjectTrackerActivity extends DemoCamera2Activity
		implements AdapterView.OnItemSelectedListener, View.OnTouchListener
{

	Spinner spinnerView;

	int mode = 0;

	// size of the minimum square which the user can select
	final static int MINIMUM_MOTION = 20;

	Point2D_I32 click0 = new Point2D_I32();
	Point2D_I32 click1 = new Point2D_I32();

	public ObjectTrackerActivity() {
		super(Resolution.R640x480);
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		LayoutInflater inflater = getLayoutInflater();
		LinearLayout controls = (LinearLayout)inflater.inflate(R.layout.objecttrack_controls,null);

		spinnerView = controls.findViewById(R.id.spinner_algs);
		ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(this,
				R.array.tracking_objects, android.R.layout.simple_spinner_item);
		adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		spinnerView.setAdapter(adapter);
		spinnerView.setOnItemSelectedListener(this);

		setControls(controls);
		displayView.setOnTouchListener(this);
	}

	@Override
	public void createNewProcessor() {
		startObjectTracking(spinnerView.getSelectedItemPosition());
	}

	@Override
	public void onItemSelected(AdapterView<?> adapterView, View view, int pos, long id ) {
		startObjectTracking(pos);
	}

	private void startObjectTracking(int pos) {
		TrackerObjectQuad tracker;

		switch (pos) {
			case 0:
				tracker = FactoryTrackerObjectQuad.circulant(null,GrayU8.class);
				break;

			case 1: {
				ImageType imageType = ImageType.pl(3, GrayU8.class);
				tracker = FactoryTrackerObjectQuad.meanShiftComaniciu2003(new ConfigComaniciu2003(false), imageType);
			}break;

			case 2: {
				ImageType imageType = ImageType.pl(3, GrayU8.class);
				tracker = FactoryTrackerObjectQuad.meanShiftComaniciu2003(new ConfigComaniciu2003(true), imageType);
			}break;

			case 3: {
				ImageType imageType = ImageType.pl(3, GrayU8.class);
				tracker = FactoryTrackerObjectQuad.meanShiftLikelihood(30, 5, 256, MeanShiftLikelihoodType.HISTOGRAM, imageType);
			}break;

			case 4:{
				SfotConfig config = new SfotConfig();
				config.numberOfSamples = 10;
				config.robustMaxError = 30;
				tracker = FactoryTrackerObjectQuad.sparseFlow(config,GrayU8.class,null);
			}break;

			case 5:
				tracker = FactoryTrackerObjectQuad.tld(new ConfigTld(false),GrayU8.class);
				break;

			default:
				throw new RuntimeException("Unknown tracker: "+pos);
		}
		setProcessing(new TrackingProcessing(tracker) );
	}

	@Override
	public void onNothingSelected(AdapterView<?> adapterView) {}

	@Override
	public boolean onTouch(View view, MotionEvent motionEvent) {
		if( mode == 0 ) {
			if(MotionEvent.ACTION_DOWN == motionEvent.getActionMasked()) {
				click0.set((int) motionEvent.getX(), (int) motionEvent.getY());
				click1.set((int) motionEvent.getX(), (int) motionEvent.getY());
				mode = 1;
			}
		} else if( mode == 1 ) {
			if(MotionEvent.ACTION_MOVE == motionEvent.getActionMasked()) {
				click1.set((int)motionEvent.getX(),(int)motionEvent.getY());
			} else if(MotionEvent.ACTION_UP == motionEvent.getActionMasked()) {
				click1.set((int)motionEvent.getX(),(int)motionEvent.getY());
				mode = 2;
			}
		}
		return true;
	}

	public void resetPressed( View view ) {
		mode = 0;
	}

	protected class TrackingProcessing extends DemoProcessingAbstract {

		TrackerObjectQuad tracker;
		boolean visible;

		Quadrilateral_F64 location = new Quadrilateral_F64();

		Paint paintSelected = new Paint();
		Paint paintLine0 = new Paint();
		Paint paintLine1 = new Paint();
		Paint paintLine2 = new Paint();
		Paint paintLine3 = new Paint();
		private Paint textPaint = new Paint();

		int width,height;

		public TrackingProcessing(TrackerObjectQuad tracker ) {
			super(tracker.getImageType());
			mode = 0;
			this.tracker = tracker;

			paintSelected.setARGB(0xFF/2,0xFF,0,0);
			paintSelected.setStyle(Paint.Style.FILL_AND_STROKE);

			paintLine0.setColor(Color.RED);
			paintLine1.setColor(Color.MAGENTA);
			paintLine2.setColor(Color.BLUE);
			paintLine3.setColor(Color.GREEN);

			// Create out paint to use for drawing
			textPaint.setARGB(255, 200, 0, 0);
		}

		private void drawLine( Canvas canvas , Point2D_F64 a , Point2D_F64 b , Paint color ) {
			canvas.drawLine((float)a.x,(float)a.y,(float)b.x,(float)b.y,color);
		}

		private void makeInBounds( Point2D_F64 p ) {
			if( p.x < 0 ) p.x = 0;
			else if( p.x >= width )
				p.x = width - 1;

			if( p.y < 0 ) p.y = 0;
			else if( p.y >= height )
				p.y = height - 1;
		}

		private boolean movedSignificantly( Point2D_F64 a , Point2D_F64 b ) {
			if( Math.abs(a.x-b.x) < MINIMUM_MOTION )
				return false;
			if( Math.abs(a.y-b.y) < MINIMUM_MOTION )
				return false;

			return true;
		}

		@Override
		public void initialize(int imageWidth, int imageHeight) {
			this.width = imageWidth;
			this.height = imageHeight;

			float density = screenDensityAdjusted();
			paintSelected.setStrokeWidth(5f*density);
			paintLine0.setStrokeWidth(5f*density);
			paintLine1.setStrokeWidth(5f*density);
			paintLine2.setStrokeWidth(5f*density);
			paintLine3.setStrokeWidth(5f*density);
			textPaint.setTextSize(60*density);
		}

		@Override
		public void onDraw(Canvas canvas, Matrix imageToView) {

			canvas.setMatrix(imageToView);
			if( mode == 1 ) {
				Point2D_F64 a = new Point2D_F64();
				Point2D_F64 b = new Point2D_F64();

				applyToPoint(viewToImage, click0.x, click0.y, a);
				applyToPoint(viewToImage, click1.x, click1.y, b);

				double x0 = Math.min(a.x,b.x);
				double x1 = Math.max(a.x,b.x);
				double y0 = Math.min(a.y,b.y);
				double y1 = Math.max(a.y,b.y);

				canvas.drawRect((int) x0, (int) y0, (int) x1, (int) y1, paintSelected);
			} else if( mode == 2 ) {
				if (!imageToView.invert(viewToImage)) {
					return;
				}
				applyToPoint(viewToImage,click0.x, click0.y, location.a);
				applyToPoint(viewToImage,click1.x, click1.y, location.c);

				// make sure the user selected a valid region
				makeInBounds(location.a);
				makeInBounds(location.c);

				if( movedSignificantly(location.a,location.c) ) {
					// use the selected region and start the tracker
					location.b.set(location.c.x, location.a.y);
					location.d.set( location.a.x, location.c.y );

					visible = true;
					mode = 3;
				} else {
					// the user screw up. Let them know what they did wrong
					runOnUiThread(() -> Toast.makeText(ObjectTrackerActivity.this,
							"Drag a larger region", Toast.LENGTH_SHORT).show());
					mode = 0;
				}
			}

			if( mode >= 2 ) {
				if( visible ) {
					Quadrilateral_F64 q = location;

					drawLine(canvas,q.a,q.b,paintLine0);
					drawLine(canvas,q.b,q.c,paintLine1);
					drawLine(canvas,q.c,q.d,paintLine2);
					drawLine(canvas,q.d,q.a,paintLine3);
				} else {
					canvas.drawText("?",width/2,height/2,textPaint);
				}
			}
		}

		@Override
		public void process(ImageBase input) {
			if( mode == 3 ) {
				tracker.initialize(input, location);
				visible = true;
				mode = 4;
			} else if( mode == 4 ) {
				visible = tracker.process(input,location);
			}
		}
	}
}