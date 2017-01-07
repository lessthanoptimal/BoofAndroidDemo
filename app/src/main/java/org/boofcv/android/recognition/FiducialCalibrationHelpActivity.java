package org.boofcv.android.recognition;

import android.app.Activity;
import android.os.Bundle;
import android.text.Html;
import android.text.method.LinkMovementMethod;
import android.widget.FrameLayout;
import android.widget.TextView;

import org.boofcv.android.R;

import boofcv.abst.fiducial.calib.CalibrationPatterns;

/**
 * Displays instructions and tips for the user
 *
 * @author Peter Abeles
 */
public class FiducialCalibrationHelpActivity extends Activity {

	private final static String text = "<p>Make sure you specify the correct calibration pattern at" +
			"the start for the one you have printed out.  Only one pattern can be visible at any time." +
			"Black squares that are of similar size right next to the fiducial can confuse it." +
			"<p>Hint, tap the screen to see thresholded image.  Useful when debugging.</p>";

	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		setContentView(R.layout.calibration_help);

		TextView textView = (TextView) findViewById(R.id.text_info);

		textView.setMovementMethod(LinkMovementMethod.getInstance());
		textView.setText(Html.fromHtml(text));

		FrameLayout view = (FrameLayout) findViewById(R.id.target_frame);
		view.addView(new DrawCalibrationFiducial(this, new DrawCalibrationFiducial.Owner() {
			@Override
			public int getGridColumns() {
				return FiducialCalibrationActivity.numCols;
			}

			@Override
			public int getGridRows() {
				return FiducialCalibrationActivity.numRows;
			}

			@Override
			public CalibrationPatterns getGridType() {
				return FiducialCalibrationActivity.targetType;
			}
		}));
	}
}