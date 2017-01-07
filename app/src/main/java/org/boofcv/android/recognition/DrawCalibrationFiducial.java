package org.boofcv.android.recognition;

import android.app.Activity;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.SurfaceView;

import boofcv.abst.fiducial.calib.CalibrationPatterns;


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

		// the smallest side in the view area
		smallest = Math.min(w,h);
	}

	@Override
	protected void onDraw(Canvas canvas){
		super.onDraw(canvas);

		int numCols = owner.getGridColumns();
		int numRows = owner.getGridRows();

		switch( owner.getGridType() ) {
			case CHESSBOARD: {
				// how wide a black square is
				int squareWidth = smallest / (Math.max(numCols, numRows));

				int gridWidth = squareWidth*numCols;
				int gridHeight = squareWidth*numRows;

				// center the grid
				canvas.translate((getWidth()-gridWidth)/2,(getHeight()-gridHeight)/2);
				renderSquareGrid(canvas, numCols, numRows, squareWidth, 0, 0);
				renderSquareGrid(canvas, numCols, numRows, squareWidth, 1, 1);
			} break;

			case SQUARE_GRID: {
				// how wide a black square is
				int squareWidth = smallest / (Math.max(numCols*2, numRows*2));

				int gridWidth = squareWidth*numCols*2;
				int gridHeight = squareWidth*numRows*2;

				// center the grid
				canvas.translate((getWidth()-gridWidth)/2,(getHeight()-gridHeight)/2);
				renderSquareGrid(canvas, numCols * 2, numRows * 2, squareWidth, 0, 0);
			} break;

			case CIRCLE_ASYMMETRIC_GRID: {
				// spacing between circle centers
				int cellWidth = smallest / (Math.max(numCols-1, numRows-1));
				int diameter = Math.max(1,2*cellWidth/3);

				int gridWidth  = cellWidth*(numCols-1) + diameter;
				int gridHeight = cellWidth*(numRows-1) + diameter;

				// center the grid
				canvas.translate((getWidth()-gridWidth)/2,(getHeight()-gridHeight)/2);

				renderCircleAsymmetric(canvas, numCols, numRows, cellWidth, diameter);
			} break;

		}
	}

	private void renderSquareGrid(Canvas canvas,
								  int numCols, int numRows,
								  int squareWidth,
								  int row0 , int col0 ) {
		for( int i = row0; i < numRows; i += 2 ) {
			int y0 = i*squareWidth;
			int y1 = y0+squareWidth;
			for( int j = col0; j < numCols; j += 2 ) {
				int x0 = j*squareWidth;
				int x1 = x0+squareWidth;

				canvas.drawRect(x0,y0,x1,y1,paintBlack);
			}
		}
	}

	private void renderCircleAsymmetric(Canvas canvas,
										int numCols, int numRows,
										int cellWidth ,
										int diameter ) {
		for( int i = 0; i < numRows; i += 2 ) {
			int y = i*cellWidth;
			for( int j = 0; j < numCols; j += 2 ) {
				int x = j*cellWidth;

				canvas.drawOval(new RectF(x,y,x+diameter,y+diameter),paintBlack);
			}
		}
		for( int i = 1; i < numRows; i += 2 ) {
			int y = i*cellWidth;
			for( int j = 1; j < numCols; j += 2 ) {
				int x = j*cellWidth;

				canvas.drawOval(new RectF(x,y,x+diameter,y+diameter),paintBlack);
			}
		}
	}

	public interface Owner
	{
		int getGridColumns();
		int getGridRows();
		CalibrationPatterns getGridType();

	}
}
