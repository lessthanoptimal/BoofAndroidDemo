package org.boofcv.android;

import android.graphics.Canvas;
import android.hardware.Camera;
import android.os.Bundle;
import android.view.GestureDetector;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.widget.*;
import boofcv.abst.feature.associate.AssociateDescription;
import boofcv.abst.feature.associate.ScoreAssociation;
import boofcv.abst.feature.detdesc.DetectDescribePoint;
import boofcv.alg.feature.UtilFeature;
import boofcv.factory.feature.associate.FactoryAssociation;
import boofcv.struct.FastQueue;
import boofcv.struct.feature.AssociatedIndex;
import boofcv.struct.feature.TupleDesc;
import boofcv.struct.image.ImageFloat32;
import georegression.struct.point.Point2D_F64;

import java.util.ArrayList;
import java.util.List;

/**
 * Activity which shows associated features between two images
 *
 * @author Peter Abeles
 */
public class AssociationActivity extends VideoDisplayActivity
		implements AdapterView.OnItemSelectedListener
{
	Spinner spinnerDesc;
	Spinner spinnerDet;

	AssociationVisualize visualize;

	int target = 0;

	private GestureDetector mDetector;

	int selectedDet = 0;
	int selectedDesc = 0;

	public AssociationActivity() {
		visualize = new AssociationVisualize(this);
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

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

	@Override
	protected void onResume() {
		super.onResume();
		Camera.Size size = mCamera.getParameters().getPreviewSize();
		visualize.initializeImages( size.width, size.height );
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

			if( !visualize.setTouch((int)e.getX(),(int)e.getY()))
			{
				// select an image to capture
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
				visualize.setSource(null);
			} else {
				visualize.setDestination(null);
			}
			visualize.forgetSelection();

			return true;
		}

		@Override
		public boolean onDoubleTapEvent(MotionEvent e)
		{
			// clear selection of individual match
			visualize.forgetSelection();
			return true;
		}
	}


	protected class AssociationProcessing<Desc extends TupleDesc> extends BoofRenderProcessing<ImageFloat32> {
		DetectDescribePoint<ImageFloat32,Desc> detDesc;
		AssociateDescription<Desc> associate;

		FastQueue<Desc> listSrc;
		FastQueue<Desc> listDst;
		FastQueue<Point2D_F64> locationSrc = new FastQueue<Point2D_F64>(Point2D_F64.class,true);
		FastQueue<Point2D_F64> locationDst = new FastQueue<Point2D_F64>(Point2D_F64.class,true);

		public AssociationProcessing( DetectDescribePoint<ImageFloat32,Desc> detDesc ,
									  AssociateDescription<Desc> associate  ) {
			super(ImageFloat32.class);
			this.detDesc = detDesc;
			this.associate = associate;

			// clear any user selected points
			visualize.forgetSelection();

			listSrc = UtilFeature.createQueue(detDesc,10);
			listDst = UtilFeature.createQueue(detDesc,10);
		}

		@Override
		protected void declareImages(int width, int height) {
			super.declareImages(width, height);

			outputWidth = visualize.getOutputWidth();
			outputHeight = visualize.getOutputHeight();
		}

		@Override
		protected synchronized void process(ImageFloat32 gray) {
			if( target == 0 )
				return;

			detDesc.detect(gray);

			if( target == 1 ) {
				visualize.setSource(gray);
				detDesc.detect(gray);
				describeImage(listSrc, locationSrc);
			} else if( target == 2 ) {
				visualize.setDestination(gray);
				detDesc.detect(gray);
				describeImage(listDst, locationDst);
			}

			if( visualize.hasLeft && visualize.hasRight ) {
				associate.setSource(listSrc);
				associate.setDestination(listDst);
				associate.associate();

				List<Point2D_F64> pointsSrc = new ArrayList<Point2D_F64>();
				List<Point2D_F64> pointsDst = new ArrayList<Point2D_F64>();

				FastQueue<AssociatedIndex> matches = associate.getMatches();
				for( int i = 0; i < matches.size; i++ ) {
					AssociatedIndex m = matches.get(i);
					pointsSrc.add( locationSrc.get(m.src));
					pointsDst.add(locationDst.get(m.dst));
				}
				visualize.setMatches(pointsSrc,pointsDst);
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
			visualize.render(canvas,tranX,tranY,scale);
		}
	}
}