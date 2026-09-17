package cz.vitekzavodnik.game;

import android.content.Context;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;
import android.os.SystemClock;
import java.nio.*;
import java.util.*;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

/** Small graphics showcase: meadow, trees, rocks and Vitek. */
public final class SafeGameRenderer implements GLSurfaceView.Renderer {
 private static final float DEG=(float)Math.PI/180f;
 private int pr,aP,aN,uMvp,uM,uC,uL,uFog,uCam;
 private Mesh cube,sphere,cyl,ground,rock,cone;
 private final float[] P=new float[16],V=new float[16],VP=new float[16],M=new float[16],MVP=new float[16];
 private volatile float mx,my,cdx,cdy; private volatile boolean paused=true;
 private float x=0,z=5,yaw=180,cyaw=180,cpitch=17,dist=6.2f,walk;
 private long last;
 private volatile GameSnapshot snap=new GameSnapshot(0,0,0,0,false,0,0,0,-1,"");
 public SafeGameRenderer(Context c){}
 @Override public void onSurfaceCreated(GL10 g,EGLConfig e){GLES20.glClearColor(.50f,.69f,.82f,1);GLES20.glEnable(GLES20.GL_DEPTH_TEST);GLES20.glEnable(GLES20.GL_CULL_FACE);GLES20.glEnable(GLES20.GL_BLEND);GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA,GLES20.GL_ONE_MINUS_SRC_ALPHA);pr=link(VS,FS);aP=GLES20.glGetAttribLocation(pr,"aP");aN=GLES20.glGetAttribLocation(pr,"aN");uMvp=GLES20.glGetUniformLocation(pr,"uMVP");uM=GLES20.glGetUniformLocation(pr,"uM");uC=GLES20.glGetUniformLocation(pr,"uC");uL=GLES20.glGetUniformLocation(pr,"uL");uFog=GLES20.glGetUniformLocation(pr,"uFog");uCam=GLES20.glGetUniformLocation(pr,"uCam");cube=Mesh.cube();sphere=Mesh.sphere(18,24);cyl=Mesh.cylinder(20);cone=Mesh.cone(20);ground=Mesh.ground(32,1.6f);rock=Mesh.rock();last=SystemClock.uptimeMillis();}
 @Override public void onSurfaceChanged(GL10 g,int w,int h){GLES20.glViewport(0,0,w,Math.max(h,1));Matrix.perspectiveM(P,0,55,(float)w/Math.max(h,1),.12f,85);}
 @Override public void onDrawFrame(GL10 g){long n=SystemClock.uptimeMillis();float dt=Math.min(.033f,Math.max(.001f,(n-last)/1000f));last=n;if(!paused)update(dt);GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT|GLES20.GL_DEPTH_BUFFER_BIT);GLES20.glUseProgram(pr);float a=cyaw*DEG,p=cpitch*DEG,ty=h(x,z)+1.45f;float ex=x-(float)Math.sin(a)*dist*(float)Math.cos(p),ez=z-(float)Math.cos(a)*dist*(float)Math.cos(p),ey=ty+dist*(float)Math.sin(p);Matrix.setLookAtM(V,0,ex,ey,ez,x,ty,z,0,1,0);Matrix.multiplyMM(VP,0,P,0,V,0);GLES20.glUniform3f(uL,-.45f,.82f,.34f);GLES20.glUniform3f(uFog,.50f,.69f,.82f);GLES20.glUniform3f(uCam,ex,ey,ez);scene();vitek();}
 private void update(float dt){cyaw+=cdx*.14f;cpitch=clamp(cpitch+cdy*.10f,8,30);cdx=cdy=0;float m=(float)Math.sqrt(mx*mx+my*my);if(m>.06f){float sx=mx/Math.max(1,m),f=-my/Math.max(1,m),a=cyaw*DEG,dx=sx*(float)Math.cos(a)+f*(float)Math.sin(a),dz=-sx*(float)Math.sin(a)+f*(float)Math.cos(a);x+=dx*3.5f*dt;z+=dz*3.5f*dt;yaw=(float)Math.toDegrees(Math.atan2(dx,dz));walk+=dt*8*m;}x=clamp(x,-20,20);z=clamp(z,-20,20);}
 private void scene(){draw(ground,0,0,0,1,1,1,0,0,0,.18f,.42f,.18f,1); // layered grass tufts
  for(int i=0;i<240;i++){float gx=-22+(i*37%440)/10f,gz=-22+(i*83%440)/10f;if(gx*gx+gz*gz<3)continue;float gy=h(gx,gz);float s=.18f+(i%5)*.025f;draw(cone,gx,gy+s*.55f,gz,s,s*1.5f,s,(i*71)%360,0,0,.20f+(i%3)*.025f,.46f+(i%4)*.02f,.16f,1);}
  tree(-10,-8,1.05f);tree(12,-9,.92f);tree(-15,7,.88f);tree(15,10,1.12f);tree(-5,-16,.78f);tree(6,-17,.9f);
  stone(-5,1,1.15f);stone(6,-3,.8f);stone(9,7,1.4f);stone(-11,10,.72f);stone(2,13,.55f);
  // wild flowers
  for(int i=0;i<34;i++){float gx=-18+(i*47%360)/10f,gz=-17+(i*91%340)/10f,gy=h(gx,gz);draw(cyl,gx,gy+.18f,gz,.025f,.36f,.025f,0,0,0,.16f,.40f,.12f,1);float r=(i%3==0)?.92f:.96f,gg=(i%3==1)?.78f:.90f,b=(i%3==2)?.30f:.70f;draw(sphere,gx,gy+.39f,gz,.13f,.08f,.13f,0,0,0,r,gg,b,1);}
 }
 private void tree(float tx,float tz,float s){float yy=h(tx,tz);draw(cyl,tx,yy+1.7f*s,tz,.42f*s,3.4f*s,.42f*s,0,0,0,.27f,.16f,.08f,1); // branch hints
  draw(cyl,tx-.35f*s,yy+2.65f*s,tz,.16f*s,1.4f*s,.16f*s,0,0,-28,.25f,.14f,.07f,1);draw(cyl,tx+.4f*s,yy+2.8f*s,tz,.14f*s,1.2f*s,.14f*s,0,0,31,.25f,.14f,.07f,1);
  crown(tx,yy+4.1f*s,tz,1.65f*s,.11f,.34f,.12f);crown(tx-1.05f*s,yy+3.75f*s,tz+.2f,1.15f*s,.13f,.39f,.13f);crown(tx+.95f*s,yy+3.85f*s,tz-.25f,1.25f*s,.09f,.30f,.10f);crown(tx+.2f*s,yy+4.85f*s,tz,1.1f*s,.15f,.42f,.14f);}
 private void crown(float a,float b,float c,float s,float r,float g,float bl){draw(sphere,a,b,c,s,1.15f*s,s,0,0,0,r,g,bl,1);draw(sphere,a-.55f*s,b+.15f*s,c+.25f*s,.65f*s,.75f*s,.65f*s,0,0,0,r*.9f,g*.92f,bl*.9f,1);}
 private void stone(float sx,float sz,float s){float yy=h(sx,sz);draw(rock,sx,yy+.42f*s,sz,1.25f*s,.9f*s,1.05f*s,27*s,8,0,.39f,.39f,.36f,1);draw(rock,sx+.38f*s,yy+.25f*s,sz-.25f*s,.55f*s,.48f*s,.62f*s,-17,0,0,.46f,.45f,.40f,1);}
 private void vitek(){float yy=h(x,z),sw=(float)Math.sin(walk)*22; // soft ground shadow
  draw(sphere,x,yy+.025f,z,.58f,.025f,.38f,yaw,0,0,.02f,.025f,.02f,.38f);
  // shoes, jeans, torso: child proportions based on supplied photo
  part(sphere,-.19f,.12f,-.08f,.25f,.14f,.42f,0,0,.10f,.10f,.11f);part(sphere,.19f,.12f,-.08f,.25f,.14f,.42f,0,0,.10f,.10f,.11f);
  part(cyl,-.18f,.57f,0,.18f,.82f,.19f,sw,0,.12f,.19f,.30f);part(cyl,.18f,.57f,0,.18f,.82f,.19f,-sw,0,.12f,.19f,.30f);
  part(sphere,0,1.30f,0,.54f,.72f,.36f,0,0,.09f,.28f,.52f);part(cube,0,1.28f,-.34f,.72f,.46f,.055f,0,0,.06f,.20f,.39f);
  // shirt detail
  part(cube,0,1.38f,-.385f,.32f,.055f,.025f,0,0,.92f,.73f,.18f);
  // arms + hands
  part(cyl,-.50f,1.28f,0,.14f,.76f,.14f,-sw*.7f,0,.91f,.66f,.47f);part(cyl,.50f,1.28f,0,.14f,.76f,.14f,sw*.7f,0,.91f,.66f,.47f);part(sphere,-.50f,.87f,0,.19f,.20f,.18f,0,0,.94f,.70f,.52f);part(sphere,.50f,.87f,0,.19f,.20f,.18f,0,0,.94f,.70f,.52f);
  // neck/head/ears/hair
  part(cyl,0,1.83f,0,.20f,.26f,.20f,0,0,.91f,.66f,.48f);part(sphere,0,2.18f,0,.48f,.58f,.45f,0,0,.94f,.70f,.53f);part(sphere,-.46f,2.18f,0,.10f,.17f,.08f,0,0,.91f,.65f,.48f);part(sphere,.46f,2.18f,0,.10f,.17f,.08f,0,0,.91f,.65f,.48f);
  part(sphere,0,2.43f,.03f,.47f,.28f,.44f,0,0,.16f,.10f,.065f);part(sphere,-.31f,2.36f,-.10f,.22f,.25f,.22f,0,0,.14f,.085f,.055f);part(sphere,.29f,2.36f,-.10f,.21f,.24f,.21f,0,0,.14f,.085f,.055f);
  // cap from photo
  part(sphere,0,2.56f,0,.52f,.20f,.48f,0,0,.08f,.28f,.55f);part(cube,0,2.50f,-.43f,.72f,.075f,.28f,0,0,.07f,.23f,.47f);
  // facial features face toward local -Z
  part(sphere,-.16f,2.23f,-.425f,.055f,.065f,.035f,0,0,.10f,.12f,.12f);part(sphere,.16f,2.23f,-.425f,.055f,.065f,.035f,0,0,.10f,.12f,.12f);part(sphere,0,2.10f,-.455f,.045f,.025f,.025f,0,0,.55f,.20f,.16f);
 }
 private void part(Mesh q,float ox,float oy,float oz,float sx,float sy,float sz,float rx,float rz,float r,float g,float b){float a=yaw*DEG,xx=x+ox*(float)Math.cos(a)+oz*(float)Math.sin(a),zz=z-ox*(float)Math.sin(a)+oz*(float)Math.cos(a);draw(q,xx,h(x,z)+oy,zz,sx,sy,sz,yaw,rx,rz,r,g,b,1);}
 private float h(float X,float Z){return .18f*(float)Math.sin(X*.22f)+.13f*(float)Math.cos(Z*.19f)+.06f*(float)Math.sin((X+Z)*.34f);}
 private void draw(Mesh q,float X,float Y,float Z,float sx,float sy,float sz,float ry,float rx,float rz,float r,float g,float b,float al){Matrix.setIdentityM(M,0);Matrix.translateM(M,0,X,Y,Z);if(ry!=0)Matrix.rotateM(M,0,ry,0,1,0);if(rx!=0)Matrix.rotateM(M,0,rx,1,0,0);if(rz!=0)Matrix.rotateM(M,0,rz,0,0,1);Matrix.scaleM(M,0,sx,sy,sz);Matrix.multiplyMM(MVP,0,VP,0,M,0);GLES20.glUniformMatrix4fv(uMvp,1,false,MVP,0);GLES20.glUniformMatrix4fv(uM,1,false,M,0);GLES20.glUniform4f(uC,r,g,b,al);q.draw(aP,aN);}
 public void setMove(float a,float b){mx=a;my=b;}public void addCamera(float a,float b){cdx+=a;cdy+=b;}public void requestJump(){}public void requestAction(){}public void setPaused(boolean p){paused=p;}public GameSnapshot getSnapshot(){return snap;}public void release(){}public void resetGame(){x=0;z=5;}
 private static float clamp(float v,float a,float b){return Math.max(a,Math.min(b,v));}
 private static int sh(int t,String s){int q=GLES20.glCreateShader(t);GLES20.glShaderSource(q,s);GLES20.glCompileShader(q);return q;}private static int link(String v,String f){int p=GLES20.glCreateProgram();GLES20.glAttachShader(p,sh(GLES20.GL_VERTEX_SHADER,v));GLES20.glAttachShader(p,sh(GLES20.GL_FRAGMENT_SHADER,f));GLES20.glLinkProgram(p);return p;}
 private static final String VS="uniform mat4 uMVP,uM;attribute vec3 aP,aN;varying vec3 N,W;void main(){vec4 w=uM*vec4(aP,1.0);W=w.xyz;N=normalize(mat3(uM)*aN);gl_Position=uMVP*vec4(aP,1.0);}";
 private static final String FS="precision mediump float;uniform vec4 uC;uniform vec3 uL,uFog,uCam;varying vec3 N,W;void main(){vec3 n=normalize(N),l=normalize(uL);float nd=max(dot(n,l),0.0);float wrap=max((dot(n,l)+.35)/1.35,0.0);vec3 v=normalize(uCam-W),h=normalize(l+v);float spec=pow(max(dot(n,h),0.0),28.0)*.10;float rim=pow(1.0-max(dot(n,v),0.0),3.0)*.07;vec3 col=uC.rgb*(.30+.60*wrap+.16*nd)+spec+rim;float fog=smoothstep(34.0,72.0,distance(uCam,W));gl_FragColor=vec4(mix(col,uFog,fog),uC.a);}";
 private static final class Mesh{final FloatBuffer b;final int n;Mesh(float[]a){b=ByteBuffer.allocateDirect(a.length*4).order(ByteOrder.nativeOrder()).asFloatBuffer();b.put(a).position(0);n=a.length/6;}void draw(int p,int q){b.position(0);GLES20.glVertexAttribPointer(p,3,GLES20.GL_FLOAT,false,24,b);GLES20.glEnableVertexAttribArray(p);b.position(3);GLES20.glVertexAttribPointer(q,3,GLES20.GL_FLOAT,false,24,b);GLES20.glEnableVertexAttribArray(q);GLES20.glDrawArrays(GLES20.GL_TRIANGLES,0,n);}static void v(ArrayList<Float>o,float x,float y,float z,float nx,float ny,float nz){Collections.addAll(o,x,y,z,nx,ny,nz);}static Mesh from(ArrayList<Float>o){float[]a=new float[o.size()];for(int i=0;i<a.length;i++)a[i]=o.get(i);return new Mesh(a);}static Mesh cube(){ArrayList<Float>o=new ArrayList<>();float[][]p={{-.5f,-.5f,-.5f},{.5f,-.5f,-.5f},{.5f,.5f,-.5f},{-.5f,.5f,-.5f},{-.5f,-.5f,.5f},{.5f,-.5f,.5f},{.5f,.5f,.5f},{-.5f,.5f,.5f}};int[][]f={{0,3,2,1},{4,5,6,7},{0,4,7,3},{1,2,6,5},{3,7,6,2},{0,1,5,4}};float[][]nn={{0,0,-1},{0,0,1},{-1,0,0},{1,0,0},{0,1,0},{0,-1,0}};for(int k=0;k<6;k++){int[]a=f[k];int[]t={0,1,2,0,2,3};for(int j:t){float[]P=p[a[j]];v(o,P[0],P[1],P[2],nn[k][0],nn[k][1],nn[k][2]);}}return from(o);}static Mesh sphere(int st,int sl){ArrayList<Float>o=new ArrayList<>();for(int i=0;i<st;i++)for(int j=0;j<sl;j++){float p0=(i/(float)st-.5f)*(float)Math.PI,p1=((i+1)/(float)st-.5f)*(float)Math.PI,t0=j*6.283185f/sl,t1=(j+1)*6.283185f/sl;sv(o,p0,t0);sv(o,p0,t1);sv(o,p1,t1);sv(o,p0,t0);sv(o,p1,t1);sv(o,p1,t0);}return from(o);}static void sv(ArrayList<Float>o,float p,float t){float y=(float)Math.sin(p),c=(float)Math.cos(p),x=c*(float)Math.sin(t),z=c*(float)Math.cos(t);v(o,x*.5f,y*.5f,z*.5f,x,y,z);}static Mesh cylinder(int s){ArrayList<Float>o=new ArrayList<>();for(int i=0;i<s;i++){float a=i*6.283185f/s,A=(i+1)*6.283185f/s,x=(float)Math.sin(a)*.5f,z=(float)Math.cos(a)*.5f,X=(float)Math.sin(A)*.5f,Z=(float)Math.cos(A)*.5f;v(o,x,-.5f,z,x*2,0,z*2);v(o,X,-.5f,Z,X*2,0,Z*2);v(o,X,.5f,Z,X*2,0,Z*2);v(o,x,-.5f,z,x*2,0,z*2);v(o,X,.5f,Z,X*2,0,Z*2);v(o,x,.5f,z,x*2,0,z*2);}return from(o);}static Mesh cone(int s){ArrayList<Float>o=new ArrayList<>();for(int i=0;i<s;i++){float a=i*6.283185f/s,A=(i+1)*6.283185f/s,x=(float)Math.sin(a)*.5f,z=(float)Math.cos(a)*.5f,X=(float)Math.sin(A)*.5f,Z=(float)Math.cos(A)*.5f;v(o,0,.5f,0,x,.5f,z);v(o,x,-.5f,z,x,.5f,z);v(o,X,-.5f,Z,X,.5f,Z);}return from(o);}static Mesh ground(int n,float s){ArrayList<Float>o=new ArrayList<>();for(int z=0;z<n;z++)for(int x=0;x<n;x++){float x0=(x-n/2)*s,z0=(z-n/2)*s,x1=x0+s,z1=z0+s;gv(o,x0,z0);gv(o,x1,z0);gv(o,x1,z1);gv(o,x0,z0);gv(o,x1,z1);gv(o,x0,z1);}return from(o);}static void gv(ArrayList<Float>o,float x,float z){float y=.18f*(float)Math.sin(x*.22f)+.13f*(float)Math.cos(z*.19f)+.06f*(float)Math.sin((x+z)*.34f);float dx=.0396f*(float)Math.cos(x*.22f)+.0204f*(float)Math.cos((x+z)*.34f),dz=-.0247f*(float)Math.sin(z*.19f)+.0204f*(float)Math.cos((x+z)*.34f),nx=-dx,ny=1,nz=-dz,l=(float)Math.sqrt(nx*nx+1+nz*nz);v(o,x,y,z,nx/l,ny/l,nz/l);}static Mesh rock(){ArrayList<Float>o=new ArrayList<>();float[][]p={{0,.65f,0},{-.58f,-.36f,-.46f},{.50f,-.42f,-.52f},{.62f,-.34f,.35f},{-.38f,-.44f,.58f},{-.65f,-.30f,.10f}};int[][]t={{0,1,2},{0,2,3},{0,3,4},{0,4,5},{0,5,1},{1,5,4},{1,4,3},{1,3,2}};for(int[]a:t){float[]A=p[a[0]],B=p[a[1]],C=p[a[2]];float ux=B[0]-A[0],uy=B[1]-A[1],uz=B[2]-A[2],vx=C[0]-A[0],vy=C[1]-A[1],vz=C[2]-A[2],nx=uy*vz-uz*vy,ny=uz*vx-ux*vz,nz=ux*vy-uy*vx,l=(float)Math.sqrt(nx*nx+ny*ny+nz*nz);v(o,A[0],A[1],A[2],nx/l,ny/l,nz/l);v(o,B[0],B[1],B[2],nx/l,ny/l,nz/l);v(o,C[0],C[1],C[2],nx/l,ny/l,nz/l);}return from(o);}}
}