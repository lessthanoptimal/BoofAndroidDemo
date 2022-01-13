package org.boofcv.android.detect;

import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.Spinner;

import org.boofcv.android.DemoBitmapCamera2Activity;
import org.boofcv.android.DemoProcessingAbstract;
import org.boofcv.android.R;
import org.ddogleg.struct.DogArray;

import java.util.List;

import boofcv.abst.filter.binary.InputToBinary;
import boofcv.alg.shapes.ellipse.BinaryEllipseDetector;
import boofcv.android.ConvertBitmap;
import boofcv.android.VisualizeImageData;
import boofcv.factory.filter.binary.FactoryThresholdBinary;
import boofcv.factory.shape.ConfigEllipseDetector;
import boofcv.factory.shape.FactoryShapeDetector;
import boofcv.struct.ConfigLength;
import boofcv.struct.image.GrayU8;
import boofcv.struct.image.ImageType;
import georegression.metric.UtilAngle;
import georegression.struct.curve.EllipseRotated_F64;

/**
 * Detects ellipses in an image which are black.
 *
 * @author Peter Abeles
 */
public class DetectBlackEllipseActivity extends DemoBitmapCamera2Activity
		implements AdapterView.OnItemSelectedListener , View.OnTouchListener
{
	Paint paint;

	Spinner spinnerThresholder;

	// which algorithm is processing the image
	int active = -1;

	boolean showInput = true;

	final Object lockBinarization = new Object();
	BinaryEllipseDetector<GrayU8> detector;
	InputToBinary<GrayU8> inputToBinary;

	GrayU8 binary = new GrayU8(1,1);

	public DetectBlackEllipseActivity() {
		super(Resolution.MEDIUM);
		super.changeResolutionOnSlow = true;
	}


	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		paint = new Paint();
		paint.setARGB(0xFF, 0xFF, 0, 0);
		paint.setStyle(Paint.Style.STROKE);
		paint.setFlags(Paint.ANTI_ALIAS_FLAG);

		LayoutInflater inflater = getLayoutInflater();
		LinearLayout controls = (LinearLayout) inflater.inflate(R.layout.detect_black_ellipse_controls, null);


		spinnerThresholder = controls.findViewById(R.id.spinner_algs);
		ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(this,
				R.array.threshold_styles, android.R.layout.simple_spinner_item);
		adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		spinnerThresholder.setAdapter(adapter);
		spinnerThresholder.setOnItemSelectedListener(this);

		setControls(controls);
		displayView.setOnTouchListener(this);
	}

	@Override
	public void createNewProcessor() {
		ConfigEllipseDetector config = new ConfigEllipseDetector();
		config.maxIterations = 1;
		config.numSampleContour = 20;
		detector = FactoryShapeDetector.ellipse(config,GrayU8.class);
		setSelection(spinnerThresholder.getSelectedItemPosition());
		setProcessing(new EllipseProcessing());
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


	private void setSelection( int which ) {
		if( which == active )
			return;
		active = which;

		synchronized (lockBinarization) {
			switch (active) {
				case 0:
					inputToBinary = FactoryThresholdBinary.globalOtsu(0, 255, 1.0,true, GrayU8.class);
					break;

				case 1:
					inputToBinary = FactoryThresholdBinary.localMean(ConfigLength.fixed(10), 0.95, true, GrayU8.class);
					break;

				default:
					throw new RuntimeException("Unknown type");
			}
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

	protected class EllipseProcessing extends DemoProcessingAbstract<GrayU8> {

		RectF r = new RectF();

		DogArray<EllipseRotated_F64> ellipses = new DogArray<>(EllipseRotated_F64::new);

		protected EllipseProcessing() {
			super(ImageType.single(GrayU8.class));
		}

		@Override
		public void initialize(int imageWidth, int imageHeight, int sensorOrientation) {
			binary.reshape(imageWidth,imageHeight);
			paint.setStrokeWidth(5.0f*cameraToDisplayDensity);
		}

		@Override
		public void onDraw(Canvas canvas, Matrix imageToView) {
			drawBitmap(canvas,imageToView);

			canvas.concat(imageToView);
			synchronized (lockGui) {
				for (EllipseRotated_F64 ellipse : ellipses.toList()) {

					float phi = (float) UtilAngle.degree(ellipse.phi);
					float cx = (float) ellipse.center.x;
					float cy = (float) ellipse.center.y;
					float w = (float) ellipse.a;
					float h = (float) ellipse.b;

					//  really skinny ones are probably just a line and not what the user wants
					if (w <= 2 || h <= 2)
						return;

					canvas.save();
					canvas.rotate(phi, cx, cy);
					r.set(cx - w, cy - h, cx + w + 1, cy + h + 1);
					canvas.drawOval(r, paint);
					canvas.restore();
				}
			}
		}

		@Override
		public void process(GrayU8 input) {
			synchronized ( lockBinarization ) {
				inputToBinary.process(input,binary);
			}

			if (showInput) {
				ConvertBitmap.boofToBitmap(input, bitmap, bitmapTmp);
			} else {
				VisualizeImageData.binaryToBitmap(binary, false, bitmap, bitmapTmp);
			}

			detector.process(input,binary);

			List<EllipseRotated_F64> found = detector.getFoundEllipses(null);

			synchronized (lockGui) {
				ellipses.reset();
				for (EllipseRotated_F64 ellipse : found) {
					ellipses.grow().setTo(ellipse);
				}
			}
		}
	}
}