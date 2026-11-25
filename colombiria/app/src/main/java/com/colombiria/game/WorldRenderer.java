package com.colombiria.game;

import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.List;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

/**
 * Colombiria Beta 2 procedural renderer.
 * GLES 2.0 compatible: terrain mesh, lit materials, distance fog,
 * animated water, layered mountains, conifer meshes, road, bridge and tunnel.
 * Geometry is generated once per GL context; the world is deliberately bounded
 * for this graphics laboratory and will be sector-streamed in the main engine.
 */
final class WorldRenderer implements GLSurfaceView.Renderer {
    private static final int STRIDE = 11; // position3 normal3 color4 material1
    private static final int MAT_LAND = 0, MAT_WATER = 1, MAT_ROAD = 2, MAT_LEAF = 3;
    private static final String VS =
        "attribute vec3 aPosition; attribute vec3 aNormal; attribute vec4 aColor; attribute float aMaterial;" +
        "uniform mat4 uMvp; uniform mat4 uModel; varying vec3 vWorld; varying vec3 vNormal;" +
        "varying vec4 vColor; varying float vMaterial;" +
        "void main(){ vec4 w=uModel*vec4(aPosition,1.0); vWorld=w.xyz;" +
        "vNormal=normalize(mat3(uModel)*aNormal); vColor=aColor; vMaterial=aMaterial;" +
        "gl_Position=uMvp*vec4(aPosition,1.0); }";
    private static final String FS =
        "precision mediump float; varying vec3 vWorld; varying vec3 vNormal; varying vec4 vColor;" +
        "varying float vMaterial; uniform vec3 uCamera; uniform float uTime; uniform vec3 uSun;" +
        "void main(){ vec3 n=normalize(vNormal); float diff=max(dot(n,normalize(uSun)),0.0);" +
        "float hemi=0.42+0.20*max(n.y,0.0); vec3 lit=vColor.rgb*(hemi+diff*0.62);" +
        "if(vMaterial>0.5 && vMaterial<1.5){" +
        "float wave=sin(vWorld.x*0.75+uTime*1.8)*0.035+cos(vWorld.z*0.53-uTime*1.2)*0.025;" +
        "float sparkle=pow(max(0.0,sin(vWorld.x*1.7+vWorld.z*0.9+uTime*2.0)),18.0)*0.25;" +
        "lit=mix(vec3(0.025,0.19,0.28),vec3(0.10,0.43,0.56),0.35+wave+sparkle);}" +
        "float d=length(uCamera-vWorld); float fog=smoothstep(75.0,205.0,d);" +
        "vec3 fogColor=vec3(0.48,0.64,0.72); lit=mix(lit,fogColor,fog);" +
        "gl_FragColor=vec4(lit,vColor.a); }";

    private int program, positionLoc, normalLoc, colorLoc, materialLoc, mvpLoc, modelLoc, cameraLoc, timeLoc, sunLoc;
    private FloatBuffer worldBuffer;
    private int worldVertices;
    private final float[] projection=new float[16], view=new float[16], model=new float[16], mvp=new float[16];
    private volatile boolean throttle;
    private volatile float steering;
    private float travel, elapsed;
    private long lastFrame;

    void setThrottle(boolean value){ throttle=value; }
    void setSteering(float value){ steering=Math.max(-1f,Math.min(1f,value)); }

    @Override public void onSurfaceCreated(GL10 gl,EGLConfig config){
        GLES20.glClearColor(0.48f,0.64f,0.72f,1f);
        GLES20.glEnable(GLES20.GL_DEPTH_TEST);
        GLES20.glDepthFunc(GLES20.GL_LEQUAL);
        GLES20.glDisable(GLES20.GL_CULL_FACE);
        GLES20.glDisable(GLES20.GL_BLEND);
        int vs=compile(GLES20.GL_VERTEX_SHADER,VS), fs=compile(GLES20.GL_FRAGMENT_SHADER,FS);
        program=GLES20.glCreateProgram();
        GLES20.glAttachShader(program,vs); GLES20.glAttachShader(program,fs); GLES20.glLinkProgram(program);
        int[] linked=new int[1]; GLES20.glGetProgramiv(program,GLES20.GL_LINK_STATUS,linked,0);
        if(linked[0]==0) throw new RuntimeException("Colombiria shader link: "+GLES20.glGetProgramInfoLog(program));
        positionLoc=GLES20.glGetAttribLocation(program,"aPosition");
        normalLoc=GLES20.glGetAttribLocation(program,"aNormal");
        colorLoc=GLES20.glGetAttribLocation(program,"aColor");
        materialLoc=GLES20.glGetAttribLocation(program,"aMaterial");
        mvpLoc=GLES20.glGetUniformLocation(program,"uMvp");
        modelLoc=GLES20.glGetUniformLocation(program,"uModel");
        cameraLoc=GLES20.glGetUniformLocation(program,"uCamera");
        timeLoc=GLES20.glGetUniformLocation(program,"uTime");
        sunLoc=GLES20.glGetUniformLocation(program,"uSun");
        buildWorld();
        lastFrame=System.nanoTime();
    }

    private int compile(int type,String source){
        int s=GLES20.glCreateShader(type); GLES20.glShaderSource(s,source); GLES20.glCompileShader(s);
        int[] ok=new int[1]; GLES20.glGetShaderiv(s,GLES20.GL_COMPILE_STATUS,ok,0);
        if(ok[0]==0) throw new RuntimeException("Colombiria shader compile: "+GLES20.glGetShaderInfoLog(s));
        return s;
    }

    private static final class MeshBuilder {
        final List<Float> values=new ArrayList<>();
        void vertex(float x,float y,float z,float nx,float ny,float nz,float r,float g,float b,float a,float mat){
            values.add(x);values.add(y);values.add(z);values.add(nx);values.add(ny);values.add(nz);
            values.add(r);values.add(g);values.add(b);values.add(a);values.add(mat);
        }
        void triangle(float[] a,float[] b,float[] c,float[] color,float mat){
            float ux=b[0]-a[0],uy=b[1]-a[1],uz=b[2]-a[2],vx=c[0]-a[0],vy=c[1]-a[1],vz=c[2]-a[2];
            float nx=uy*vz-uz*vy,ny=uz*vx-ux*vz,nz=ux*vy-uy*vx;
            float len=(float)Math.sqrt(nx*nx+ny*ny+nz*nz); if(len<0.0001f)return;
            nx/=len;ny/=len;nz/=len; if(ny<0f){nx=-nx;ny=-ny;nz=-nz;}
            vertex(a[0],a[1],a[2],nx,ny,nz,color[0],color[1],color[2],1,mat);
            vertex(b[0],b[1],b[2],nx,ny,nz,color[0],color[1],color[2],1,mat);
            vertex(c[0],c[1],c[2],nx,ny,nz,color[0],color[1],color[2],1,mat);
        }
        void quad(float x1,float z1,float x2,float z2,float y,float r,float g,float b,float mat){
            float[] c={r,g,b};
            triangle(new float[]{x1,y,z1},new float[]{x1,y,z2},new float[]{x2,y,z2},c,mat);
            triangle(new float[]{x1,y,z1},new float[]{x2,y,z2},new float[]{x2,y,z1},c,mat);
        }
        float[] toArray(){float[] a=new float[values.size()];for(int i=0;i<a.length;i++)a[i]=values.get(i);return a;}
    }

    private float terrain(float x,float z){
        return -2.0f+(float)Math.sin(x*0.075f+z*0.035f)*1.35f+(float)Math.cos(z*0.09f-x*0.025f)*0.9f;
    }
    private float roadX(float z){return (float)Math.sin(z*0.026f)*8.0f+(float)Math.sin(z*0.009f)*4.0f;}

    private void buildWorld(){
        MeshBuilder b=new MeshBuilder();
        // Dense tessellated rolling terrain with subtle vertex-color variation.
        int nx=100,nz=150; float sx=1.35f,sz=1.65f;
        for(int ix=0;ix<nx;ix++) for(int iz=0;iz<nz;iz++){
            float x0=(ix-nx/2f)*sx,x1=x0+sx,z0=45-iz*sz,z1=z0-sz;
            float h00=terrain(x0,z0),h10=terrain(x1,z0),h01=terrain(x0,z1),h11=terrain(x1,z1);
            float shade=0.94f+(float)Math.sin(ix*0.7+iz*0.31)*0.045f;
            float[] c={0.16f*shade,0.31f*shade,0.18f*shade};
            b.triangle(new float[]{x0,h00,z0},new float[]{x0,h01,z1},new float[]{x1,h11,z1},c,MAT_LAND);
            b.triangle(new float[]{x0,h00,z0},new float[]{x1,h11,z1},new float[]{x1,h10,z0},c,MAT_LAND);
        }
        // Layered mountain meshes around the valley, with rocky upper slopes.
        for(int side=-1;side<=1;side+=2) for(int i=0;i<13;i++){
            float x=side*(22+i*4.1f), baseZ=-95+i*8.2f;
            float peak=12+(float)Math.sin(i*0.83f)*7+(i%3)*1.8f;
            float[] grass={0.19f,0.29f,0.22f},rock={0.34f,0.36f,0.34f};
            float[] p0={x-14,terrain(x-14,baseZ),baseZ-22},p1={x+14,terrain(x+14,baseZ),baseZ-22};
            float[] p2={x,peak,baseZ-42};
            b.triangle(p0,p1,p2,grass,MAT_LAND);
            b.triangle(new float[]{x-8,peak*.56f,baseZ-31},new float[]{x+8,peak*.56f,baseZ-31},p2,rock,MAT_LAND);
        }
        // Road ribbon follows terrain and bends through the valley.
        for(int s=0;s<190;s++){
            float z0=52-s*1.05f,z1=z0-1.05f,x0=roadX(z0),x1=roadX(z1);
            float y0=terrain(x0,z0)+0.12f,y1=terrain(x1,z1)+0.12f;
            strip(b,x0-5.4f,z0,x1-5.4f,z1,y0,y1,0.47f,0.45f,0.40f,MAT_ROAD);
            strip(b,x0+5.4f,z0,x1+5.4f,z1,y0,y1,0.47f,0.45f,0.40f,MAT_ROAD);
            strip(b,x0-5,z0,x1-5,z1,y0+0.025f,y1+0.025f,0.105f,0.115f,0.12f,MAT_ROAD);
            strip(b,x0+5,z0,x1+5,z1,y0+0.025f,y1+0.025f,0.105f,0.115f,0.12f,MAT_ROAD);
            if(s%7<4) strip(b,x0-.12f,z0,x1-.12f,z1,y0+0.05f,y1+0.05f,0.88f,0.82f,0.62f,MAT_ROAD);
        }
        // River with banks and a slightly winding channel.
        for(int i=0;i<100;i++){
            float z=42-i*1.9f, bend=(float)Math.sin(z*.035f)*2.8f;
            float x=bend+19;
            b.quad(x-3.4f,z,x+3.4f,z-1.95f,terrain(x,z)+0.05f,.035f,.25f,.36f,MAT_WATER);
            b.quad(x-4.3f,z,x-3.4f,z-1.95f,terrain(x,z)+0.02f,.31f,.34f,.25f,MAT_LAND);
            b.quad(x+3.4f,z,x+4.3f,z-1.95f,terrain(x,z)+0.02f,.31f,.34f,.25f,MAT_LAND);
        }
        // Bridge deck and rails across the river.
        float bridgeZ=-26, bridgeY=terrain(19,bridgeZ)+1.1f;
        b.quad(10,bridgeZ+3,29,bridgeZ-3,bridgeY,.34f,.36f,.38f,MAT_ROAD);
        for(int i=0;i<10;i++){
            float z=bridgeZ+2.6f-i*.58f;
            strip(b,10,z,29,z,bridgeY+0.9f,bridgeY+0.9f,.58f,.61f,.62f,MAT_ROAD);
        }
        // Tunnel rock portal, interior darkness and visible structural arch approximation.
        float tz=-72, tx=roadX(tz), ty=terrain(tx,tz);
        b.quad(tx-5.2f,tz,tx+5.2f,tz-1,ty+0.4f,.045f,.055f,.065f,MAT_ROAD);
        b.quad(tx-7.2f,tz+0.8f,tx-5.1f,tz-1,ty+1.2f,.31f,.30f,.27f,MAT_LAND);
        b.quad(tx+5.1f,tz+0.8f,tx+7.2f,tz-1,ty+1.2f,.31f,.30f,.27f,MAT_LAND);
        // Fixed trunks and layered conifer crowns; trees are non-destructible world props.
        for(int i=0;i<230;i++){
            float z=38-(i*37%180), side=(i%2==0?-1:1);
            float x=roadX(z)+side*(9+(i*17%24));
            if(i%5==0)x=side*(24+(i*11%18));
            float y=terrain(x,z);
            tree(b,x,y,z,0.75f+(i%4)*0.17f,4.0f+(i%5)*0.55f);
        }
        // Rocks and boulders as faceted low-poly forms.
        for(int i=0;i<85;i++){
            float z=34-(i*23%170),x=roadX(z)+(i%2==0?-1:1)*(8+(i*13%30));
            rock(b,x,terrain(x,z),z,.45f+(i%4)*.18f);
        }
        // AndesDrive ATX-01 placeholder with body, cabin and four wheels.
        vehicle(b);
        float[] data=b.toArray();
        worldBuffer=ByteBuffer.allocateDirect(data.length*4).order(ByteOrder.nativeOrder()).asFloatBuffer();
        worldBuffer.put(data).position(0); worldVertices=data.length/STRIDE;
    }

    private void strip(MeshBuilder b,float x0,float z0,float x1,float z1,float y0,float y1,float r,float g,float bl,float mat){
        float[] c={r,g,bl};
        b.triangle(new float[]{x0,y0,z0},new float[]{x0,y1,z1},new float[]{x1,y1,z1},c,mat);
        b.triangle(new float[]{x0,y0,z0},new float[]{x1,y1,z1},new float[]{x1,y0,z0},c,mat);
    }
    private void tree(MeshBuilder b,float x,float y,float z,float radius,float height){
        float[] trunk={0.25f,0.13f,0.075f},leaf={0.045f,0.22f,0.105f},leaf2={0.065f,0.30f,0.14f};
        cone(b,x,y,z,radius*.16f,height*.34f,radius*.16f,6,trunk);
        cone(b,x,y+height*.18f,z,radius,height*.60f,0,7,leaf);
        cone(b,x,y+height*.43f,z,radius*.76f,height*.55f,0,7,leaf2);
        cone(b,x,y+height*.66f,z,radius*.48f,height*.46f,0,7,leaf);
    }
    private void cone(MeshBuilder b,float x,float y,float z,float radius,float h,float top,int sides,float[] c){
        for(int i=0;i<sides;i++){
            double a=i*Math.PI*2/sides, d=(i+1)*Math.PI*2/sides;
            float[] p0={x+(float)Math.cos(a)*radius,y,z+(float)Math.sin(a)*radius};
            float[] p1={x+(float)Math.cos(d)*radius,y,z+(float)Math.sin(d)*radius};
            float[] p2={x+(float)Math.cos((a+d)*.5)*top,y+h,z+(float)Math.sin((a+d)*.5)*top};
            b.triangle(p0,p1,p2,c,MAT_LEAF);
        }
    }
    private void rock(MeshBuilder b,float x,float y,float z,float r){
        float[] c={.30f,.31f,.29f};
        float[] p0={x-r,y,z-r},p1={x+r,y,z-r},p2={x+r,y,z+r},p3={x-r,y,z+r},top={x+r*.2f,y+r*1.25f,z-r*.1f};
        b.triangle(p0,p1,top,c,MAT_LAND);b.triangle(p1,p2,top,c,MAT_LAND);
        b.triangle(p2,p3,top,new float[]{.36f,.37f,.34f},MAT_LAND);b.triangle(p3,p0,top,c,MAT_LAND);
    }
    private void vehicle(MeshBuilder b){
        float[] blue={.035f,.18f,.55f},glass={.055f,.12f,.15f},rubber={.025f,.03f,.035f};
        b.quad(-1.85f,8,1.85f,14,terrain(0,11)+.75f,blue[0],blue[1],blue[2],MAT_ROAD);
        b.quad(-1.30f,9.2f,1.30f,12.8f,terrain(0,11)+1.55f,.08f,.23f,.62f,MAT_ROAD);
        b.quad(-1.05f,9.7f,1.05f,12.3f,terrain(0,11)+1.62f,glass[0],glass[1],glass[2],MAT_ROAD);
        for(int side=-1;side<=1;side+=2) for(int axle=0;axle<2;axle++){
            float x=side*1.65f,z=axle==0?9.1f:12.7f,y=terrain(x,z)+.55f;
            cylinder(b,x,y,z,.48f,.28f,8,rubber);
        }
    }
    private void cylinder(MeshBuilder b,float x,float y,float z,float r,float w,int sides,float[] c){
        for(int i=0;i<sides;i++){
            double a=i*Math.PI*2/sides,d=(i+1)*Math.PI*2/sides;
            float[] p0={x+(float)Math.cos(a)*r,y,z+(float)Math.sin(a)*r};
            float[] p1={x+(float)Math.cos(d)*r,y,z+(float)Math.sin(d)*r};
            float[] p2={p1[0],y+w,p1[2]},p3={p0[0],y+w,p0[2]};
            b.triangle(p0,p1,p2,c,MAT_ROAD);b.triangle(p0,p2,p3,c,MAT_ROAD);
        }
    }

    @Override public void onSurfaceChanged(GL10 gl,int width,int height){
        GLES20.glViewport(0,0,width,height);
        Matrix.perspectiveM(projection,0,58f,Math.max(.1f,(float)width/height),.15f,260f);
    }

    @Override public void onDrawFrame(GL10 gl){
        long now=System.nanoTime(); float dt=Math.min(.05f,(now-lastFrame)/1.0e9f); lastFrame=now;
        elapsed+=dt;
        if(throttle)travel+=dt*13.0f;
        float camX=steering*2.5f, camY=5.1f, camZ=19.5f+(travel%18f);
        GLES20.glClearColor(.48f,.64f,.72f,1f);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT|GLES20.GL_DEPTH_BUFFER_BIT);
        Matrix.setLookAtM(view,0,camX,camY,camZ,steering*1.4f,1.0f,camZ-34f,0,1,0);
        Matrix.setIdentityM(model,0);
        Matrix.multiplyMM(mvp,0,view,0,model,0); Matrix.multiplyMM(mvp,0,projection,0,mvp,0);
        GLES20.glUseProgram(program);
        GLES20.glUniformMatrix4fv(mvpLoc,1,false,mvp,0);
        GLES20.glUniformMatrix4fv(modelLoc,1,false,model,0);
        GLES20.glUniform3f(cameraLoc,camX,camY,camZ);
        GLES20.glUniform1f(timeLoc,elapsed);
        GLES20.glUniform3f(sunLoc,-.35f,.82f,.42f);
        int stride=STRIDE*4;
        worldBuffer.position(0); GLES20.glEnableVertexAttribArray(positionLoc);
        GLES20.glVertexAttribPointer(positionLoc,3,GLES20.GL_FLOAT,false,stride,worldBuffer);
        worldBuffer.position(3); GLES20.glEnableVertexAttribArray(normalLoc);
        GLES20.glVertexAttribPointer(normalLoc,3,GLES20.GL_FLOAT,false,stride,worldBuffer);
        worldBuffer.position(6); GLES20.glEnableVertexAttribArray(colorLoc);
        GLES20.glVertexAttribPointer(colorLoc,4,GLES20.GL_FLOAT,false,stride,worldBuffer);
        worldBuffer.position(10); GLES20.glEnableVertexAttribArray(materialLoc);
        GLES20.glVertexAttribPointer(materialLoc,1,GLES20.GL_FLOAT,false,stride,worldBuffer);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES,0,worldVertices);
        GLES20.glDisableVertexAttribArray(positionLoc); GLES20.glDisableVertexAttribArray(normalLoc);
        GLES20.glDisableVertexAttribArray(colorLoc); GLES20.glDisableVertexAttribArray(materialLoc);
    }
}
