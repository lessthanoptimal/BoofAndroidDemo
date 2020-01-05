package org.boofcv.android.visalize;

import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

public class PointCloud3DRenderer implements GLSurfaceView.Renderer {
    public static float MAX_RANGE = 40.0f;

    final PointCloud3D cloud = new PointCloud3D();

    // vPMatrix is an abbreviation for "Model View Projection Matrix"
    private final float[] vPMatrix = new float[16];
    private final float[] projectionMatrix = new float[16];
    private final float[] viewMatrix = new float[16];

    private float[] scratch = new float[16];
    private float[] rotateMatrix = new float[16];
    private float[] poseMatrix = new float[16];

    public final Object lock = new Object();
    public volatile float tranX=0,tranY=0,tranZ=0;
    public volatile float rotX=0,rotY=0,rotZ=0;

    public PointCloud3DRenderer(){
        setCameraToHome();
    }

    public void setCameraToHome() {
        Matrix.setIdentityM(poseMatrix,0);
        // move the camera to be closer to the scene and appear larger
        Matrix.translateM(poseMatrix, 0, 0, 0, 1.0f);
    }

    @Override
    public void onSurfaceCreated(GL10 unused, EGLConfig config) {
        // Set the background frame color
        GLES20.glClearColor(0.0f, 0.0f, 0.0f, 1.0f);
        // Enable depth testing so that close objects always appear in front of far objects
        GLES20.glEnable(GLES20.GL_DEPTH_TEST);
        GLES20.glDepthFunc(GLES20.GL_LESS);

        cloud.initOpenGL();
    }

    @Override
    public void onDrawFrame(GL10 unused) {
        // Redraw background color
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);

        // Set the camera position (View matrix)
        Matrix.setLookAtM(viewMatrix, 0, 0, 0, 0.9f, 0f, 0f, 0f, 0f, 1.0f, 0.0f);

        // Calculate the projection and view transformation
        Matrix.multiplyMM(vPMatrix, 0, projectionMatrix, 0, viewMatrix, 0);

        float tranX,tranY,tranZ;
        float rotX,rotY,rotZ;
        synchronized (lock){
            tranX = this.tranX;
            tranY = this.tranY;
            tranZ = this.tranZ;
            this.tranX = this.tranY = this.tranZ = 0.0f;
            rotX = this.rotX;
            rotY = this.rotY;
            rotZ = this.rotZ;
            this.rotX = this.rotY = this.rotZ = 0.0f;
        }

        // TODO apply rotation in local coordinate system

        // Apply the rotation
        Matrix.setRotateEulerM(rotateMatrix, 0, rotX, rotY, rotZ);
        Matrix.multiplyMM(scratch, 0, poseMatrix, 0, rotateMatrix, 0);
        System.arraycopy(scratch,0,poseMatrix,0,16);
        // compute the translation in the local frame
        float tx = poseMatrix[0]*tranX + poseMatrix[1]*tranY + poseMatrix[2 ]*tranZ;
        float ty = poseMatrix[4]*tranX + poseMatrix[5]*tranY + poseMatrix[6 ]*tranZ;
        float tz = poseMatrix[8]*tranX + poseMatrix[9]*tranY + poseMatrix[10]*tranZ;
        Matrix.translateM(poseMatrix, 0, tx, ty, tz);
        Matrix.multiplyMM(scratch, 0, vPMatrix, 0, poseMatrix, 0);

        cloud.draw(scratch);
    }


    @Override
    public void onSurfaceChanged(GL10 unused, int width, int height) {
        GLES20.glViewport(0, 0, width, height);

        float ratio = (float) width / height;

        // this projection matrix is applied to object coordinates
        // in the onDrawFrame() method
        Matrix.frustumM(projectionMatrix, 0, -ratio, ratio, -1, 1, 1, MAX_RANGE);
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
