package org.boofcv.android.visalize;

import android.opengl.GLES20;

import org.ddogleg.struct.GrowQueue_F32;
import org.ejml.data.DMatrixRMaj;
import org.ejml.data.FMatrixRMaj;
import org.ejml.ops.ConvertMatrixData;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.Random;

import boofcv.alg.geo.PerspectiveOps;
import boofcv.misc.BoofMiscOps;
import boofcv.struct.calib.CameraPinhole;
import boofcv.struct.distort.Point2Transform2_F64;
import boofcv.struct.image.GrayF32;
import boofcv.struct.image.InterleavedU8;
import georegression.geometry.GeometryMath_F32;
import georegression.struct.point.Point2D_F64;
import georegression.struct.point.Point3D_F32;

import static org.boofcv.android.visalize.PointCloud3DRenderer.checkError;

// https://stackoverflow.com/questions/24055683/drawing-gl-points
public class PointCloud3D {

    final int NUM_POINTS = 100;

    private int COORDS_PER_VERTEX = 3; // (x,y,z)

    private final String vertexShaderCode =
//            "attribute vec4 vPosition;" +
//                    "void main() {" +
//                    "  gl_Position = vec4(0, 0, 0, 1);" +
//                    "  gl_PointSize = 2.0;" +
//                    "}";
            "uniform mat4 uMVPMatrix;" +
            "attribute vec4 vPosition;" +
            "attribute vec4 vColorA;" +
            "varying vec4 vColor;" +
            "void main() {" +
            "  gl_Position = uMVPMatrix * vPosition;" +
            "  vColor = vColorA;" +
            "  gl_PointSize = 5.0;" +
            "}";

    private final String fragmentShaderCode =
            "precision mediump float;" +
            "varying vec4 vColor;" +
            "void main() {" +
//            "   gl_FragColor = vec4(1, 0, 1, 1);" +
            "   gl_FragColor = vec4(vColor.rgb, 1.0);" +
            "}";

    private GrowQueue_F32 points = new GrowQueue_F32();
    private GrowQueue_F32 colors = new GrowQueue_F32();
    private FloatBuffer vertexBuffer;
    private FloatBuffer colorBuffer;

    Point3D_F32 p = new Point3D_F32();
    Point2D_F64 colorPt = new Point2D_F64();
    FMatrixRMaj rectifiedR = new FMatrixRMaj(3,3);

    private int mProgram;

    private int positionHandle;
    private int colorHandle;
    private int vPMatrixHandle;

    public PointCloud3D() {
        clearCloud();
    }

    public void initOpenGL() {
        int vertexShader = PointCloud3DRenderer.loadShader(GLES20.GL_VERTEX_SHADER,
                vertexShaderCode);
        int fragmentShader = PointCloud3DRenderer.loadShader(GLES20.GL_FRAGMENT_SHADER,
                fragmentShaderCode);

        // create empty OpenGL ES Program
        mProgram = GLES20.glCreateProgram();
        checkError("glCreateProgram");
        GLES20.glAttachShader(mProgram, vertexShader);
        checkError("glAttachShader(vertexShader)");
        GLES20.glAttachShader(mProgram, fragmentShader);
        checkError("glAttachShader(fragmentShader)");
        GLES20.glLinkProgram(mProgram);
        checkError("glLinkProgram");
    }

    public void clearCloud() {
        declarePoints(0);
        vertexBuffer.put(points.data,0,points.size);
        vertexBuffer.position(0);
        colorBuffer.put(colors.data,0,colors.size);
        colorBuffer.position(0);
    }

    public void createRandomCloud() {
        declarePoints(NUM_POINTS);

        Random rand = new Random(2345);
        for (int i = 0,idx=0; i < NUM_POINTS; i++) {
            points.data[idx++] = rand.nextFloat()-0.5f;
            points.data[idx++] = rand.nextFloat()-0.5f;
            points.data[idx++] = rand.nextFloat()-0.5f;
        }

        for (int i = 0,idx=0; i < NUM_POINTS; i++) {
            colors.data[idx++] = rand.nextFloat()*0.5f+0.5f;
            colors.data[idx++] = rand.nextFloat()*0.5f+0.5f;
            colors.data[idx++] = rand.nextFloat()*0.5f+0.5f;
        }
        vertexBuffer.put(points.data,0,points.size);
        vertexBuffer.position(0);
        colorBuffer.put(colors.data,0,colors.size);
        colorBuffer.position(0);
    }

    public void disparityToCloud(InterleavedU8 colorImage ,
                                 GrayF32 disparity , int disparityMin , int disparityRange ,
                                 Point2Transform2_F64 rectifiedToColor,
                                 DMatrixRMaj rectifiedK , DMatrixRMaj _rectifiedR )
    {
        ConvertMatrixData.convert(_rectifiedR,rectifiedR);
        CameraPinhole camera = PerspectiveOps.matrixToPinhole(rectifiedK,disparity.width,disparity.height,null);

        final float focalLengthX = (float)camera.fx;
        final float focalLengthY = (float)camera.fy;
        final float centerX = (float)camera.cx;
        final float centerY = (float)camera.cy;

        int count = 0;
        for (int y = 0; y < disparity.height; y++) {
            int idx = disparity.startIndex + y*disparity.stride;
            for (int x = 0; x < disparity.width; x++) {
                float v = disparity.data[idx++];
                if( v < disparityRange && v != 0.0f )
                    count++;
            }
        }

        declarePoints(count);

        // scale it so that z varies from 0 to 1.0
        float scale = 50.0f*disparityMin/focalLengthX;

        int i = 0,j=0;
        for (int py = 0; py < disparity.height; py++) {
            int idx = disparity.startIndex + py*disparity.stride;
            for (int px = 0; px < disparity.width; px++) {
                float v = disparity.data[idx++];
                if( v >= disparityRange || v == 0.0f )
                    continue;

                v += disparityMin;

                // Note that this will be in the rectified left camera's reference frame.
                // An additional rotation is needed to put it into the original left camera frame.
                float z = -scale*focalLengthX/v;
                float x = -z*(px - centerX)/focalLengthX;
                float y = z*(py - centerY)/focalLengthY;
                // The above puts it into the OpenGL standard coordinate system

                // Bring it back into left camera frame
                GeometryMath_F32.multTran(rectifiedR,p,p);

                points.data[i++] = x;
                points.data[i++] = y;
                points.data[i++] = z;

                rectifiedToColor.compute(px,py,colorPt);
                int cpx = (int)colorPt.x;
                int cpy = (int)colorPt.y;


                if( BoofMiscOps.checkInside(colorImage, cpx, cpy) ) {
                    int rgb = colorImage.get24(cpx, cpy);
                    colors.data[j++] = ((rgb>>16)&0xFF)/255.0f;
                    colors.data[j++] = ((rgb>>8)&0xFF)/255.0f;
                    colors.data[j++] = (rgb&0xFF)/255.0f;
                } else {
                    // set color based on distance to give it some depth
                    colors.data[j++] = 1.0f-z;
                    colors.data[j++] = 0.1f;
                    colors.data[j++] = 0.1f;
                }
            }
        }

        vertexBuffer.put(points.data,0,points.size);
        vertexBuffer.position(0);
        colorBuffer.put(colors.data,0,colors.size);
        colorBuffer.position(0);
    }

    private void declarePoints(int totalPoints ) {
        points.resize(totalPoints*3);
        colors.resize(totalPoints*3);
        {
            ByteBuffer bb = ByteBuffer.allocateDirect(points.size * 4);
            bb.order(ByteOrder.nativeOrder());
            vertexBuffer = bb.asFloatBuffer();
        }
        {
            ByteBuffer bb = ByteBuffer.allocateDirect(colors.size * 4);
            bb.order(ByteOrder.nativeOrder());
            colorBuffer = bb.asFloatBuffer();
        }
    }

    public void draw(float[] mvpMatrix) {
        // Add program to OpenGL ES environment
        GLES20.glUseProgram(mProgram);

        // get handle to vertex shader's vPosition member
        positionHandle = GLES20.glGetAttribLocation(mProgram, "vPosition");
        GLES20.glEnableVertexAttribArray(positionHandle);
        final int vertexStride = COORDS_PER_VERTEX * 4; // 4 bytes per vertex
        GLES20.glVertexAttribPointer(positionHandle, COORDS_PER_VERTEX,
                GLES20.GL_FLOAT, false,
                vertexStride, vertexBuffer);

        // get handle to fragment shader's vColor member
        colorHandle = GLES20.glGetAttribLocation(mProgram, "vColorA");
        GLES20.glEnableVertexAttribArray(colorHandle);
        final int colorStride = COORDS_PER_VERTEX * 4; // 4 bytes per vertex
        GLES20.glVertexAttribPointer(colorHandle, COORDS_PER_VERTEX,
                GLES20.GL_FLOAT, false,
                colorStride, colorBuffer);

//        colorHandle = GLES20.glGetAttribLocation(mProgram, "vColorA");
//        GLES20.glEnableVertexAttribArray(colorHandle);
//        final int vertexStride = COORDS_PER_VERTEX * 4; // 4 bytes per vertex
//        GLES20.glVertexAttribPointer(colorHandle, COORDS_PER_VERTEX,
//                GLES20.GL_FLOAT, false,
//                vertexStride, colorBuffer);

        // get handle to shape's transformation matrix
        vPMatrixHandle = GLES20.glGetUniformLocation(mProgram, "uMVPMatrix");

        // Pass the projection and view transformation to the shader
        GLES20.glUniformMatrix4fv(vPMatrixHandle, 1, false, mvpMatrix, 0);

        // Draw the particles
        GLES20.glDrawArrays(GLES20.GL_POINTS, 0, points.size/COORDS_PER_VERTEX);

        // Disable vertex array
        GLES20.glDisableVertexAttribArray(positionHandle);
        GLES20.glDisableVertexAttribArray(colorHandle);
    }
}
