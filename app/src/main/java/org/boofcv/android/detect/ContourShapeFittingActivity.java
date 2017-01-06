package org.boofcv.android.detect;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.ToggleButton;

import org.boofcv.android.DemoVideoDisplayActivity;
import org.boofcv.android.R;

import java.util.List;

import boofcv.alg.filter.binary.BinaryImageOps;
import boofcv.alg.filter.binary.Contour;
import boofcv.alg.filter.binary.GThresholdImageOps;
import boofcv.alg.filter.binary.LinearContourLabelChang2004;
import boofcv.alg.filter.binary.ThresholdImageOps;
import boofcv.alg.shapes.FitData;
import boofcv.alg.shapes.ShapeFittingOps;
import boofcv.android.VisualizeImageData;
import boofcv.android.gui.VideoImageProcessing;
import boofcv.struct.ConnectRule;
import boofcv.struct.PointIndex_I32;
import boofcv.struct.image.GrayS32;
import boofcv.struct.image.GrayU8;
import boofcv.struct.image.ImageType;
import georegression.metric.UtilAngle;
import georegression.struct.point.Point2D_I32;
import georegression.struct.shapes.EllipseRotated_F64;

/**
 * Fits different shapes to binary images
 *
 * @author Peter Abeles
 */
public class ContourShapeFittingActivity extends DemoVideoDisplayActivity
		implements AdapterView.OnItemSelectedListener
{

	Spinner spinnerView;

	volatile boolean down;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		LayoutInflater inflater = getLayoutInflater();
		LinearLayout controls = (LinearLayout)inflater.inflate(R.layout.shape_fitting_controls,null);

		LinearLayout parent = getViewContent();
		parent.addView(controls);

		spinnerView = (Spinner)controls.findViewById(R.id.spinner_algs);
		ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(this,
				R.array.shapes, android.R.layout.simple_spinner_item);
		adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		spinnerView.setAdapter(adapter);
		spinnerView.setOnItemSelectedListener(this);

		ToggleButton toggle = (ToggleButton)controls.findViewById(R.id.toggle_threshold);
		down = toggle.isChecked();

		toggle.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
			@Override
			public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
				down = isChecked;
			}
		});
	}

	@Override
	protected void onResume() {
		super.onResume();
		startShapeFitting(spinnerView.getSelectedItemPosition());
	}

	@Override
	public void onItemSelected(AdapterView<?> adapterView, View view, int pos, long id ) {
		startShapeFitting(pos);
	}

	private void startShapeFitting(int pos) {
		switch (pos) {
			case 0:
				setProcessing(new EllipseProcessing() );
				break;

			case 1:
				setProcessing(new PolygonProcessing() );
				break;
//
//			case 2:
//				setProcessing(new BlurProcessing(FactoryBlurFilter.median(GrayU8.class,2)) );
//				break;
		}
	}

	@Override
	public void onNothingSelected(AdapterView<?> adapterView) {}

	protected abstract class BaseProcessing extends VideoImageProcessing<GrayU8> {
		GrayU8 binary;
		GrayU8 filtered1;
		GrayS32 contourOutput;
		Paint paint = new Paint();
		RectF r = new RectF();
		LinearContourLabelChang2004 findContours = new LinearContourLabelChang2004(ConnectRule.EIGHT);

		protected BaseProcessing() {
			super(ImageType.single(GrayU8.class));
		}

		@Override
		protected void declareImages( int width , int height ) {
			super.declareImages(width, height);

			binary = new GrayU8(width,height);
			filtered1 = new GrayU8(width,height);
			contourOutput = new GrayS32(width,height);

			paint.setStyle(Paint.Style.STROKE);
			paint.setStrokeWidth(3f);
			paint.setColor(Color.RED);
		}

		@Override
		protected void process(GrayU8 input, Bitmap output, byte[] storage) {

			// Select a reasonable threshold
			int mean = GThresholdImageOps.computeOtsu(input,0,255);

			// create a binary image by thresholding
			ThresholdImageOps.threshold(input, binary, mean, down);

			// reduce noise with some filtering
			BinaryImageOps.removePointNoise(binary, filtered1);

			// draw binary image for output
			VisualizeImageData.binaryToBitmap(filtered1, false, output, storage);

			// draw the ellipses
			findContours.process(filtered1,contourOutput);
			List<Contour> contours = findContours.getContours().toList();

			Canvas canvas = new Canvas(output);

			for( Contour contour : contours ) {
				List<Point2D_I32> points = contour.external;
				if( points.size() < 20 )
					continue;

				fitShape(points,canvas);
			}
		}

		protected abstract void fitShape( List<Point2D_I32> contour , Canvas canvas );
	}

	protected class EllipseProcessing extends BaseProcessing {

		FitData<EllipseRotated_F64> ellipse = new FitData<EllipseRotated_F64>(new EllipseRotated_F64());

		@Override
		protected void fitShape(List<Point2D_I32> contour, Canvas canvas) {
			// TODO unroll and recycle this function
			ShapeFittingOps.fitEllipse_I32(contour, 0, false, ellipse);

			float phi = (float)UtilAngle.radianToDegree(ellipse.shape.phi);
			float cx =  (float)ellipse.shape.center.x;
			float cy =  (float)ellipse.shape.center.y;
			float w = (float)ellipse.shape.a;
			float h = (float)ellipse.shape.b;

			//  really skinny ones are probably just a line and not what the user wants
			if( w <= 2 || h <= 2 )
				return;

			canvas.rotate(phi, cx, cy);
			r.set(cx-w,cy-h,cx+w+1,cy+h+1);
			canvas.drawOval(r,paint);
			canvas.rotate(-phi, cx, cy);
		}
	}

	protected class PolygonProcessing extends BaseProcessing {

		@Override
		protected void fitShape(List<Point2D_I32> contour, Canvas canvas) {
			// TODO unroll and recycle this function
			List<PointIndex_I32> poly = ShapeFittingOps.fitPolygon(contour, true, 0.05, 0.025f, 10);

			for( int i = 1; i < poly.size(); i++ ) {
				PointIndex_I32 a = poly.get(i-1);
				PointIndex_I32 b = poly.get(i);

				canvas.drawLine(a.x,a.y,b.x,b.y,paint);
			}

			PointIndex_I32 a = poly.get(poly.size()-1);
			PointIndex_I32 b = poly.get(0);

			canvas.drawLine(a.x,a.y,b.x,b.y,paint);
		}
	}
}