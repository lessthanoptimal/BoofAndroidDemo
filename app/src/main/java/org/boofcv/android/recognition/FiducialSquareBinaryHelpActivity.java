package org.boofcv.android.recognition;

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
 * Displays instructions and tips for the user
 *
 * @author Peter Abeles
 */
public class FiducialSquareBinaryHelpActivity extends Activity {

	private final static String text = "<p>Print one or more square binary fiducials "+
			"and place them on a flat surface.  See below for an example."+
			"For detailed instructions go to the following webpage: "+
			"<a href=\"http://boofcv.org/index.php?title=Tutorial_Fiducials\">Tutorial Fiducials</a></p><br>"+
			"</p><br>" +
			"<p>Hint, tap the screen to see thresholded image.  Useful when debugging.</p>";

	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		setContentView(R.layout.fiducial_help);

		TextView textView = (TextView) findViewById(R.id.text_info);

		textView.setMovementMethod(LinkMovementMethod.getInstance());
		textView.setText(Html.fromHtml(text));

		BitmapFactory.Options options = new BitmapFactory.Options();
		options.inScaled = false;
		Bitmap input = BitmapFactory.decodeResource(getResources(), R.drawable.fiducial_square_binary, options);

		ImageView view = (ImageView) findViewById(R.id.imageView);
		view.setImageBitmap(input);
	}
}