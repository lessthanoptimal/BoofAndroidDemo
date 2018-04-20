package org.boofcv.android.tracker;

import android.graphics.Canvas;
import android.graphics.Matrix;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.Spinner;

import org.boofcv.android.DemoBitmapCamera2Activity;
import org.boofcv.android.DemoProcessingAbstract;
import org.boofcv.android.R;

import boofcv.abst.distort.FDistort;
import boofcv.alg.background.BackgroundModelStationary;
import boofcv.alg.background.stationary.BackgroundStationaryBasic;
import boofcv.alg.background.stationary.BackgroundStationaryGaussian;
import boofcv.alg.background.stationary.BackgroundStationaryGmm;
import boofcv.alg.misc.ImageMiscOps;
import boofcv.core.image.GeneralizedImageOps;
import boofcv.factory.background.ConfigBackgroundBasic;
import boofcv.factory.background.ConfigBackgroundGaussian;
import boofcv.factory.background.ConfigBackgroundGmm;
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
public class StaticBackgroundMotionActivity extends DemoBitmapCamera2Activity
		implements AdapterView.OnItemSelectedListener
{

	int selected;

	boolean resetRequested;
	float threshold;

	SeekBar seek;
	BackgroundModelStationary model;

	public StaticBackgroundMotionActivity() {
		super(Resolution.LOW);
		super.changeResolutionOnSlow = true;
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		LayoutInflater inflater = getLayoutInflater();
		LinearLayout controls = (LinearLayout) inflater.inflate(R.layout.background_controls, null);


		Spinner spinnerView = controls.findViewById(R.id.spinner_algs);
		ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(this,
				R.array.background_models, android.R.layout.simple_spinner_item);
		adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		spinnerView.setAdapter(adapter);
		spinnerView.setOnItemSelectedListener(this);

		seek = controls.findViewById(R.id.slider_threshold);

		seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
			@Override
			public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
				threshold = progress;
				if( model instanceof BackgroundStationaryBasic) {
					((BackgroundStationaryBasic)model).setThreshold(threshold);
				} else if( model instanceof BackgroundStationaryGaussian ) {
					((BackgroundStationaryGaussian)model).setThreshold(threshold);
				} else if( model instanceof BackgroundStationaryGmm) {
					((BackgroundStationaryGmm)model).setMaxDistance(threshold/10f);
				}
			}

			@Override
			public void onStartTrackingTouch(SeekBar seekBar) {}

			@Override
			public void onStopTrackingTouch(SeekBar seekBar) {}
		});

		setControls(controls);
		super.activateTouchToShowInput();
	}

	@Override
	protected void onResume() {
		super.onResume();
		createNewProcessor();
	}

	@Override
	public void onItemSelected(AdapterView<?> adapterView, View view, int pos, long id ) {
		selected = pos;
		createNewProcessor();
	}

	@Override
	public void onNothingSelected(AdapterView<?> parent) {}

	public void pressedReset( View view ) {
		resetRequested = true;
	}

	@Override
	public void createNewProcessor() {
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

		ConfigBackgroundGmm configGmm = new ConfigBackgroundGmm();

		switch( selected ) {
			case 0:
				model = FactoryBackgroundModel.stationaryBasic(
						configBasic,ImageType.single(GrayU8.class));
				break;

			case 1:
				model = FactoryBackgroundModel.stationaryBasic(configBasic,(ImageType)ImageType.il(3, ImageDataType.U8));
				break;

			case 2:
				model = FactoryBackgroundModel.stationaryGaussian(configGaussian, ImageType.single(GrayU8.class));
				break;

			case 3:
				model = FactoryBackgroundModel.stationaryGaussian(configGaussian, (ImageType)ImageType.il(3, ImageDataType.U8));
				break;

			case 4:
				model = FactoryBackgroundModel.stationaryGmm(configGmm, ImageType.single(GrayU8.class));
				break;

			case 5:
				model = FactoryBackgroundModel.stationaryGmm(configGmm, (ImageType)ImageType.il(3, ImageDataType.U8));
				break;

			default:
				throw new RuntimeException("unknown selected");
		}

		setProcessing(new BackgroundProcessing(model));
	}

	protected class BackgroundProcessing<T extends ImageBase<T>> extends DemoProcessingAbstract<T> {
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
		public void initialize(int imageWidth, int imageHeight) {
			binary.reshape(imageWidth, imageHeight);
			work.reshape(imageWidth, imageHeight);
			scaled.reshape(imageWidth/3,imageHeight/3);

			shrink = new FDistort();
		}

		@Override
		public void onDraw(Canvas canvas, Matrix imageToView) {
			synchronized (bitmapLock) {
				drawBitmap(canvas,imageToView);
			}
		}

		@Override
		public void process(T input) {
			if( resetRequested ) {
				resetRequested = false;
				model.reset();
				ImageMiscOps.fill(binary,0);
			} else {
				model.segment(input, binary);
				model.updateBackground(input);
			}
			synchronized (bitmapLock) {
				if( showProcessed )
					convertBinaryToBitmapDisplay(binary);
				else
					convertToBitmapDisplay(input);
			}
		}
	}
}