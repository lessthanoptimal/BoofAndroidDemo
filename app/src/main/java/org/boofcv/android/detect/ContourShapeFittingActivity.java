package org.boofcv.android.detect;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.ToggleButton;

import org.boofcv.android.DemoBitmapCamera2Activity;
import org.boofcv.android.DemoProcessingAbstract;
import org.boofcv.android.R;
import org.ddogleg.struct.DogArray;

import java.util.List;

import boofcv.alg.filter.binary.BinaryImageOps;
import boofcv.alg.filter.binary.Contour;
import boofcv.alg.filter.binary.GThresholdImageOps;
import boofcv.alg.filter.binary.LinearContourLabelChang2004;
import boofcv.alg.filter.binary.ThresholdImageOps;
import boofcv.alg.shapes.FitData;
import boofcv.alg.shapes.ShapeFittingOps;
import boofcv.alg.shapes.polyline.splitmerge.PolylineSplitMerge;
import boofcv.android.ConvertBitmap;
import boofcv.android.VisualizeImageData;
import boofcv.struct.ConnectRule;
import boofcv.struct.image.GrayS32;
import boofcv.struct.image.GrayU8;
import boofcv.struct.image.ImageType;
import georegression.metric.UtilAngle;
import georegression.struct.curve.EllipseRotated_F64;
import georegression.struct.point.Point2D_I32;
import georegression.struct.shapes.Polygon2D_I32;

/**
 * Fits different shapes to binary images
 *
 * @author Peter Abeles
 */
public class ContourShapeFittingActivity extends DemoBitmapCamera2Activity
		implements AdapterView.OnItemSelectedListener
{
	Spinner spinnerView;

	volatile boolean down;
	volatile boolean showBinary=true;

	public ContourShapeFittingActivity() {
		super(Resolution.MEDIUM);
		super.changeResolutionOnSlow = true;
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		LayoutInflater inflater = getLayoutInflater();
		LinearLayout controls = (LinearLayout)inflater.inflate(R.layout.shape_fitting_controls,null);

		spinnerView = controls.findViewById(R.id.spinner_algs);
		ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(this,
				R.array.shapes, android.R.layout.simple_spinner_item);
		adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		spinnerView.setAdapter(adapter);
		spinnerView.setOnItemSelectedListener(this);

		ToggleButton toggle = controls.findViewById(R.id.toggle_threshold);
		down = toggle.isChecked();

		toggle.setOnCheckedChangeListener((buttonView, isChecked) -> down = isChecked);

		setControls(controls);
		displayView.setOnTouchListener((view, event) -> {
			switch( event.getActionMasked() ) {
				case MotionEvent.ACTION_DOWN:showBinary=false;break;
				case MotionEvent.ACTION_UP:showBinary=true;break;
				default:return false;
			}
            return true;

        });
	}

	@Override
	public void createNewProcessor() {
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
		}
	}

	@Override
	public void onNothingSelected(AdapterView<?> adapterView) {}

	protected abstract class BaseProcessing extends DemoProcessingAbstract<GrayU8> {
		GrayU8 binary;
		GrayU8 filtered1;
		GrayS32 contourOutput;
		Paint paint = new Paint();
		RectF r = new RectF();
		LinearContourLabelChang2004 findContours = new LinearContourLabelChang2004(ConnectRule.EIGHT);

		protected BaseProcessing() {
			super(ImageType.single(GrayU8.class));
		}

		protected abstract void fitShape( List<Point2D_I32> contour );

		protected abstract void resetShapes();
		protected abstract void finalizeShapes();

		@Override
		public void initialize(int imageWidth, int imageHeight, int sensorOrientation) {
			binary = new GrayU8(imageWidth,imageHeight);
			filtered1 = new GrayU8(imageWidth,imageHeight);
			contourOutput = new GrayS32(imageWidth,imageHeight);

			paint.setStyle(Paint.Style.STROKE);
			paint.setStrokeWidth(3f*cameraToDisplayDensity);
			paint.setColor(Color.RED);
		}

		@Override
		public void process(GrayU8 input) {
			// Select a reasonable threshold
			double mean = GThresholdImageOps.computeOtsu(input,0,255);

			// create a binary image by thresholding
			ThresholdImageOps.threshold(input, binary, (int)mean, down);

			// reduce noise with some filtering
			BinaryImageOps.removePointNoise(binary, filtered1);

			// draw binary image for output
			if( showBinary ) {
				VisualizeImageData.binaryToBitmap(filtered1, false, bitmap, bitmapTmp);
			} else {
				ConvertBitmap.boofToBitmap(input,bitmap,bitmapTmp);
			}

			// draw the ellipses
			findContours.process(filtered1,contourOutput);
			List<Contour> contours = BinaryImageOps.contour(filtered1, ConnectRule.EIGHT,null);

			resetShapes();
			for (Contour contour : contours) {
				List<Point2D_I32> points = contour.external;
				if (points.size() < 20)
					continue;

				fitShape(points);
			}
			finalizeShapes();
			visualizationPending = true;
		}
	}

	protected class EllipseProcessing extends BaseProcessing {

		final FitData<EllipseRotated_F64> ellipseStorage = new FitData<>(new EllipseRotated_F64());
		final DogArray<EllipseRotated_F64> ellipsesVis = new DogArray<>(EllipseRotated_F64::new);
		final DogArray<EllipseRotated_F64> ellipsesWork = new DogArray<>(EllipseRotated_F64::new);

		@Override
		protected void fitShape(List<Point2D_I32> contour) {
			ShapeFittingOps.fitEllipse_I32(contour, 0, false, ellipseStorage);
			ellipsesWork.grow().setTo(ellipseStorage.shape);
		}

		@Override
		protected void resetShapes() {
			ellipsesWork.reset();
		}

		@Override
		protected void finalizeShapes() {
			synchronized (lockGui) {
				ellipsesVis.reset();
				for (int i = 0; i < ellipsesWork.size; i++) {
					ellipsesVis.grow().setTo(ellipsesWork.get(i));
				}
			}
		}

		@Override
		public void onDraw(Canvas canvas, Matrix imageToView) {
			drawBitmap(canvas,imageToView);
			canvas.concat(imageToView);
			synchronized (lockGui) {
				for(int i = 0; i < ellipsesVis.size; i++ ) {
					EllipseRotated_F64 ellipse = ellipsesVis.get(i);
					float phi = (float) UtilAngle.radianToDegree(ellipse.phi);
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
	}

	protected class PolygonProcessing extends BaseProcessing {

		PolylineSplitMerge alg = new PolylineSplitMerge();

		final DogArray<Polygon2D_I32> workPoly = new DogArray<>(Polygon2D_I32::new);
		final DogArray<Polygon2D_I32> visPoly = new DogArray<>(Polygon2D_I32::new);
		Path path = new Path();

		public PolygonProcessing() {
			alg.setLoops(true);
			alg.setMinimumSideLength(10);
			alg.setMaxSides(20);
			alg.setCornerScorePenalty(0.25);
		}

		@Override
		protected void fitShape(List<Point2D_I32> contour) {
			if( !alg.process(contour) )
				return;
			PolylineSplitMerge.CandidatePolyline best = alg.getBestPolyline();
			Polygon2D_I32 poly = workPoly.grow();
			poly.vertexes.resize(best.splits.size);
			for (int i = 0; i < best.splits.size; i++) {
				Point2D_I32 p = contour.get(best.splits.get(i));
				poly.get(i).setTo(p);
			}
		}

		@Override
		protected void resetShapes() {
			workPoly.reset();
		}

		@Override
		protected void finalizeShapes() {
			synchronized (lockGui) {
				visPoly.reset();
				for (int i = 0; i < workPoly.size; i++) {
					visPoly.grow().setTo(workPoly.get(i));
				}
			}
		}

		@Override
		public void onDraw(Canvas canvas, Matrix imageToView) {
			drawBitmap(canvas,imageToView);
			canvas.concat(imageToView);
			synchronized (lockGui) {
				for( int i = 0; i < visPoly.size; i++ ) {
					Polygon2D_I32 poly = visPoly.get(i);
					path.reset();
					for (int j = 0; j < poly.size(); j++) {
						Point2D_I32 p = poly.get(j);
						if(j ==0)
							path.moveTo(p.x,p.y);
						else
							path.lineTo(p.x,p.y);
					}
					path.close();
					canvas.drawPath(path,paint);
//					for (int j = 0,k=poly.size()-1; j < poly.size(); k=j,j++) {
//						Point2D_I32 a = poly.vertexes.get(j);
//						Point2D_I32 b = poly.vertexes.get(k);
//						canvas.drawLine(a.x,a.y,b.x,b.y,paint);
//					}
				}
			}
		}
	}
}