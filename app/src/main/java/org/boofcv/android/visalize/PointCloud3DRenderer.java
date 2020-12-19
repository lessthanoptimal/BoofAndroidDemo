package org.boofcv.android.visalize;

import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

import georegression.geometry.ConvertRotation3D_F64;
import georegression.struct.EulerType;
import georegression.struct.se.Se3_F64;

public class PointCloud3DRenderer implements GLSurfaceView.Renderer {
    public static float MAX_RANGE = 40.0f;

    final PointCloud3D cloud = new PointCloud3D();

    // vPMatrix is an abbreviation for "Model View Projection Matrix"
    private final float[] vPMatrix = new float[16];
    private final float[] projectionMatrix = new float[16];
    private final float[] viewMatrix = new float[16];

    private final float[] scratch = new float[16];
    private final float[] poseMatrix = new float[16];

    public final Object lock = new Object();
    public volatile double tranX=0,tranY=0,tranZ=0;
    public volatile double rotX=0,rotY=0,rotZ=0;

    // Transform from previous to current frame
    private final Se3_F64 prev_to_curr = new Se3_F64();
    // World to previous frame
    public final Se3_F64 world_to_prev = new Se3_F64();
    // World to current frame
    private final Se3_F64 world_to_curr = new Se3_F64();

    public PointCloud3DRenderer(){
        setCameraToHome();
    }

    public void setCameraToHome() {
        // Move the camera a little bit closer
        world_to_prev.reset();
//        world_to_prev.T.z = 1.0f;
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

        double tranX,tranY,tranZ;
        double rotX,rotY,rotZ;
        synchronized (lock){
            tranX = this.tranX;
            tranY = this.tranY;
            tranZ = this.tranZ;
            this.tranX = this.tranY = this.tranZ = 0.0;
            rotX = this.rotX;
            rotY = this.rotY;
            rotZ = this.rotZ;
            this.rotX = this.rotY = this.rotZ = 0.0;
        }

        prev_to_curr.T.setTo(tranX, tranY, tranZ);
        ConvertRotation3D_F64.eulerToMatrix(EulerType.XYZ,rotX,rotY,rotZ,prev_to_curr.R);
        world_to_prev.concat(prev_to_curr, world_to_curr);

        // Convert into pose matrix in a format GLES can understand
        poseMatrix[0] = (float)world_to_curr.R.data[0];
        poseMatrix[1] = (float)world_to_curr.R.data[1];
        poseMatrix[2] = (float)world_to_curr.R.data[2];
        poseMatrix[3] = 0;

        poseMatrix[4] = (float)world_to_curr.R.data[3];
        poseMatrix[5] = (float)world_to_curr.R.data[4];
        poseMatrix[6] = (float)world_to_curr.R.data[5];
        poseMatrix[7] = 0;

        poseMatrix[8] = (float)world_to_curr.R.data[6];
        poseMatrix[9] = (float)world_to_curr.R.data[7];
        poseMatrix[10] = (float)world_to_curr.R.data[8];
        poseMatrix[11] = 0;

        poseMatrix[12] = (float)world_to_curr.T.x;
        poseMatrix[13] = (float)world_to_curr.T.y;
        poseMatrix[14] = (float)world_to_curr.T.z;
        poseMatrix[15] = 1;

        // save the current transform for use in the future
        world_to_prev.setTo(world_to_curr);

        Matrix.multiplyMM(scratch, 0, vPMatrix, 0, poseMatrix, 0);
        cloud.draw(scratch);
    }


    @Override
    public void onSurfaceChanged(GL10 unused, int width, int height) {
        GLES20.glViewport(0, 0, width, height);

        float ratio = (float) width / height;

        // this projection matrix is applied to object coordinates
        // in the onDrawFrame() method
        Matrix.frustumM(projectionMatrix, 0, -ratio, ratio, -1, 1, 3.0f, MAX_RANGE);
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
