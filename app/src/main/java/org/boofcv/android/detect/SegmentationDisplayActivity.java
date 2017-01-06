package org.boofcv.android.detect;

import android.graphics.Bitmap;
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
import android.widget.Toast;

import org.boofcv.android.DemoVideoDisplayActivity;
import org.boofcv.android.R;
import org.ddogleg.struct.FastQueue;
import org.ddogleg.struct.GrowQueue_I32;

import boofcv.abst.segmentation.ImageSuperpixels;
import boofcv.alg.segmentation.ComputeRegionMeanColor;
import boofcv.alg.segmentation.ImageSegmentationOps;
import boofcv.android.ConvertBitmap;
import boofcv.android.VisualizeImageData;
import boofcv.android.gui.VideoImageProcessing;
import boofcv.factory.segmentation.ConfigSlic;
import boofcv.factory.segmentation.FactoryImageSegmentation;
import boofcv.factory.segmentation.FactorySegmentationAlg;
import boofcv.struct.feature.ColorQueue_F32;
import boofcv.struct.image.GrayS32;
import boofcv.struct.image.GrayU8;
import boofcv.struct.image.ImageType;
import boofcv.struct.image.Planar;

/**
 * Displays the results of image segmentation after the user clicks on an image
 *
 * @author Peter Abeles
 */
public class SegmentationDisplayActivity extends DemoVideoDisplayActivity
		implements AdapterView.OnItemSelectedListener
{

	Spinner spinnerView;

	Mode mode = Mode.VIEW_VIDEO;
	boolean hasSegment = false;

	private GestureDetector mDetector;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		LayoutInflater inflater = getLayoutInflater();
		LinearLayout controls = (LinearLayout)inflater.inflate(R.layout.select_algorithm,null);

		LinearLayout parent = getViewContent();
		parent.addView(controls);

		spinnerView = (Spinner)controls.findViewById(R.id.spinner_algs);
		ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(this,
				R.array.segmentation_algs, android.R.layout.simple_spinner_item);
		adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		spinnerView.setAdapter(adapter);
		spinnerView.setOnItemSelectedListener(this);

		FrameLayout iv = getViewPreview();
		mDetector = new GestureDetector(this, new MyGestureDetector(iv));
		iv.setOnTouchListener(new View.OnTouchListener(){
			@Override
			public boolean onTouch(View v, MotionEvent event)
			{
				mDetector.onTouchEvent(event);
				return true;
			}});

		Toast.makeText(this,"FAST DEVICES ONLY! Can take minutes.",Toast.LENGTH_LONG).show();
	}

	@Override
	protected void onResume() {
		super.onResume();
		startSegmentProcess(spinnerView.getSelectedItemPosition());
	}

	@Override
	public void onItemSelected(AdapterView<?> adapterView, View view, int pos, long id ) {
		startSegmentProcess(pos);
	}

	private void startSegmentProcess(int pos) {

		mode = Mode.VIEW_VIDEO;
		hasSegment = false;

		ImageType<Planar<GrayU8>> type = ImageType.pl(3, GrayU8.class);

		switch (pos) {
			case 0:
				setProcessing(new SegmentationProcessing(FactoryImageSegmentation.watershed(null, type)) );
				break;

			case 1:
				setProcessing(new SegmentationProcessing(FactoryImageSegmentation.fh04(null, type)) );
				break;

			case 2:
				setProcessing(new SegmentationProcessing(FactoryImageSegmentation.slic(new ConfigSlic(100), type)) );
				break;

			case 3:
				setProcessing(new SegmentationProcessing(FactoryImageSegmentation.meanShift(null, type)) );
				break;
		}
	}

	@Override
	public void onNothingSelected(AdapterView<?> adapterView) {}

	protected class MyGestureDetector extends GestureDetector.SimpleOnGestureListener {
		View v;

		public MyGestureDetector(View v) {
			this.v = v;
		}

		@Override
		public boolean onDown(MotionEvent e) {
			// toggle just displaying the video feed versus a segmented image
			switch( mode ) {
				case VIEW_VIDEO:mode = Mode.VIEW_MEAN;break;
				case VIEW_MEAN:mode = Mode.VIEW_LINES;break;
				case VIEW_LINES:mode = Mode.VIEW_VIDEO;break;
			}
			if( mode == Mode.VIEW_MEAN ) {
				hasSegment = false;
			}
			return true;
		}
	}

	protected class SegmentationProcessing extends VideoImageProcessing<Planar<GrayU8>> {
		GrayS32 pixelToRegion;
		ImageSuperpixels<Planar<GrayU8>> segmentation;
		Planar<GrayU8> background;

		ComputeRegionMeanColor colorize;
		FastQueue<float[]> segmentColor = new ColorQueue_F32(3);
		GrowQueue_I32 regionMemberCount = new GrowQueue_I32();

		public SegmentationProcessing(ImageSuperpixels<Planar<GrayU8>> segmentation) {
			super(ImageType.pl(3, GrayU8.class));
			this.segmentation = segmentation;
			this.colorize = FactorySegmentationAlg.regionMeanColor(segmentation.getImageType());
		}

		@Override
		protected void declareImages( int width , int height ) {
			super.declareImages(width, height);

			pixelToRegion = new GrayS32(width,height);
			background = new Planar<GrayU8>(GrayU8.class,width,height,3);
		}

		@Override
		protected void process(Planar<GrayU8> input, Bitmap output, byte[] storage) {

			// TODO if the user tries to exit while computing a segmentation things get weird
			if( mode != Mode.VIEW_VIDEO ) {
				if( !hasSegment ) {
					// save the current image
					background.setTo(input);
					hasSegment = true;
					setProgressMessage("Slowly Segmenting");
					segmentation.segment(input, pixelToRegion);

					// Computes the mean color inside each region
					ComputeRegionMeanColor colorize = FactorySegmentationAlg.regionMeanColor(input.getImageType());

					int numSegments = segmentation.getTotalSuperpixels();

					segmentColor.resize(numSegments);
					regionMemberCount.resize(numSegments);

					ImageSegmentationOps.countRegionPixels(pixelToRegion, numSegments, regionMemberCount.data);
					colorize.process(background,pixelToRegion,regionMemberCount,segmentColor);

					hideProgressDialog();
				}
				VisualizeImageData.regionsColor(pixelToRegion, segmentColor, output, storage);
				if( mode == Mode.VIEW_LINES ) {
					VisualizeImageData.regionBorders(pixelToRegion, 0xFF0000, output, storage);
				}
			} else {
				ConvertBitmap.multiToBitmap(input,output,storage);
			}
		}
	}

	static enum Mode {
		VIEW_VIDEO,
		VIEW_MEAN,
		VIEW_LINES
	}
}