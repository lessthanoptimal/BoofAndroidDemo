package org.boofcv.android.visalize;

import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

public class PointCloud3DRenderer implements GLSurfaceView.Renderer {

    final PointCloud3D cloud = new PointCloud3D();

    // vPMatrix is an abbreviation for "Model View Projection Matrix"
    private final float[] vPMatrix = new float[16];
    private final float[] projectionMatrix = new float[16];
    private final float[] viewMatrix = new float[16];

    private float[] translateMatrix = new float[16];
    private float[] rotateMatrix = new float[16];
    private float[] poseMatrix = new float[16];

    public final Object lock = new Object();
    public volatile float tranX=0;
    public volatile float tranY=0;
    public volatile float tranZ=0;

    public PointCloud3DRenderer(){
        Matrix.setIdentityM(poseMatrix,0);
    }

    @Override
    public void onSurfaceCreated(GL10 unused, EGLConfig config) {
        // Set the background frame color
        GLES20.glClearColor(0.0f, 0.0f, 0.0f, 1.0f);

        cloud.initOpenGL();
    }

    @Override
    public void onDrawFrame(GL10 unused) {
        // Redraw background color
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);

        // Set the camera position (View matrix)
        Matrix.setLookAtM(viewMatrix, 0, 0, 0, 0.9f, 0f, 0f, 0f, 0f, 1.0f, 0.0f);

        // Calculate the projection and view transformation
        Matrix.multiplyMM(vPMatrix, 0, projectionMatrix, 0, viewMatrix, 0);

        float tranX,tranY,tranZ;
        synchronized (lock){
            tranX = this.tranX;
            tranY = this.tranY;
            tranZ = this.tranZ;
            this.tranX = this.tranY = this.tranZ = 0.0f;
        }
        float[] scratch = new float[16];

        Matrix.translateM(poseMatrix, 0, tranX, tranY, tranZ);
        Matrix.multiplyMM(scratch, 0, vPMatrix, 0, poseMatrix, 0);

        cloud.draw(scratch);
    }

    @Override
    public void onSurfaceChanged(GL10 unused, int width, int height) {
        GLES20.glViewport(0, 0, width, height);

        float ratio = (float) width / height;

        // this projection matrix is applied to object coordinates
        // in the onDrawFrame() method
        Matrix.frustumM(projectionMatrix, 0, -ratio, ratio, -1, 1, 1,20 );
    }

    public static int loadShader(int type, String source) {
        int shader = GLES20.glCreateShader(type);
        GLES20.glShaderSource(shader, source);
        checkError("glShaderSource");
        GLES20.glCompileShader(shader);
        checkError("glCompileShader");
        return shader;
    }

    public static void checkError(String message) {
        int err = GLES20.glGetError();
        if (err != GLES20.GL_NO_ERROR) {
            throw new RuntimeException(message + " -> " + err);
        }
    }

    public PointCloud3D getCloud() {
        return cloud;
    }
}
