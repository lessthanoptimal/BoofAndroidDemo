package org.boofcv.android.visalize;

import android.app.Activity;
import android.os.Bundle;
import android.view.Window;
import android.view.WindowManager;

public class PointCloudActivity extends Activity {

    private PointCloudSurfaceView mGLView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(
                WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);

        mGLView = new PointCloudSurfaceView(this);
        mGLView.getRenderer().getCloud().createRandomCloud();
        setContentView(mGLView);
    }
}
