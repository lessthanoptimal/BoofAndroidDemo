package org.boofcv.android.sfm;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.Spinner;

import org.boofcv.android.R;

import java.util.Objects;

import boofcv.abst.disparity.DisparitySmoother;
import boofcv.abst.disparity.StereoDisparity;
import boofcv.factory.disparity.ConfigDisparity;
import boofcv.factory.disparity.ConfigDisparityBM;
import boofcv.factory.disparity.ConfigDisparityBMBest5;
import boofcv.factory.disparity.ConfigDisparitySGM;
import boofcv.factory.disparity.DisparityError;
import boofcv.factory.disparity.DisparitySgmError;
import boofcv.factory.disparity.FactoryStereoDisparity;
import boofcv.struct.image.GrayF32;
import boofcv.struct.image.GrayU8;
import boofcv.struct.image.ImageGray;

/**
 * Opens a dialog and lets you select high level parameters for stereo disparity;
 *
 * TODO Switch to AndroidX API (look at example online) after BoofCV has been updated
 *
 * @author Peter Abeles
 */
public class StereoDisparityDialog extends DialogFragment
        implements AdapterView.OnItemSelectedListener,
        SeekBar.OnSeekBarChangeListener{
    // Which error models are selected for each disparity algorithm family
    public DisparityError errorBM = DisparityError.CENSUS;
    public DisparitySgmError errorSGM = DisparitySgmError.CENSUS;

    // Used to let the user know OK was selected
    ListenerOK listenerOK;

    public int disparityMin = 5;
    public int disparityRange = 120;

    // Region radius for block matching
    public int regionRadiusBM = 4;

    public ConfigDisparity.Approach selectedType = ConfigDisparity.Approach.BLOCK_MATCH;
    public FilterType filterType = FilterType.SPECKLE;

    Spinner spinnerTypes;
    Spinner spinnerError;
    Spinner spinnerFilter;
    SeekBar seekRange;

    @SuppressLint("NewApi")
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        Objects.requireNonNull(listenerOK,"Must specify listenerOK");

        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        // Get the layout inflater
        LayoutInflater inflater = getActivity().getLayoutInflater();

        // Inflate and set the layout for the dialog
        // Pass null as the parent view because its going in the dialog layout
        LinearLayout controls = (LinearLayout)inflater.inflate(R.layout.disparity_dialog_layout,null);

        ArrayAdapter<CharSequence> adapter;

        spinnerTypes = Objects.requireNonNull(controls.findViewById(R.id.spinner_type));
        adapter = ArrayAdapter.createFromResource(getContext(),
                R.array.disparity_algs, android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerTypes.setAdapter(adapter);
        spinnerTypes.setSelection(selectedType.ordinal());
        spinnerTypes.setOnItemSelectedListener(this);

        spinnerError = Objects.requireNonNull(controls.findViewById(R.id.spinner_error));
        setupErrorSpinner(selectedType);
        spinnerError.setOnItemSelectedListener(this);

        spinnerFilter = Objects.requireNonNull(controls.findViewById(R.id.spinner_filter));
        adapter = ArrayAdapter.createFromResource(getContext(),
                R.array.disparity_filter_algs, android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerFilter.setAdapter(adapter);
        spinnerFilter.setSelection(filterType.ordinal());
        spinnerFilter.setOnItemSelectedListener(this);

        seekRange = Objects.requireNonNull(controls.findViewById(R.id.seek_range));
        seekRange.setProgress(disparityRange);
        seekRange.setOnSeekBarChangeListener(this);

        builder.setTitle("Disparity Settings");
        builder.setView(controls)
                .setPositiveButton("OK", (dialog, id) -> listenerOK.handleOK())
                .setNegativeButton("Cancel",
                        (dialog, id) -> StereoDisparityDialog.this.getDialog().cancel());
        return builder.create();
    }

    private void setupErrorSpinner( ConfigDisparity.Approach type ) {
        int res = isBlockMatch(type) ?
                R.array.disparity_bm_errors : R.array.disparity_sgm_errors;
        int ordinal = isBlockMatch(type) ?
                errorBM.ordinal() : errorSGM.ordinal();

        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(getContext(),
                res, android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerError.setAdapter(adapter);
        spinnerError.setSelection(ordinal,false);
        adapter.notifyDataSetChanged();
    }

    private boolean isBlockMatch( ConfigDisparity.Approach type ) {
        return type.ordinal() < 2;
    }

    @Override
    public void onItemSelected(AdapterView<?> adapterView, View view, int pos, long id ) {
        if( adapterView == spinnerTypes) {
            ConfigDisparity.Approach changeType = ConfigDisparity.Approach.values()[pos];

            // update the list of possible error models
            if( changeType != selectedType) {
                selectedType = changeType;
                setupErrorSpinner(changeType);
            }
        } else if( adapterView == spinnerError ) {
            if( isBlockMatch(selectedType)) {
                DisparityError selected = DisparityError.values()[pos];
                if( selected == errorBM )
                    return;
                this.errorBM = selected;
            } else {
                DisparitySgmError selected = DisparitySgmError.values()[pos];
                if( selected == errorSGM )
                    return;
                this.errorSGM = selected;
            }
        } else if( adapterView == spinnerFilter ) {
            filterType = FilterType.values()[pos];
        }
    }

    @Override public void onNothingSelected(AdapterView<?> adapterView) {}

    public ConfigDisparity createDisparityConfig() {
        ConfigDisparity configDisparity = new ConfigDisparity();
        // ensure disparity has a valid range
        int disparityRange = Math.max(1,this.disparityRange);

        switch(selectedType) {
            case BLOCK_MATCH: {
                ConfigDisparityBM config = configDisparity.approachBM;
                config.disparityMin = disparityMin;
                config.disparityRange = disparityRange;
                config.regionRadiusX = config.regionRadiusY = regionRadiusBM;
                config.errorType = errorBM;
                config.subpixel = true;
                break;
            }

            case BLOCK_MATCH_5: {
                ConfigDisparityBMBest5 config = configDisparity.approachBM5;
                config.disparityMin = disparityMin;
                config.disparityRange = disparityRange;
                config.regionRadiusX = config.regionRadiusY = regionRadiusBM;
                config.errorType = errorBM;
                config.subpixel = true;
                break;
            }

            case SGM: {
                ConfigDisparitySGM config = configDisparity.approachSGM;
                config.disparityMin = disparityMin;
                config.disparityRange = disparityRange;
                config.errorType = errorSGM;
                config.useBlocks = true;
                config.subpixel = true;
                break;
            }

            default:
                throw new RuntimeException("Unknown algorithm "+ selectedType);
        }

        return configDisparity;
    }

    public StereoDisparity<?, GrayF32> createDisparity() {
        // ensure disparity has a valid range
        int disparityRange = Math.max(1,this.disparityRange);

        switch(selectedType) {
            case BLOCK_MATCH: {
                ConfigDisparityBM config = new ConfigDisparityBM();
                config.disparityMin = disparityMin;
                config.disparityRange = disparityRange;
                config.regionRadiusX = config.regionRadiusY = regionRadiusBM;
                config.errorType = errorBM;
                config.subpixel = true;
                Class inputType = errorBM.isCorrelation() ? GrayF32.class : GrayU8.class;
                return FactoryStereoDisparity.blockMatch(config,inputType,GrayF32.class);
            }
            case BLOCK_MATCH_5: {
                ConfigDisparityBMBest5 config = new ConfigDisparityBMBest5();
                config.disparityMin = disparityMin;
                config.disparityRange = disparityRange;
                config.regionRadiusX = config.regionRadiusY = regionRadiusBM;
                config.errorType = errorBM;
                config.subpixel = true;
                Class inputType = errorBM.isCorrelation() ? GrayF32.class : GrayU8.class;
                return FactoryStereoDisparity.blockMatchBest5(config,inputType,GrayF32.class);
            }
            case SGM: {
                ConfigDisparitySGM config = new ConfigDisparitySGM();
                config.disparityMin = disparityMin;
                config.disparityRange = disparityRange;
                config.errorType = errorSGM;
                config.useBlocks = true;
                config.subpixel = true;
                return FactoryStereoDisparity.sgm(config,GrayU8.class,GrayF32.class);
            }

            default:
                throw new RuntimeException("Unknown algorithm "+ selectedType);
        }
    }

    public <T extends ImageGray<T>> DisparitySmoother<T, GrayF32> createSmoother() {
        return FactoryStereoDisparity.removeSpeckle(null,GrayF32.class);
    }

    @Override
    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
        disparityRange = progress;
    }

    @Override public void onStartTrackingTouch(SeekBar seekBar) {}

    @Override public void onStopTrackingTouch(SeekBar seekBar) {}

    @FunctionalInterface
    interface ListenerOK {
        void handleOK();
    }

    enum FilterType {
        SPECKLE,
        NONE
    }
}
