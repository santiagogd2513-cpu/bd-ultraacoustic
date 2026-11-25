package com.colombiria.game;

import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

final class WorldRenderer implements GLSurfaceView.Renderer {
    private static final String VS =
        "attribute vec3 aPos; attribute vec3 aColor; uniform mat4 uMvp; varying vec3 vColor;" +
        "void main(){ gl_Position=uMvp*vec4(aPos,1.0); vColor=aColor; }";
    private static final String FS =
        "precision mediump float; varying vec3 vColor; void main(){ gl_FragColor=vec4(vColor,1.0); }";
    private int program, posLoc, colorLoc, mvpLoc;
    private FloatBuffer vertices;
    private int vertexCount;
    private final float[] projection = new float[16], view = new float[16], model = new float[16], mvp = new float[16];
    private volatile boolean throttle;
    private volatile float steering;
    private float travel, wheelTurn;

    void setThrottle(boolean value) { throttle = value; }
    void setSteering(float value) { steering = Math.max(-1f, Math.min(1f, value)); }

    @Override public void onSurfaceCreated(GL10 gl, EGLConfig config) {
        GLES20.glClearColor(.55f, .72f, .82f, 1f);
        GLES20.glEnable(GLES20.GL_DEPTH_TEST);
        program = GLES20.glCreateProgram();
        int vs = shader(GLES20.GL_VERTEX_SHADER, VS), fs = shader(GLES20.GL_FRAGMENT_SHADER, FS);
        GLES20.glAttachShader(program, vs); GLES20.glAttachShader(program, fs); GLES20.glLinkProgram(program);
        posLoc = GLES20.glGetAttribLocation(program, "aPos");
        colorLoc = GLES20.glGetAttribLocation(program, "aColor");
        mvpLoc = GLES20.glGetUniformLocation(program, "uMvp");
        buildWorld();
    }

    private int shader(int type, String source) {
        int s = GLES20.glCreateShader(type); GLES20.glShaderSource(s, source); GLES20.glCompileShader(s); return s;
    }

    private void add(float[] out, int[] at, float x,float y,float z,float r,float g,float b) {
        out[at[0]++] = x; out[at[0]++] = y; out[at[0]++] = z;
        out[at[0]++] = r; out[at[0]++] = g; out[at[0]++] = b;
    }
    private void tri(float[] o,int[] a,float[] p,float[] c) {
        for(int i=0;i<3;i++) add(o,a,p[i*3],p[i*3+1],p[i*3+2],c[0],c[1],c[2]);
    }
    private void quad(float[] o,int[] a,float x1,float z1,float x2,float z2,float y,float r,float g,float b) {
        tri(o,a,new float[]{x1,y,z1,x2,y,z1,x2,y,z2},new float[]{r,g,b});
        tri(o,a,new float[]{x1,y,z1,x2,y,z2,x1,y,z2},new float[]{r,g,b});
    }
    private void buildWorld() {
        float[] data = new float[180000]; int[] at={0};
        // Broad rolling terrain planes.
        quad(data,at,-42,-70,42,55,-2.3f,.20f,.39f,.22f);
        // Mountain ridges, layered to form a distant valley.
        for(int i=0;i<12;i++) {
            float x=-36+i*6.4f, h=7+(float)Math.sin(i*.8)*5;
            tri(data,at,new float[]{x,-2.2f,-34,x+7,-2.2f,-34,x+3.5f,h,-44},new float[]{.25f,.39f,.31f});
            tri(data,at,new float[]{x,-2.2f,18,x+7,-2.2f,18,x+3.5f,h+4,28},new float[]{.18f,.34f,.25f});
        }
        // Curving road ribbon with shoulders and center markings.
        for(int s=0;s<8;s++) {
            float z1=42-s*14, z2=z1-14, curve=(float)Math.sin(s*.38)*5;
            quad(data,at,curve-5,z1,curve+5,z2,-2.05f,.18f,.19f,.20f);
            quad(data,at,curve-5.8f,z1,curve-5,z2,-2.02f,.54f,.49f,.36f);
            quad(data,at,curve+5,z1,curve+5.8f,z2,-2.02f,.54f,.49f,.36f);
            for(int m=0;m<3;m++) quad(data,at,curve-.12f,z1-m*4,curve+.12f,z1-m*4-1.8f,-2.00f,.92f,.86f,.62f);
        }
        // River and banks.
        quad(data,at,17,-42,28,5,-2.18f,.12f,.40f,.62f);
        quad(data,at,15,-42,17,5,-2.12f,.34f,.37f,.24f);
        quad(data,at,28,-42,30,5,-2.12f,.34f,.37f,.24f);
        // Bridge deck across river.
        quad(data,at,10,-17,34,-12,-.9f,.42f,.43f,.45f);
        // Tunnel portal: dark opening and rock shoulders.
        quad(data,at,-9,-49,9,-47,-.1f,.10f,.12f,.13f);
        // Trees as stylized trunks and canopy triangles, fixed in place.
        for(int i=0;i<26;i++) {
            float x=-28+(i*13%55), z=-42+(i*17%78);
            if(Math.abs(x)<8 && z> -5 && z<40) continue;
            tri(data,at,new float[]{x-.35f,-2.1f,z,x+.35f,-2.1f,z,x,1.0f,z},new float[]{.30f,.19f,.10f});
            tri(data,at,new float[]{x-1.5f,.2f,z,x+1.5f,.2f,z,x,4.0f,z},new float[]{.08f,.30f,.14f});
            tri(data,at,new float[]{x-1.1f,1.7f,z,x+1.1f,1.7f,z,x,5.1f,z},new float[]{.10f,.38f,.17f});
        }
        // Player SUV: simple recognizable body, cabin and wheels.
        quad(data,at,-1.7f,8,1.7f,14,-.8f,.12f,.34f,.72f);
        quad(data,at,-1.15f,9,1.15f,12,-.05f,.13f,.40f,.78f);
        quad(data,at,-.95f,9.5f,.95f,11.5f,.08f,.20f,.29f,.34f);
        vertices=ByteBuffer.allocateDirect(at[0]*4).order(ByteOrder.nativeOrder()).asFloatBuffer();
        vertices.put(data,0,at[0]).position(0); vertexCount=at[0]/6;
    }

    @Override public void onSurfaceChanged(GL10 gl,int width,int height) {
        GLES20.glViewport(0,0,width,height);
        Matrix.perspectiveM(projection,0,62f,Math.max(.1f,(float)width/height),.1f,180f);
    }

    @Override public void onDrawFrame(GL10 gl) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT|GLES20.GL_DEPTH_BUFFER_BIT);
        if(throttle) travel += .10f;
        wheelTurn += (steering-wheelTurn)*.08f;
        Matrix.setLookAtM(view,0,steering*2.2f,3.0f,18.5f+travel%7f,steering*1.4f,-.8f,-5f,0,1,0);
        Matrix.setIdentityM(model,0);
        Matrix.multiplyMM(mvp,0,view,0,model,0);
        Matrix.multiplyMM(mvp,0,projection,0,mvp,0);
        GLES20.glUseProgram(program);
        GLES20.glUniformMatrix4fv(mvpLoc,1,false,mvp,0);
        vertices.position(0);
        GLES20.glEnableVertexAttribArray(posLoc); GLES20.glEnableVertexAttribArray(colorLoc);
        vertices.position(0); GLES20.glVertexAttribPointer(posLoc,3,GLES20.GL_FLOAT,false,24,vertices);
        vertices.position(3); GLES20.glVertexAttribPointer(colorLoc,3,GLES20.GL_FLOAT,false,24,vertices);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES,0,vertexCount);
        GLES20.glDisableVertexAttribArray(posLoc); GLES20.glDisableVertexAttribArray(colorLoc);
    }
}
