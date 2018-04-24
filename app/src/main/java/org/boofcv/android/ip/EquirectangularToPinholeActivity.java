package org.boofcv.android.ip;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.widget.FrameLayout;

import org.boofcv.android.R;

import boofcv.alg.distort.ImageDistort;
import boofcv.alg.distort.spherical.PinholeToEquirectangular_F32;
import boofcv.alg.interpolate.InterpolatePixel;
import boofcv.alg.interpolate.InterpolationType;
import boofcv.android.ConvertBitmap;
import boofcv.core.image.border.BorderType;
import boofcv.factory.distort.FactoryDistort;
import boofcv.factory.interpolate.FactoryInterpolation;
import boofcv.struct.calib.CameraPinhole;
import boofcv.struct.image.GrayU8;
import boofcv.struct.image.Planar;
import georegression.geometry.ConvertRotation3D_F32;
import georegression.struct.EulerType;

/**
 * Shows an equirectangular image with a pinhole camera super imposed on top of it.
 */
public class EquirectangularToPinholeActivity extends Activity {

    CameraPinhole pinholeModel = new CameraPinhole(200,200,0,250,250,500,500);



    Planar<GrayU8> equiImage = new Planar<>(GrayU8.class,1,1,3);
    Planar<GrayU8> pinholeImage;
    byte[] bitmapTmp = new byte[1];
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Free up more screen space
        android.app.ActionBar actionBar = getActionBar();
        if( actionBar != null ) {
            actionBar.hide();
        }

        setContentView(R.layout.equirectangular);

        FrameLayout surfaceLayout = findViewById(R.id.image_frame);

        Bitmap icon = BitmapFactory.decodeResource(getResources(),
                R.drawable.equirectangular_half_dome_01);

        equiImage.reshape(icon.getWidth(),icon.getHeight());
        ConvertBitmap.bitmapToBoof(icon,equiImage,null);

        // Declare storage for pinhole camera image
        pinholeImage = equiImage.createNew(pinholeModel.width, pinholeModel.height);

        // Create the image distorter which will render the image
        InterpolatePixel<Planar<GrayU8>> interp = FactoryInterpolation.
                createPixel(0, 255, InterpolationType.BILINEAR, BorderType.EXTENDED, equiImage.getImageType());
        ImageDistort<Planar<GrayU8>,Planar<GrayU8>> distorter =
                FactoryDistort.distort(false,interp,equiImage.getImageType());

        // This is where the magic is done.  It defines the transform rfom equirectangular to pinhole
        PinholeToEquirectangular_F32 pinholeToEqui = new PinholeToEquirectangular_F32();
        pinholeToEqui.setEquirectangularShape(equiImage.width,equiImage.height);
        pinholeToEqui.setPinhole(pinholeModel);

        // Pass in the transform to the image distorter
        distorter.setModel(pinholeToEqui);

        // TODO put bitmap into background
        // TODO render pinhole on top but place center at pixel

        // TODO periodic task that checks to see if view has changed and then triggers a render

        // TODO touch to move view to that location

        // change the orientation of the camera to make the view better
        ConvertRotation3D_F32.eulerToMatrix(EulerType.YXZ,0, 1.45f, 2.2f,pinholeToEqui.getRotation());

        // Render the image
        distorter.apply(equiImage,pinholeImage);
    }
}
