package org.boofcv.android;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.*;

public class DemoMain extends Activity implements AdapterView.OnItemClickListener {
	/**
	 * Called when the activity is first created.
	 */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main);

		ListView listView = (ListView) findViewById(R.id.DemoListView);

		// Demonstration of image processing
		String[] valuesIP = new String[] { "Blur", "Threshold", "Derivative" };

		// Detection Demos
		String[] valuesDetect = new String[] { "Blobs", "Edges", "Points",
				"Scale Points", "Line", "Line Segment", "Calibration Grids"};
		// Demonstration for tracking

		// Demonstration for SFM
		String[] valuesSFM = new String[] { "Stabilize", "Mosaic", "Calibration", "2 View Stereo"};

// Define a new Adapter
// First parameter - Context
// Second parameter - Layout for the row
// Third parameter - ID of the TextView to which the data is written
// Forth - the Array of data

		ArrayAdapter<String> adapter = new ArrayAdapter<String>(this,
				android.R.layout.simple_list_item_1, android.R.id.text1, valuesIP);


// Assign adapter to ListView
		listView.setAdapter(adapter);
		listView.setOnItemClickListener(this);
	}

	@Override
	public void onItemClick(AdapterView<?> adapterView, View view, int position, long id) {
		Toast.makeText(getApplicationContext(),
				"Click ListItem Number " + position, Toast.LENGTH_LONG)
				.show();

		Intent intent;
		if( position == 0 ) {
			intent = new Intent(this, CameraInfoActivity.class);
		} else {
			intent = new Intent(this, VideoDetectPoints.class);
		}

		startActivity(intent);
	}
}
