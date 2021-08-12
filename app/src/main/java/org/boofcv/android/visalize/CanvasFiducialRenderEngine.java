package org.boofcv.android.visalize;

import android.graphics.Canvas;
import android.graphics.Paint;

import boofcv.alg.drawing.FiducialRenderEngine;
import boofcv.struct.image.GrayU8;
import georegression.struct.point.Point2D_F64;

public class CanvasFiducialRenderEngine extends FiducialRenderEngine {
    Canvas canvas;

    Paint paint = new Paint();
    double unit_to_pixel;

    public CanvasFiducialRenderEngine(Canvas canvas, double unit_to_pixel) {
        this.canvas = canvas;
        this.unit_to_pixel = unit_to_pixel;
        setGray(0.0);
    }

    @Override public void init() {}

    @Override
    public void setGray(double value) {
        int v = (int)(255*value);
        paint.setColor((0xFF << 24) | (v << 16) | (v << 8) | v);
    }

    @Override
    public void circle(double cx, double cy, double radius) {
        System.err.println("CanvasFiducialRenderEngine.circle() not yet supported");
    }

    @Override
    public void square(double x0, double y0, double width, double thickness) {
        rectangle(x0, y0, x0+width, y0+thickness);
        rectangle(x0, y0+width-thickness, x0+width, y0+thickness);
        rectangle(x0, y0+thickness, x0+thickness, y0+width-2*thickness);
        rectangle(x0+width-thickness, y0+thickness, x0+thickness, y0+width-2*thickness);
    }

    @Override
    public void rectangle(double x0, double y0, double x1, double y1) {
        canvas.drawRect((float)(x0*unit_to_pixel), (float)(y0*unit_to_pixel),
                (float)(x1*unit_to_pixel), (float)(y1*unit_to_pixel), paint);
    }

    @Override
    public void draw(GrayU8 image, double x0, double y0, double x1, double y1) {
        System.err.println("CanvasFiducialRenderEngine.draw() not yet supported");
//        canvas.drawBitmap();
    }

    @Override
    public void inputToDocument(double x, double y, Point2D_F64 document) {
        document.setTo(x*unit_to_pixel, y*unit_to_pixel);
    }
}
