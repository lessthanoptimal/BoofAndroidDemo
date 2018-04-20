package org.boofcv.android;

import java.util.ArrayList;
import java.util.List;

import boofcv.struct.calib.CameraPinholeRadial;

/**
 * @author Peter Abeles
 */
public class DemoPreference {
	public final Object lock = new Object();
	public String cameraId;
	public boolean showSpeed;
	public boolean autoReduce;
	public int resolution=0;
	public List<CameraPinholeRadial> calibration = new ArrayList<>();

	public void reset() {
		synchronized (lock) {
			calibration.clear();
		}
	}

	public void add( CameraPinholeRadial a ) {
		synchronized (lock) {
			for( int i = calibration.size()-1; i >= 0; i-- ) {
				CameraPinholeRadial c = calibration.get(i);
				if( c.width == a.width && c.height == a.height ) {
					calibration.remove(i);
				}
			}
			calibration.add(a);
		}
	}

	public CameraPinholeRadial lookup( int width , int height ) {
		CameraPinholeRadial found = null;
		synchronized (lock) {
			for( int i = 0; i < calibration.size(); i++ ) {
				CameraPinholeRadial c = calibration.get(i);
				if( c.width == width && c.height == height ) {
					found = c;
				}
			}
		}
		return found;
	}
}
