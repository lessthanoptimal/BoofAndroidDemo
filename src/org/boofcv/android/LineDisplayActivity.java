package org.boofcv.android;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.*;
import boofcv.abst.feature.detect.line.DetectLine;
import boofcv.abst.feature.detect.line.DetectLineSegment;
import boofcv.alg.feature.detect.line.LineImageOps;
import boofcv.android.ConvertBitmap;
import boofcv.factory.feature.detect.line.FactoryDetectLineAlgs;
import boofcv.struct.image.ImageSInt16;
import boofcv.struct.image.ImageUInt8;
import georegression.struct.line.LineParametric2D_F32;
import georegression.struct.line.LineSegment2D_F32;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Peter Abeles
 */
public class LineDisplayActivity extends VideoDisplayActivity
		implements AdapterView.OnItemSelectedListener  {

	int width;
	Paint paint;

	int active = -1;

	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		paint = new Paint();
		paint.setColor(Color.RED);
		paint.setStyle(Paint.Style.STROKE);
		paint.setStrokeWidth(2.0f);

		width = mCamera.getParameters().getPreviewSize().width;

		LayoutInflater inflater = getLayoutInflater();
		LinearLayout controls = (LinearLayout)inflater.inflate(R.layout.select_algorithm,null);

		LinearLayout parent = (LinearLayout)findViewById(R.id.camera_preview_parent);
		parent.addView(controls);

		Spinner spinner = (Spinner)controls.findViewById(R.id.spinner_algs);
		ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(this,
				R.array.line_features, android.R.layout.simple_spinner_item);
		adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		spinner.setAdapter(adapter);
		spinner.setOnItemSelectedListener(this);

		setSelection( spinner.getSelectedItemPosition() );
	}

	private void setSelection( int which ) {
		if( which == active )
			return;
		active = which;

		DetectLine<ImageUInt8> detector = null;
		DetectLineSegment<ImageUInt8> detectorSegment = null;


		switch( which ) {
			case 0:
				detector = FactoryDetectLineAlgs.houghFoot(5,6,5,40,10,ImageUInt8.class,ImageSInt16.class);
				break;

			case 1:
				detector = FactoryDetectLineAlgs.houghPolar(5,6,2,Math.PI/120.0,40,10,ImageUInt8.class,ImageSInt16.class);
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

	@Override
	public void onItemSelected(AdapterView<?> adapterView, View view, int pos, long id) {
		setSelection( pos );
	}

	@Override
	public void onNothingSelected(AdapterView<?> adapterView) {}


	protected class LineProcessing extends BoofRenderProcessing {
		DetectLine<ImageUInt8> detector;
		DetectLineSegment<ImageUInt8> detectorSegment = null;

		List<LineSegment2D_F32> lines = new ArrayList<LineSegment2D_F32>();

		Bitmap bitmap;
		byte[] storage;

		public LineProcessing(DetectLine<ImageUInt8> detector) {
			this.detector = detector;
		}

		public LineProcessing(DetectLineSegment<ImageUInt8> detectorSegment) {
			this.detectorSegment = detectorSegment;
		}

		@Override
		protected void declareImages(int width, int height) {
			super.declareImages(width, height);
			bitmap = Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888);
			storage = ConvertBitmap.declareStorage(bitmap,storage);
		}

		@Override
		protected void process(ImageUInt8 gray) {
			ConvertBitmap.grayToBitmap(gray,bitmap,storage);

			if( detector != null ) {
				List<LineParametric2D_F32> found = detector.detect(gray);
				lines.clear();
				for( LineParametric2D_F32 p : found ) {
					LineSegment2D_F32 ls = LineImageOps.convert(p, gray.width,gray.height);
					lines.add(ls);
				}
			} else {
				lines.clear();
				lines.addAll( detectorSegment.detect(gray) );
			}
		}

		@Override
		protected void render(Canvas canvas, double imageToOutput) {
			canvas.drawBitmap(bitmap,0,0,null);

			for( LineSegment2D_F32 s : lines )  {
				canvas.drawLine((int)s.a.x,(int)s.a.y,(int)s.b.x,(int)s.b.y,paint);
			}
		}
	}
}