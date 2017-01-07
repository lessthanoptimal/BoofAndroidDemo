package org.boofcv.android.calib;

import android.app.Activity;
import android.os.Bundle;
import android.text.Html;
import android.text.method.LinkMovementMethod;
import android.widget.FrameLayout;
import android.widget.TextView;

import org.boofcv.android.R;
import org.boofcv.android.recognition.DrawCalibrationFiducial;

import boofcv.abst.fiducial.calib.CalibrationPatterns;

/**
 * Displays instructions and tips for the user
 *
 * @author Peter Abeles
 */
public class CalibrationHelpActivity extends Activity implements DrawCalibrationFiducial.Owner {

	private static final String text = "<p>Calibration requires a calibration grid to work.  "+
			"The expected calibration target is shown below.  "+
			"For detailed instructions go to the following webpage: "+
			"<a href=\"http://peterabeles.com/blog/?p=204\">http://peterabeles.com/blog/?p=204</a></p><br>"+
			"* GENTLY tap the picture to detect target.<br>" +
			"* Tapping too hard will cause motion blur.<br>" +
			"* Red dots means it worked.<br>"+
			"* B&W threshold image means it did not work.<br>"+
			"* Try to maximize target size in the screen";

	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		setContentView(R.layout.calibration_help);

		TextView textView = (TextView) findViewById(R.id.text_info);

		textView.setMovementMethod(LinkMovementMethod.getInstance());
		textView.setText(Html.fromHtml(text));

		FrameLayout preview = (FrameLayout) findViewById(R.id.target_frame);

		DrawCalibrationFiducial vis = new DrawCalibrationFiducial(this,this);
		preview.addView(vis);
	}

	@Override
	public int getGridColumns() {
		return CalibrationActivity.numCols;
	}

	@Override
	public int getGridRows() {
		return CalibrationActivity.numRows;
	}

	@Override
	public CalibrationPatterns getGridType() {
		return CalibrationActivity.targetType;
	}
}