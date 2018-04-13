package org.boofcv.android;

import android.media.Image;
import android.os.Bundle;
import android.util.Log;
import android.view.TextureView;

// TODO Configure so that it doesn't display a preview but render the gray scale one instead
public class DisplayCamera2Activity extends BoofCamera2VideoActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Free up more screen space
        android.support.v7.app.ActionBar actionBar = getSupportActionBar();
        if( actionBar != null ) {
            actionBar.hide();
        }

        setContentView(R.layout.display_camera2);

        TextureView textureView = findViewById(R.id.texture);
        startCamera(textureView);
    }

    @Override
    protected void processFrame(Image image) {
        Log.i("DisplayCamera2","Got image!");
    }
}
