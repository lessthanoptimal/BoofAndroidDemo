package org.boofcv.android;

import java.util.ArrayList;
import java.util.List;

import boofcv.struct.calib.CameraPinholeBrown;

/**
 * @author Peter Abeles
 */
public class DemoPreference {
	public final Object lock = new Object();
	public String cameraId="";
	public boolean showSpeed;
	public boolean autoReduce;
	// JDK 1.8 features like streams and concurrency don't work prior to 24
	public boolean useConcurrent=android.os.Build.VERSION.SDK_INT>=24;
	public int resolution=0; // 0 = automatic resolution
	public List<CameraPinholeBrown> calibration = new ArrayList<>();

	public void reset() {
		synchronized (lock) {
			calibration.clear();
		}
	}

	public void add( CameraPinholeBrown a ) {
		synchronized (lock) {
			for( int i = calibration.size()-1; i >= 0; i-- ) {
				CameraPinholeBrown c = calibration.get(i);
				if( c.width == a.width && c.height == a.height ) {
					calibration.remove(i);
				}
			}
			calibration.add(a);
		}
	}

	public CameraPinholeBrown lookup( int width , int height ) {
		CameraPinholeBrown found = null;
		synchronized (lock) {
			for( int i = 0; i < calibration.size(); i++ ) {
				CameraPinholeBrown c = calibration.get(i);
				if( c.width == width && c.height == height ) {
					found = c;
				}
			}
		}
		return found;
	}
}
