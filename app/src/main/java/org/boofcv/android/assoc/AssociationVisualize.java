package org.boofcv.android.assoc;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.TypedValue;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import boofcv.android.ConvertBitmap;
import boofcv.struct.geo.AssociatedPair;
import boofcv.struct.image.GrayF32;
import georegression.struct.point.Point2D_F64;

/**
 * Visualizes associated features between two images.
 *
 * @author Peter Abeles
 */
public class AssociationVisualize {

	public static int SEPARATION = 10;

	Activity owner;

	// what color everything is drawn
	public Paint paintPoint = new Paint();
	public Paint paintWideLine = new Paint();
	public Paint textPaint = new Paint();
	public Paint paintLine = new Paint();

	public boolean hasLeft = false;
	public boolean hasRight = false;

	public GrayF32 graySrc;
	public GrayF32 grayDst;
	public Bitmap bitmapSrc;
	public Bitmap bitmapDst;
	public byte[] storage;

	// transform between image and output
	double scale;
	double tranX,tranY;

	// which features are matched between the two views
	List<Point2D_F64> locationSrc = new ArrayList<Point2D_F64>();
	List<Point2D_F64> locationDst = new ArrayList<Point2D_F64>();

	// closest match when the user is selecting individual associations
	AssociatedPair matchedPair = new AssociatedPair();
	int mouseX,mouseY;
	boolean hasMatch = false;

	public AssociationVisualize(Activity owner) {
		this.owner = owner;

		paintPoint.setColor(Color.RED);
		paintPoint.setStyle(Paint.Style.FILL);

		paintWideLine.setColor(Color.RED);
		paintWideLine.setStrokeWidth(3);

		textPaint.setColor(Color.BLUE);
		textPaint.setTextSize(60);

		paintLine.setStrokeWidth(2);
	}

	/**
	 * Specifies size of input image and predeclares data structures
	 */
	public void initializeImages( int width , int height ) {
		if( graySrc != null && graySrc.width == width && graySrc.height == height )
			return;

		graySrc = new GrayF32(width,height);
		grayDst = new GrayF32(width,height);

		bitmapSrc = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
		bitmapDst = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
		storage = ConvertBitmap.declareStorage(bitmapSrc, storage);
	}

	public int getOutputWidth() {
		return graySrc.width*2 + SEPARATION;
	}

	public int getOutputHeight() {
		return graySrc.height;
	}


	/**
	 * Specifies location of associated features between the two images
	 */
	public void setMatches( List<Point2D_F64> locationSrc , List<Point2D_F64> locationDst ) {
		this.locationSrc.clear();
		this.locationDst.clear();

		for( Point2D_F64 p : locationSrc ) {
			this.locationSrc.add(p.copy());
		}
		for( Point2D_F64 p : locationDst ) {
			this.locationDst.add(p.copy());
		}
	}

	public void setMatches( List<AssociatedPair> matches ) {
		this.locationSrc.clear();
		this.locationDst.clear();

		for( int i = 0; i < matches.size(); i++ ) {
			AssociatedPair p = matches.get(i);

			locationSrc.add( p.p1.copy() );
			locationDst.add( p.p2.copy() );
		}
	}

	/**
	 * Forget what the user has selected
	 */
	public void forgetSelection() {
		mouseX = -1;
		hasMatch = false;
	}

	/**
	 * Specifies where the user touched the screen
	 * @return true if processed and false if ignored
	 */
	public boolean setTouch( int x , int y ) {
		if( hasLeft && hasRight ) {
			mouseX = x;
			mouseY = y;
			hasMatch = false;
			return true;
		}
		return false;
	}

	/**
	 * Set the source (left) image
	 */
	public void setSource( GrayF32 image ) {
		locationSrc.clear();
		locationDst.clear();

		if( image == null ) {
			hasLeft = false;
		} else {
			hasLeft = true;
			graySrc.setTo(image);
			ConvertBitmap.grayToBitmap(image,bitmapSrc,storage);
		}
	}

	/**
	 * Set the destination (right) image
	 */
	public void setDestination( GrayF32 image ) {
		locationSrc.clear();
		locationDst.clear();

		if( image == null ) {
			hasRight = false;
		} else {
			hasRight = true;
			grayDst.setTo(image);
			ConvertBitmap.grayToBitmap(image,bitmapDst,storage);
		}
	}

	public void render(Canvas canvas, double tranX , double tranY , double scale ) {
		this.scale = scale;
		this.tranX = tranX;
		this.tranY = tranY;

		int startX = bitmapSrc.getWidth()+SEPARATION;

		// draw captured images
		if( hasLeft ) {
			canvas.drawBitmap(bitmapSrc,0,0,null);
		}

		if( hasRight ) {
			canvas.drawBitmap(bitmapDst,startX,0,null);
		}

		// draw features and matches
		if( hasMatches() ) {
			// see if the user is selecting a point
			if( mouseX >= 0 ) {
				handleUserSelection(canvas, startX);
			} else {
				drawAllMatches(canvas, startX);
			}
		}

		// it's scaled to image size
		canvas.restore();

		// provide a hint to the user for what they should be doing
		int x4 = canvas.getWidth()/4;
		int x2 = canvas.getWidth()/2;
		int y2 = canvas.getHeight()/2;

		int textLength = (int)textPaint.measureText("Touch Here");

		if( !hasLeft ) {
			canvas.drawText("Touch Here", x4-textLength/2,y2, textPaint);
		}
		if( !hasRight ) {
			canvas.drawText("Touch Here", x2+x4-textLength/2,y2, textPaint);
		}
	}

	/**
	 * THe user has selected a point on the screen.  If a closest point has not been found then do so.  If
	 * no match is found then draw all the matches
	 */
	private void handleUserSelection(Canvas canvas, int startX) {
		if( !hasMatch ) {
			if( !findBestMatch( startX) ) {
				forgetSelection();
				drawAllMatches(canvas, startX);
			} else {
				hasMatch = true;
			}
		}

		if( hasMatch ) {
			Point2D_F64 s = matchedPair.p1;
			Point2D_F64 d = matchedPair.p2;
			canvas.drawLine((float)s.x,(float)s.y,(float)d.x+startX,(float)d.y,paintWideLine);
		}
	}

	private void drawAllMatches(Canvas canvas, int startX) {

		Random rand = new Random(234);

		for( int i = 0; i < locationSrc.size(); i++ ) {
			Point2D_F64 s = locationSrc.get(i);
			Point2D_F64 d = locationDst.get(i);
			paintLine.setARGB(255,rand.nextInt(255),rand.nextInt(255),rand.nextInt(255));
			canvas.drawLine((float)s.x,(float)s.y,(float)d.x+startX,(float)d.y,paintLine);
		}
	}

	/**
	 * Selects the features which is closest to the where the user selected
	 */
	private boolean findBestMatch(int startX ) {
		// 1 mm in pixels
		float px = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_MM, 1,
				owner.getResources().getDisplayMetrics());
		// 1 cm tolerance

		double bestDistance = Math.pow((px*10)/scale,2);
		int best = -1;

		Point2D_F64 imagePt = new Point2D_F64();
		imageToOutput(mouseX,mouseY,imagePt);

		// see if it is inside the left image
		if( imagePt.x < startX ) {
			for( int i = 0; i < locationSrc.size(); i++ ) {
				Point2D_F64 s = locationSrc.get(i);

				double dist = s.distance2(imagePt);
				if( dist < bestDistance ) {
					bestDistance = dist;
					best = i;
				}
			}
		} else {
			imagePt.x -= startX;
			for( int i = 0; i < locationDst.size(); i++ ) {
				Point2D_F64 d = locationDst.get(i);

				double dist = d.distance2(imagePt);
				if( dist < bestDistance ) {
					bestDistance = dist;
					best = i;
				}
			}
		}

		if( best != -1 ) {
			Point2D_F64 s = locationSrc.get(best);
			Point2D_F64 d = locationDst.get(best);

			matchedPair.p1.set(s);
			matchedPair.p2.set(d);
			return true;
		}
		return false;
	}

	/**
	 * Converts a coordinate from pixel to the output image coordinates
	 */
	protected void imageToOutput( double x , double y , Point2D_F64 pt ) {
		pt.x = x/scale - tranX/scale;
		pt.y = y/scale - tranY/scale;
	}

	private boolean hasMatches() {
		return !locationSrc.isEmpty();
	}
}
