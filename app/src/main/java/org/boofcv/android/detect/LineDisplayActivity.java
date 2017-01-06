package org.boofcv.android.detect;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;

import org.boofcv.android.DemoVideoDisplayActivity;
import org.boofcv.android.R;
import org.ddogleg.struct.FastQueue;

import java.util.List;

import boofcv.abst.feature.detect.line.DetectLine;
import boofcv.abst.feature.detect.line.DetectLineSegment;
import boofcv.alg.feature.detect.line.LineImageOps;
import boofcv.android.ConvertBitmap;
import boofcv.android.gui.VideoRenderProcessing;
import boofcv.factory.feature.detect.line.ConfigHoughFoot;
import boofcv.factory.feature.detect.line.ConfigHoughPolar;
import boofcv.factory.feature.detect.line.FactoryDetectLineAlgs;
import boofcv.struct.image.GrayS16;
import boofcv.struct.image.GrayU8;
import boofcv.struct.image.ImageType;
import georegression.struct.line.LineParametric2D_F32;
import georegression.struct.line.LineSegment2D_F32;

/**
 * Displays detected lines.  User can adjust the number of lines it will display.  Default is set to three to
 * reduce false positives.
 *
 * @author Peter Abeles
 */
public class LineDisplayActivity extends DemoVideoDisplayActivity
		implements AdapterView.OnItemSelectedListener {

	Paint paint;

	EditText editLines;
	Spinner spinner;

	// which algorithm is processing the image
	int active = -1;
	// the number of lines its configured to detect
	int numLines = 3;

	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		paint = new Paint();
		paint.setColor(Color.RED);
		paint.setStyle(Paint.Style.STROKE);
		paint.setStrokeWidth(2.0f);

		LayoutInflater inflater = getLayoutInflater();
		LinearLayout controls = (LinearLayout)inflater.inflate(R.layout.detect_line_controls,null);

		LinearLayout parent = getViewContent();
		parent.addView(controls);

		spinner = (Spinner)controls.findViewById(R.id.spinner_algs);
		ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(this,
				R.array.line_features, android.R.layout.simple_spinner_item);
		adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		spinner.setAdapter(adapter);
		spinner.setOnItemSelectedListener(this);

		editLines = (EditText) controls.findViewById(R.id.num_lines);
		editLines.setText("" + numLines);
		editLines.setOnEditorActionListener(
				new EditText.OnEditorActionListener() {
					@Override
					public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
						if ( actionId == EditorInfo.IME_ACTION_DONE ) {
							checkUpdateLines();
						}
						return false; // pass on to other listeners.
					}
				});
		// TODO This doesn't cover the back where the user dismisses the keyboard with the back button
	}

	@Override
	protected void onResume() {
		super.onResume();
		setSelection( spinner.getSelectedItemPosition() );
	}

	@Override
	protected void onPause() {
		super.onPause();
		active = -1;
	}

	private void setSelection( int which ) {
		if( which == active )
			return;
		active = which;

		createLineDetector();
	}

	private void createLineDetector() {
		DetectLine<GrayU8> detector = null;
		DetectLineSegment<GrayU8> detectorSegment = null;

		switch( active ) {
			case 0:
				detector = FactoryDetectLineAlgs.houghFoot(
						new ConfigHoughFoot(5,6,5,40,numLines),GrayU8.class,GrayS16.class);
				break;

			case 1:
				detector = FactoryDetectLineAlgs.houghPolar(
						new ConfigHoughPolar(5,6,2,Math.PI/120.0,40,numLines),GrayU8.class,GrayS16.class);
				break;

			default:
				throw new RuntimeException("Unknown selection");
		}

		if( detector != null )
			setProcessing(new LineProcessing(detector));
		else {
			setProcessing(new LineProcessing(detectorSegment));
		}
	}

	private void checkUpdateLines() {
		try {
			int num = Integer.parseInt(editLines.getText().toString());
			if( numLines == num )
				return;

			if( num > 0 && num <= 30  ) {
				numLines = num;
				createLineDetector();
				return;
			}
		} catch( RuntimeException e ) {

		}
		// undo the bad change
		editLines.setText(""+numLines);
	}

	@Override
	public void onItemSelected(AdapterView<?> adapterView, View view, int pos, long id) {
		setSelection( pos );
	}

	@Override
	public void onNothingSelected(AdapterView<?> adapterView) {}

	protected class LineProcessing extends VideoRenderProcessing<GrayU8> {
		DetectLine<GrayU8> detector;
		DetectLineSegment<GrayU8> detectorSegment = null;

		FastQueue<LineSegment2D_F32> lines = new FastQueue<LineSegment2D_F32>(LineSegment2D_F32.class,true);

		Bitmap bitmap;
		byte[] storage;

		public LineProcessing(DetectLine<GrayU8> detector) {
			super(ImageType.single(GrayU8.class));
			this.detector = detector;
		}

		public LineProcessing(DetectLineSegment<GrayU8> detectorSegment) {
			super(ImageType.single(GrayU8.class));
			this.detectorSegment = detectorSegment;
		}

		@Override
		protected void declareImages(int width, int height) {
			super.declareImages(width, height);
			bitmap = Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888);
			storage = ConvertBitmap.declareStorage(bitmap,storage);
		}

		@Override
		protected void process(GrayU8 gray) {

			if( detector != null ) {
				List<LineParametric2D_F32> found = detector.detect(gray);

				synchronized ( lockGui ) {
					ConvertBitmap.grayToBitmap(gray,bitmap,storage);
					lines.reset();
					for( LineParametric2D_F32 p : found ) {
						LineSegment2D_F32 ls = LineImageOps.convert(p, gray.width,gray.height);
						lines.grow().set(ls.a,ls.b);
					}
				}

			} else {
				List<LineSegment2D_F32> found = detectorSegment.detect(gray);
				synchronized ( lockGui ) {
					ConvertBitmap.grayToBitmap(gray,bitmap,storage);
					lines.reset();
					for( LineSegment2D_F32 p : found ) {
						lines.grow().set(p.a,p.b);
					}
				}
			}
		}

		@Override
		protected void render(Canvas canvas, double imageToOutput) {
			canvas.drawBitmap(bitmap,0,0,null);

			for( LineSegment2D_F32 s : lines.toList() )  {
				canvas.drawLine(s.a.x,s.a.y,s.b.x,s.b.y,paint);
			}
		}
	}
}