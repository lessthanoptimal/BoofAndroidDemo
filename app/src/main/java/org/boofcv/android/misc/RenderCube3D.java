package org.boofcv.android.misc;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;

import boofcv.alg.geo.PerspectiveOps;
import boofcv.struct.calib.CameraPinholeBrown;
import georegression.struct.point.Point2D_F32;
import georegression.struct.point.Point2D_F64;
import georegression.struct.point.Point3D_F64;
import georegression.struct.se.Se3_F64;
import georegression.transform.se.SePointOps_F64;

/**
 * Draws a 3D cube for fiducials
 *
 * @author Peter Abeles
 */
public class RenderCube3D {
    Paint paintLine0 = new Paint();
    Paint paintLine1 = new Paint();
    Paint paintLine2 = new Paint();
    Paint paintLine3 = new Paint();
    private Paint paintTextVideo = new Paint(); // drawn in image coordinates
    private Paint paintTextBorder = new Paint();

    Rect bounds = new Rect();

    public RenderCube3D() {
        paintLine0.setColor(Color.RED);
        paintLine0.setFlags(Paint.ANTI_ALIAS_FLAG);
        paintLine1.setColor(Color.BLACK);
        paintLine1.setFlags(Paint.ANTI_ALIAS_FLAG);
        paintLine2.setColor(Color.BLUE);
        paintLine2.setFlags(Paint.ANTI_ALIAS_FLAG);
        paintLine3.setColor(Color.GREEN);
        paintLine3.setFlags(Paint.ANTI_ALIAS_FLAG);

        paintTextVideo.setARGB(255, 255, 100, 100);

        paintTextBorder.setARGB(255, 0, 0, 0);
        paintTextBorder.setStyle(Paint.Style.STROKE);
    }

    public void initialize( float cameraToDisplayDensity ) {
        paintTextVideo.setTextSize(30*cameraToDisplayDensity);
        paintTextBorder.setTextSize(30*cameraToDisplayDensity);
        paintTextBorder.setStrokeWidth(3*cameraToDisplayDensity);
        paintLine0.setStrokeWidth(4f*cameraToDisplayDensity);
        paintLine1.setStrokeWidth(4f*cameraToDisplayDensity);
        paintLine2.setStrokeWidth(4f*cameraToDisplayDensity);
        paintLine3.setStrokeWidth(4f*cameraToDisplayDensity);
    }

    /**
     * Draws a flat cube to show where the square fiducial is on the image
     *
     */
    public void drawCube(String label , Se3_F64 targetToCamera , CameraPinholeBrown intrinsic , double width ,
                         Canvas canvas )
    {
        double r = width/2.0;
        Point3D_F64 corners[] = new Point3D_F64[8];
        corners[0] = new Point3D_F64(-r,-r,0);
        corners[1] = new Point3D_F64( r,-r,0);
        corners[2] = new Point3D_F64( r, r,0);
        corners[3] = new Point3D_F64(-r, r,0);
        corners[4] = new Point3D_F64(-r,-r,r);
        corners[5] = new Point3D_F64( r,-r,r);
        corners[6] = new Point3D_F64( r, r,r);
        corners[7] = new Point3D_F64(-r, r,r);

        Point2D_F32 pixel[] = new Point2D_F32[8];
        Point2D_F64 p = new Point2D_F64();
        for (int i = 0; i < 8; i++) {
            Point3D_F64 c = corners[i];
            SePointOps_F64.transform(targetToCamera, c, c);
            PerspectiveOps.convertNormToPixel(intrinsic, c.x / c.z, c.y / c.z, p);
            pixel[i] = new Point2D_F32((float)p.x,(float)p.y);
        }

        Point3D_F64 centerPt = new Point3D_F64();

        SePointOps_F64.transform(targetToCamera, centerPt, centerPt);
        PerspectiveOps.convertNormToPixel(intrinsic,
                centerPt.x / centerPt.z, centerPt.y / centerPt.z, p);
        Point2D_F32 centerPixel  = new Point2D_F32((float)p.x,(float)p.y);

        // red
        drawLine(canvas,pixel[0],pixel[1],paintLine0);
        drawLine(canvas,pixel[1],pixel[2],paintLine0);
        drawLine(canvas,pixel[2],pixel[3],paintLine0);
        drawLine(canvas,pixel[3],pixel[0],paintLine0);

        // black
        drawLine(canvas,pixel[0],pixel[4],paintLine1);
        drawLine(canvas,pixel[1],pixel[5],paintLine1);
        drawLine(canvas,pixel[2],pixel[6],paintLine1);
        drawLine(canvas,pixel[3],pixel[7],paintLine1);

        drawLine(canvas,pixel[4],pixel[5],paintLine2);
        drawLine(canvas,pixel[5],pixel[6],paintLine2);
        drawLine(canvas,pixel[6],pixel[7],paintLine2);
        drawLine(canvas,pixel[7],pixel[4],paintLine3);


        paintTextVideo.getTextBounds(label,0,label.length(),bounds);

        int textLength = bounds.width();
        int textHeight = bounds.height();

        canvas.drawText(label, centerPixel.x-textLength/2,centerPixel.y+textHeight/2, paintTextBorder);
        canvas.drawText(label, centerPixel.x-textLength/2,centerPixel.y+textHeight/2, paintTextVideo);
    }

    private void drawLine( Canvas canvas , Point2D_F32 a , Point2D_F32 b , Paint color ) {
        canvas.drawLine(a.x,a.y,b.x,b.y,color);
    }
}
