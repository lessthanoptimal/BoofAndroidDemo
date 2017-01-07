package org.boofcv.android.recognition;

import android.app.Dialog;
import android.os.Bundle;

import boofcv.abst.fiducial.FiducialDetector;
import boofcv.abst.fiducial.calib.CalibrationPatterns;
import boofcv.abst.fiducial.calib.ConfigChessboard;
import boofcv.abst.fiducial.calib.ConfigCircleAsymmetricGrid;
import boofcv.abst.fiducial.calib.ConfigSquareGrid;
import boofcv.factory.fiducial.FactoryFiducial;
import boofcv.struct.image.GrayU8;

/**
 * Detects calibration target fiducials
 */
public class FiducialCalibrationActivity extends FiducialSquareActivity {

	public static final int TARGET_DIALOG = 10;

	public static CalibrationPatterns targetType = CalibrationPatterns.CHESSBOARD;
	public static int numRows = 5;
	public static int numCols = 7;

	public FiducialCalibrationActivity() {
		super(FiducialCalibrationHelpActivity.class);
		disableControls = true;
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		showDialog(TARGET_DIALOG);
	}

	@Override
	protected Dialog onCreateDialog(int id) {
		switch (id) {
			case TARGET_DIALOG:
				final SelectCalibrationFiducial dialog = new SelectCalibrationFiducial(numRows,numCols,targetType);

				dialog.create(this, new Runnable() {
					@Override
					public void run() {
						numCols = dialog.getGridColumns();
						numRows = dialog.getGridRows();
						targetType = dialog.getGridType();

						changed = true;
						FiducialCalibrationActivity.this.startDetector();
					}
				});
		}
		return super.onCreateDialog(id);
	}

	@Override
	protected FiducialDetector<GrayU8> createDetector() {

		if( targetType == CalibrationPatterns.CHESSBOARD ) {
			ConfigChessboard config = new ConfigChessboard(numCols, numRows, 1);
			return FactoryFiducial.calibChessboard(config, GrayU8.class);
		} else if( targetType == CalibrationPatterns.SQUARE_GRID ) {
			return FactoryFiducial.calibSquareGrid(new ConfigSquareGrid(numCols, numRows, 1,1), GrayU8.class);
		} else if( targetType == CalibrationPatterns.CIRCLE_ASYMMETRIC_GRID ) {
			return FactoryFiducial.calibCircleAsymGrid(new ConfigCircleAsymmetricGrid(numCols, numRows, 1,6), GrayU8.class);
		} else {
			throw new RuntimeException("Unknown");
		}
	}
}
