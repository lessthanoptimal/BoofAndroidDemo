package org.boofcv.android.recognition;

import android.app.Dialog;
import android.os.Bundle;

import boofcv.abst.fiducial.FiducialDetector;
import boofcv.abst.fiducial.calib.CalibrationPatterns;
import boofcv.factory.fiducial.FactoryFiducial;
import boofcv.struct.image.GrayU8;

/**
 * Detects calibration target fiducials
 */
public class FiducialCalibrationActivity extends FiducialSquareActivity {

	public static final int TARGET_DIALOG = 10;

	public static ConfigAllCalibration cc = new ConfigAllCalibration();

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
				final SelectCalibrationFiducial dialog = new SelectCalibrationFiducial(cc);

				dialog.create(this, new Runnable() {
					@Override
					public void run() {
						changed = true;
						FiducialCalibrationActivity.this.startDetector();
					}
				});
		}
		return super.onCreateDialog(id);
	}

	@Override
	protected FiducialDetector<GrayU8> createDetector() {

		if( cc.targetType == CalibrationPatterns.CHESSBOARD ) {
			return FactoryFiducial.calibChessboard(cc.chessboard, GrayU8.class);
		} else if( cc.targetType == CalibrationPatterns.SQUARE_GRID ) {
			return FactoryFiducial.calibSquareGrid(cc.squareGrid, GrayU8.class);
		} else if( cc.targetType == CalibrationPatterns.CIRCLE_HEXAGONAL ) {
			return FactoryFiducial.calibCircleHexagonalGrid(cc.hexagonal, GrayU8.class);
		} else if( cc.targetType == CalibrationPatterns.CIRCLE_GRID ) {
			return FactoryFiducial.calibCircleRegularGrid(cc.circleGrid, GrayU8.class);
		} else {
			throw new RuntimeException("Unknown");
		}
	}
}
