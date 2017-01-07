package org.boofcv.android.recognition;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.Toast;

import org.boofcv.android.R;

import boofcv.abst.fiducial.calib.CalibrationPatterns;

/**
 * Opens a dialog which lets the user configure the type of calibration fiducial it will look for.
 */
public class SelectCalibrationFiducial implements DrawCalibrationFiducial.Owner{
	Spinner spinnerTarget;
	EditText textRows;
	EditText textCols;

	int numRows,numCols;
	CalibrationPatterns targetType;

	Activity activity;

	public SelectCalibrationFiducial(int numRows, int numCols , CalibrationPatterns targetType) {
		this.numRows = numRows;
		this.numCols = numCols;
		this.targetType = targetType;
	}

	/**
	 * Creates and displays dialog
	 *
	 * @param activity Reference to acitvity launching this dialog
	 * @param success If use selects OK then run() is called.  Called while in GUI thread.
	 */
	public void create( Activity activity , final Runnable success ) {
		this.activity = activity;
		LayoutInflater inflater = activity.getLayoutInflater();
		final LinearLayout controls = (LinearLayout)inflater.inflate(R.layout.calibration_configure, null);

		// Create out AlterDialog
		final AlertDialog.Builder builder = new AlertDialog.Builder(activity);
		builder.setView(controls);
		builder.setCancelable(true);
		builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialogInterface, int i) {
				numCols = Integer.parseInt(textCols.getText().toString());
				numRows = Integer.parseInt(textRows.getText().toString());
				targetType = indexToCalib(spinnerTarget.getSelectedItemPosition());

				// ensure the chessboard has an odd number of rows and columns
				if (targetType == CalibrationPatterns.CHESSBOARD) {
					if (numCols % 2 == 0)
						numCols--;
					if (numRows % 2 == 0)
						numRows--;
				}

				if (numCols > 0 && numRows > 0) {
					success.run();
				} else {
					Toast.makeText(SelectCalibrationFiducial.this.activity, "Invalid configuration!", Toast.LENGTH_SHORT).show();
				}
			}
		});

		spinnerTarget = (Spinner) controls.findViewById(R.id.spinner_type);
		textRows = (EditText) controls.findViewById(R.id.text_rows);
		textCols = (EditText) controls.findViewById(R.id.text_cols);

		textRows.setText("" + numRows);
		textCols.setText("" + numCols);

		final FrameLayout preview = (FrameLayout) controls.findViewById(R.id.target_frame);
		final DrawCalibrationFiducial vis = new DrawCalibrationFiducial(activity,this);
		preview.addView(vis);

		final AlertDialog dialog = builder.create();

		TextWatcher watcher = new TextWatcher() {
			@Override
			public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

			@Override
			public void onTextChanged(CharSequence s, int start, int before, int count) {
				try {
					numRows = Integer.parseInt(textRows.getText().toString());
					numCols = Integer.parseInt(textCols.getText().toString());
					vis.invalidate();
				} catch( NumberFormatException ignore ){}
			}

			@Override
			public void afterTextChanged(Editable s) {}
		};

		AdapterView.OnItemSelectedListener spinnerSelected = new AdapterView.OnItemSelectedListener() {

			@Override
			public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
				try {
					targetType = indexToCalib(spinnerTarget.getSelectedItemPosition());
					vis.invalidate();
				} catch( NumberFormatException ignore ){}
			}

			@Override
			public void onNothingSelected(AdapterView<?> parent) {}
		};

		textRows.addTextChangedListener(watcher);
		textCols.addTextChangedListener(watcher);
		spinnerTarget.setOnItemSelectedListener(spinnerSelected);

		setupTargetSpinner();


		dialog.show();
	}

	private void setupTargetSpinner() {
		ArrayAdapter<CharSequence> adapter =
				new ArrayAdapter<CharSequence>(activity, android.R.layout.simple_spinner_item);
		adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		adapter.add("Chessboard");
		adapter.add("Square Grid");
		adapter.add("Circle Asymm");

		spinnerTarget.setAdapter(adapter);
	}

	private CalibrationPatterns indexToCalib( int index ) {
		switch( index ) {
			case 0: return CalibrationPatterns.CHESSBOARD;
			case 1: return CalibrationPatterns.SQUARE_GRID;
			case 2: return CalibrationPatterns.CIRCLE_ASYMMETRIC_GRID;
			case 3: return CalibrationPatterns.BINARY_GRID;
		}
		throw new RuntimeException("Egads");
	}

	@Override
	public int getGridColumns() {
		return numCols;
	}

	@Override
	public int getGridRows() {
		return numRows;
	}

	@Override
	public CalibrationPatterns getGridType() {
		return targetType;
	}
}
