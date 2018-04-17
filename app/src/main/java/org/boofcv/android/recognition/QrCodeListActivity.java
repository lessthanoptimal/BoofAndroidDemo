package org.boofcv.android.recognition;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.Window;

import org.boofcv.android.R;

/**
 * Presents a list of detected QR Codes. Can select each one to get more info and copy the message
 */
public class QrCodeListActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.qrcode_list);
    }

}
