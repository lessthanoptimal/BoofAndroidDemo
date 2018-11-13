package org.boofcv.android.misc;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;

import georegression.struct.point.Point2D_F64;
import georegression.struct.shapes.Polygon2D_F64;

/**
 * @author Peter Abeles
 */
public class MiscUtil {


	public static void renderPolygon(Polygon2D_F64 s, Path path , Canvas canvas , Paint paint ) {
		path.reset();
		for (int j = 0; j < s.size(); j++) {
			Point2D_F64 p = s.get(j);
			if (j == 0)
				path.moveTo((float) p.x, (float) p.y);
			else
				path.lineTo((float) p.x, (float) p.y);
		}
		Point2D_F64 p = s.get(0);
		path.lineTo((float) p.x, (float) p.y);
		path.close();
		canvas.drawPath(path, paint);
	}
}
