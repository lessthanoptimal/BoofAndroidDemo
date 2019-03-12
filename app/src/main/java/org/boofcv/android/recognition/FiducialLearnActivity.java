package org.boofcv.android.recognition;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Spinner;

import org.boofcv.android.DemoCamera2Activity;
import org.boofcv.android.DemoProcessingAbstract;
import org.boofcv.android.R;
import org.boofcv.android.misc.MiscUtil;
import org.boofcv.android.misc.UnitsDistance;
import org.ddogleg.sorting.QuickSelect;
import org.ddogleg.struct.GrowQueue_F64;
import org.ddogleg.struct.GrowQueue_I32;

import java.util.List;

import boofcv.alg.distort.LensDistortionNarrowFOV;
import boofcv.android.VisualizeImageData;
import boofcv.factory.distort.LensDistortionFactory;
import boofcv.struct.calib.CameraPinholeBrown;
import boofcv.struct.image.GrayU8;
import boofcv.struct.image.ImageType;
import georegression.geometry.UtilPolygons2D_F64;
import georegression.metric.Area2D_F64;
import georegression.metric.Intersection2D_F64;
import georegression.struct.point.Point2D_F64;
import georegression.struct.shapes.Polygon2D_F64;
import georegression.struct.shapes.Quadrilateral_F64;

/**
 * Opens a view which selects quadrilaterals in the image and lets you select one to use
 * as a pattern in a fiducial.
 *
 * @author Peter Abeles
 */
public class FiducialLearnActivity extends DemoCamera2Activity
		implements View.OnTouchListener
{
	public static final String TAG = "FiducialLearnActivity";

	boolean touched = false;
	Point2D_F64 touch = new Point2D_F64();
	GrayU8 touchedFiducial = new GrayU8(1,1);
	boolean dialogActive = false;

	FiducialManager manager;

	public FiducialLearnActivity() {
		super(Resolution.MEDIUM);
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		setControls(null);
		displayView.setOnTouchListener(this);

		manager = new FiducialManager(this);
		manager.loadList();
	}

	@Override
	public void createNewProcessor() {
		setProcessing(new FiducialProcessor());
	}

	@Override
	public boolean onTouch(View v, MotionEvent event) {
		if( dialogActive )
			return false;

		touched = true;

		this.touch.x = event.getX();
		this.touch.y = event.getY();

		return true;
	}

	protected class FiducialProcessor extends DemoProcessingAbstract<GrayU8> {

		final FiducialDetector detector = new FiducialDetector();

		Paint paintBorder = new Paint();
		Paint paintInside = new Paint();

		FiducialDetector.Detected detected[];
		int numDetected = 0;

		GrowQueue_I32 indexes = new GrowQueue_I32();
		GrowQueue_F64 area = new GrowQueue_F64();

		Path path = new Path();
		Polygon2D_F64 polygon = new Polygon2D_F64(4);

		public FiducialProcessor() {
			super(ImageType.single(GrayU8.class));

			detected = new FiducialDetector.Detected[3];
			for (int i = 0; i < detected.length; i++) {
				detected[i] = new FiducialDetector.Detected();
				detected[i].binary = new GrayU8(1,1);
				detected[i].location = new Quadrilateral_F64();
			}
		}

		private boolean validQuadrilateral( Quadrilateral_F64 quad ) {
			double smallest =  Double.MAX_VALUE;
			double largest  = -Double.MAX_VALUE;

			for (int i = 0; i < 4; i++) {
				double length = quad.getSideLength(i);
				if( length < smallest )
					smallest = length;
				if( length > largest )
					largest = length;
			}

			return smallest/largest >= 0.4;
		}

		@Override
		public void initialize(int imageWidth, int imageHeight, int sensorOrientation) {
			paintBorder.setColor(Color.BLACK);
			paintBorder.setStyle(Paint.Style.STROKE);
			paintBorder.setStrokeWidth(3*cameraToDisplayDensity);

			paintInside.setColor(Color.RED);
			paintInside.setStyle(Paint.Style.STROKE);
			paintInside.setStrokeWidth(2*cameraToDisplayDensity);

			CameraPinholeBrown intrinsic = lookupIntrinsics();
			LensDistortionNarrowFOV distort = LensDistortionFactory.narrow(intrinsic);
			detector.configure(distort, intrinsic.width, intrinsic.height, true);
			numDetected = 0;
		}

		@Override
		public void onDraw(Canvas canvas, Matrix imageToView) {

			if( touched) {
				applyToPoint(viewToImage,touch.x,touch.y,touch);
			}

			boolean selected = false;
			synchronized (lockGui) {
				canvas.concat(imageToView);
				for (int i = 0; i < numDetected; i++) {
					FiducialDetector.Detected d = detected[i];

					if (validQuadrilateral(d.location)) {
						drawQuad(canvas, d.location, 3, paintBorder);
						drawQuad(canvas, d.location, 0, paintInside);

						if (touched && Intersection2D_F64.contains(d.location, touch)) {
							touchedFiducial.setTo(d.binary);
							selected = true;
						}
					}
				}
			}

			if( selected ) {
				// it's in the GUI thread already so this call is OK
				dialogAcceptFiducial("","");
			} else {
				touched = false;
			}
		}

		@Override
		public void process(GrayU8 gray) {
			detector.process(gray);

			synchronized ( lockGui ) {
				List<FiducialDetector.Detected> found = detector.getDetected();

				// Select the largest quadrilaterals
				if( found.size() <= detected.length ) {
					numDetected = found.size();
					for (int i = 0; i < numDetected; i++) {
						detected[i].binary.setTo(found.get(i).binary);
						detected[i].location.set(found.get(i).location);
					}
				} else {
					indexes.resize( found.size() );
					area.resize( found.size());

					for (int i = 0; i < found.size(); i++) {
						area.set(i, -Area2D_F64.quadrilateral(found.get(i).location));
					}

					QuickSelect.selectIndex(area.data, detected.length, found.size(), indexes.data);

					numDetected = detected.length;
					for (int i = 0; i < numDetected; i++) {
						int index = indexes.data[i];

						detected[i].binary.setTo(found.get(index).binary);
						detected[i].location.set(found.get(index).location);
					}
				}
			}
		}

		private void drawQuad( Canvas canvas , Quadrilateral_F64 quad , int extend, Paint color ) {
			UtilPolygons2D_F64.convert(quad, polygon);
			MiscUtil.renderPolygon(polygon, path, canvas, color);
		}
	}

	public static void setupSpinners( Context context , Spinner spinner , List<UnitsDistance> members ) {
		ArrayAdapter<CharSequence> adapter = new ArrayAdapter<CharSequence>(context, android.R.layout.simple_spinner_item);
		adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		spinner.setAdapter(adapter);

		for( UnitsDistance u : members ) {
			adapter.add(u.getFull());
		}
	}

	/**
	 * Displays a dialog showing the fiducial and asks the user to name it and if they want
	 * to save it
	 */
	protected void dialogAcceptFiducial( String name , String size ) {

		dialogActive = true;

		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		LayoutInflater inflater = getLayoutInflater();

		Bitmap image = Bitmap.createBitmap(touchedFiducial.getWidth(),touchedFiducial.getHeight(),
				Bitmap.Config.ARGB_8888);
		VisualizeImageData.binaryToBitmap(touchedFiducial, true, image, null);

		LinearLayout layout = (LinearLayout) inflater.inflate(R.layout.dialog_fiducial_accept, null);
		ImageView imageView = layout.findViewById(R.id.imageView);

		final EditText textName = layout.findViewById(R.id.imageName);
		final EditText textSize = layout.findViewById(R.id.imageSize);
		final Spinner units = layout.findViewById(R.id.spinner_units_distance);

		textName.setText(name);
		textSize.setText(size);

		setupSpinners(this, units, UnitsDistance.all);

		imageView.setImageBitmap(image);

		// Create the GUI and show it
		builder.setView(layout)
				.setPositiveButton("OK", (dialog, id) -> {
                    UnitsDistance selectedUnit = UnitsDistance.all.get(units.getSelectedItemPosition());
                    handleSaveFiducial(textName.getText().toString(),textSize.getText().toString(),selectedUnit);
                    dialogActive = false;
                })
				.setNegativeButton("Cancel", (dialog, id) -> dialogActive = false)
				.setOnCancelListener(dialog -> dialogActive = false);
		AlertDialog dialog = builder.create();
		dialog.show();
	}

	private void handleSaveFiducial(String name, String length, UnitsDistance unit) {
		if( name.isEmpty() || length.isEmpty()) {
			dialogAcceptFiducial(name, length);
		} else {
			manager.addFiducial(touchedFiducial, Double.parseDouble(length), unit, name);
			finish();
		}
	}
}
