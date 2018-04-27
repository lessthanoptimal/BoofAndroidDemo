package org.boofcv.android;

import android.app.Application;

import java.util.ArrayList;
import java.util.List;

/**
 * Used for storage of global variables. These were originally static variables that could
 * get discarded if the main activity was unloaded.
 */
public class DemoApplication extends Application{
    // contains information on all the cameras.  less error prone and easier to deal with
    public final List<CameraSpecs> specs = new ArrayList<CameraSpecs>();
    // specifies which camera to use an image size
    public DemoPreference preference;
    // If another activity modifies the demo preferences this needs to be set to true so that it knows to reload
    // camera parameters.
    public boolean changedPreferences = false;
}
