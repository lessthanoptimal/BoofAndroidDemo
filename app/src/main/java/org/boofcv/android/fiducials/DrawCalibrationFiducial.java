package org.boofcv.android.fiducials;

import android.app.Activity;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.SurfaceView;


/**
 * Draws a preview of what the selected calibration target will look like.
 */
public class DrawCalibrationFiducial extends SurfaceView {

	private Paint paintBlack = new Paint();

	Activity activity;

	Owner owner;



	// smallest part of view area
	int smallest;

	public DrawCalibrationFiducial(Activity context, Owner owner ) {
		super(context);
		this.activity = context;
		this.owner = owner;

		paintBlack.setColor(Color.BLACK);
		// This call is necessary, or else the
		// draw method will not be called.
		setWillNotDraw(false);
		setBackgroundColor(Color.WHITE);
	}

	@Override
	protected void onSizeChanged (int w, int h, int oldw, int oldh) {
		super.onSizeChanged(w,h,oldw,oldh);

		int numCols = owner.getGridColumns();
		int numRows= owner.getGridRows();

		// the smallest side in the view area
		smallest = Math.min(w,h);
	}

	@Override
	protected void onDraw(Canvas canvas){
		super.onDraw(canvas);

		int numCols = owner.getGridColumns();
		int numRows = owner.getGridRows();

		// how wide a black square is
		int squareWidth = smallest / (Math.max(numCols, numRows));

		int gridWidth = squareWidth*numCols;
		int gridHeight = squareWidth*numRows;

		// center the grid
		canvas.translate((getWidth()-gridWidth)/2,(getHeight()-gridHeight)/2);

		for( int i = 0; i < numRows; i += 2 ) {
			int y0 = i*squareWidth;
			int y1 = y0+squareWidth;
			for( int j = 0; j < numCols; j += 2 ) {
				int x0 = j*squareWidth;
				int x1 = x0+squareWidth;

				canvas.drawRect(x0,y0,x1,y1,paintBlack);
			}
		}

		if( owner.getGridType() == 0 ) {
			for( int i = 1; i < numRows; i += 2 ) {
				int y0 = i*squareWidth;
				int y1 = y0+squareWidth;
				for( int j = 1; j < numCols-1; j += 2) {
					int x0 = j*squareWidth;
					int x1 = x0+squareWidth;

					canvas.drawRect(x0,y0,x1,y1,paintBlack);
				}
			}
		}
	}

	public interface Owner
	{
		int getGridColumns();
		int getGridRows();
		int getGridType();

	}
}
