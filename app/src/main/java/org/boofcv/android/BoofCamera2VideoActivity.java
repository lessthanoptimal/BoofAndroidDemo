package org.boofcv.android;

import android.content.Context;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.util.Size;
import android.view.Surface;
import android.view.TextureView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Activity for viewing a camera preview using the camera2 API.
 *
 * To start the camera invoke {@link #startCamera} inside your Activity's onCreate function.
 *
 * To customize it's behavior override the following functions:
 * <ul>
 *     <li>{@link #selectResolution}</li>
 *     <li>{@link #onCameraResolutionChange}</li>
 *     <li>{@link #configureCamera}</li>
 *     <li>{@link #selectCamera}</li>
 *     <li>{@link #processFrame}</li>
 * </ul>
 *
 * Specify the following permissions and features in AndroidManifest.xml
 * <pre>
 * {@code
 * <uses-permission android:name="android.permission.CAMERA" />
 * <uses-feature android:name="android.hardware.camera2.full" />
 * }</pre>
 *
 * @author Peter Abeles
 */
public abstract class BoofCamera2VideoActivity extends AppCompatActivity {
    private static final String TAG = "BoofCamera2Activity";

    private CameraDevice mCameraDevice;
    private CameraCaptureSession mPreviewSession;
    protected TextureView mTextureView;
    // size of camera preview
    private Size mPreviewSize;

    protected int mSensorOrientation;

    // the camera that was selected to view
    protected String cameraId;

    private ReentrantLock mCameraOpenCloseLock = new ReentrantLock();

    // Image reader for capturing the preview
    private ImageReader mPreviewReader;
    private CaptureRequest.Builder mPreviewRequestBuilder;

    private boolean verbose = true;

    protected void startCamera( TextureView view ) {
        this.mTextureView = view;
        this.mTextureView.setSurfaceTextureListener(mSurfaceTextureListener);
    }

    @Override
    public void onResume() {
        super.onResume();

        if( mTextureView == null )
            return;
        if (mTextureView.isAvailable()) {
            openCamera(mTextureView.getWidth(), mTextureView.getHeight());
        } else {
            mTextureView.setSurfaceTextureListener(mSurfaceTextureListener);
        }
    }

    @Override
    public void onPause() {
        closeCamera();
        super.onPause();
    }

    /**
     * Selects the camera resolution from the list of possible values. By default it picks the
     * resolution which best fits the texture's aspect ratio. If there's a tie the area is
     * maximized.
     *
     * @param widthTexture Width of the texture the preview is displayed inside of
     * @param heightTexture Height of the texture the preview is displayed inside of
     * @param resolutions List of possible resolutions
     * @return index of the resolution
     */
    protected int selectResolution( int widthTexture, int heightTexture, Size[] resolutions  ) {
        int bestIndex = -1;
        double bestAspect = Double.MAX_VALUE;
        double bestArea = 0;

        double textureAspect = widthTexture/(double)heightTexture;

        for( int i = 0; i < resolutions.length; i++ ) {
            Size s = resolutions[i];
            int width = s.getWidth();
            int height = s.getHeight();

            double aspectScore = Math.abs(width - height*textureAspect)/width;

            if( aspectScore < bestAspect ) {
                bestIndex = i;
                bestAspect = aspectScore;
                bestArea = width*height;
            } else if( Math.abs(aspectScore-bestArea) <= 1e-8 ) {
                bestIndex = i;
                double area = width*height;
                if( area > bestArea ) {
                    bestArea = area;
                }
            }
        }

        return bestIndex;
    }

    /**
     * Called when the camera's resolution has changed.
     */
    protected void onCameraResolutionChange( int width , int height ) {
        if( verbose )
            Log.i(TAG,"onCameraResolutionChange( "+width+" , "+height+")");

    }

    /**
     * Override to do custom configuration of the camera's settings. By default the camera
     * is put into auto mode.
     *
     * @param captureRequestBuilder used to configure the camera
     */
    protected void configureCamera( CaptureRequest.Builder captureRequestBuilder ) {
        captureRequestBuilder.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO);
    }

    /**
     * By default this will select the backfacing camera. override to change the camera it selects.
     */
    protected boolean selectCamera( CameraCharacteristics characteristics ) {
        Integer facing = characteristics.get(CameraCharacteristics.LENS_FACING);
        return facing == null || facing != CameraCharacteristics.LENS_FACING_FRONT;
    }

    /**
     * Process a single frame from the video feed. Image is automatically
     * closed after this function exists. No need to invoke image.close() manually.
     *
     * All implementations of this function must run very fast. Less than 5 milliseconds is a good
     * rule of thumb. If longer than that then you should spawn a thread and process the
     * image inside of that.
     */
    protected abstract void processFrame( Image image );

    /**
     * Tries to open a {@link CameraDevice}. The result is listened by `mStateCallback`.
     */
    @SuppressWarnings("MissingPermission")
    private void openCamera(int widthTexture, int heightTexture) {
        if( verbose )
            Log.i(TAG,"openCamera( texture "+widthTexture+" , "+heightTexture+")");
        if (isFinishing()) {
            return;
        }
        CameraManager manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        if( manager == null )
            throw new RuntimeException("Null camera manager");

        try {
            if( verbose )
                Log.d(TAG, "before tryAcquire mCameraOpenCloseLock");
            if (!mCameraOpenCloseLock.tryLock(2500, TimeUnit.MILLISECONDS)) {
                throw new RuntimeException("Time out waiting to lock camera opening.");
            }

            String[] cameras = manager.getCameraIdList();
            for( String cameraId : cameras ) {
                CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);
                if(!selectCamera(characteristics))
                    continue;

                StreamConfigurationMap map = characteristics.
                        get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                if (map == null) {
                    continue;
                }

                Size[] sizes = map.getOutputSizes(SurfaceTexture.class);
                int which = selectResolution(widthTexture, heightTexture,sizes);
                if( which < 0 || which >= sizes.length )
                    continue;
                mPreviewSize = sizes[which];
                this.cameraId = cameraId;
                mSensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);

                if( verbose )
                    Log.i(TAG,"selected cameraId="+cameraId+" orientation="+mSensorOrientation);

                onCameraResolutionChange( mPreviewSize.getWidth(), mPreviewSize.getHeight() );
                try {
                    mPreviewReader = ImageReader.newInstance(
                            mPreviewSize.getWidth(), mPreviewSize.getHeight(),
                            ImageFormat.YUV_420_888, 2);
                    mPreviewReader.setOnImageAvailableListener(onAvailableListener, null);
                    configureTransform(widthTexture, heightTexture);
                    manager.openCamera(cameraId, mStateCallback, null);
                } catch( IllegalArgumentException e ) {
                    Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show();
                    finish();
                }
                return;
            }
            mCameraOpenCloseLock.unlock();
            throw new RuntimeException("No camera selected!");

        } catch (CameraAccessException e) {
            Toast.makeText(this, "Cannot access the camera.", Toast.LENGTH_SHORT).show();
            finish();
        } catch (NullPointerException e) {
            // Currently an NPE is thrown when the Camera2API is used but not supported on the
            // device this code runs.
            Toast.makeText(this,"Camera2 API not supported on the device",Toast.LENGTH_LONG).show();
            finish();
        } catch (InterruptedException e) {
            throw new RuntimeException("Interrupted while trying to lock camera opening.");
        }
    }

    private void closeCamera() {
        if( verbose )
            Log.i(TAG,"closeCamera");
        try {
            mCameraOpenCloseLock.lock();
            closePreviewSession();
            if (null != mCameraDevice) {
                mCameraDevice.close();
                mCameraDevice = null;
            }
        } finally {
            mCameraOpenCloseLock.unlock();
        }
    }

    /**
     * Start the camera preview.
     */
    private void startPreview() {
        if (null == mCameraDevice || !mTextureView.isAvailable() || null == mPreviewSize) {
            return;
        }
        try {
            closePreviewSession();
            SurfaceTexture texture = mTextureView.getSurfaceTexture();
            assert texture != null;
            texture.setDefaultBufferSize(mPreviewSize.getWidth(), mPreviewSize.getHeight());
            mPreviewRequestBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);

            List<Surface> surfaces = new ArrayList<>();

            // Display the camera preview into this texture
            Surface previewSurface = new Surface(texture);
            surfaces.add(previewSurface);
            mPreviewRequestBuilder.addTarget(previewSurface);

            // This is where the image for processing is extracted from
            Surface readerSurface = mPreviewReader.getSurface();
            surfaces.add(readerSurface);
            mPreviewRequestBuilder.addTarget(readerSurface);

            configureCamera(mPreviewRequestBuilder);

            mCameraDevice.createCaptureSession(surfaces,
                    new CameraCaptureSession.StateCallback() {

                        @Override
                        public void onConfigured(@NonNull CameraCaptureSession session) {
                            mPreviewSession = session;
                            updatePreview();
                        }

                        @Override
                        public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                            Toast.makeText(BoofCamera2VideoActivity.this, "Failed", Toast.LENGTH_SHORT).show();
                        }
                    }, null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    /**
     * Update the camera preview. {@link #startPreview()} needs to be called in advance.
     */
    private void updatePreview() {
        if (null == mCameraDevice) {
            return;
        }
        try {
            mPreviewSession.setRepeatingRequest(mPreviewRequestBuilder.build(), null, null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    /**
     * Configures the necessary {@link android.graphics.Matrix} transformation to `mTextureView`.
     * This method should not to be called until the camera preview size is determined in
     * openCamera, or until the size of `mTextureView` is fixed.
     *
     * @param viewWidth  The width of `mTextureView`
     * @param viewHeight The height of `mTextureView`
     */
    private void configureTransform(int viewWidth, int viewHeight) {
        if (null == mTextureView || null == mPreviewSize) {
            return;
        }
        int rotation = getWindowManager().getDefaultDisplay().getRotation();
        Matrix matrix = new Matrix();
        RectF viewRect = new RectF(0, 0, viewWidth, viewHeight);
        RectF bufferRect = new RectF(0, 0, mPreviewSize.getHeight(), mPreviewSize.getWidth());
        float centerX = viewRect.centerX();
        float centerY = viewRect.centerY();
        if (Surface.ROTATION_90 == rotation || Surface.ROTATION_270 == rotation) {
            bufferRect.offset(centerX - bufferRect.centerX(), centerY - bufferRect.centerY());
            matrix.setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.FILL);
            float scale = Math.max(
                    (float) viewHeight / mPreviewSize.getHeight(),
                    (float) viewWidth / mPreviewSize.getWidth());
            matrix.postScale(scale, scale, centerX, centerY);
            matrix.postRotate(90 * (rotation - 2), centerX, centerY);
        }
        mTextureView.setTransform(matrix);
    }

    private void closePreviewSession() {
        if( verbose )
            Log.i(TAG,"closePreviewSession");

        if (mPreviewSession != null) {
            mPreviewSession.close();
            mPreviewSession = null;
        }
    }

    private TextureView.SurfaceTextureListener mSurfaceTextureListener
            = new TextureView.SurfaceTextureListener() {

        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture surfaceTexture,
                                              int width, int height) {
            openCamera(width, height);
        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture surfaceTexture,
                                                int width, int height) {
            configureTransform(width, height);
        }

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture surfaceTexture) {
            return true;
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture surfaceTexture) {
        }
    };

    private CameraDevice.StateCallback mStateCallback = new CameraDevice.StateCallback() {

        @Override
        public void onOpened(@NonNull CameraDevice cameraDevice) {
            if( verbose )
                Log.i(TAG,"CameraDevice onOpened() id="+cameraDevice.getId());
            mCameraDevice = cameraDevice;
            startPreview();
            mCameraOpenCloseLock.unlock();
            if (null != mTextureView) {
                configureTransform(mTextureView.getWidth(), mTextureView.getHeight());
            }
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice cameraDevice) {
            if( verbose )
                Log.i(TAG,"CameraDevice onDisconnected() id="+cameraDevice.getId());
            mCameraOpenCloseLock.unlock();
            cameraDevice.close();
            mCameraDevice = null;
        }

        @Override
        public void onError(@NonNull CameraDevice cameraDevice, int error) {
            if( verbose )
                Log.e(TAG,"CameraDevice onError() error="+error);
            mCameraOpenCloseLock.unlock();
            cameraDevice.close();
            mCameraDevice = null;
            finish();
        }
    };

    private ImageReader.OnImageAvailableListener onAvailableListener = imageReader -> {
        Image image = imageReader.acquireLatestImage();
        if (image == null)
            return;

        processFrame(image);
        image.close();
    };

    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }
}
