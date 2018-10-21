package org.boofcv.android.segmentation;

import android.graphics.Canvas;
import android.graphics.Matrix;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.Toast;

import org.boofcv.android.DemoBitmapCamera2Activity;
import org.boofcv.android.DemoProcessingAbstract;
import org.boofcv.android.R;
import org.ddogleg.struct.FastQueue;
import org.ddogleg.struct.GrowQueue_I32;
import org.ddogleg.struct.Stoppable;

import boofcv.abst.segmentation.ImageSuperpixels;
import boofcv.alg.segmentation.ComputeRegionMeanColor;
import boofcv.alg.segmentation.ImageSegmentationOps;
import boofcv.android.ConvertBitmap;
import boofcv.android.VisualizeImageData;
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
public class SuperpixelDisplayActivity extends DemoBitmapCamera2Activity
		implements AdapterView.OnItemSelectedListener
{
	private static final String TAG = "Superpixel";

	Spinner spinnerView;

	Mode mode = Mode.VIDEO;


	public SuperpixelDisplayActivity() {
		super(Resolution.MEDIUM);
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		LayoutInflater inflater = getLayoutInflater();
		LinearLayout controls = (LinearLayout)inflater.inflate(R.layout.superpixel_controls,null);

		setControls(controls);

		spinnerView = controls.findViewById(R.id.spinner_algs);
		ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(this,
				R.array.segmentation_algs, android.R.layout.simple_spinner_item);
		adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		spinnerView.setAdapter(adapter);
		spinnerView.setOnItemSelectedListener(this);

		displayView.setOnTouchListener((view, motionEvent) -> {
			if( motionEvent.getAction() != MotionEvent.ACTION_DOWN )
				return false;
			Log.i(TAG,"on touch. Mode = "+mode);
            if( mode == Mode.VIDEO) {
                mode = Mode.PROCESS;
                return true;
            } else if( mode == Mode.PROCESS )
                return false;
            mode = Mode.values()[2+(mode.ordinal()-1)%3];
			Log.i(TAG," After Mode = "+mode);
            return true;
        });

		Toast.makeText(this,"FAST DEVICES ONLY! Can take minutes.",Toast.LENGTH_LONG).show();
	}

	public void pressedReset( View view ) {
		if( mode != Mode.PROCESS )
			mode = Mode.VIDEO;
	}

	@Override
	public void createNewProcessor() {
		startSegmentProcess(spinnerView.getSelectedItemPosition());
	}

	@Override
	public void onItemSelected(AdapterView<?> adapterView, View view, int pos, long id ) {
		startSegmentProcess(pos);
	}

	private void startSegmentProcess(int pos) {
		mode = Mode.VIDEO;
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


	protected class SegmentationProcessing extends DemoProcessingAbstract<Planar<GrayU8>> {
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
		public void initialize(int imageWidth, int imageHeight, int sensorOrientation) {
			pixelToRegion = new GrayS32(imageWidth,imageHeight);
			background = new Planar<>(GrayU8.class, imageWidth, imageHeight, 3);
		}

		@Override
		public void onDraw(Canvas canvas, Matrix imageToView) {
			drawBitmap(canvas,imageToView);
		}

		@Override
		public void process(Planar<GrayU8> input) {
			if (mode == Mode.VIDEO) {
				convertToBitmapDisplay(input);
			} else if( mode == Mode.PROCESS ) {
				// save the current image
				background.setTo(input);
				setProgressMessage("Segmenting", true);
				try {
					segmentation.segment(input, pixelToRegion);
				} catch( Stoppable.Stopped ignore ){}
				if( wasStopped() ) {
					Log.d(TAG,"Was stopped!!!");
					mode = Mode.VIDEO;
					return;
				}
				// Computes the mean color inside each region

				ComputeRegionMeanColor colorize = FactorySegmentationAlg.regionMeanColor(input.getImageType());

				int numSegments = segmentation.getTotalSuperpixels();

				segmentColor.resize(numSegments);
				regionMemberCount.resize(numSegments);

				ImageSegmentationOps.countRegionPixels(pixelToRegion, numSegments, regionMemberCount.data);
				colorize.process(background,pixelToRegion,regionMemberCount,segmentColor);

				hideProgressDialog();
				mode = Mode.SHOW_MEAN;
			} else {
				if (mode == Mode.SHOW_IMAGE) {
					ConvertBitmap.planarToBitmap(background, bitmap, bitmapTmp);
				} else if (mode == Mode.SHOW_MEAN) {
					VisualizeImageData.regionsColor(pixelToRegion, segmentColor, bitmap, bitmapTmp);
				} else if (mode == Mode.SHOW_LINES) {
					VisualizeImageData.regionsColor(pixelToRegion, segmentColor, bitmap, bitmapTmp);
					VisualizeImageData.regionBorders(pixelToRegion, 0xFF0000, bitmap, bitmapTmp);
				}
			}
		}

		@Override
		public void stop() {
			if( mode == Mode.PROCESS ) {
				requestStop();
			}
		}

		private boolean wasStopped() {
			try {
				return ((Stoppable)segmentation).isStopRequested();
			} catch( RuntimeException ignore){
				return false;
			}
		}

		public void requestStop() {
			try {
				((Stoppable)segmentation).requestStop();
			} catch( RuntimeException ignore){}
		}
	}

	@Override
	protected void progressCanceled() {
		Log.d(TAG,"Requesting stop.");
		synchronized (lockProcessor ) {
			if( processor != null )
				((SegmentationProcessing) processor).requestStop();
		}
	}

	enum Mode {
		VIDEO,
		PROCESS,
		SHOW_MEAN,
		SHOW_LINES,
		SHOW_IMAGE
	}
}