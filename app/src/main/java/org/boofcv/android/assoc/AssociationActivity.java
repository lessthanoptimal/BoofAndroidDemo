package org.boofcv.android.assoc;

import android.graphics.Canvas;
import android.os.Bundle;
import android.view.GestureDetector;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.Spinner;

import org.boofcv.android.CreateDetectorDescriptor;
import org.boofcv.android.DemoVideoDisplayActivity;
import org.boofcv.android.R;
import org.ddogleg.struct.FastQueue;

import java.util.ArrayList;
import java.util.List;

import boofcv.abst.feature.associate.AssociateDescription;
import boofcv.abst.feature.associate.ScoreAssociation;
import boofcv.abst.feature.detdesc.DetectDescribePoint;
import boofcv.alg.descriptor.UtilFeature;
import boofcv.android.gui.VideoRenderProcessing;
import boofcv.factory.feature.associate.FactoryAssociation;
import boofcv.struct.feature.AssociatedIndex;
import boofcv.struct.feature.TupleDesc;
import boofcv.struct.image.GrayF32;
import boofcv.struct.image.ImageType;
import georegression.struct.point.Point2D_F64;

/**
 * Activity which shows associated features between two images
 *
 * @author Peter Abeles
 */
public class AssociationActivity extends DemoVideoDisplayActivity
		implements AdapterView.OnItemSelectedListener
{
	Spinner spinnerDesc;
	Spinner spinnerDet;

	AssociationVisualize visualize;
	// if true the algorithm changed and it should reprocess the images it has in memory
	boolean changedAlg = false;

	// indicate where the user touched the screen
	volatile int touchEventType = 0;
	volatile int touchX;
	volatile int touchY;

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

		LinearLayout parent = getViewContent();
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

		FrameLayout iv = getViewPreview();
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
		visualize.setSource(null);
		visualize.setDestination(null);

		startAssociationProcessing();
	}

	@Override
	public void onItemSelected(AdapterView<?> adapterView, View view, int pos, long id ) {
		if( adapterView == spinnerDesc ) {
			selectedDesc = spinnerDesc.getSelectedItemPosition();
		} else if( adapterView == spinnerDet ) {
			selectedDet = spinnerDet.getSelectedItemPosition();
		}

		startAssociationProcessing();
	}

	private void startAssociationProcessing() {
		DetectDescribePoint detDesc = CreateDetectorDescriptor.create(selectedDet, selectedDesc, GrayF32.class);

		ScoreAssociation score = FactoryAssociation.defaultScore(detDesc.getDescriptionType());
		AssociateDescription assoc = FactoryAssociation.greedy(score,Double.MAX_VALUE,true);

		setProcessing(new AssociationProcessing(detDesc, assoc));
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

			touchEventType = 1;
			touchX = (int)e.getX();
			touchY = (int)e.getY();

			return true;
		}

		/**
		 * If the user flings an image discard the results in the image
		 */
		@Override
		public boolean onFling( MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
			touchEventType = (int)e1.getX() < v.getWidth()/2 ? 2 : 3;

			return true;
		}

		@Override
		public boolean onDoubleTapEvent(MotionEvent e)
		{
			touchEventType = 4;
			return true;
		}
	}


	protected class AssociationProcessing<Desc extends TupleDesc> extends VideoRenderProcessing<GrayF32> {
		DetectDescribePoint<GrayF32,Desc> detDesc;
		AssociateDescription<Desc> associate;

		FastQueue<Desc> listSrc;
		FastQueue<Desc> listDst;
		FastQueue<Point2D_F64> locationSrc = new FastQueue<Point2D_F64>(Point2D_F64.class,true);
		FastQueue<Point2D_F64> locationDst = new FastQueue<Point2D_F64>(Point2D_F64.class,true);

		public AssociationProcessing( DetectDescribePoint<GrayF32,Desc> detDesc ,
									  AssociateDescription<Desc> associate  ) {
			super(ImageType.single(GrayF32.class));
			this.detDesc = detDesc;
			this.associate = associate;


			listSrc = UtilFeature.createQueue(detDesc,10);
			listDst = UtilFeature.createQueue(detDesc,10);
		}

		@Override
		protected void declareImages(int width, int height) {
			super.declareImages(width, height);
			visualize.initializeImages( width, height );

			outputWidth = visualize.getOutputWidth();
			outputHeight = visualize.getOutputHeight();
			changedAlg = true;
		}

		@Override
		protected void process(GrayF32 gray) {
			boolean computedFeatures = false;

			int target = 0;

			// process GUI interactions
			synchronized ( lockGui ) {
				if( touchEventType == 1 ) {
					// first see if there are any features to select
					if( !visualize.setTouch(touchX,touchY) ) {
						// if not then it must be a capture image request
						target = touchX < view.getWidth()/2 ? 1 : 2;
					}
				} else if( touchEventType == 2 ) {
					visualize.setSource(null);
					visualize.forgetSelection();
				} else if( touchEventType == 3 ) {
					visualize.setDestination(null);
					visualize.forgetSelection();
				} else if( touchEventType == 4 ) {
					visualize.forgetSelection();
				}
			}
			touchEventType = 0;

			// The algorithm being processed was changed and the old image should be reprocessed
			if( changedAlg ) {
				changedAlg = false;
				if( visualize.hasLeft || visualize.hasRight ) {
					setProgressMessage("Detect/Describe images");
				}

				// recompute image features with the newly selected algorithm
				if( visualize.hasLeft ) {
					detDesc.detect(visualize.graySrc);
					describeImage(listSrc, locationSrc);
					computedFeatures = true;
				}
				if( visualize.hasRight ) {
					detDesc.detect(visualize.grayDst);
					describeImage(listDst, locationDst);
					computedFeatures = true;
				}

				synchronized ( lockGui ) {
					visualize.forgetSelection();
				}
			}

			// compute image features for left or right depending on user selection
			if( target != 0 )
				setProgressMessage("Detect/Describe image");

			if( target == 1 ) {
				detDesc.detect(gray);
				describeImage(listSrc, locationSrc);
				computedFeatures = true;
			} else if( target == 2 ) {
				detDesc.detect(gray);
				describeImage(listDst, locationDst);
				computedFeatures = true;
			}

			synchronized ( lockGui ) {
				if( target == 1 ) {
					visualize.setSource(gray);
				} else if( target == 2 ) {
					visualize.setDestination(gray);
				}
			}

			// associate image features
			if( computedFeatures && visualize.hasLeft && visualize.hasRight ) {
				setProgressMessage("Associating");
				associate.setSource(listSrc);
				associate.setDestination(listDst);
				associate.associate();

				synchronized ( lockGui ) {
					List<Point2D_F64> pointsSrc = new ArrayList<Point2D_F64>();
					List<Point2D_F64> pointsDst = new ArrayList<Point2D_F64>();

					FastQueue<AssociatedIndex> matches = associate.getMatches();
					for( int i = 0; i < matches.size; i++ ) {
						AssociatedIndex m = matches.get(i);
						pointsSrc.add(locationSrc.get(m.src));
						pointsDst.add(locationDst.get(m.dst));
					}
					visualize.setMatches(pointsSrc,pointsDst);
					visualize.forgetSelection();
				}
			}

			hideProgressDialog();
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
		protected void render(Canvas canvas, double imageToOutput) {
			visualize.render(canvas,tranX,tranY,scale);
		}
	}
}