package org.boofcv.android.recognition;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Spinner;

import org.boofcv.android.DemoVideoDisplayActivity;
import org.boofcv.android.R;
import org.boofcv.android.misc.MiscUtil;
import org.boofcv.android.misc.UnitsDistance;
import org.ddogleg.sorting.QuickSelect;
import org.ddogleg.struct.GrowQueue_F64;
import org.ddogleg.struct.GrowQueue_I32;

import java.util.List;

import boofcv.alg.distort.LensDistortionNarrowFOV;
import boofcv.alg.distort.LensDistortionOps;
import boofcv.android.ConvertBitmap;
import boofcv.android.VisualizeImageData;
import boofcv.android.gui.VideoRenderProcessing;
import boofcv.struct.calib.CameraPinholeRadial;
import boofcv.struct.image.GrayU8;
import boofcv.struct.image.ImageType;
import georegression.metric.Area2D_F64;
import georegression.metric.Intersection2D_F64;
import georegression.struct.point.Point2D_F64;
import georegression.struct.shapes.Quadrilateral_F64;

/**
 * Opens a view which selects quadrilaterals in the image and lets you select one to use
 * as a pattern in a fiducial.
 *
 * @author Peter Abeles
 */
public class FiducialLearnActivity extends DemoVideoDisplayActivity
		implements View.OnTouchListener
{
	public static final String TAG = "FiducialLearnActivity";

	boolean touched = false;
	Point2D_F64 touch = new Point2D_F64();
	GrayU8 touchedFiducial = new GrayU8(1,1);
	boolean dialogActive = false;

	FiducialManager manager;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		getViewPreview().setOnTouchListener(this);

		manager = new FiducialManager(this);
		manager.loadList();
	}

	@Override
	protected void onResume() {
		super.onResume();
		startFiducialDetector();
	}

	protected void startFiducialDetector() {
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

	protected class FiducialProcessor extends VideoRenderProcessing<GrayU8> {

		final FiducialDetector detector = new FiducialDetector();

		Paint paintBorder = new Paint();
		Paint paintInside = new Paint();

		Bitmap bitmap;
		byte[] storage;

		FiducialDetector.Detected detected[];
		int numDetected = 0;

		GrowQueue_I32 indexes = new GrowQueue_I32();
		GrowQueue_F64 area = new GrowQueue_F64();

		public FiducialProcessor() {
			super(ImageType.single(GrayU8.class));

			paintBorder.setColor(Color.BLACK);
			paintBorder.setStrokeWidth(6);

			paintInside.setColor(Color.RED);
			paintInside.setStrokeWidth(3);

			detected = new FiducialDetector.Detected[3];
			for (int i = 0; i < detected.length; i++) {
				detected[i] = new FiducialDetector.Detected();
				detected[i].binary = new GrayU8(1,1);
				detected[i].location = new Quadrilateral_F64();
			}
		}

		@Override
		protected void declareImages(int width, int height) {
			super.declareImages(width, height);
			CameraPinholeRadial intrinsic = MiscUtil.checkThenInventIntrinsic();
			LensDistortionNarrowFOV distort = LensDistortionOps.transformPoint(intrinsic);
			detector.configure(distort, intrinsic.width, intrinsic.height, true);
			bitmap = Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888);
			storage = ConvertBitmap.declareStorage(bitmap, storage);
			numDetected = 0;
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
		protected void process(GrayU8 gray) {

			detector.process(gray);

			synchronized ( lockGui ) {
				ConvertBitmap.grayToBitmap(gray,bitmap,storage);

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

		@Override
		protected void render(Canvas canvas, double imageToOutput) {

			canvas.drawBitmap(bitmap, 0, 0, null);

			if( touched) {
				imageToOutput(touch.x,touch.y,touch);
			}

			boolean selected = false;
			for (int i = 0; i < numDetected; i++) {
				FiducialDetector.Detected d = detected[i];

				if( validQuadrilateral(d.location) ) {
					drawQuad(canvas, d.location, 3, paintBorder);
					drawQuad(canvas, d.location, 0, paintInside);

					if( touched && Intersection2D_F64.contains(d.location,touch)) {
						touchedFiducial.setTo(d.binary);
						selected = true;
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

		private void drawQuad( Canvas canvas , Quadrilateral_F64 quad , int extend, Paint color ) {
			drawLine(canvas, quad.a, quad.b, extend, color);
			drawLine(canvas, quad.b, quad.c, extend, color);
			drawLine(canvas, quad.c, quad.d, extend, color);
			drawLine(canvas, quad.d, quad.a, extend, color);
		}

		private void drawLine( Canvas canvas , Point2D_F64 a, Point2D_F64 b, int extend, Paint color ) {

			double slopeX = b.x-a.x;
			double slopeY = b.y-a.y;

			double r = Math.sqrt(slopeX*slopeX + slopeY*slopeY);

			slopeX /= r;
			slopeY /= r;

			float x0 = (float)(a.x - slopeX*extend);
			float y0 = (float)(a.y - slopeY*extend);

			float x1 = (float)(b.x + slopeX*extend);
			float y1 = (float)(b.y + slopeY*extend);

			canvas.drawLine(x0, y0, x1, y1,color);
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
		ImageView imageView = (ImageView) layout.findViewById(R.id.imageView);

		final EditText textName = (EditText) layout.findViewById(R.id.imageName);
		final EditText textSize = (EditText) layout.findViewById(R.id.imageSize);
		final Spinner units = (Spinner) layout.findViewById(R.id.spinner_units_distance);

		textName.setText(name);
		textSize.setText(size);

		setupSpinners(this, units, (List) UnitsDistance.all);

		imageView.setImageBitmap(image);

		// Create the GUI and show it
		builder.setView(layout)
				.setPositiveButton("OK", new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int id) {
						UnitsDistance selectedUnit = UnitsDistance.all.get(units.getSelectedItemPosition());
						handleSaveFiducial(textName.getText().toString(),textSize.getText().toString(),selectedUnit);
						dialogActive = false;
					}
				})
				.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int id) {
						dialogActive = false;
					}
				})
				.setOnCancelListener(new DialogInterface.OnCancelListener() {
					@Override
					public void onCancel(DialogInterface dialog) {
						dialogActive = false;
					}
				});
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
