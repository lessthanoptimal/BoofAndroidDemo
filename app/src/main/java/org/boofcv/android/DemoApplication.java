package org.boofcv.android;

import android.app.Application;
import android.content.Context;

import org.acra.ACRA;
import org.acra.annotation.AcraCore;
import org.acra.annotation.AcraHttpSender;
import org.acra.annotation.AcraLimiter;
import org.acra.annotation.AcraToast;
import org.acra.data.StringFormat;
import org.acra.sender.HttpSender;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import boofcv.BoofVersion;

/**
 * Used for storage of global variables. These were originally static variables that could
 * get discarded if the main activity was unloaded.
 */
@AcraToast( resText = R.string.acra_toast)
@AcraLimiter(period = 10, periodUnit = TimeUnit.MINUTES)
@AcraCore(reportFormat= StringFormat.JSON)
@AcraHttpSender(uri = "https://collector.tracepot.com/034cb7eb",
        httpMethod = HttpSender.Method.POST)
public class DemoApplication extends Application{
    // contains information on all the cameras.  less error prone and easier to deal with
    public final List<CameraSpecs> specs = new ArrayList<>();
    // specifies which camera to use an image size
    public DemoPreference preference = new DemoPreference();
    // If another activity modifies the demo preferences this needs to be set to true so that it knows to reload
    // camera parameters.
    public boolean changedPreferences = false;

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);

        // Only post bugs if in release mode
//        if( BuildConfig.BUILD_TYPE.equals("release"))
            ACRA.init(this);

        ACRA.getErrorReporter().putCustomData("BOOFCV-VERSION", BoofVersion.VERSION);
        ACRA.getErrorReporter().putCustomData("BOOFCV-GIT-SHA", BoofVersion.GIT_SHA);
        ACRA.getErrorReporter().putCustomData("BOOFCV-GIT-DATE", BoofVersion.GIT_DATE);

    }
}
