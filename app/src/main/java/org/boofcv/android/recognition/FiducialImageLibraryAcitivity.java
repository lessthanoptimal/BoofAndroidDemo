package org.boofcv.android.recognition;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.TextView;

import org.boofcv.android.R;

import java.util.ArrayList;
import java.util.List;

import boofcv.android.VisualizeImageData;
import boofcv.struct.image.GrayU8;

/**
 * Activity which displays all the saved image fiducials.  From here the user can get information
 * on each fiducial, delete a fiducial, and open an activity to create more fiducials.
 *
 * @author Peter Abeles
 */
public class FiducialImageLibraryAcitivity extends Activity {

	private static String TAG = "FiducialImageLibraryAcitivity";

	TextView textName;
	TextView textSize;

	GridView gridview;
	ImageAdapter adaptor;

	FiducialManager fiducialManager;
	List<FiducialManager.Info> list = new ArrayList<>();

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		requestWindowFeature(Window.FEATURE_NO_TITLE);
		setContentView(R.layout.fiducial_image_library);

		fiducialManager = new FiducialManager(this);
		fiducialManager.cleanUpDirectory();


//		Log.d(TAG,"Number of fiducials = "+list.size() );

		textName = (TextView) findViewById(R.id.fiducialName);
		textSize = (TextView) findViewById(R.id.fiducialSize);

		gridview = (GridView) findViewById(R.id.gridview);
		adaptor = new ImageAdapter(this);
		gridview.setAdapter(adaptor);

		gridview.setOnItemClickListener(new AdapterView.OnItemClickListener() {
			public void onItemClick(AdapterView<?> parent, View v,
									int position, long id) {
				v.setSelected(true);
				FiducialManager.Info info = list.get(position);
				textName.setText(info.name);
				textSize.setText(info.sideLength+" "+info.units.getSmall());

				textName.invalidate();
				textSize.invalidate();
			}
		});

		gridview.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
			@Override
			public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
				dialogDelete(position);
				return true;
			}
		});
	}

	@Override
	protected void onResume() {
		super.onResume();

		fiducialManager.loadList();
		list = fiducialManager.copyList();
		adaptor.notifyDataSetChanged();
	}

	public void buttonCapture( View view ) {
		Intent intent = new Intent(this, FiducialLearnActivity.class);
		startActivity(intent);
	}

	public void buttonHelp( View view ) {
		Intent intent = new Intent(this, FiducialSelectHelpActivity.class);
		startActivity(intent);
	}

	protected void performDelete( int index ) {
		// this is all happening in the GUI thread so it should be save to manipulate list
		FiducialManager.Info info = list.get(index);
		fiducialManager.deleteFiducial(info);
		list.remove(index);
		adaptor.notifyDataSetChanged();
	}

	protected void dialogDelete(  final int index ) {
		AlertDialog.Builder builder = new AlertDialog.Builder(this);

		String name = list.get(index).name;

		// Create the GUI and show it
		builder.setTitle("Delete Fiducial?")
				.setMessage(name)
				.setPositiveButton("OK", new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int id) {
						performDelete(index);
					}
				})
				.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int id) {
					}
				});
		AlertDialog dialog = builder.create();
		dialog.show();
	}

	public class ImageAdapter extends BaseAdapter {
		private Context mContext;

		public ImageAdapter(Context c) {
			mContext = c;
		}

		public int getCount() {
			return list.size();
		}

		public Object getItem(int position) {
			return null;
		}

		public long getItemId(int position) {
			return 0;
		}

		// create a new ImageView for each item referenced by the Adapter
		public View getView(int position, View convertView, ViewGroup parent) {
			ImageView imageView;

			int sizeInDp = 100;
			int w = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, sizeInDp,
					getResources().getDisplayMetrics());
			int p = (int)TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 5,
					getResources().getDisplayMetrics());

			if (convertView == null) {
				// if it's not recycled, initialize some attributes
				imageView = new ImageView(mContext);
				imageView.setLayoutParams(new GridView.LayoutParams(w, w));
				imageView.setScaleType(ImageView.ScaleType.CENTER_CROP);
				imageView.setPadding(p, p, p, p);
			} else {
				imageView = (ImageView) convertView;
			}

			FiducialManager.Info info = list.get(position);

			GrayU8 image = fiducialManager.loadBinaryImage(info.id);
			if( image == null ) {
				throw new RuntimeException("BUG!");
			}

			Bitmap bitmap = Bitmap.createBitmap(image.width,image.height,Bitmap.Config.ARGB_8888);
			VisualizeImageData.binaryToBitmap(image, true, bitmap, null);
			imageView.setImageBitmap(bitmap);

			return imageView;
		}

	}
}
