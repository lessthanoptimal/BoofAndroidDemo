package org.boofcv.android;

import android.app.Activity;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Bundle;
import android.text.Html;
import android.text.method.LinkMovementMethod;
import android.view.SurfaceView;
import android.widget.FrameLayout;
import android.widget.TextView;

/**
 * Displays instructions and tips for the user
 *
 * @author Peter Abeles
 */
public class CalibrationHelpActivity extends Activity {

	private static final String text = "<p>Calibration requires a calibration grid to work.  "+
			"The expected calibration target is shown below.  "+
			"For detailed instructions go to the following webpage: "+
			"<a href=\"http://peterabeles.com/blog/?p=204\">http://peterabeles.com/blog/?p=204</a></p><br>"+
			"* Tap picture to detect target.<br>" +
			"* Red dots means it work.<br>"+
			"* B&W threshold image means it did not work.<br>"+
			"* Works best when there are no shadows<br>"+
			"* Try to maximize target size in the screen";

	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		setContentView(R.layout.calibration_help);

		TextView textView = (TextView) findViewById(R.id.text_info);

		textView.setMovementMethod(LinkMovementMethod.getInstance());
		textView.setText(Html.fromHtml(text));

		FrameLayout preview = (FrameLayout) findViewById(R.id.target_frame);

		DrawTarget vis = new DrawTarget(this);
		preview.addView(vis);
	}

	private class DrawTarget extends SurfaceView {

		private Paint paintBlack = new Paint();

		Activity activity;

		// how wide a black square is
		int squareWidth;
		// size of rendered grid
		int gridWidth,gridHeight;

		public DrawTarget(Activity context) {
			super(context);
			this.activity = context;

			paintBlack.setColor(Color.BLACK);
			// This call is necessary, or else the
			// draw method will not be called.
			setWillNotDraw(false);
			setBackgroundColor(Color.WHITE);
		}

		@Override
		protected void onSizeChanged (int w, int h, int oldw, int oldh) {
			super.onSizeChanged(w,h,oldw,oldh);

			int numCols = CalibrationActivity.numCols;
			int numRows= CalibrationActivity.numRows;

			// the smallest side in the view area
			int smallest = Math.min(w,h);

			// how wide a black square is
			squareWidth = smallest / (Math.max(numCols, numRows)*2-1);

			gridWidth = squareWidth*(numCols*2-1);
			gridHeight = squareWidth*(numRows*2-1);
		}

		@Override
		protected void onDraw(Canvas canvas){
			super.onDraw(canvas);

			int numCols = CalibrationActivity.numCols;
			int numRows= CalibrationActivity.numRows;

			// center the grid
			canvas.translate((getWidth()-gridWidth)/2,(getHeight()-gridHeight)/2);

			for( int i = 0; i < numRows; i++ ) {
				int y0 = i*2*squareWidth;
				int y1 = y0+squareWidth;
				for( int j = 0; j < numCols; j++ ) {
					int x0 = j*2*squareWidth;
					int x1 = x0+squareWidth;

					canvas.drawRect(x0,y0,x1,y1,paintBlack);
				}
			}

			if( CalibrationActivity.targetType == 0 ) {
				for( int i = 0; i < numRows-1; i++ ) {
					int y0 = (i*2+1)*squareWidth;
					int y1 = y0+squareWidth;
					for( int j = 0; j < numCols-1; j++) {
						int x0 = (j*2+1)*squareWidth;
						int x1 = x0+squareWidth;

						canvas.drawRect(x0,y0,x1,y1,paintBlack);
					}
				}
			}
		}
	}
}