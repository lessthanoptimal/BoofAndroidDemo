package org.boofcv.android.misc;

import java.util.Arrays;
import java.util.List;

/**
 * List of allowed distance units
 *
 * @author Peter Abeles
 */
public enum UnitsDistance {
	CENTIMETERS("centimeters","cm",0.01),
	METERS("meters","m",1.0),
	KILOMETERS("kilometers","km",1000.0),
	FEET("feet","ft",0.3048),
	YARDS("yards","yd",0.9144),
	MILES("miles","ml",1609.34);

	public static UnitsDistance DEFAULT = METERS;

	public static final List<UnitsDistance> all = Arrays.asList(values());

	String full,small;
	double toDefault;

	UnitsDistance(String full, String small, double toDefault) {
		this.full = full;
		this.small = small;
		this.toDefault = toDefault;
	}

	public double toDefault( double value ) {
		return value* toDefault;
	}

	public double fromDefault( double value ) {
		return value/ toDefault;
	}

	public String getFull() {
		return full;
	}

	public String getSmall() {
		return small;
	}
}
