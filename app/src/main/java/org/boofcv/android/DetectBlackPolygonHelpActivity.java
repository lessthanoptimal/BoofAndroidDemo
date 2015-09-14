package org.boofcv.android;

import android.app.Activity;
import android.os.Bundle;
import android.text.Html;
import android.text.method.LinkMovementMethod;
import android.widget.TextView;

/**
 * Displays instructions and tips for the user
 *
 * @author Peter Abeles
 */
public class DetectBlackPolygonHelpActivity extends Activity {

	private static final String text = "<p>Demonstration of a black convex polygon detector. "+
			" This is used as the first step for detecting calibration targets "+
			"and square fiducials.  It initially detects shapes in a binary image, then refines " +
			"their sides to within pixel accuracy using a gray scale image.  It does a very good " +
			"job of detecting squares, but it is a little bit less reliable at detecting other " +
			"polygons.  Still room for improvement at reducing false positives.</p>"+
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