package org.boofcv.android.tracker;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.Toast;

import org.boofcv.android.DemoVideoDisplayActivity;
import org.boofcv.android.R;

import boofcv.abst.tracker.ConfigComaniciu2003;
import boofcv.abst.tracker.ConfigTld;
import boofcv.abst.tracker.MeanShiftLikelihoodType;
import boofcv.abst.tracker.TrackerObjectQuad;
import boofcv.alg.tracker.sfot.SfotConfig;
import boofcv.android.ConvertBitmap;
import boofcv.android.gui.VideoImageProcessing;
import boofcv.core.image.ConvertImage;
import boofcv.factory.tracker.FactoryTrackerObjectQuad;
import boofcv.struct.image.GrayU8;
import boofcv.struct.image.ImageBase;
import boofcv.struct.image.ImageType;
import boofcv.struct.image.Planar;
import georegression.struct.point.Point2D_F64;
import georegression.struct.point.Point2D_I32;
import georegression.struct.shapes.Quadrilateral_F64;

/**
 * Allow the user to select an object in the image and then track it
 *
 * @author Peter Abeles
 */
public class ObjectTrackerActivity extends DemoVideoDisplayActivity
		implements AdapterView.OnItemSelectedListener, View.OnTouchListener
{

	Spinner spinnerView;

	int mode = 0;

	// size of the minimum square which the user can select
	final static int MINIMUM_MOTION = 20;

	Point2D_I32 click0 = new Point2D_I32();
	Point2D_I32 click1 = new Point2D_I32();

	public ObjectTrackerActivity() {
		super(true);
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		LayoutInflater inflater = getLayoutInflater();
		LinearLayout controls = (LinearLayout)inflater.inflate(R.layout.objecttrack_controls,null);

		LinearLayout parent = getViewContent();
		parent.addView(controls);

		FrameLayout iv = getViewPreview();
		iv.setOnTouchListener(this);

		spinnerView = (Spinner)controls.findViewById(R.id.spinner_algs);
		ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(this,
				R.array.tracking_objects, android.R.layout.simple_spinner_item);
		adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		spinnerView.setAdapter(adapter);
		spinnerView.setOnItemSelectedListener(this);
	}

	@Override
	protected void onResume() {
		super.onResume();
		startObjectTracking(spinnerView.getSelectedItemPosition());
	}

	@Override
	public void onItemSelected(AdapterView<?> adapterView, View view, int pos, long id ) {
		startObjectTracking(pos);
	}

	private void startObjectTracking(int pos) {
		TrackerObjectQuad tracker = null;
		ImageType imageType = null;

		switch (pos) {
			case 0:
				imageType = ImageType.single(GrayU8.class);
				tracker = FactoryTrackerObjectQuad.circulant(null,GrayU8.class);
				break;

			case 1:
				imageType = ImageType.pl(3, GrayU8.class);
				tracker = FactoryTrackerObjectQuad.meanShiftComaniciu2003(new ConfigComaniciu2003(false),imageType);
				break;

			case 2:
				imageType = ImageType.pl(3, GrayU8.class);
				tracker = FactoryTrackerObjectQuad.meanShiftComaniciu2003(new ConfigComaniciu2003(true),imageType);
				break;

			case 3:
				imageType = ImageType.pl(3, GrayU8.class);
				tracker = FactoryTrackerObjectQuad.meanShiftLikelihood(30,5,256, MeanShiftLikelihoodType.HISTOGRAM,imageType);
				break;

			case 4:{
				imageType = ImageType.single(GrayU8.class);
				SfotConfig config = new SfotConfig();
				config.numberOfSamples = 10;
				config.robustMaxError = 30;
				tracker = FactoryTrackerObjectQuad.sparseFlow(config,GrayU8.class,null);
			}break;

			case 5:
				imageType = ImageType.single(GrayU8.class);
				tracker = FactoryTrackerObjectQuad.tld(new ConfigTld(false),GrayU8.class);
				break;

			default:
				throw new RuntimeException("Unknown tracker: "+pos);
		}
		setProcessing(new TrackingProcessing(tracker,imageType) );
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

	protected class TrackingProcessing<T extends ImageBase> extends VideoImageProcessing<Planar<GrayU8>>
	{

		T input;
		ImageType<T> inputType;

		TrackerObjectQuad tracker;
		boolean visible;

		Quadrilateral_F64 location = new Quadrilateral_F64();

		Paint paintSelected = new Paint();
		Paint paintLine0 = new Paint();
		Paint paintLine1 = new Paint();
		Paint paintLine2 = new Paint();
		Paint paintLine3 = new Paint();
		private Paint textPaint = new Paint();

		protected TrackingProcessing(TrackerObjectQuad tracker , ImageType<T> inputType) {
			super(ImageType.pl(3, GrayU8.class));
			this.inputType = inputType;

			if( inputType.getFamily() == ImageType.Family.GRAY ) {
				input = inputType.createImage(1,1);
			}

			mode = 0;
			this.tracker = tracker;

			paintSelected.setColor(Color.argb(0xFF/2,0xFF,0,0));

			paintLine0.setColor(Color.RED);
			paintLine0.setStrokeWidth(3f);
			paintLine1.setColor(Color.MAGENTA);
			paintLine1.setStrokeWidth(3f);
			paintLine2.setColor(Color.BLUE);
			paintLine2.setStrokeWidth(3f);
			paintLine3.setColor(Color.GREEN);
			paintLine3.setStrokeWidth(3f);

			// Create out paint to use for drawing
			textPaint.setARGB(255, 200, 0, 0);
			textPaint.setTextSize(60);

		}

		@Override
		protected void process(Planar<GrayU8> input, Bitmap output, byte[] storage)
		{
			updateTracker(input);
			visualize(input, output, storage);
		}

		private void updateTracker(Planar<GrayU8> color) {
			if( inputType.getFamily() == ImageType.Family.GRAY ) {
				input.reshape(color.width,color.height);
				ConvertImage.average(color,(GrayU8)input);
			} else {
				input = (T)color;
			}

			if( mode == 2 ) {
				imageToOutput(click0.x, click0.y, location.a);
				imageToOutput(click1.x, click1.y, location.c);

				// make sure the user selected a valid region
				makeInBounds(location.a);
				makeInBounds(location.c);

				if( movedSignificantly(location.a,location.c) ) {
					// use the selected region and start the tracker
					location.b.set(location.c.x, location.a.y);
					location.d.set( location.a.x, location.c.y );

					tracker.initialize(input, location);
					visible = true;
					mode = 3;
				} else {
					// the user screw up. Let them know what they did wrong
					runOnUiThread(new Runnable() {
						@Override
						public void run() {
							Toast.makeText(ObjectTrackerActivity.this, "Drag a larger region", Toast.LENGTH_SHORT).show();
						}
					});
					mode = 0;
				}
			} else if( mode == 3 ) {
				visible = tracker.process(input,location);
			}
		}

		private void visualize(Planar<GrayU8> color, Bitmap output, byte[] storage) {
			ConvertBitmap.multiToBitmap(color, output, storage);
			Canvas canvas = new Canvas(output);

			if( mode == 1 ) {
				Point2D_F64 a = new Point2D_F64();
				Point2D_F64 b = new Point2D_F64();

				imageToOutput(click0.x, click0.y, a);
				imageToOutput(click1.x, click1.y, b);

				canvas.drawRect((int)a.x,(int)a.y,(int)b.x,(int)b.y,paintSelected);
			} else if( mode >= 2 ) {
				if( visible ) {
					Quadrilateral_F64 q = location;

					drawLine(canvas,q.a,q.b,paintLine0);
					drawLine(canvas,q.b,q.c,paintLine1);
					drawLine(canvas,q.c,q.d,paintLine2);
					drawLine(canvas,q.d,q.a,paintLine3);
				} else {
					canvas.drawText("?",color.width/2,color.height/2,textPaint);
				}
			}
		}

		private void drawLine( Canvas canvas , Point2D_F64 a , Point2D_F64 b , Paint color ) {
			canvas.drawLine((float)a.x,(float)a.y,(float)b.x,(float)b.y,color);
		}

		private void makeInBounds( Point2D_F64 p ) {
			if( p.x < 0 ) p.x = 0;
			else if( p.x >= input.width )
				p.x = input.width - 1;

			if( p.y < 0 ) p.y = 0;
			else if( p.y >= input.height )
				p.y = input.height - 1;

		}

		private boolean movedSignificantly( Point2D_F64 a , Point2D_F64 b ) {
			if( Math.abs(a.x-b.x) < MINIMUM_MOTION )
				return false;
			if( Math.abs(a.y-b.y) < MINIMUM_MOTION )
				return false;

			return true;
		}
	}
}