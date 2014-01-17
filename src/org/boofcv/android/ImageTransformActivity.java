package org.boofcv.android;

import android.graphics.Bitmap;
import android.hardware.Camera;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.Spinner;
import boofcv.abst.transform.fft.DiscreteFourierTransform;
import boofcv.abst.transform.wavelet.WaveletTransform;
import boofcv.alg.misc.ImageStatistics;
import boofcv.alg.misc.PixelMath;
import boofcv.alg.transform.fft.DiscreteFourierTransformOps;
import boofcv.alg.transform.wavelet.UtilWavelet;
import boofcv.android.ConvertBitmap;
import boofcv.android.VisualizeImageData;
import boofcv.core.image.ConvertImage;
import boofcv.factory.transform.pyramid.FactoryPyramid;
import boofcv.factory.transform.wavelet.FactoryWaveletTransform;
import boofcv.factory.transform.wavelet.GFactoryWavelet;
import boofcv.struct.image.*;
import boofcv.struct.pyramid.ImagePyramid;
import boofcv.struct.wavelet.WaveletDescription;
import boofcv.struct.wavelet.WlCoef;

/**
 * Visualizes several common image transforms
 *
 * @author Peter Abeles
 */
public class ImageTransformActivity extends VideoDisplayActivity
		implements AdapterView.OnItemSelectedListener
{

	Spinner spinnerView;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		LayoutInflater inflater = getLayoutInflater();
		LinearLayout controls = (LinearLayout)inflater.inflate(R.layout.select_algorithm,null);

		LinearLayout parent = (LinearLayout)findViewById(R.id.camera_preview_parent);
		parent.addView(controls);

		spinnerView = (Spinner)controls.findViewById(R.id.spinner_algs);
		ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(this,
				R.array.transforms, android.R.layout.simple_spinner_item);
		adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		spinnerView.setAdapter(adapter);
		spinnerView.setOnItemSelectedListener(this);
	}

	@Override
	protected void onResume() {
		super.onResume();
		startTransformProcess(spinnerView.getSelectedItemPosition());
	}

	@Override
	public void onItemSelected(AdapterView<?> adapterView, View view, int pos, long id) {
		startTransformProcess(pos);
	}

	@Override
	public void onNothingSelected(AdapterView<?> adapterView) {}

	private void startTransformProcess(int pos) {
		switch (pos) {
			case 0:
				setProcessing(new FourierProcessing() );
				break;

			case 1:
				setProcessing(new PyramidProcessing() );
				break;

			case 2:
				setProcessing(new WaveletProcessing() );
				break;
		}
	}

	protected class FourierProcessing extends BoofImageProcessing<ImageUInt8> {
		DiscreteFourierTransform<ImageFloat32,InterleavedF32> dft = DiscreteFourierTransformOps.createTransformF32();
		ImageFloat32 grayF;
		InterleavedF32 transform;

		protected FourierProcessing() {
			super(ImageType.single(ImageUInt8.class));
		}

		@Override
		public void init(View view, Camera camera) {
			super.init(view, camera);
			Camera.Size size = camera.getParameters().getPreviewSize();

			grayF = new ImageFloat32(size.width,size.height);
			transform = new InterleavedF32(size.width,size.height,2);
		}

		@Override
		protected void process(ImageUInt8 input, Bitmap output, byte[] storage) {
			ConvertImage.convert(input, grayF);
			PixelMath.divide(grayF,255.0f,grayF);
			dft.forward(grayF, transform);
			DiscreteFourierTransformOps.shiftZeroFrequency(transform, true);
			DiscreteFourierTransformOps.magnitude(transform, grayF);
			PixelMath.log(grayF,grayF);
			float max = ImageStatistics.maxAbs(grayF);
			PixelMath.multiply(grayF, 255f / max, grayF);
			ConvertBitmap.grayToBitmap(grayF, output, storage);
		}
	}

	protected class PyramidProcessing<C extends WlCoef>
			extends BoofImageProcessing<ImageUInt8>
	{
		ImagePyramid<ImageUInt8> pyramid = FactoryPyramid.discreteGaussian(new int[]{2,4,8,16},-1,2,false,ImageUInt8.class);

		ImageUInt8 output;
		ImageUInt8 sub = new ImageUInt8();

		protected PyramidProcessing() {
			super(ImageType.single(ImageUInt8.class));
		}

		@Override
		public void init(View view, Camera camera) {
			super.init(view, camera);

			Camera.Size size = camera.getParameters().getPreviewSize();

			output = new ImageUInt8(size.width,size.height);
		}

		@Override
		protected void process(ImageUInt8 input, Bitmap output, byte[] storage) {

			pyramid.process(input);

			draw(0, 0, pyramid.getLayer(0));
			int height = 0;
			int width = pyramid.getLayer(0).getWidth();
			for( int i = 1; i < pyramid.getNumLayers(); i++ ) {
				ImageUInt8 l = pyramid.getLayer(i);
				draw(width, height, l);
				height += l.getHeight();
			}

			ConvertBitmap.grayToBitmap(this.output, output, storage);
		}

		private void draw( int x0 , int y0 , ImageUInt8 layer ) {
			output.subimage(x0,y0,x0+layer.width,y0+layer.height,sub);
			sub.setTo(layer);
		}
	}

	protected class WaveletProcessing<C extends WlCoef>
			extends BoofImageProcessing<ImageUInt8>
	{
		WaveletDescription<C> desc = GFactoryWavelet.haar(ImageUInt8.class);
		WaveletTransform<ImageUInt8,ImageSInt32,C> waveletTran =
				FactoryWaveletTransform.create(ImageUInt8.class, desc, 3, 0, 255);
		ImageSInt32 transform;

		protected WaveletProcessing() {
			super(ImageType.single(ImageUInt8.class));
		}

		@Override
		public void init(View view, Camera camera) {
			super.init(view, camera);

			Camera.Size size = camera.getParameters().getPreviewSize();


			ImageDimension d = UtilWavelet.transformDimension(size.width, size.height, waveletTran.getLevels() );
			transform = new ImageSInt32(d.width,d.height);
		}

		@Override
		protected void process(ImageUInt8 input, Bitmap output, byte[] storage) {

			waveletTran.transform(input,transform);
			System.out.println("BOOF: num levels " + waveletTran.getLevels());
			System.out.println("BOOF: width "+transform.getWidth()+" "+transform.getHeight());
			UtilWavelet.adjustForDisplay(transform, waveletTran.getLevels(), 255);

			// if needed, crop the transform for visualization
			ImageSInt32 transform = this.transform;
			if( transform.width != output.getWidth() || transform.height != output.getHeight() )
			    transform = transform.subimage(0,0,output.getWidth(),output.getHeight(),null);

			VisualizeImageData.grayMagnitude(transform,255,output,storage);
		}
	}
}