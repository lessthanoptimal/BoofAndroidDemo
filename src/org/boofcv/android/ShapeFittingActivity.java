package org.boofcv.android;

import android.graphics.*;
import android.hardware.Camera;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.Spinner;
import boofcv.alg.feature.shapes.FitData;
import boofcv.alg.feature.shapes.ShapeFittingOps;
import boofcv.alg.filter.binary.BinaryImageOps;
import boofcv.alg.filter.binary.Contour;
import boofcv.alg.filter.binary.LinearContourLabelChang2004;
import boofcv.alg.filter.binary.ThresholdImageOps;
import boofcv.alg.misc.ImageStatistics;
import boofcv.android.VisualizeImageData;
import boofcv.struct.image.ImageSInt32;
import boofcv.struct.image.ImageType;
import boofcv.struct.image.ImageUInt8;
import georegression.metric.UtilAngle;
import georegression.struct.point.Point2D_I32;
import georegression.struct.shapes.EllipseRotated_F64;

import java.util.ArrayList;
import java.util.List;

/**
 * Fits different shapes to binary images
 *
 * @author Peter Abeles
 */
public class ShapeFittingActivity extends VideoDisplayActivity
		implements AdapterView.OnItemSelectedListener
{

	Spinner spinnerView;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		LayoutInflater inflater = getLayoutInflater();
		LinearLayout controls = (LinearLayout)inflater.inflate(R.layout.select_algorithm,null);

		LinearLayout parent = (LinearLayout)findViewById(R.id.camera_preview_parent);
		parent.addView(controls);

		spinnerView = (Spinner)controls.findViewById(R.id.spinner_algs);
		ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(this,
				R.array.shapes, android.R.layout.simple_spinner_item);
		adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		spinnerView.setAdapter(adapter);
		spinnerView.setOnItemSelectedListener(this);
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

//			case 1:
//				setProcessing(new BlurProcessing(FactoryBlurFilter.gaussian(ImageUInt8.class,-1,2)) );
//				break;
//
//			case 2:
//				setProcessing(new BlurProcessing(FactoryBlurFilter.median(ImageUInt8.class,2)) );
//				break;
		}
	}

	@Override
	public void onNothingSelected(AdapterView<?> adapterView) {}

	protected class EllipseProcessing extends BoofImageProcessing<ImageUInt8> {
		ImageUInt8 binary;
		ImageUInt8 filtered1;
		ImageUInt8 filtered2;
		ImageSInt32 contourOutput;
		Paint paint = new Paint();
		RectF r = new RectF();
		LinearContourLabelChang2004 findContours = new LinearContourLabelChang2004(8);

		FitData<EllipseRotated_F64> ellipse = new FitData<EllipseRotated_F64>();
		List<List<Point2D_I32>> allContours = new ArrayList<List<Point2D_I32>>();

		protected EllipseProcessing() {
			super(ImageType.single(ImageUInt8.class));
		}

		@Override
		public void init(View view, Camera camera) {
			super.init(view, camera);
			Camera.Size size = camera.getParameters().getPreviewSize();

			binary = new ImageUInt8(size.width,size.height);
			filtered1 = new ImageUInt8(size.width,size.height);
			filtered2 = new ImageUInt8(size.width,size.height);
			contourOutput = new ImageSInt32(size.width,size.height);

			paint.setStyle(Paint.Style.STROKE);
			paint.setStrokeWidth(3f);
			paint.setColor(Color.RED);
			ellipse.shape = new EllipseRotated_F64();
		}

		@Override
		protected void process(ImageUInt8 gray, Bitmap output, byte[] storage) {
			// use the mean value to threshold the image
			int mean = (int)ImageStatistics.mean(gray);

			// create a binary image by thresholding
			ThresholdImageOps.threshold(gray, binary, mean, true);

			// reduce noise with some filtering
			BinaryImageOps.erode8(binary, filtered1);
			BinaryImageOps.dilate8(filtered1, filtered2);

			// draw binary image for output
			VisualizeImageData.binaryToBitmap(filtered2, output, storage);

			// draw the ellipses
			findContours.process(filtered2,contourOutput);
			List<Contour> contours = findContours.getContours().toList();

			allContours.clear();
			for( Contour contour : contours ) {
				allContours.add( contour.external);
				allContours.addAll( contour.internal );
			}

			Canvas canvas = new Canvas(output);

			for( List<Point2D_I32> contour : allContours ) {
				// TODO unroll and recycle this function
				ShapeFittingOps.fitEllipse_I32(contour, 0, false, ellipse);

				float phi = (float)UtilAngle.radianToDegree(ellipse.shape.phi);
				float cx =  (float)ellipse.shape.center.x;
				float cy =  (float)ellipse.shape.center.y;
				float w = (float)ellipse.shape.a;
				float h = (float)ellipse.shape.b;

				canvas.rotate(phi, cx, cy);
				r.set(cx-w,cy-h,cx+w+1,cy+h+1);
				canvas.drawOval(r,paint);
				canvas.rotate(-phi, cx, cy);
			}
		}
	}
}