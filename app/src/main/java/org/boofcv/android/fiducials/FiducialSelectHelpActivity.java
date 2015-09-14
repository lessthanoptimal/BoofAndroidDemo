package org.boofcv.android.fiducials;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.text.Html;
import android.text.method.LinkMovementMethod;
import android.widget.ImageView;
import android.widget.TextView;

import org.boofcv.android.R;

/**
 * Displays instructions for importing fiducials
 *
 * @author Peter Abeles
 */
public class FiducialSelectHelpActivity extends Activity {

	private final static String text = "<p>Imports fiducial image patterns by taking a photo of " +
			"them.</p>"+
			"Instructions:<br>"+
			"* Click 'Capture' button<br>" +
			"* Move close to fiducial, see image<br>" +
			"* Touch inside highlighted region<br"+
			"* Enter all information and save<br>" +
			"* It should now appear in the library";

	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		setContentView(R.layout.fiducial_help);

		TextView textView = (TextView) findViewById(R.id.text_info);

		textView.setMovementMethod(LinkMovementMethod.getInstance());
		textView.setText(Html.fromHtml(text));

		BitmapFactory.Options options = new BitmapFactory.Options();
		options.inScaled = false;
		Bitmap input = BitmapFactory.decodeResource(getResources(), R.drawable.fiducial_select, options);

		ImageView view = (ImageView) findViewById(R.id.imageView);
		view.setImageBitmap(input);
	}
}