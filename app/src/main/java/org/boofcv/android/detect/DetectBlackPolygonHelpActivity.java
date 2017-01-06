package org.boofcv.android.detect;

import android.app.Activity;
import android.os.Bundle;
import android.text.Html;
import android.text.method.LinkMovementMethod;
import android.widget.TextView;

import org.boofcv.android.R;

/**
 * Displays instructions and tips for the user
 *
 * @author Peter Abeles
 */
public class DetectBlackPolygonHelpActivity extends Activity {

	private static final String text = "<p>Demonstration of a black convex polygon detector. "+
			" This is used as the first step for detecting calibration targets "+
			"and square fiducials.  It initially detects shapes in a binary image, then refines " +
			"their sides to within subpixel accuracy using a gray scale image.</p>"+
			"<br>" +
			"* Tap to see the binary image<br>" +
			"* Global uses a global adaptive threshold<br>"+
			"* Local use a robust locally adaptive threshold<br>";

	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		setContentView(R.layout.black_polygon_help);

		TextView textView = (TextView) findViewById(R.id.text_info);

		textView.setMovementMethod(LinkMovementMethod.getInstance());
		textView.setText(Html.fromHtml(text));
	}
}