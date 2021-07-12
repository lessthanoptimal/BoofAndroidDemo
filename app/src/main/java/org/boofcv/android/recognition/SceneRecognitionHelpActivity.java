package org.boofcv.android.recognition;

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
public class SceneRecognitionHelpActivity extends Activity{

	private static final String text = "<p>Scene recognition is used to find images which where " +
			"taken of the same scene from a similar perspective. Left image is the current camera "+
			"preview. Right is the best match image from the database. The number drawn on top is " +
			"the error between current image and the database image.<br>" +
			"Buttons:<br>"+
			"* Add: Adds a new image to the database<br>" +
			"* Save DB: Saves the updated database with new images<br>" +
			"* Clear DB: Deletes all images and resets the database";

	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		setContentView(R.layout.calibration_help);

		TextView textView = findViewById(R.id.text_info);

		textView.setMovementMethod(LinkMovementMethod.getInstance());
		textView.setText(Html.fromHtml(text));
	}
}