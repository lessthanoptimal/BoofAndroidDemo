package org.boofcv.android.ip;

import android.graphics.Canvas;
import android.graphics.Matrix;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.Spinner;

import org.boofcv.android.DemoBitmapCamera2Activity;
import org.boofcv.android.DemoProcessingAbstract;
import org.boofcv.android.R;
import org.ddogleg.struct.DogArray_I32;

import boofcv.alg.enhance.EnhanceImageOps;
import boofcv.alg.misc.ImageStatistics;
import boofcv.android.ConvertBitmap;
import boofcv.concurrency.BoofConcurrency;
import boofcv.core.image.ConvertImage;
import boofcv.struct.image.GrayU8;
import boofcv.struct.image.ImageBase;
import boofcv.struct.image.ImageType;
import boofcv.struct.image.Planar;
import pabeles.concurrency.GrowArray;

/**
 * Blurs the input video image using different algorithms.
 *
 * @author Peter Abeles
 */
public class EnhanceDisplayActivity extends DemoBitmapCamera2Activity
		implements AdapterView.OnItemSelectedListener, CompoundButton.OnCheckedChangeListener
{

	GrowArray<DogArray_I32> work = new GrowArray<>(DogArray_I32::new);
	Spinner spinnerView;
	CheckBox checkColor;

	public EnhanceDisplayActivity() {
		super(Resolution.MEDIUM);
		super.changeResolutionOnSlow = true;
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		LayoutInflater inflater = getLayoutInflater();
		LinearLayout controls = (LinearLayout)inflater.inflate(R.layout.enhance_controls,null);

		spinnerView = controls.findViewById(R.id.spinner_enhance);
		ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(this,
				R.array.enhance, android.R.layout.simple_spinner_item);
		adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		spinnerView.setAdapter(adapter);
		spinnerView.setOnItemSelectedListener(this);

		checkColor = controls.findViewById(R.id.check_color);
		checkColor.setOnCheckedChangeListener(this);

		setControls(controls);
		activateTouchToShowInput();
	}

	@Override
	public void createNewProcessor() {
		startEnhance(spinnerView.getSelectedItemPosition(),checkColor.isChecked());
	}

	@Override
	public void onItemSelected(AdapterView<?> adapterView, View view, int pos, long id ) {
		startEnhance(pos,checkColor.isChecked());
	}

	private void startEnhance(int pos, boolean color ) {
		switch (pos) {
			case 0:
				if( color )
					setProcessing(new HistogramGlobalProcessingColor() );
				else
					setProcessing(new HistogramGlobalProcessing() );
				break;

			case 1:
				if( color )
					setProcessing(new HistogramLocalProcessingColor() );
				else
					setProcessing(new HistogramLocalProcessing() );
				break;

			case 2:
				if( color )
					setProcessing(new SharpenProcessingColor(4) );
				else
					setProcessing(new SharpenProcessing(4) );
				break;

			case 3:
				if( color )
					setProcessing(new SharpenProcessingColor(8) );
				else
					setProcessing(new SharpenProcessing(8) );
				break;
		}
	}

	@Override
	public void onNothingSelected(AdapterView<?> adapterView) {}

	@Override
	public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
		startEnhance(spinnerView.getSelectedItemPosition(),isChecked);
	}

	protected void renderOutput( ImageBase output ) {
		ConvertBitmap.boofToBitmap(output, bitmap, bitmapTmp);
	}

	protected abstract class EnhanceProcessing extends DemoProcessingAbstract<GrayU8> {
		int histogram[] = new int[256];
		int transform[] = new int[256];
		GrayU8 enhanced = new GrayU8(1,1);

		protected EnhanceProcessing() {
			super(ImageType.single(GrayU8.class));
		}

		@Override
		public void initialize(int imageWidth, int imageHeight, int sensorOrientation) {
			enhanced.reshape(imageWidth,imageHeight);
		}

		@Override
		public void onDraw(Canvas canvas, Matrix imageToView) {
			drawBitmap(canvas,imageToView);
		}
	}

	protected abstract class EnhanceProcessingColor extends DemoProcessingAbstract<Planar<GrayU8>> {
		int histogram[] = new int[256];
		int transform[] = new int[256];

		Planar<GrayU8> enhanced = new Planar<>(GrayU8.class, 1, 1, 3);
		GrayU8 gray = new GrayU8(1,1);

		public EnhanceProcessingColor() {
			super(ImageType.pl(3, GrayU8.class));
		}

		@Override
		public void initialize(int imageWidth, int imageHeight, int sensorOrientation) {
			gray.reshape(imageWidth,imageHeight);
			enhanced.reshape(imageWidth,imageHeight);
		}

		@Override
		public void onDraw(Canvas canvas, Matrix imageToView) {
			drawBitmap(canvas,imageToView);
		}
	}

	protected class HistogramGlobalProcessing extends EnhanceProcessing {

		@Override
		public void process(GrayU8 input) {
			ImageStatistics.histogram(input,0, histogram);
			EnhanceImageOps.equalize(histogram, transform);
			EnhanceImageOps.applyTransform(input, transform, enhanced);
			renderOutput(enhanced);
		}
	}

	protected class HistogramGlobalProcessingColor extends EnhanceProcessingColor {
		@Override
		public void process(Planar<GrayU8> input) {
			ConvertImage.average(input,gray);
			ImageStatistics.histogram(gray,0, histogram);
			EnhanceImageOps.equalize(histogram, transform);
			for( int i = 0; i < 3; i++ )
				EnhanceImageOps.applyTransform(input.getBand(i), transform, enhanced.getBand(i));
			renderOutput(enhanced);
		}
	}

	protected class HistogramLocalProcessing extends EnhanceProcessing {
		@Override
		public void process(GrayU8 input) {
			EnhanceImageOps.equalizeLocal(input, 50, enhanced, 256, work);
			renderOutput(enhanced);
		}
	}

	protected class HistogramLocalProcessingColor extends EnhanceProcessingColor {

		@Override
		public void process(Planar<GrayU8> input ) {
			for( int i = 0; i < 3; i++ )
				EnhanceImageOps.equalizeLocal(input.getBand(i), 50, enhanced.getBand(i), 256, work);
			renderOutput(enhanced);
		}
	}

	protected class SharpenProcessing extends EnhanceProcessing {

		int which;

		public SharpenProcessing( int which ) {
			this.which = which;
		}

		@Override
		public void process(GrayU8 input) {
			if( which == 4 )
				EnhanceImageOps.sharpen4(input, enhanced);
			else
				EnhanceImageOps.sharpen8(input, enhanced);
			renderOutput(enhanced);
		}
	}

	protected class SharpenProcessingColor extends EnhanceProcessingColor {

		int which;

		public SharpenProcessingColor( int which ) {
			this.which = which;
		}

		@Override
		public void process( Planar<GrayU8> input) {
			for( int i = 0; i < 3; i++ ) {
				if( which == 4 )
					EnhanceImageOps.sharpen4(input.getBand(i), enhanced.getBand(i));
				else
					EnhanceImageOps.sharpen8(input.getBand(i), enhanced.getBand(i));
			}
			renderOutput(enhanced);
		}
	}
}