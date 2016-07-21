package org.boofcv.android.fiducials;

import android.app.Dialog;
import android.os.Bundle;

import boofcv.abst.fiducial.FiducialDetector;
import boofcv.abst.fiducial.calib.ConfigChessboard;
import boofcv.abst.fiducial.calib.ConfigSquareGrid;
import boofcv.factory.fiducial.FactoryFiducial;
import boofcv.struct.image.GrayU8;

/**
 * Detects calibration target fiducials
 */
public class FiducialCalibrationActivity extends FiducialSquareActivity {

	public static final int TARGET_DIALOG = 10;

	public static int targetType = 0;
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

		if( targetType == 0 ) {
			ConfigChessboard config = new ConfigChessboard(numCols, numRows, 1);
			return FactoryFiducial.calibChessboard(config, GrayU8.class);
		} else {
			return FactoryFiducial.calibSquareGrid(new ConfigSquareGrid(numCols, numRows, 1,1), GrayU8.class);
		}
	}
}
