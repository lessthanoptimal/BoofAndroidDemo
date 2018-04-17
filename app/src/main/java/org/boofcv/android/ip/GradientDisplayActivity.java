package org.boofcv.android.ip;

import android.graphics.Canvas;
import android.graphics.Matrix;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.Spinner;

import org.boofcv.android.DemoBitmapCamera2Activity;
import org.boofcv.android.DemoProcessingAbstract;
import org.boofcv.android.R;

import boofcv.abst.filter.derivative.ImageGradient;
import boofcv.android.VisualizeImageData;
import boofcv.factory.filter.derivative.FactoryDerivative;
import boofcv.struct.image.GrayS16;
import boofcv.struct.image.GrayU8;

/**
 * Displays the gradient of a gray scale image.  The gradient is colorized so that x and y directions are visible.
 * User can select which gradient algorithm to use.
 *
 * @author Peter Abeles
 */
public class GradientDisplayActivity extends DemoBitmapCamera2Activity
implements AdapterView.OnItemSelectedListener
{
	Spinner spinnerGradient;

	Class imageType = GrayU8.class;
	Class derivType = GrayS16.class;

	public GradientDisplayActivity() {
		super(Resolution.MEDIUM);
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		LayoutInflater inflater = getLayoutInflater();
		LinearLayout controls = (LinearLayout)inflater.inflate(R.layout.gradient_controls,null);

		spinnerGradient = controls.findViewById(R.id.spinner_gradient);
		ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(this,
				R.array.gradients, android.R.layout.simple_spinner_item);
		adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		spinnerGradient.setAdapter(adapter);
		spinnerGradient.setOnItemSelectedListener(this);

		setControls(controls);
		activateTouchToShowInput();
	}

	@Override
	protected void onResume() {
		super.onResume();
		startGradientProcess(spinnerGradient.getSelectedItemPosition());
	}

	@Override
	public void onItemSelected(AdapterView<?> adapterView, View view, int pos, long id) {
		startGradientProcess(pos);
	}

	private void startGradientProcess(int pos) {
		switch( pos ) {
			case 0:
				setProcessing(new GradientProcessing(FactoryDerivative.three(imageType, derivType)) );
				break;

			case 1:
				setProcessing(new GradientProcessing(FactoryDerivative.sobel(imageType, derivType)) );
				break;

			case 2:
				setProcessing(new GradientProcessing(FactoryDerivative.prewitt(imageType, derivType)) );
				break;

			case 3:
				setProcessing(new GradientProcessing(FactoryDerivative.two0(imageType, derivType)) );
				break;

			case 4:
				setProcessing(new GradientProcessing(FactoryDerivative.two1(imageType, derivType)) );
				break;

			default:
				throw new RuntimeException("Unknown gradient");
		}
	}

	@Override
	public void onNothingSelected(AdapterView<?> adapterView) {}

	protected class GradientProcessing extends DemoProcessingAbstract<GrayU8> {
		GrayS16 derivX;
		GrayS16 derivY;
		ImageGradient<GrayU8,GrayS16> gradient;

		public GradientProcessing(ImageGradient<GrayU8,GrayS16> gradient) {
			super(GrayU8.class);
			this.gradient = gradient;
		}

		@Override
		public void initialize(int imageWidth, int imageHeight) {
			derivX = new GrayS16(imageWidth,imageHeight);
			derivY = new GrayS16(imageWidth,imageHeight);
		}

		@Override
		public void onDraw(Canvas canvas, Matrix imageToView) {
			synchronized (bitmapLock) {
				drawBitmap(canvas,imageToView);
			}
		}

		@Override
		public void process(GrayU8 input) {
			gradient.process(input,derivX,derivY);
			synchronized (bitmapLock ) {
				VisualizeImageData.colorizeGradient(derivX,derivY,-1,bitmap,bitmapTmp);
			}
		}
	}
}