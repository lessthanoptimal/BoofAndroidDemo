package org.boofcv.android.detect;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.ToggleButton;

import org.boofcv.android.DemoVideoDisplayActivity;
import org.boofcv.android.R;
import org.ddogleg.struct.FastQueue;

import boofcv.abst.filter.binary.InputToBinary;
import boofcv.alg.color.ColorHsv;
import boofcv.alg.shapes.polygon.BinaryPolygonDetector;
import boofcv.android.ConvertBitmap;
import boofcv.android.VisualizeImageData;
import boofcv.android.gui.VideoImageProcessing;
import boofcv.factory.filter.binary.FactoryThresholdBinary;
import boofcv.factory.shape.ConfigPolygonDetector;
import boofcv.factory.shape.FactoryShapeDetector;
import boofcv.struct.image.GrayU8;
import boofcv.struct.image.ImageType;
import georegression.struct.point.Point2D_F64;
import georegression.struct.shapes.Polygon2D_F64;

/**
 * Detects polygons in an image which are convex and black.
 *
 * @author Peter Abeles
 */
public class DetectBlackPolygonActivity extends DemoVideoDisplayActivity
		implements AdapterView.OnItemSelectedListener , View.OnTouchListener {

	static final int MAX_SIDES = 20;
	static final int MIN_SIDES = 3;

	Paint paint;

	EditText editMin;
	EditText editMax;
	Spinner spinnerThresholder;
	ToggleButton toggleConvex;

	// which algorithm is processing the image
	int active = -1;

	// minimum and maximum number of sides
	int minSides = 3;
	int maxSides = 5;
	boolean sidesUpdated = false;
	boolean convex;

	boolean showInput = true;

	BinaryPolygonDetector<GrayU8> detector;
	InputToBinary<GrayU8> inputToBinary;

	GrayU8 binary = new GrayU8(1,1);

	int colors[] = new int[ MAX_SIDES - MIN_SIDES + 1];

	public DetectBlackPolygonActivity() {
		double rgb[] = new double[3];

		for (int i = 0; i < colors.length; i++) {
			double frac = i/(double)(colors.length);

			double hue = 2*Math.PI*frac;
			double sat = 1.0;

			ColorHsv.hsvToRgb(hue,sat,255,rgb);

			colors[i] = 255 << 24 | ((int)rgb[0] << 16) | ((int)rgb[1] << 8) | (int)rgb[2];
		}
	}

	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		paint = new Paint();
		paint.setStyle(Paint.Style.STROKE);
		paint.setFlags(Paint.ANTI_ALIAS_FLAG);
		paint.setStrokeWidth(3.0f);

		LayoutInflater inflater = getLayoutInflater();
		LinearLayout controls = (LinearLayout)inflater.inflate(R.layout.detect_black_polygon_controls,null);

		LinearLayout parent = getViewContent();
		parent.addView(controls);

		FrameLayout iv = getViewPreview();
		iv.setOnTouchListener(this);

		spinnerThresholder = (Spinner)controls.findViewById(R.id.spinner_algs);
		ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(this,
				R.array.threshold_styles, android.R.layout.simple_spinner_item);
		adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		spinnerThresholder.setAdapter(adapter);
		spinnerThresholder.setOnItemSelectedListener(this);

		TextView.OnEditorActionListener listener = new EditText.OnEditorActionListener() {
			@Override
			public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
				if (actionId == EditorInfo.IME_ACTION_DONE) {
					checkUpdateSides();
				}
				return false; // pass on to other listeners.
			}
		};

		editMin = (EditText) controls.findViewById(R.id.sides_minimum);
		editMin.setText("" + minSides);
		editMin.setOnEditorActionListener(listener);

		editMax = (EditText) controls.findViewById(R.id.sides_maximum);
		editMax.setText("" + maxSides);
		editMax.setOnEditorActionListener(listener);

		toggleConvex = (ToggleButton) controls.findViewById(R.id.toggle_convex);
		convex = toggleConvex.isChecked();
		toggleConvex.setOnClickListener(new ToggleButton.OnClickListener() {
			@Override
			public void onClick(View v) {
				convex = toggleConvex.isChecked();
				synchronized ( DetectBlackPolygonActivity.this ) {
					detector.setConvex(convex);
				}
			}
		});
	}

	@Override
	protected void onResume() {
		super.onResume();
		ConfigPolygonDetector configPoly = new ConfigPolygonDetector(minSides,maxSides);
		configPoly.convex = convex;

		detector = FactoryShapeDetector.polygon(configPoly,GrayU8.class);
		setSelection(spinnerThresholder.getSelectedItemPosition());
		setProcessing(new PolygonProcessing());
	}

	@Override
	protected void onPause() {
		super.onPause();
		active = -1;
	}

	public void pressedHelp( View view ) {
		Intent intent = new Intent(this, DetectBlackPolygonHelpActivity.class);
		startActivity(intent);
	}

	private void checkUpdateSides() {
		int min = Integer.parseInt(editMin.getText().toString());
		int max = Integer.parseInt(editMax.getText().toString());

		boolean changed = false;
		if( min < MIN_SIDES ) {
			min = MIN_SIDES;
			changed = true;
		}
		if( max > MAX_SIDES ) {
			max = MAX_SIDES;
			changed = true;
		}

		if( min > max ) {
			min = max;
			changed = true;
		}

		if( changed ) {
			editMin.setText(""+min);
			editMax.setText(""+max);
		}
		this.minSides = min;
		this.maxSides = max;

		synchronized ( this ) {
			detector.setNumberOfSides(minSides,maxSides);
		}
	}

	private void setSelection( int which ) {
		if( which == active )
			return;
		active = which;

		switch( active ) {
			case 0 :
				inputToBinary = FactoryThresholdBinary.globalOtsu(0, 255, true, GrayU8.class);
				break;

			case 1:
				inputToBinary = FactoryThresholdBinary.localSquare(10,0.95,true,GrayU8.class);
				break;

			default:
				throw new RuntimeException("Unknown type");
		}
	}


	@Override
	public void onItemSelected(AdapterView<?> adapterView, View view, int pos, long id) {
		setSelection( pos );
	}

	@Override
	public void onNothingSelected(AdapterView<?> adapterView) {}

	@Override
	public boolean onTouch(View v, MotionEvent event) {
		if( MotionEvent.ACTION_DOWN == event.getActionMasked()) {
			showInput = !showInput;
			return true;
		}
		return false;
	}

	protected class PolygonProcessing extends VideoImageProcessing<GrayU8> {

		protected PolygonProcessing() {
			super(ImageType.single(GrayU8.class));
		}

		@Override
		protected void declareImages(int width, int height) {
			super.declareImages(width, height);
			binary.reshape(width,height);
		}

		@Override
		protected void process(GrayU8 image, Bitmap output, byte[] storage) {
			if( sidesUpdated ) {
				sidesUpdated = false;
				detector.setNumberOfSides(minSides,maxSides);
			}

			synchronized ( this ) {
				inputToBinary.process(image,binary);
			}

			detector.process(image,binary);

			if( showInput ) {
				ConvertBitmap.boofToBitmap(image,output,storage);
			} else {
				VisualizeImageData.binaryToBitmap(binary,false,output,storage);
			}

			Canvas canvas = new Canvas(output);

			FastQueue<Polygon2D_F64> found = detector.getFoundPolygons();

			for( Polygon2D_F64 s : found.toList() )  {
				paint.setColor(colors[s.size()-MIN_SIDES]);

				for (int i = 1; i < s.size(); i++) {
					Point2D_F64 a = s.get(i-1);
					Point2D_F64 b = s.get(i);
					canvas.drawLine((float)a.x,(float)a.y,(float)b.x,(float)b.y,paint);
				}
				Point2D_F64 a = s.get(s.size()-1);
				Point2D_F64 b = s.get(0);
				canvas.drawLine((float)a.x,(float)a.y,(float)b.x,(float)b.y,paint);
			}
		}
	}
}