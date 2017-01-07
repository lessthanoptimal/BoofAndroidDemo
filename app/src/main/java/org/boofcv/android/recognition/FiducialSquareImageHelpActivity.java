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
public class FiducialSquareImageHelpActivity extends Activity {

	private final static String text = "<p>Detects a square fiducial which is composed "+
			" of a square with a black border and a pattern inside, see image.  "+
			"Initially there are no fiducials and you need to load them into the library.  "+
			"To do that go back to the main menu and select 'Square Image Library' and "+
			"follow the instructions there.  You will need to make a fiducial then take a " +
			"picture of it.</p>" +
			"<p>The tutorial below has some fiducials you can use:<br>" +
			"<a href=\"http://boofcv.org/index.php?title=Tutorial_Fiducials\">Tutorial Fiducials</a></p><br>" +
			"<p>Hint, tap the screen to see thresholded image.  Useful when debugging.</p>";;

	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		setContentView(R.layout.fiducial_help);

		TextView textView = (TextView) findViewById(R.id.text_info);

		textView.setMovementMethod(LinkMovementMethod.getInstance());
		textView.setText(Html.fromHtml(text));

		BitmapFactory.Options options = new BitmapFactory.Options();
		options.inScaled = false;
		Bitmap input = BitmapFactory.decodeResource(getResources(), R.drawable.fiducial_square_image, options);

		ImageView view = (ImageView) findViewById(R.id.imageView);
		view.setImageBitmap(input);
	}
}