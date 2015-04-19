package org.boofcv.android;

import android.app.Activity;
import android.os.Bundle;
import android.text.Html;
import android.text.method.LinkMovementMethod;
import android.widget.TextView;

/**
 * Displays information about this application.
 *
 * @author Peter Abeles
 */
public class AboutActivity extends Activity {

	private static String text = "<center><h2>BoofCV Demonstration</h2><center><br>" +
			"<p>This application is a demonstration of " +
			"BoofCV's capabilities.  <a href=\"http://boofcv.org\">BoofCV</a> is a free open source computer vision " +
			"library written entirely in Java " +
			"for real-time computer vision and robotics.</p>"+
			"<p>For more information about how to use this application, see the blog post at " +
			"<a href=\"http://peterabeles.com/blog/?p=204\">peterabeles.com</a>.  It provides usage tips and " +
			"additional instructions for several demos.</p>"+
			"<p>The application's source code is available on " +
			"<a href=\"https://github.com/lessthanoptimal/BoofAndroidDemo\">GitHub</a>." +
			"<p>Written by Peter Abeles</p>";

	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		setContentView(R.layout.about);

		TextView textView = (TextView) findViewById(R.id.text_about);

		textView.setMovementMethod(LinkMovementMethod.getInstance());
		textView.setText(Html.fromHtml(text));
	}
}