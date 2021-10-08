package org.boofcv.android.recognition;

import boofcv.abst.fiducial.calib.CalibrationPatterns;
import boofcv.abst.fiducial.calib.ConfigECoCheckMarkers;
import boofcv.abst.fiducial.calib.ConfigGridDimen;

/**
 * Configuration for all possible calibration targets
 */
public class ConfigAllCalibration {
    public CalibrationPatterns targetType = CalibrationPatterns.CHESSBOARD;
    public ConfigGridDimen chessboard = new ConfigGridDimen(5, 7, 1);
    public ConfigGridDimen squareGrid = new ConfigGridDimen(4, 3, 1, 0.5);
    public ConfigGridDimen hexagonal = new ConfigGridDimen(20, 24, 1, 1.5);
    public ConfigGridDimen circleGrid = new ConfigGridDimen(17, 12, 1, 1.5);
    public ConfigECoCheckMarkers ecocheck = ConfigECoCheckMarkers.singleShape(9,7,1, 1);
}
