package org.boofcv.android.detect;

import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.ToggleButton;

import org.boofcv.android.DemoBitmapCamera2Activity;
import org.boofcv.android.DemoProcessing;
import org.boofcv.android.R;
import org.boofcv.android.misc.MiscUtil;
import org.ddogleg.struct.FastQueue;

import java.util.ArrayList;
import java.util.List;

import boofcv.abst.filter.binary.InputToBinary;
import boofcv.alg.color.ColorHsv;
import boofcv.alg.shapes.polygon.DetectPolygonBinaryGrayRefine;
import boofcv.android.ConvertBitmap;
import boofcv.android.VisualizeImageData;
import boofcv.factory.filter.binary.FactoryThresholdBinary;
import boofcv.factory.shape.ConfigPolygonDetector;
import boofcv.factory.shape.FactoryShapeDetector;
import boofcv.struct.ConfigLength;
import boofcv.struct.image.GrayU8;
import boofcv.struct.image.ImageType;
import georegression.struct.shapes.Polygon2D_F64;

/**
 * Detects polygons in an image which are convex and black.
 *
 * @author Peter Abeles
 */
public class DetectBlackPolygonActivity extends DemoBitmapCamera2Activity
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

	DetectPolygonBinaryGrayRefine<GrayU8> detector;
	InputToBinary<GrayU8> inputToBinary;

	GrayU8 binary = new GrayU8(1,1);

	int colors[] = new int[ MAX_SIDES - MIN_SIDES + 1];

	public DetectBlackPolygonActivity() {
		super(Resolution.MEDIUM);

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
		paint.setStrokeWidth(2.0f*screenDensityAdjusted());

		LayoutInflater inflater = getLayoutInflater();
		LinearLayout controls = (LinearLayout)inflater.inflate(R.layout.detect_black_polygon_controls,null);

		spinnerThresholder = controls.findViewById(R.id.spinner_algs);
		ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(this,
				R.array.threshold_styles, android.R.layout.simple_spinner_item);
		adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		spinnerThresholder.setAdapter(adapter);
		spinnerThresholder.setOnItemSelectedListener(this);

		TextView.OnEditorActionListener listener = (v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                checkUpdateSides();
            }
            return false; // pass on to other listeners.
        };

		editMin = controls.findViewById(R.id.sides_minimum);
		editMin.setText("" + minSides);
		editMin.setOnEditorActionListener(listener);

		editMax = controls.findViewById(R.id.sides_maximum);
		editMax.setText("" + maxSides);
		editMax.setOnEditorActionListener(listener);

		toggleConvex = controls.findViewById(R.id.toggle_convex);
		convex = toggleConvex.isChecked();
		toggleConvex.setOnClickListener(v -> {
            convex = toggleConvex.isChecked();
            synchronized ( DetectBlackPolygonActivity.this ) {
                detector.getDetector().setConvex(convex);
            }
        });

		setControls(controls);
		displayView.setOnTouchListener(this);
	}

	@Override
	protected void onResume() {
		super.onResume();
		ConfigPolygonDetector configPoly = new ConfigPolygonDetector(minSides,maxSides);

		detector = FactoryShapeDetector.polygon(configPoly,GrayU8.class);
		detector.getDetector().setConvex(convex);
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
			detector.getDetector().setNumberOfSides(minSides,maxSides);
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
				inputToBinary = FactoryThresholdBinary.localMean(ConfigLength.fixed(10),0.95,true,GrayU8.class);
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

	protected class PolygonProcessing implements DemoProcessing<GrayU8> {

		final List<Polygon2D_F64> found = new ArrayList<>();
		final FastQueue<Polygon2D_F64> copy = new FastQueue<>(Polygon2D_F64.class,true);

		Path path = new Path();

		@Override
		public void initialize(int imageWidth, int imageHeight) {
			binary.reshape(imageWidth,imageHeight);
		}

		@Override
		public void onDraw(Canvas canvas, Matrix imageToView) {
			synchronized (bitmapLock) {
				canvas.drawBitmap(bitmap, imageToView, null);
			}

			canvas.setMatrix(imageToView);
			synchronized (copy) {
				for( int i = 0; i < copy.size; i++ ) {
					Polygon2D_F64 s = copy.get(i);
					paint.setColor(colors[s.size() - MIN_SIDES]);

					MiscUtil.renderPolygon(s,path,canvas,paint);
				}
			}
		}

		@Override
		public void process(GrayU8 image) {
			if( sidesUpdated ) {
				sidesUpdated = false;
				detector.getDetector().setNumberOfSides(minSides,maxSides);
			}

			synchronized ( this ) {
				inputToBinary.process(image,binary);
			}

			detector.process(image,binary);

			synchronized (copy) {
				copy.reset();
				detector.getPolygons(found, null);
				for (int i = 0; i < found.size(); i++) {
					copy.grow().set(found.get(i));
				}
			}

			synchronized (bitmapLock) {
				if (showInput) {
					ConvertBitmap.boofToBitmap(image, bitmap,bitmapTmp );
				} else {
					VisualizeImageData.binaryToBitmap(binary, false, bitmap, bitmapTmp);
				}
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
			return ImageType.single(GrayU8.class);
		}
	}
}