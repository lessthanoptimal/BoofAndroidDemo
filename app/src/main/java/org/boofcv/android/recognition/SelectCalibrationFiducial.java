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

	ConfigAllCalibration cc;

	Activity activity;

	public SelectCalibrationFiducial(ConfigAllCalibration configCalibration) {
		this.cc = configCalibration;
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
				cc.targetType = indexToCalib(spinnerTarget.getSelectedItemPosition());

				int numCols = Integer.parseInt(textCols.getText().toString());
				int numRows = Integer.parseInt(textRows.getText().toString());

				if (numCols > 0 && numRows > 0) {
					setRowCol(numRows,numCols);
					success.run();
				} else {
					Toast.makeText(SelectCalibrationFiducial.this.activity, "Invalid configuration!", Toast.LENGTH_SHORT).show();
				}
			}
		});

		spinnerTarget = controls.findViewById(R.id.spinner_type);
		textRows = controls.findViewById(R.id.text_rows);
		textCols = controls.findViewById(R.id.text_cols);

		updateControlValues();

		final FrameLayout preview = controls.findViewById(R.id.target_frame);
		final DrawCalibrationFiducial vis = new DrawCalibrationFiducial(activity,this);
		preview.addView(vis);

		final AlertDialog dialog = builder.create();

		TextWatcher watcher = new TextWatcher() {
			@Override
			public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

			@Override
			public void onTextChanged(CharSequence s, int start, int before, int count) {
				try {
					int numRows = Integer.parseInt(textRows.getText().toString());
					int numCols = Integer.parseInt(textCols.getText().toString());
					setRowCol(numRows,numCols);
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
					cc.targetType = indexToCalib(spinnerTarget.getSelectedItemPosition());
					updateControlValues();
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

	private void setRowCol( int numRows , int numCols ) {
		switch( cc.targetType ) {
			case CHESSBOARD:{
				cc.chessboard.numCols = numCols;
				cc.chessboard.numRows = numRows;
			} break;

			case SQUARE_GRID:{
				cc.squareGrid.numCols = numCols;
				cc.squareGrid.numRows = numRows;
			} break;

			case CIRCLE_HEXAGONAL:{
				cc.hexagonal.numCols = numCols;
				cc.hexagonal.numRows = numRows;
			} break;

			case CIRCLE_GRID:{
				cc.circleGrid.numCols = numCols;
				cc.circleGrid.numRows = numRows;
			} break;
		}
	}

	private void updateControlValues() {
		int numCols,numRows;

		switch( cc.targetType ) {
			case CHESSBOARD:{
				numCols = cc.chessboard.numCols;
				numRows = cc.chessboard.numRows;
			} break;

			case SQUARE_GRID:{
				numCols = cc.squareGrid.numCols;
				numRows = cc.squareGrid.numRows;
			} break;

			case CIRCLE_HEXAGONAL:{
				numCols = cc.hexagonal.numCols;
				numRows = cc.hexagonal.numRows;
			} break;

			case CIRCLE_GRID:{
				numCols = cc.circleGrid.numCols;
				numRows = cc.circleGrid.numRows;
			} break;

			default:
				throw new RuntimeException("Unknown target type");
		}

		textRows.setText("" + numRows);
		textCols.setText("" + numCols);

	}

	private void setupTargetSpinner() {
		ArrayAdapter<CharSequence> adapter =
				new ArrayAdapter<CharSequence>(activity, android.R.layout.simple_spinner_item);
		adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		adapter.add("Chessboard");
		adapter.add("Square Grid");
		adapter.add("Circle Hex");
		adapter.add("Circle Grid");

		spinnerTarget.setAdapter(adapter);
	}

	private CalibrationPatterns indexToCalib( int index ) {
		switch( index ) {
			case 0: return CalibrationPatterns.CHESSBOARD;
			case 1: return CalibrationPatterns.SQUARE_GRID;
			case 2: return CalibrationPatterns.CIRCLE_HEXAGONAL;
			case 3: return CalibrationPatterns.CIRCLE_GRID;
		}
		throw new RuntimeException("Egads");
	}

	@Override
	public ConfigAllCalibration getConfigAllCalibration() {
		return cc;
	}
}
