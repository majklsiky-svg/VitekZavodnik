package cz.vitekzavodnik.game;

import android.content.Context;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;
import android.os.SystemClock;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

/** Mineral-exploration rebuild. No racing systems. */
public final class SafeGameRenderer implements GLSurfaceView.Renderer {
    private static final float DEG=(float)Math.PI/180f;
    private int program,aPos,aNormal,uMvp,uModel,uColor,uLight,uFogColor,uCamera;
    private Mesh cube,sphere,cylinder,terrain,rock;
    private final float[] proj=new float[16],view=new float[16],vp=new float[16],model=new float[16],mvp=new float[16];
    private volatile float moveX,moveY,camDx,camDy;
    private volatile boolean paused=true,jumpReq,actionReq;
    private float px=0,py=0,pz=12,yaw=180,camYaw=180,camPitch=20,camDist=7.2f,walk,vY;
    private boolean grounded=true;
    private long last;
    private final ArrayList<Mineral> minerals=new ArrayList<>();
    private volatile GameSnapshot snapshot=new GameSnapshot(0,0,0,0,false,0,5,0,-1,"Prozkoumej okolí a najdi minerály");

    private static final class Mineral { final float x,z; final int kind; boolean found; Mineral(float x,float z,int kind){this.x=x;this.z=z;this.kind=kind;} }
    public SafeGameRenderer(Context c){
        minerals.add(new Mineral(-15,-10,0)); minerals.add(new Mineral(13,-14,1)); minerals.add(new Mineral(21,8,2)); minerals.add(new Mineral(-21,14,3)); minerals.add(new Mineral(5,24,4));
    }
    @Override public void onSurfaceCreated(GL10 gl,EGLConfig cfg){
        GLES20.glClearColor(.53f,.70f,.78f,1); GLES20.glEnable(GLES20.GL_DEPTH_TEST); GLES20.glEnable(GLES20.GL_CULL_FACE); GLES20.glCullFace(GLES20.GL_BACK);
        program=link(VS,FS); aPos=GLES20.glGetAttribLocation(program,"aPosition"); aNormal=GLES20.glGetAttribLocation(program,"aNormal");
        uMvp=GLES20.glGetUniformLocation(program,"uMVP"); uModel=GLES20.glGetUniformLocation(program,"uModel"); uColor=GLES20.glGetUniformLocation(program,"uColor"); uLight=GLES20.glGetUniformLocation(program,"uLight"); uFogColor=GLES20.glGetUniformLocation(program,"uFogColor"); uCamera=GLES20.glGetUniformLocation(program,"uCamera");
        cube=Mesh.cube(); sphere=Mesh.sphere(12,16); cylinder=Mesh.cylinder(16); terrain=Mesh.terrain(28,3.0f); rock=Mesh.rock(); last=SystemClock.uptimeMillis();
    }
    @Override public void onSurfaceChanged(GL10 gl,int w,int h){GLES20.glViewport(0,0,w,Math.max(1,h)); Matrix.perspectiveM(proj,0,58,(float)w/Math.max(1,h),.15f,115);}
    @Override public void onDrawFrame(GL10 gl){
        long now=SystemClock.uptimeMillis(); float dt=Math.min(.033f,Math.max(.001f,(now-last)/1000f)); last=now; if(!paused)update(dt);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT|GLES20.GL_DEPTH_BUFFER_BIT); GLES20.glUseProgram(program);
        float a=camYaw*DEG,p=camPitch*DEG,ty=height(px,pz)+1.55f; py=height(px,pz);
        float ex=px-(float)Math.sin(a)*camDist*(float)Math.cos(p), ez=pz-(float)Math.cos(a)*camDist*(float)Math.cos(p), ey=ty+camDist*(float)Math.sin(p);
        Matrix.setLookAtM(view,0,ex,ey,ez,px,ty,pz,0,1,0); Matrix.multiplyMM(vp,0,proj,0,view,0);
        GLES20.glUniform3f(uLight,-.35f,.86f,.42f); GLES20.glUniform3f(uFogColor,.53f,.70f,.78f); GLES20.glUniform3f(uCamera,ex,ey,ez);
        world(now); character(); drawMinerals(now);
    }
    private void update(float dt){
        camYaw+=camDx*.16f; camPitch=clamp(camPitch+camDy*.11f,8,36); camDx=camDy=0;
        float mag=(float)Math.sqrt(moveX*moveX+moveY*moveY);
        if(mag>.07f){ float sx=moveX/Math.max(1,mag), f=-moveY/Math.max(1,mag),a=camYaw*DEG; float dx=sx*(float)Math.cos(a)+f*(float)Math.sin(a),dz=-sx*(float)Math.sin(a)+f*(float)Math.cos(a); px+=dx*4.4f*dt;pz+=dz*4.4f*dt;yaw=(float)Math.toDegrees(Math.atan2(dx,dz));walk+=dt*8*mag; }
        px=clamp(px,-39,39); pz=clamp(pz,-39,39); py=height(px,pz);
        int found=0; for(Mineral m:minerals){if(!m.found&&d2(px,pz,m.x,m.z)<2.0f){m.found=true;}if(m.found)found++;}
        snapshot=new GameSnapshot(0,found,0,0,false,found,5,0,-1,found==5?"Sbírka je kompletní":"Nalezené minerály: "+found+"/5"); jumpReq=false;actionReq=false;
    }
    private void world(long now){
        draw(terrain,0,0,0,1,1,1,0,0,0,.23f,.38f,.20f,1);
        // distant ridges
        for(int i=0;i<22;i++){float ang=i*16.36f*DEG,r=45+(i%4)*2,x=(float)Math.sin(ang)*r,z=(float)Math.cos(ang)*r;draw(rock,x,2,z,5+(i%3),6+(i%5),5, i*19,0,0,.30f,.32f,.29f,1);}
        // rocky exploration zones
        for(int i=0;i<34;i++){float x=-28+(i*17%57),z=-29+(i*23%59); if(Math.abs(x)<5&&Math.abs(z-12)<8)continue; float y=height(x,z);float s=.65f+(i%5)*.24f;draw(rock,x,y+s*.35f,z,s,s*.8f,s,i*37,0,0,.35f+.025f*(i%4),.33f,.28f,1);}
        // forest, layered trunks/crowns
        for(int i=0;i<48;i++){float ang=i*2.39996f,r=20+(i%13)*1.45f,x=(float)Math.sin(ang)*r,z=(float)Math.cos(ang)*r;if(x*x+z*z>1500)continue; tree(x,z,.8f+(i%5)*.1f);}
        // footpath stones
        for(int i=-11;i<=11;i++){float z=i*3.2f,x=(float)Math.sin(i*.65f)*2.1f,y=height(x,z);draw(rock,x,y+.04f,z,1.3f,.12f,1.8f,i*23,0,0,.42f,.40f,.34f,1);}
        // small creek
        for(int i=-13;i<=13;i++){float z=i*3.0f,x=-10+(float)Math.sin(i*.55f)*2.3f,y=height(x,z)-.08f;draw(cube,x,y,z,3.8f,.04f,3.3f,i*4,0,0,.12f,.42f,.55f,.88f);}
        // quarry wall
        for(int i=0;i<12;i++){float x=-30+i*3.8f,z=-25+(i%3)*.7f,y=height(x,z);draw(rock,x,y+1.7f,z,3.8f,4.5f,3.2f,i*31,0,0,.39f,.36f,.31f,1);}
    }
    private void tree(float x,float z,float s){float y=height(x,z);draw(cylinder,x,y+1.5f*s,z,.36f*s,3*s,.36f*s,0,0,0,.25f,.15f,.075f,1);draw(sphere,x,y+3.7f*s,z,2.2f*s,2.4f*s,2.2f*s,0,0,0,.10f,.30f,.12f,1);draw(sphere,x-1.0f*s,y+3.25f*s,z+.3f,1.55f*s,1.7f*s,1.55f*s,0,0,0,.13f,.36f,.14f,1);draw(sphere,x+.9f*s,y+3.4f*s,z-.25f,1.45f*s,1.65f*s,1.45f*s,0,0,0,.08f,.26f,.10f,1);}
    private void drawMinerals(long now){int n=0;for(Mineral m:minerals){if(m.found)continue;float y=height(m.x,m.z),bob=(float)Math.sin(now*.003+n)*.05f;float[][] c={{.68f,.32f,.80f},{.84f,.72f,.48f},{.24f,.48f,.62f},{.58f,.58f,.62f},{.74f,.30f,.24f}};float[] q=c[m.kind];draw(rock,m.x,y+.38f+bob,m.z,.72f,.82f,.66f,now*.02f+n*27,12,0,q[0],q[1],q[2],1);n++;}}
    private void character(){float y=height(px,pz),s=(float)Math.sin(walk)*18; shadow(px,y,pz); part(cube,0,.54f,0,.28f,.95f,.32f,s,0,.12f,.12f,.14f);part(cube,.36f,.54f,0,.28f,.95f,.32f,-s,0,.12f,.12f,.14f);part(cube,.18f,1.45f,0,.92f,1.05f,.48f,0,0,.10f,.24f,.42f);part(cube,.18f,1.58f,-.26f,.75f,.10f,.06f,0,0,.78f,.72f,.18f);part(cylinder,-.42f,1.45f,0,.20f,.92f,.20f,-s,0,.94f,.69f,.50f);part(cylinder,.78f,1.45f,0,.20f,.92f,.20f,s,0,.94f,.69f,.50f);part(sphere,.18f,2.45f,0,.72f,.84f,.70f,0,0,.94f,.72f,.54f);part(sphere,.18f,2.90f,.02f,.78f,.26f,.70f,0,0,.10f,.28f,.52f);part(cube,.18f,2.82f,-.60f,1.05f,.10f,.36f,0,0,.07f,.19f,.36f);}
    private void shadow(float x,float y,float z){draw(sphere,x,y+.025f,z,.72f,.025f,.46f,0,0,0,.025f,.03f,.025f,.45f);}
    private void part(Mesh m,float ox,float oy,float oz,float sx,float sy,float sz,float rx,float rz,float r,float g,float b){float a=yaw*DEG,xx=px+ox*(float)Math.cos(a)+oz*(float)Math.sin(a),zz=pz-ox*(float)Math.sin(a)+oz*(float)Math.cos(a);draw(m,xx,height(px,pz)+oy,zz,sx,sy,sz,yaw,rx,rz,r,g,b,1);}
    private float height(float x,float z){return .48f*(float)Math.sin(x*.105f)+.34f*(float)Math.cos(z*.12f)+.18f*(float)Math.sin((x+z)*.18f);}
    private void draw(Mesh mesh,float x,float y,float z,float sx,float sy,float sz,float ry,float rx,float rz,float r,float g,float b,float alpha){Matrix.setIdentityM(model,0);Matrix.translateM(model,0,x,y,z);if(ry!=0)Matrix.rotateM(model,0,ry,0,1,0);if(rx!=0)Matrix.rotateM(model,0,rx,1,0,0);if(rz!=0)Matrix.rotateM(model,0,rz,0,0,1);Matrix.scaleM(model,0,sx,sy,sz);Matrix.multiplyMM(mvp,0,vp,0,model,0);GLES20.glUniformMatrix4fv(uMvp,1,false,mvp,0);GLES20.glUniformMatrix4fv(uModel,1,false,model,0);GLES20.glUniform4f(uColor,r,g,b,alpha);mesh.draw(aPos,aNormal);}
    public void setMove(float x,float y){moveX=x;moveY=y;} public void addCamera(float x,float y){camDx+=x;camDy+=y;} public void requestJump(){jumpReq=true;} public void requestAction(){actionReq=true;} public void setPaused(boolean b){paused=b;} public GameSnapshot getSnapshot(){return snapshot;} public void release(){} public void resetGame(){for(Mineral m:minerals)m.found=false;px=0;pz=12;snapshot=new GameSnapshot(0,0,0,0,false,0,5,0,-1,"Najdi minerály");}
    private static float clamp(float v,float a,float b){return Math.max(a,Math.min(b,v));}private static float d2(float x,float z,float a,float b){float dx=x-a,dz=z-b;return dx*dx+dz*dz;}
    private static int shader(int t,String s){int q=GLES20.glCreateShader(t);GLES20.glShaderSource(q,s);GLES20.glCompileShader(q);return q;}private static int link(String v,String f){int p=GLES20.glCreateProgram(),a=shader(GLES20.GL_VERTEX_SHADER,v),b=shader(GLES20.GL_FRAGMENT_SHADER,f);GLES20.glAttachShader(p,a);GLES20.glAttachShader(p,b);GLES20.glLinkProgram(p);return p;}
    private static final String VS="uniform mat4 uMVP;uniform mat4 uModel;attribute vec3 aPosition;attribute vec3 aNormal;varying vec3 vN;varying vec3 vW;void main(){vec4 w=uModel*vec4(aPosition,1.0);vW=w.xyz;vN=normalize(mat3(uModel)*aNormal);gl_Position=uMVP*vec4(aPosition,1.0);}";
    private static final String FS="precision mediump float;uniform vec4 uColor;uniform vec3 uLight;uniform vec3 uFogColor;uniform vec3 uCamera;varying vec3 vN;varying vec3 vW;void main(){float d=max(dot(normalize(vN),normalize(uLight)),0.0);float hemi=.42+.58*d;vec3 V=normalize(uCamera-vW);vec3 H=normalize(normalize(uLight)+V);float sp=pow(max(dot(normalize(vN),H),0.0),22.0)*.12;vec3 col=uColor.rgb*hemi+sp;float fog=smoothstep(45.0,90.0,distance(uCamera,vW));gl_FragColor=vec4(mix(col,uFogColor,fog),uColor.a);}";
    private static final class Mesh{final FloatBuffer b;final int count;Mesh(float[] a){b=ByteBuffer.allocateDirect(a.length*4).order(ByteOrder.nativeOrder()).asFloatBuffer();b.put(a).position(0);count=a.length/6;}void draw(int p,int n){b.position(0);GLES20.glVertexAttribPointer(p,3,GLES20.GL_FLOAT,false,24,b);GLES20.glEnableVertexAttribArray(p);b.position(3);GLES20.glVertexAttribPointer(n,3,GLES20.GL_FLOAT,false,24,b);GLES20.glEnableVertexAttribArray(n);GLES20.glDrawArrays(GLES20.GL_TRIANGLES,0,count);}
        static void v(ArrayList<Float>o,float x,float y,float z,float nx,float ny,float nz){o.add(x);o.add(y);o.add(z);o.add(nx);o.add(ny);o.add(nz);}static Mesh cube(){ArrayList<Float>o=new ArrayList<>();float[][]f={{0,0,1},{0,0,-1},{-1,0,0},{1,0,0},{0,1,0},{0,-1,0}};float[][][]q={{{-.5f,-.5f,.5f},{.5f,-.5f,.5f},{.5f,.5f,.5f},{-.5f,.5f,.5f}},{{.5f,-.5f,-.5f},{-.5f,-.5f,-.5f},{-.5f,.5f,-.5f},{.5f,.5f,-.5f}},{{-.5f,-.5f,-.5f},{-.5f,-.5f,.5f},{-.5f,.5f,.5f},{-.5f,.5f,-.5f}},{{.5f,-.5f,.5f},{.5f,-.5f,-.5f},{.5f,.5f,-.5f},{.5f,.5f,.5f}},{{-.5f,.5f,.5f},{.5f,.5f,.5f},{.5f,.5f,-.5f},{-.5f,.5f,-.5f}},{{-.5f,-.5f,-.5f},{.5f,-.5f,-.5f},{.5f,-.5f,.5f},{-.5f,-.5f,.5f}}};for(int i=0;i<6;i++){int[]ix={0,1,2,0,2,3};for(int j:ix)v(o,q[i][j][0],q[i][j][1],q[i][j][2],f[i][0],f[i][1],f[i][2]);}return from(o);}static Mesh sphere(int st,int sl){ArrayList<Float>o=new ArrayList<>();for(int i=0;i<st;i++)for(int j=0;j<sl;j++){float p0=(i/(float)st-.5f)*(float)Math.PI,p1=((i+1)/(float)st-.5f)*(float)Math.PI,t0=j*6.28318f/sl,t1=(j+1)*6.28318f/sl;sv(o,p0,t0);sv(o,p0,t1);sv(o,p1,t1);sv(o,p0,t0);sv(o,p1,t1);sv(o,p1,t0);}return from(o);}static void sv(ArrayList<Float>o,float p,float t){float y=(float)Math.sin(p),c=(float)Math.cos(p),x=c*(float)Math.sin(t),z=c*(float)Math.cos(t);v(o,x*.5f,y*.5f,z*.5f,x,y,z);}static Mesh cylinder(int sl){ArrayList<Float>o=new ArrayList<>();for(int i=0;i<sl;i++){float a=i*6.28318f/sl,b=(i+1)*6.28318f/sl,x=(float)Math.sin(a)*.5f,z=(float)Math.cos(a)*.5f,X=(float)Math.sin(b)*.5f,Z=(float)Math.cos(b)*.5f;v(o,x,-.5f,z,x*2,0,z*2);v(o,X,-.5f,Z,X*2,0,Z*2);v(o,X,.5f,Z,X*2,0,Z*2);v(o,x,-.5f,z,x*2,0,z*2);v(o,X,.5f,Z,X*2,0,Z*2);v(o,x,.5f,z,x*2,0,z*2);}return from(o);}static Mesh terrain(int n,float step){ArrayList<Float>o=new ArrayList<>();for(int z=0;z<n;z++)for(int x=0;x<n;x++){float x0=(x-n/2)*step,z0=(z-n/2)*step,x1=x0+step,z1=z0+step;tv(o,x0,z0);tv(o,x1,z0);tv(o,x1,z1);tv(o,x0,z0);tv(o,x1,z1);tv(o,x0,z1);}return from(o);}static void tv(ArrayList<Float>o,float x,float z){float y=.48f*(float)Math.sin(x*.105f)+.34f*(float)Math.cos(z*.12f)+.18f*(float)Math.sin((x+z)*.18f);float dx=.0504f*(float)Math.cos(x*.105f)+.0324f*(float)Math.cos((x+z)*.18f),dz=-.0408f*(float)Math.sin(z*.12f)+.0324f*(float)Math.cos((x+z)*.18f);float nx=-dx,ny=1,nz=-dz,l=(float)Math.sqrt(nx*nx+1+nz*nz);v(o,x,y,z,nx/l,ny/l,nz/l);}static Mesh rock(){ArrayList<Float>o=new ArrayList<>();float[][]p={{0,.6f,0},{-.5f,-.4f,-.45f},{.55f,-.35f,-.4f},{.48f,-.42f,.5f},{-.55f,-.38f,.42f}};int[][]t={{0,1,2},{0,2,3},{0,3,4},{0,4,1},{1,4,3},{1,3,2}};for(int[]a:t){float[]A=p[a[0]],B=p[a[1]],C=p[a[2]];float ux=B[0]-A[0],uy=B[1]-A[1],uz=B[2]-A[2],vx=C[0]-A[0],vy=C[1]-A[1],vz=C[2]-A[2],nx=uy*vz-uz*vy,ny=uz*vx-ux*vz,nz=ux*vy-uy*vx,l=(float)Math.sqrt(nx*nx+ny*ny+nz*nz);nx/=l;ny/=l;nz/=l;v(o,A[0],A[1],A[2],nx,ny,nz);v(o,B[0],B[1],B[2],nx,ny,nz);v(o,C[0],C[1],C[2],nx,ny,nz);}return from(o);}static Mesh from(ArrayList<Float>o){float[]a=new float[o.size()];for(int i=0;i<a.length;i++)a[i]=o.get(i);return new Mesh(a);}}
}