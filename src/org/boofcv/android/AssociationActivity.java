package org.boofcv.android;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.hardware.Camera;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.GestureDetector;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.widget.*;
import boofcv.abst.feature.associate.AssociateDescription;
import boofcv.abst.feature.associate.ScoreAssociation;
import boofcv.abst.feature.detdesc.DetectDescribePoint;
import boofcv.alg.feature.UtilFeature;
import boofcv.android.ConvertBitmap;
import boofcv.core.image.ConvertImage;
import boofcv.factory.feature.associate.FactoryAssociation;
import boofcv.struct.FastQueue;
import boofcv.struct.feature.AssociatedIndex;
import boofcv.struct.feature.TupleDesc;
import boofcv.struct.geo.AssociatedPair;
import boofcv.struct.image.ImageFloat32;
import boofcv.struct.image.ImageUInt8;
import georegression.struct.point.Point2D_F64;

/**
 * @author Peter Abeles
 */
public class AssociationActivity extends VideoDisplayActivity
implements AdapterView.OnItemSelectedListener
{
	private final static int SEPARATION = 10;

	Spinner spinnerDesc;
	Spinner spinnerDet;

	int target = 0;

	boolean hasLeft = false;
	boolean hasRight = false;

	ImageFloat32 graySrc;
	ImageFloat32 grayDst;
	Bitmap bitmapSrc;
	Bitmap bitmapDst;
	byte[] storage;

	private GestureDetector mDetector;

	Paint paintPoint = new Paint();
	Paint paintWideLine = new Paint();
	private Paint textPaint = new Paint();

	int selectedDet = 0;
	int selectedDesc = 0;

	// closest match when the user is selecting individual associations
	AssociatedPair matchedPair = new AssociatedPair();
	int mouseX,mouseY;
	boolean hasMatch = false;

	public AssociationActivity() {
		paintPoint.setColor(Color.RED);
		paintPoint.setStyle(Paint.Style.FILL);

		paintWideLine.setColor(Color.RED);
		paintWideLine.setStrokeWidth(3);

		textPaint.setColor(Color.BLUE);
		textPaint.setTextSize(60);
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		Camera.Size size = mCamera.getParameters().getPreviewSize();
		initializeImages( size.width, size.height );

		LayoutInflater inflater = getLayoutInflater();
		LinearLayout controls = (LinearLayout)inflater.inflate(R.layout.associate_controls,null);

		LinearLayout parent = (LinearLayout)findViewById(R.id.camera_preview_parent);
		parent.addView(controls);

		spinnerDet = (Spinner)controls.findViewById(R.id.spinner_detector);
		ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(this,
				R.array.detectors, android.R.layout.simple_spinner_item);
		adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		spinnerDet.setAdapter(adapter);
		spinnerDet.setOnItemSelectedListener(this);

		spinnerDesc = (Spinner)controls.findViewById(R.id.spinner_descriptor);
		adapter = ArrayAdapter.createFromResource(this,
				R.array.descriptors, android.R.layout.simple_spinner_item);
		adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		spinnerDesc.setAdapter(adapter);
		spinnerDesc.setOnItemSelectedListener(this);

		FrameLayout iv = (FrameLayout)findViewById(R.id.camera_preview);
		mDetector = new GestureDetector(this, new MyGestureDetector(iv));
		iv.setOnTouchListener(new View.OnTouchListener(){
			@Override
			public boolean onTouch(View v, MotionEvent event)
			{
				mDetector.onTouchEvent(event);
				return true;
			}});
	}

	private void initializeImages( int width , int height ) {
		graySrc = new ImageFloat32(width,height);
		grayDst = new ImageFloat32(width,height);

		bitmapSrc = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
		bitmapDst = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
		storage = ConvertBitmap.declareStorage(bitmapSrc, storage);
	}


	@Override
	public void onItemSelected(AdapterView<?> adapterView, View view, int pos, long id ) {
		if( adapterView == spinnerDesc ) {
			selectedDesc = spinnerDesc.getSelectedItemPosition();
		} else if( adapterView == spinnerDet ) {
			selectedDet = spinnerDet.getSelectedItemPosition();
		}

		DetectDescribePoint detDesc = CreateDetectorDescriptor.create(selectedDet,selectedDesc,ImageFloat32.class);

		ScoreAssociation score = FactoryAssociation.defaultScore(detDesc.getDescriptionType());
		AssociateDescription assoc = FactoryAssociation.greedy(score,Double.MAX_VALUE,true);

		setProcessing(new AssociationProcessing(detDesc, assoc ));
	}

	@Override
	public void onNothingSelected(AdapterView<?> adapterView) {}

	protected class MyGestureDetector extends GestureDetector.SimpleOnGestureListener
	{
		View v;

		public MyGestureDetector(View v) {
			this.v = v;
		}

		@Override
		public boolean onDown(MotionEvent e) {

			if( hasLeft && hasRight ) {
				mouseX = (int)e.getX();
				mouseY = (int)e.getY();
				hasMatch = false;
			} else {
				int half = v.getWidth()/2;

				if( e.getX() < half ) {
					target = 1;
				} else {
					target = 2;
				}
			}

			return true;
		}

		/**
		 * If the user flings an image discard the results in the image
		 */
		@Override
		public boolean onFling( MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
			int half = v.getWidth()/2;

			if( e1.getX() < half ) {
				hasLeft = false;
			} else {
				hasRight = false;
			}
			forgetSelection();

			return true;
		}

		@Override
		public boolean onDoubleTapEvent(MotionEvent e)
		{
			// clear selection of individual match
			forgetSelection();
			return true;
		}
	}


	protected class AssociationProcessing<Desc extends TupleDesc> extends BoofRenderProcessing<ImageUInt8> {
		DetectDescribePoint<ImageFloat32,Desc> detDesc;
		AssociateDescription<Desc> associate;

		boolean checkPrevious = true;

		ImageFloat32 grayF32;

		FastQueue<Desc> listSrc;
		FastQueue<Desc> listDst;
		FastQueue<Point2D_F64> locationSrc = new FastQueue<Point2D_F64>(Point2D_F64.class,true);
		FastQueue<Point2D_F64> locationDst = new FastQueue<Point2D_F64>(Point2D_F64.class,true);

		public AssociationProcessing( DetectDescribePoint<ImageFloat32,Desc> detDesc ,
									  AssociateDescription<Desc> associate  ) {
			super(ImageUInt8.class);
			this.detDesc = detDesc;
			this.associate = associate;

			// clear any user selected points
			forgetSelection();

			listSrc = UtilFeature.createQueue(detDesc,10);
			listDst = UtilFeature.createQueue(detDesc,10);
		}

		@Override
		protected void declareImages(int width, int height) {
			super.declareImages(width, height);

			outputWidth = width + SEPARATION + width;
			outputHeight = height;

			grayF32 = new ImageFloat32(width,height);
		}

		@Override
		protected synchronized void process(ImageUInt8 gray) {
			if( checkPrevious ) {
				checkPrevious = false;
				if( hasLeft ) {
					detDesc.detect(graySrc);
					describeImage(listSrc, locationSrc);
				}
				if( hasRight ) {
					detDesc.detect(grayDst);
					describeImage(listDst, locationDst);
				}
				if( hasLeft && hasRight ) {
					associate.setSource(listSrc);
					associate.setDestination(listDst);
					associate.associate();
				}
				return;
			}

			if( target == 0 )
				return;

			ConvertImage.convert(gray,grayF32);
			detDesc.detect(grayF32);

			FastQueue<Desc> listDesc = target == 1 ? listSrc : listDst;
			FastQueue<Point2D_F64> listLoc = target == 1 ? locationSrc : locationDst;
			describeImage(listDesc, listLoc);

			if( target == 1 ) {
				hasLeft = true;
				graySrc.setTo(grayF32);
				ConvertBitmap.grayToBitmap(gray,bitmapSrc,storage);
			} else if( target == 2 ) {
				hasRight = true;
				grayDst.setTo(grayF32);
				ConvertBitmap.grayToBitmap(gray,bitmapDst,storage);
			}

			if( hasLeft && hasRight ) {
				associate.setSource(listSrc);
				associate.setDestination(listDst);
				associate.associate();
			}

			target = 0;
		}

		private void describeImage(FastQueue<Desc> listDesc, FastQueue<Point2D_F64> listLoc) {
			listDesc.reset();
			listLoc.reset();
			int N = detDesc.getNumberOfFeatures();
			for( int i = 0; i < N; i++ ) {
				listLoc.grow().set(detDesc.getLocation(i));
				listDesc.grow().setTo(detDesc.getDescription(i));
			}
		}

		@Override
		protected synchronized void render(Canvas canvas, double imageToOutput) {
			int startX = bitmapSrc.getWidth()+SEPARATION;

			// draw captured images
			if( hasLeft ) {
				canvas.drawBitmap(bitmapSrc,0,0,null);
			}

			if( hasRight ) {
				canvas.drawBitmap(bitmapDst,startX,0,null);
			}

			// draw features and matches
			if( hasLeft && hasRight ) {
				// see if the user is selecting a point
				if( mouseX >= 0 ) {
					handleUserSelection(canvas, startX);
				} else {
					drawAllMatches(canvas, startX);
				}

			} else if( hasLeft ) {
				for( int i = 0; i < locationSrc.size; i++ ) {
					Point2D_F64 p = locationSrc.get(i);
					canvas.drawCircle((float)p.x,(float)p.y,3,paintPoint);
				}
			} else if( hasRight ) {
				for( int i = 0; i < locationDst.size; i++ ) {
					Point2D_F64 p = locationDst.get(i);
					canvas.drawCircle((float)p.x + startX,(float)p.y,3,paintPoint);
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
			FastQueue<AssociatedIndex> matches = associate.getMatches();

			for( int i = 0; i < matches.size; i++ ) {
				AssociatedIndex m = matches.get(i);
				Point2D_F64 s = locationSrc.get(m.src);
				Point2D_F64 d = locationDst.get(m.dst);
				canvas.drawLine((float)s.x,(float)s.y,(float)d.x+startX,(float)d.y,paintPoint);
			}
		}

		/**
		 * Selects the features which is closest to the where the user selected
		 */
		private boolean findBestMatch(int startX) {
			FastQueue<AssociatedIndex> matches = associate.getMatches();

			// 1 mm in pixels
			float px = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_MM, 1,
					getResources().getDisplayMetrics());
			// 1 cm tolerance

			double bestDistance = Math.pow((px*10)/scale,2);
			AssociatedIndex best = null;

			Point2D_F64 imagePt = new Point2D_F64();
			imageToOutput(mouseX,mouseY,imagePt);

			// see if it is inside the left image
			if( imagePt.x < startX ) {
				for( int i = 0; i < matches.size; i++ ) {
					AssociatedIndex m = matches.get(i);
					Point2D_F64 s = locationSrc.get(m.src);

					double dist = s.distance2(imagePt);
					if( dist < bestDistance ) {
						bestDistance = dist;
						best = m;
					}
				}
			} else {
				imagePt.x -= startX;
				for( int i = 0; i < matches.size; i++ ) {
					AssociatedIndex m = matches.get(i);
					Point2D_F64 d = locationDst.get(m.dst);

					double dist = d.distance2(imagePt);
					if( dist < bestDistance ) {
						bestDistance = dist;
						best = m;
					}
				}
			}

			if( best != null ) {
				Point2D_F64 s = locationSrc.get(best.src);
				Point2D_F64 d = locationDst.get(best.dst);

				matchedPair.p1.set(s);
				matchedPair.p2.set(d);
				return true;
			}
			return false;
		}
	}

	private void forgetSelection() {
		mouseX = -1;
		hasMatch = false;
	}
}