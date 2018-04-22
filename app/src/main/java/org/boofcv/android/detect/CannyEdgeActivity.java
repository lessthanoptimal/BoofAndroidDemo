package org.boofcv.android.detect;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.os.Bundle;
import android.text.TextPaint;
import android.view.LayoutInflater;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.SeekBar;

import org.boofcv.android.DemoCamera2Activity;
import org.boofcv.android.DemoProcessingAbstract;
import org.boofcv.android.R;
import org.ddogleg.struct.FastQueue;
import org.ddogleg.struct.GrowQueue_I32;

import java.util.List;
import java.util.Random;

import boofcv.alg.feature.detect.edge.CannyEdge;
import boofcv.alg.feature.detect.edge.EdgeContour;
import boofcv.alg.feature.detect.edge.EdgeSegment;
import boofcv.factory.feature.detect.edge.FactoryEdgeDetectors;
import boofcv.struct.image.GrayS16;
import boofcv.struct.image.GrayU8;
import georegression.struct.point.Point2D_I32;

/**
 * Displays results from the canny edge detector
 *
 * @author Peter Abeles
 */
public class CannyEdgeActivity extends DemoCamera2Activity
	implements SeekBar.OnSeekBarChangeListener, CompoundButton.OnCheckedChangeListener
{
	Random rand = new Random(234);

	float threshold;
	boolean colorize;

	public CannyEdgeActivity() {
		super(Resolution.MEDIUM);
		super.showBitmap = true;
		super.changeResolutionOnSlow = true;
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		LayoutInflater inflater = getLayoutInflater();
		LinearLayout controls = (LinearLayout)inflater.inflate(R.layout.canny_controls,null);

		SeekBar seek = controls.findViewById(R.id.slider_threshold);
		seek.setOnSeekBarChangeListener(this);
		threshold = seek.getProgress()/100f;

		CheckBox toggle = controls.findViewById(R.id.check_colorize);
		toggle.setOnCheckedChangeListener(this);
		colorize = toggle.isChecked();

		setControls(controls);
		activateTouchToShowInput();
	}

	@Override
	public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
		threshold = progress/100.0f;
	}

	@Override
	public void onStartTrackingTouch(SeekBar seekBar) {}

	@Override
	public void onStopTrackingTouch(SeekBar seekBar) {}

	@Override
	public void createNewProcessor() {
		setProcessing(new CannyProcessing());
	}

	@Override
	public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
		colorize = b;
	}

	protected class CannyProcessing extends DemoProcessingAbstract<GrayU8> {
		CannyEdge<GrayU8,GrayS16> canny;

		private final FastQueue<Point2D_I32> contours = new FastQueue<>(Point2D_I32.class,true);
		private final GrowQueue_I32 edgeLengths = new GrowQueue_I32();
		private final GrowQueue_I32 colorEdges = new GrowQueue_I32();

		Paint paintFade = new Paint();
		TextPaint paintText = new TextPaint();
		Paint paintLine = new Paint();

		boolean lineLimit = false;

		public CannyProcessing() {
			super(GrayU8.class);
			this.canny = FactoryEdgeDetectors.canny(2, true, true, GrayU8.class, GrayS16.class);

			paintLine.setStrokeWidth(1);
			paintLine.setStyle(Paint.Style.FILL);

			paintText.setColor(Color.WHITE);
			paintText.setTextSize(20* getResources().getDisplayMetrics().density);

			paintFade.setARGB(200,0,0,0);
			paintFade.setStyle(Paint.Style.FILL);
		}

		@Override
		public void initialize(int imageWidth, int imageHeight) {
			// predeclare some memory
			synchronized (contours) {
				contours.growArray(5000);
				edgeLengths.resize(1000);
				contours.size = 0;
				edgeLengths.size = 0;
			}
		}

		@Override
		public void onDraw(Canvas canvas, Matrix imageToView) {
			int width = bitmap.getWidth();
			int height = bitmap.getHeight();
			int wh = width+height;
			canvas.save();
			canvas.setMatrix(imageToView);
			canvas.drawRect(0,0,width,height,paintFade);

			if( lineLimit ) {
				canvas.restore();
				canvas.drawText("Too Many\nPoints", 80, 80, paintText);
				canvas.setMatrix(imageToView);
			}

			if( !colorize )
				paintLine.setColor(Color.WHITE);

			synchronized (contours) {
				int pointIndex = 0;
				int edgeIndex = 0;
				int nextColor = 0;
				for (int i = 0; i < edgeLengths.size; i++) {
					int length = edgeLengths.get(i);

					// Color is supposed to help you pick out individual lines
					// Before it was random and might cause a seizure. This now computes the
					// color based on its location.  much easier on the eyes but not as easy
					// to pick out individual lines
					if( colorize && i >= nextColor) {
						Point2D_I32 p = contours.get(pointIndex);

						int red = (int)(0xFF*(1.0-(p.x/(double)width)));
						int green = (int)(0xFF*(1.0-(p.y/(double)height)));
						int blue = (int)(0xFF*((p.x+p.y)/(double)wh));

						// draw all points from the same shape  the same color
						paintLine.setARGB(0xFF,red,green,blue);
						nextColor += colorEdges.get(edgeIndex++);
					}

					// Renders much faster drawing rects than a path
					for (int j = 0; j < length; j++) {
						Point2D_I32 p = contours.get(pointIndex++);
						canvas.drawRect(p.x,p.y,p.x+1,p.y+1,paintLine);
					}
				}
			}
		}

		@Override
		public void process(GrayU8 input) {
			if( threshold <= 0.03f )
				threshold = 0.03f;

			canny.process(input,threshold/3.0f,threshold,null);
			List<EdgeContour> found = canny.getContours();

			lineLimit = false;
			synchronized (this.contours) {
				contours.reset();
				edgeLengths.reset();
				colorEdges.reset();
				for (int i = 0; i < found.size(); i++) {
					EdgeContour e = found.get(i);

					for (int j = 0; j < e.segments.size(); j++) {
						EdgeSegment s = e.segments.get(j);
						for (int k = 0; k < s.points.size(); k++) {
							Point2D_I32 p = s.points.get(k);
							contours.grow().set(p.x,p.y);
						}
						edgeLengths.add(s.points.size());
						// abort if there are too many points and rendering will get slow
//						if( contours.size > 5000 ) {
//							lineLimit = true;
//							colorEdges.add( j+1 );
//							break escape;
//						}
					}
					colorEdges.add( e.segments.size() );
				}
			}
		}
	}
}