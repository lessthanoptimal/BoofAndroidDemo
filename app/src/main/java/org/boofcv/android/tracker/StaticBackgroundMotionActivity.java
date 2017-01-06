package org.boofcv.android.tracker;

import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.Spinner;

import org.boofcv.android.DemoVideoDisplayActivity;
import org.boofcv.android.R;

import boofcv.abst.distort.FDistort;
import boofcv.alg.background.BackgroundModelStationary;
import boofcv.alg.background.stationary.BackgroundStationaryBasic;
import boofcv.alg.background.stationary.BackgroundStationaryGaussian;
import boofcv.alg.misc.ImageMiscOps;
import boofcv.alg.misc.PixelMath;
import boofcv.android.ConvertBitmap;
import boofcv.android.VisualizeImageData;
import boofcv.android.gui.VideoImageProcessing;
import boofcv.core.image.GConvertImage;
import boofcv.core.image.GeneralizedImageOps;
import boofcv.factory.background.ConfigBackgroundBasic;
import boofcv.factory.background.ConfigBackgroundGaussian;
import boofcv.factory.background.FactoryBackgroundModel;
import boofcv.struct.image.GrayU8;
import boofcv.struct.image.ImageBase;
import boofcv.struct.image.ImageDataType;
import boofcv.struct.image.ImageGray;
import boofcv.struct.image.ImageType;

/**
 * Motion detection using static video images
 *
 * @author Peter Abeles
 */
public class StaticBackgroundMotionActivity extends DemoVideoDisplayActivity
		implements AdapterView.OnItemSelectedListener, View.OnTouchListener
{

	int selected;

	boolean resetRequested;
	float threshold;

	SeekBar seek;
	BackgroundModelStationary model;

	// if true turn on picture-in-picture mode
	boolean pip = true;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		LayoutInflater inflater = getLayoutInflater();
		LinearLayout controls = (LinearLayout) inflater.inflate(R.layout.background_controls, null);

		LinearLayout parent = getViewContent();
		parent.addView(controls);

		FrameLayout iv = getViewPreview();
		iv.setOnTouchListener(this);

		Spinner spinnerView = (Spinner)controls.findViewById(R.id.spinner_algs);
		ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(this,
				R.array.background_models, android.R.layout.simple_spinner_item);
		adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		spinnerView.setAdapter(adapter);
		spinnerView.setOnItemSelectedListener(this);

		seek = (SeekBar)controls.findViewById(R.id.slider_threshold);

		seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
			@Override
			public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
				threshold = progress;
				if( model instanceof BackgroundStationaryBasic) {
					((BackgroundStationaryBasic)model).setThreshold(threshold);
				} else if( model instanceof BackgroundStationaryGaussian ) {
					((BackgroundStationaryGaussian)model).setThreshold(threshold);
				}
			}

			@Override
			public void onStartTrackingTouch(SeekBar seekBar) {}

			@Override
			public void onStopTrackingTouch(SeekBar seekBar) {}
		});
	}

	@Override
	protected void onResume() {
		super.onResume();
		setBackground();
	}

	@Override
	public void onItemSelected(AdapterView<?> adapterView, View view, int pos, long id ) {
		selected = pos;
		setBackground();
	}

	@Override
	public void onNothingSelected(AdapterView<?> parent) {}

	public void pressedReset( View view ) {
		resetRequested = true;
	}

	private void setBackground() {
		switch( selected ) {
			case 0:
			case 1:
				threshold = 25;
				break;

			case 2:
				threshold = 12;
				break;
			case 3:
				threshold = 40;
				break;
		}

		seek.setProgress((int)threshold);

		ConfigBackgroundBasic configBasic = new ConfigBackgroundBasic(threshold,0.005f);
		ConfigBackgroundGaussian configGaussian = new ConfigBackgroundGaussian(threshold, 0.005f);
		configGaussian.initialVariance = 100;
		configGaussian.minimumDifference = 2;

		switch( selected ) {
			case 0:
				model = FactoryBackgroundModel.stationaryBasic(
						configBasic,ImageType.single(GrayU8.class));
				break;

			case 1:
				model = FactoryBackgroundModel.stationaryBasic(configBasic,ImageType.il(3, ImageDataType.U8));
				break;

			case 2:
				model = FactoryBackgroundModel.stationaryGaussian(configGaussian, ImageType.single(GrayU8.class));
				break;

			case 3:
				model = FactoryBackgroundModel.stationaryGaussian(configGaussian, ImageType.il(3, ImageDataType.U8));
				break;

			default:
				throw new RuntimeException("unknown selected");
		}

		setProcessing(new BackgroundProcessing(model));
	}

	@Override
	public boolean onTouch(View v, MotionEvent event) {
		if( MotionEvent.ACTION_DOWN == event.getActionMasked()) {
			pip = !pip;
			return true;
		}
		return false;
	}

	protected class BackgroundProcessing<T extends ImageBase> extends VideoImageProcessing<T> {
		BackgroundModelStationary<T> model;

		GrayU8 binary = new GrayU8(1,1);
		T scaled;

		ImageGray work;
		FDistort shrink;

		public BackgroundProcessing(BackgroundModelStationary<T> model ) {
			super(model.getImageType());
			this.model = model;
			this.scaled = model.getImageType().createImage(1, 1);

			this.work = GeneralizedImageOps.createSingleBand(model.getImageType().getDataType(),1,1);
		}

		@Override
		protected void declareImages(int width, int height) {
			super.declareImages(width, height);
			binary.reshape(width, height);
			work.reshape(width, height);
			scaled.reshape(width/3,height/3);

			shrink = new FDistort();
		}

		@Override
		protected void process(T image, Bitmap output, byte[] storage) {
			if( resetRequested ) {
				resetRequested = false;
				model.reset();
				ImageMiscOps.fill(binary,0);
			} else {
				model.segment(image, binary);
				model.updateBackground(image);
			}

			if( pip ) {

				// shrink the input image
				shrink.init(image,scaled).scaleExt();
				shrink.apply();

				// rescale the binary image so that it can be viewed
				PixelMath.multiply(binary,255,binary);

				// overwrite the original image with the binary one
				GConvertImage.convert(binary, work);
				// if the input image is color then it needs a gray scale image of the same type
				GConvertImage.convert(work,image);

				// render the shrunk image inside the original
				int x0 = image.width  - scaled.width;
				int y0 = image.height - scaled.height;

				image.subimage(x0,y0,binary.width,binary.height).setTo(scaled);
				ConvertBitmap.boofToBitmap(image,output,storage);
			} else {
				VisualizeImageData.binaryToBitmap(binary, false, output, storage);
			}
		}
	}
}