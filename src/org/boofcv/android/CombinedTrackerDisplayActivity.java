package org.boofcv.android;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.Spinner;
import boofcv.abst.feature.associate.AssociateDescription;
import boofcv.abst.feature.associate.ScoreAssociation;
import boofcv.abst.feature.detdesc.DetectDescribePoint;
import boofcv.abst.feature.tracker.PointTracker;
import boofcv.alg.tracker.klt.PkltConfig;
import boofcv.factory.feature.associate.FactoryAssociation;
import boofcv.factory.feature.tracker.FactoryPointTracker;
import boofcv.struct.feature.TupleDesc_B;
import boofcv.struct.image.ImageUInt8;

import java.util.ArrayList;
import java.util.List;

import static org.boofcv.android.CreateDetectorDescriptor.*;

/**
 * Displays tracking results from the combined tracker.
 *
 * @author Peter Abeles
 */
public class CombinedTrackerDisplayActivity extends PointTrackerDisplayActivity
		implements AdapterView.OnItemSelectedListener
{

	int tableDet[] = new int[]{DETECT_SHITOMASI,DETECT_FAST,DETECT_FH};
	int tableDesc[] = new int[]{DESC_BRIEF,DESC_SURF,DESC_NCC};

	int selectedDetector = 0;
	int selectedDescriptor = 0;

	Spinner spinnerDet;
	Spinner spinnerDesc;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		LayoutInflater inflater = getLayoutInflater();
		LinearLayout controls = (LinearLayout)inflater.inflate(R.layout.combined_tracker_controls,null);

		LinearLayout parent = (LinearLayout)findViewById(R.id.camera_preview_parent);
		parent.addView(controls);

		List<String> local = new ArrayList<String>();
		local.add( "KLT");

		List<String> detectors = new ArrayList<String>();
		detectors.add( "Shi-Tomasi");
		detectors.add( "Fast");
		detectors.add( "Fast Hessian");

		List<String> descriptors = new ArrayList<String>();
		descriptors.add( "BRIEF");
		descriptors.add( "SURF");
		descriptors.add( "NCC");

		Spinner spinnerLocal = (Spinner)controls.findViewById(R.id.spinner_local);
		ArrayAdapter<String> spinnerAdapter =
				new ArrayAdapter<String>(this, android.R.layout.simple_spinner_dropdown_item, local);
		spinnerLocal.setAdapter(spinnerAdapter);

		spinnerDet = (Spinner)controls.findViewById(R.id.spinner_detector);
		spinnerAdapter = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_dropdown_item, detectors);
		spinnerDet.setAdapter(spinnerAdapter);
		spinnerDet.setOnItemSelectedListener(this);

		spinnerDesc = (Spinner)controls.findViewById(R.id.spinner_descriptor);
		spinnerAdapter = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_dropdown_item, descriptors);
		spinnerDesc.setAdapter(spinnerAdapter);
		spinnerDesc.setOnItemSelectedListener(this);
	}

	@Override
	protected void onResume() {
		super.onResume();

		PointTracker<ImageUInt8> tracker = createTracker(selectedDetector,selectedDescriptor);
		setProcessing(new PointProcessing(tracker));
	}


	private PointTracker<ImageUInt8> createTracker( int detector , int descriptor  )
	{
		DetectDescribePoint detDesc = create(tableDet[detector],tableDesc[descriptor],ImageUInt8.class);

		ScoreAssociation score = FactoryAssociation.defaultScore(detDesc.getDescriptionType());

		AssociateDescription<TupleDesc_B> association = FactoryAssociation.greedy(score, Double.MAX_VALUE, true);

		PkltConfig config = new PkltConfig();
		config.templateRadius = 3;
		config.pyramidScaling = new int[]{1,2,4};

		return FactoryPointTracker.combined(detDesc,association,config,75,ImageUInt8.class);
	}

	@Override
	public void onItemSelected(AdapterView<?> adapterView, View view, int pos, long id ) {
		if( adapterView == spinnerDesc ) {
			if( selectedDescriptor == spinnerDesc.getSelectedItemPosition() )
				return;
			selectedDescriptor = spinnerDesc.getSelectedItemPosition();
		} else if( adapterView == spinnerDet ) {
			if( selectedDetector == spinnerDet.getSelectedItemPosition() )
				return;
			selectedDetector = spinnerDet.getSelectedItemPosition();
		}

		PointTracker<ImageUInt8> tracker = createTracker(selectedDetector,selectedDescriptor);
		setProcessing(new PointProcessing(tracker));
	}

	@Override
	public void onNothingSelected(AdapterView<?> adapterView) {}
}