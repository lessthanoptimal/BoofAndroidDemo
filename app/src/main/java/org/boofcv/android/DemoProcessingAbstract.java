package org.boofcv.android;

import android.content.res.Resources;
import android.util.DisplayMetrics;

import boofcv.struct.image.ImageBase;
import boofcv.struct.image.ImageType;

public abstract class DemoProcessingAbstract<T extends ImageBase<T>> implements DemoProcessing<T> {

    protected ImageType<T> imageType;
    protected DisplayMetrics displayMetrics;
    protected final Object lockGui = new Object();

    {
        displayMetrics = Resources.getSystem().getDisplayMetrics();
    }

    public DemoProcessingAbstract( ImageType<T> type ) {
        imageType = type;
    }

    public DemoProcessingAbstract( Class type ) {
        imageType = ImageType.single(type);
    }

    public DemoProcessingAbstract( Class type , int numBands ) {
        imageType = ImageType.pl(numBands,type);
    }

    @Override
    public void stop() {

    }

    @Override
    public boolean isThreadSafe() {
        return false;
    }

    @Override
    public ImageType<T> getImageType() {
        return imageType;
    }
}
