package org.boofcv.android.recognition;

import android.app.Activity;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.SurfaceView;

import boofcv.abst.fiducial.calib.ConfigChessboard;
import boofcv.abst.fiducial.calib.ConfigCircleHexagonalGrid;
import boofcv.abst.fiducial.calib.ConfigCircleRegularGrid;
import boofcv.abst.fiducial.calib.ConfigSquareGrid;
import georegression.metric.UtilAngle;


/**
 * Draws a preview of what the selected calibration target will look like.
 */
public class DrawCalibrationFiducial extends SurfaceView {

	private Paint paintBlack = new Paint();

	Activity activity;

	Owner owner;

	// smallest part of view area
	int smallest;
	int surfaceWidth, surfaceHeight;

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
		this.surfaceWidth = w;
		this.surfaceHeight = h;
	}

	@Override
	protected void onDraw(Canvas canvas){
		super.onDraw(canvas);

		ConfigAllCalibration cc = owner.getConfigAllCalibration();
		switch( cc.targetType ) {
			case CHESSBOARD: {
				// how wide a black square is
				ConfigChessboard config = cc.chessboard;
				int squareWidth = (int)Math.min(surfaceWidth/(config.numCols+1),surfaceHeight/(config.numRows+1));

				int gridWidth = squareWidth*config.numCols;
				int gridHeight = squareWidth*config.numRows;

				int cols0 = config.numCols/2 + config.numCols%2;
				int rows0 = config.numRows/2 + config.numRows%2;

				// center the grid
				canvas.translate((surfaceWidth-gridWidth)/2,(surfaceHeight-gridHeight)/2);
				renderSquareGrid(canvas, cols0, rows0, squareWidth,squareWidth, 0, 0);
				renderSquareGrid(canvas, config.numCols-cols0, config.numRows-rows0, squareWidth,squareWidth, 1, 1);
			} break;

			// TODO update
			case SQUARE_GRID: {
				// how wide a black square is
				ConfigSquareGrid config = cc.squareGrid;
				double ratio = config.spaceWidth/config.squareWidth;
				double unitsWidth = config.numCols + (config.numCols-1)*ratio;
				double unitsHeight = config.numRows + (config.numRows-1)*ratio;

				int squareWidth = (int)Math.min(surfaceWidth/(unitsWidth+1),surfaceHeight/(unitsHeight+1));

				int gridWidth = (int)(squareWidth*(config.numCols +(config.numCols-1)*ratio));
				int gridHeight = (int)(squareWidth*(config.numRows +(config.numRows-1)*ratio));

				// center the grid
				canvas.translate((surfaceWidth-gridWidth)/2,(surfaceHeight-gridHeight)/2);
				renderSquareGrid(canvas, config.numCols, config.numRows, squareWidth, (int)(squareWidth*ratio+0.5),0, 0);
			} break;

			// TODO update
			case CIRCLE_HEXAGONAL: {
				// spacing between circle centers
				ConfigCircleHexagonalGrid config = cc.hexagonal;

				double ratio = config.centerDistance/config.circleDiameter;
				double spaceX = ratio/2.0;
				double spaceY = ratio*Math.sin(UtilAngle.radian(60));
				double radius = 1.0/2.0;

				double unitsWidth = (config.numCols-1)*spaceX + 2*radius;
				double unitsHeight = (config.numRows-1)*spaceY + 2*radius;

				int diameter = (int)Math.min(
						surfaceWidth/(unitsWidth+1),surfaceHeight/(unitsHeight+1));

				int gridWidth  = (int)(unitsWidth*diameter);
				int gridHeight = (int)(unitsHeight*diameter);

				// center the grid
				canvas.translate((getWidth()-gridWidth)/2,(getHeight()-gridHeight)/2);

				renderCircleHexagonal(canvas, config.numCols, config.numRows,(int)(ratio*diameter+0.5), diameter);
			} break;

			case CIRCLE_GRID: {
				// spacing between circle centers
				ConfigCircleRegularGrid config = cc.circleGrid;
				int centerDistance = Math.min(
						surfaceWidth/(config.numCols+1),surfaceHeight/(config.numRows+1));
				int diameter = (int)(centerDistance*config.circleDiameter/config.centerDistance+0.5);

				int gridWidth  = centerDistance*(config.numCols-1) + diameter;
				int gridHeight = centerDistance*(config.numRows-1) + diameter;

				// center the grid
				canvas.translate((surfaceWidth-gridWidth)/2,(surfaceHeight-gridHeight)/2);

				renderCircleRegular(canvas, config.numCols, config.numRows, diameter, centerDistance);
			} break;

		}
	}

	private void renderSquareGrid(Canvas canvas,
								  int numCols, int numRows,
								  int squareWidth, int separation,
								  int row0 , int col0 ) {
		for( int i = 0; i < numRows; i++ ) {
			int y0 = (row0+i)*squareWidth+i*separation;
			int y1 = y0+squareWidth;
			for( int j = 0; j < numCols; j++ ) {
				int x0 = (col0+j)*squareWidth+j*separation;
				int x1 = x0+squareWidth;

				canvas.drawRect(x0,y0,x1,y1,paintBlack);
			}
		}
	}

	private void renderCircleHexagonal(Canvas canvas,
									   int cols, int rows,
									   int centerDistance ,
									   int circleDiameter )
	{
		double spaceX = centerDistance/2.0;
		double spaceY = centerDistance*Math.sin(UtilAngle.radian(60));

		for (int row = 0; row < rows; row++) {
			int y = (int)((rows-row-1)*spaceY);
			for (int col = row%2; col < cols; col += 2) {
				int x = (int)(col*spaceX);

				canvas.drawOval(new RectF(x,y,x+circleDiameter,y+circleDiameter),paintBlack);
			}
		}
	}

	private void renderCircleRegular(Canvas canvas,
									 int numCols, int numRows,
									 int diameter, int centerDistance )
	{
		for (int col = 0; col < numCols; col++) {
			float x = centerDistance*col;
			for (int row = 0; row < numRows; row++) {
				float y = centerDistance*row;
				canvas.drawOval(new RectF(x,y,x+diameter,y+diameter),paintBlack);
			}
		}
	}

	public interface Owner
	{
		ConfigAllCalibration getConfigAllCalibration();
	}
}
