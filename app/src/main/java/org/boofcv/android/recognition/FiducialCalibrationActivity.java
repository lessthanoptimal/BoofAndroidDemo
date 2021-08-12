package org.boofcv.android.recognition;

import android.os.Bundle;
import android.widget.SeekBar;
import android.widget.ToggleButton;

import boofcv.abst.fiducial.FiducialDetector;
import boofcv.abst.fiducial.calib.CalibrationPatterns;
import boofcv.factory.fiducial.FactoryFiducial;
import boofcv.struct.image.GrayU8;

/**
 * Detects calibration target fiducials
 */
public class FiducialCalibrationActivity extends FiducialSquareActivity {

	public static ConfigAllCalibration cc = new ConfigAllCalibration();

	ToggleButton toggle;

	public FiducialCalibrationActivity() {
		super(FiducialCalibrationHelpActivity.class);
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		// don't start processing fiducials until the user has selected the specifics
		detectFiducial = false;
		super.onCreate(savedInstanceState);

		SelectCalibrationFiducial dialog = new SelectCalibrationFiducial(cc);
		dialog.show(this, ()->{
			detectFiducial=true;//needs to be before createNewProcessor

			// only enable if the user selected a chessboard
			toggle.setEnabled(cc.targetType==CalibrationPatterns.CHESSBOARD);

			// Create the detector and start processing images!
			createNewProcessor();
		});
	}

	@Override
	protected void configureControls(ToggleButton toggle, SeekBar seek) {
		this.toggle = toggle;
		// disable seek bar some nothing uses it
		seek.setEnabled(false);

		// We want robust to be configurable for chessboard
		toggle.setChecked(false); // default to the fast option for slower devices
		robust = toggle.isChecked();
		toggle.setOnCheckedChangeListener((buttonView, isChecked) -> {
			synchronized (lock) {
				robust = isChecked;
				seek.setEnabled(!robust);
				createNewProcessor();
			}
		});
	}

	@Override
	protected FiducialDetector<GrayU8> createDetector() {

		if( cc.targetType == CalibrationPatterns.CHESSBOARD ) {
			if (robust) {
				return FactoryFiducial.calibChessboardX(null, cc.chessboard, GrayU8.class);
			} else {
				return FactoryFiducial.calibChessboardB(null, cc.chessboard, GrayU8.class);
			}
		} else if (cc.targetType == CalibrationPatterns.ECOCHECK) {
			return FactoryFiducial.ecocheck(null,cc.ecocheck, GrayU8.class);
		} else if( cc.targetType == CalibrationPatterns.SQUARE_GRID ) {
			return FactoryFiducial.calibSquareGrid(null,cc.squareGrid, GrayU8.class);
		} else if( cc.targetType == CalibrationPatterns.CIRCLE_HEXAGONAL ) {
			return FactoryFiducial.calibCircleHexagonalGrid(null,cc.hexagonal, GrayU8.class);
		} else if( cc.targetType == CalibrationPatterns.CIRCLE_GRID ) {
			return FactoryFiducial.calibCircleRegularGrid(null,cc.circleGrid, GrayU8.class);
		} else {
			throw new RuntimeException("Unknown");
		}
	}
}
