package org.boofcv.android.recognition;

import boofcv.abst.fiducial.calib.CalibrationPatterns;
import boofcv.abst.fiducial.calib.ConfigChessboard;
import boofcv.abst.fiducial.calib.ConfigCircleHexagonalGrid;
import boofcv.abst.fiducial.calib.ConfigCircleRegularGrid;
import boofcv.abst.fiducial.calib.ConfigSquareGrid;

/**
 * Configuration for all possible calibration targets
 */
public class ConfigAllCalibration {
    public CalibrationPatterns targetType = CalibrationPatterns.CHESSBOARD;
    public ConfigChessboard chessboard = new ConfigChessboard(5,7,1);
    public ConfigSquareGrid squareGrid = new ConfigSquareGrid(5,7,1,1.5);
    public ConfigCircleHexagonalGrid hexagonal = new ConfigCircleHexagonalGrid(24,28,1,1.2);
    public ConfigCircleRegularGrid circleGrid = new ConfigCircleRegularGrid(8,10,1.5,2.5);
}
