package cz.vitekzavodnik.game;

import android.content.Context;
import android.content.SharedPreferences;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;
import android.os.SystemClock;

import java.util.ArrayList;
import java.util.List;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

public final class GameRenderer implements GLSurfaceView.Renderer {
    private static final float DEG=(float)(Math.PI/180.0);
    private final SharedPreferences prefs;
    private final AudioEngine audio;
    private Mesh cube,sphere,cylinder,pyramid;
    private int program,aPos,aNorm,uMvp,uModel,uColor,uLight,uFog,uCam;
    private final float[] proj=new float[16],view=new float[16],vp=new float[16],model=new float[16],mvp=new float[16];

    private volatile float moveX,moveY,camDx,camDy;
    private volatile boolean jumpReq,actionReq,paused=true;
    private float px=-18f,py=0f,pz=16f,yaw=25f,vY=0f,walk=0f;
    private float camYaw=35f,camPitch=18f,camDist=8.5f;
    private boolean grounded=true,inKart=false;
    private float kartSpeed=0f,raceTime=0f,bestTime=-1f;
    private int stage=0,minerals=0,tools=0,coins=0,checkpoint=0;
    private long lastMs,messageUntil;
    private String message="";
    private volatile GameSnapshot snap=new GameSnapshot(0,0,0,0,false,0,8,0,-1,"Vítej!");

    private static final class Item{
        static final int MINERAL=0,TOOL=1,COIN=2;
        final int type; final float x,z; boolean active=true;
        Item(int t,float x,float z){type=t;this.x=x;this.z=z;}
    }
    private final List<Item> items=new ArrayList<>();
    private final float[][] gates={{-12,10},{-2,8},{10,5},{17,-3},{12,-14},{0,-18},{-13,-12},{-20,0}};

    public GameRenderer(Context c){
        Context app=c.getApplicationContext();
        prefs=app.getSharedPreferences("vitek_zavodnik_save",Context.MODE_PRIVATE);
        audio=new AudioEngine(app);
        stage=prefs.getInt("stage",0); coins=prefs.getInt("coins",0); bestTime=prefs.getFloat("best",-1f);
        if(stage>=1) minerals=5; if(stage>=2) tools=3;
        populate(); show("Najdi 5 minerálů!",3200);
    }

    private void populate(){
        items.clear();
        float[][] ms={{-14,4},{-7,-5},{2,-12},{13,-9},{16,10}};
        for(float[] p:ms)items.add(new Item(Item.MINERAL,p[0],p[1]));
        float[][] ts={{-4,15},{8,13},{18,2}};
        for(float[] p:ts)items.add(new Item(Item.TOOL,p[0],p[1]));
        for(int i=0;i<24;i++){ double a=i*1.57; float r=6f+(i%5)*3.3f; items.add(new Item(Item.COIN,(float)Math.cos(a)*r,(float)Math.sin(a)*r)); }
        if(stage>=1)for(Item i:items)if(i.type==Item.MINERAL)i.active=false;
        if(stage>=2)for(Item i:items)if(i.type==Item.TOOL)i.active=false;
    }

    @Override public void onSurfaceCreated(GL10 gl,EGLConfig cfg){
        GLES20.glClearColor(.52f,.78f,.95f,1f); GLES20.glEnable(GLES20.GL_DEPTH_TEST); GLES20.glEnable(GLES20.GL_CULL_FACE);
        GLES20.glEnable(GLES20.GL_BLEND); GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA,GLES20.GL_ONE_MINUS_SRC_ALPHA);
        program=program(VS,FS); aPos=GLES20.glGetAttribLocation(program,"aPosition"); aNorm=GLES20.glGetAttribLocation(program,"aNormal");
        uMvp=GLES20.glGetUniformLocation(program,"uMVP"); uModel=GLES20.glGetUniformLocation(program,"uModel"); uColor=GLES20.glGetUniformLocation(program,"uColor");
        uLight=GLES20.glGetUniformLocation(program,"uLightDir"); uFog=GLES20.glGetUniformLocation(program,"uFogColor"); uCam=GLES20.glGetUniformLocation(program,"uCameraPos");
        cube=Shapes.cube(); sphere=Shapes.sphere(10,16); cylinder=Shapes.cylinder(16); pyramid=Shapes.pyramid(); lastMs=SystemClock.uptimeMillis();
    }

    @Override public void onSurfaceChanged(GL10 gl,int w,int h){ GLES20.glViewport(0,0,w,h); Matrix.perspectiveM(proj,0,56f,(float)w/Math.max(1,h),.1f,100f); }

    @Override public void onDrawFrame(GL10 gl){
        long now=SystemClock.uptimeMillis(); float dt=Math.min(.033f,Math.max(.001f,(now-lastMs)/1000f)); lastMs=now;
        if(!paused)update(dt,now);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT|GLES20.GL_DEPTH_BUFFER_BIT); GLES20.glUseProgram(program);
        float cr=camYaw*DEG,pr=camPitch*DEG,ty=py+(inKart?1f:1.7f);
        float ex=px-(float)Math.sin(cr)*camDist*(float)Math.cos(pr), ez=pz-(float)Math.cos(cr)*camDist*(float)Math.cos(pr), ey=ty+camDist*(float)Math.sin(pr);
        Matrix.setLookAtM(view,0,ex,ey,ez,px,ty,pz,0,1,0); Matrix.multiplyMM(vp,0,proj,0,view,0);
        GLES20.glUniform3f(uLight,-.45f,.82f,.35f); GLES20.glUniform4f(uFog,.52f,.78f,.95f,1f); GLES20.glUniform3f(uCam,ex,ey,ez);
        world(now); collectibles(now); kartAndPlayer(); if(stage==2&&inKart)checkpoint();
    }

    private void update(float dt,long now){
        camYaw+=camDx*.18f; camPitch=clamp(camPitch+camDy*.12f,9f,35f); camDx=camDy=0;
        if(actionReq){actionReq=false;action();}
        if(!inKart){
            float mag=(float)Math.sqrt(moveX*moveX+moveY*moveY);
            if(mag>.08f){ float mx=moveX/Math.max(1f,mag),fw=-moveY/Math.max(1f,mag),a=camYaw*DEG; float dx=mx*(float)Math.cos(a)+fw*(float)Math.sin(a),dz=-mx*(float)Math.sin(a)+fw*(float)Math.cos(a); px+=dx*5f*dt;pz+=dz*5f*dt;yaw=(float)Math.toDegrees(Math.atan2(dx,dz));walk+=dt*8f*mag; }
            if(jumpReq&&grounded){grounded=false;vY=5.5f;audio.jump();} jumpReq=false;
            if(!grounded){vY-=12.5f*dt;py+=vY*dt;if(py<=0){py=0;vY=0;grounded=true;}}
        }else{
            jumpReq=false; float th=clamp(-moveY,-1,1),st=clamp(moveX,-1,1); kartSpeed+=th*(th>=0?9f:12f)*dt;kartSpeed*=(float)Math.pow(.45f,dt);kartSpeed=clamp(kartSpeed,-3f,10.5f);
            if(Math.abs(kartSpeed)>.12f)yaw+=st*(55f+Math.abs(kartSpeed)*2f)*dt*Math.signum(kartSpeed); float a=yaw*DEG;px+=(float)Math.sin(a)*kartSpeed*dt;pz+=(float)Math.cos(a)*kartSpeed*dt;camYaw+=shortest(yaw-camYaw)*dt*1.2f;audio.setEnginePitch(Math.abs(kartSpeed)/10.5f);
            if(stage==2){raceTime+=dt;if(raceTime>75f){show("Čas vypršel – zkus to znovu!",2600);resetRace();}}
        }
        px=clamp(px,-27,27);pz=clamp(pz,-27,27);collect();if(stage==2&&inKart)race();
        snap=new GameSnapshot(stage,minerals,tools,coins,inKart,checkpoint,gates.length,raceTime,bestTime,now<messageUntil?message:"");
    }

    private void action(){
        float d=dist2(px,pz,-18,16);
        if(!inKart&&stage>=2&&d<10f){inKart=true;py=0;kartSpeed=0;audio.startEngine();if(stage==2){checkpoint=0;raceTime=0;show("Závod! Projeď 8 modrými bránami.",2500);}return;}
        if(inKart&&stage>=3){inKart=false;kartSpeed=0;audio.stopEngine();show("Volná jízda dokončena.",1500);}
    }

    private void collect(){
        for(Item i:items){ if(!i.active)continue; if(i.type==Item.MINERAL&&stage!=0)continue;if(i.type==Item.TOOL&&stage!=1)continue;if(dist2(px,pz,i.x,i.z)>2.1f)continue;i.active=false;audio.collect();
            if(i.type==Item.MINERAL){minerals++;show("Minerál "+minerals+"/5",1100);if(minerals>=5){stage=1;save();audio.success();show("Super! Najdi 3 díly pro mechanika.",3000);}}
            else if(i.type==Item.TOOL){tools++;show("Díl "+tools+"/3",1100);if(tools>=3){stage=2;save();audio.success();show("Motokára čeká v depu!",3000);}}
            else{coins++;prefs.edit().putInt("coins",coins).apply();}
        }
    }

    private void race(){ if(checkpoint>=gates.length)return;float[] g=gates[checkpoint];if(dist2(px,pz,g[0],g[1])<9f){checkpoint++;audio.collect();if(checkpoint>=gates.length){stage=3;inKart=false;audio.stopEngine();if(bestTime<0||raceTime<bestTime)bestTime=raceTime;prefs.edit().putInt("stage",stage).putFloat("best",bestTime).putInt("coins",coins).apply();audio.success();show("CÍL! Výborná jízda!",4500);}else show("Brána "+checkpoint+"/8",900);}}
    private void resetRace(){checkpoint=0;raceTime=0;px=-18;pz=16;yaw=25;kartSpeed=0;}
    private void save(){prefs.edit().putInt("stage",stage).putInt("coins",coins).apply();}

    private void world(long now){
        draw(cube,0,-.55f,0,60,1,60,0,.22f,.56f,.22f,1);
        road(0,0,52,5,0); road(0,0,42,4.2f,90); road(-15,7,25,3.4f,34); road(15,-8,23,3.4f,-38);
        house(-20,-12,.72f,.18f,.12f);house(18,-12,.10f,.45f,.75f);house(18,13,.82f,.55f,.10f);
        draw(cube,-18,1.5f,18,8,3,5,0,.78f,.23f,.08f,1);draw(cube,-18,3.3f,18,8.6f,.35f,5.6f,0,.14f,.16f,.19f,1);
        draw(cube,-18,1.2f,15.45f,4.8f,2.3f,.15f,0,.12f,.15f,.18f,1);
        for(int i=-24;i<=24;i+=6){tree(i,-25);tree(i,25);if(i%12==0){tree(-25,i);tree(25,i);}}
        for(int i=0;i<14;i++){double a=i*.93;float r=17+(i%3)*3;draw(pyramid,(float)Math.cos(a)*r,0,(float)Math.sin(a)*r,2.1f,2.8f,2.1f,0,.42f,.39f,.34f,1);}
    }

    private void collectibles(long now){
        float bob=(float)Math.sin(now*.004)*.18f;
        for(Item i:items){if(!i.active)continue;if(i.type==Item.MINERAL&&stage!=0)continue;if(i.type==Item.TOOL&&stage!=1)continue;
            if(i.type==Item.MINERAL)draw(pyramid,i.x,.55f+bob,i.z,.8f,1.4f,.8f,now*.06f,.55f,.25f,.85f,1);
            else if(i.type==Item.TOOL){draw(cube,i.x,.55f+bob,i.z,1.1f,.26f,.26f,now*.04f,.78f,.78f,.82f,1);draw(cube,i.x,.55f+bob,i.z,.28f,1.1f,.28f,now*.04f,.25f,.28f,.31f,1);}
            else draw(cylinder,i.x,.55f+bob,i.z,.62f,.14f,.62f,90,.98f,.76f,.05f,1);
        }
    }

    private void kartAndPlayer(){ if(inKart){kart(px,py,pz,yaw);seated(px,py,pz,yaw);}else{player(px,py,pz,yaw);kart(-18,0,16,25);} }
    private void checkpoint(){if(checkpoint>=gates.length)return;float[] g=gates[checkpoint];draw(cube,g[0]-1.8f,1.7f,g[1],.2f,3.4f,.2f,0,.08f,.63f,1,.85f);draw(cube,g[0]+1.8f,1.7f,g[1],.2f,3.4f,.2f,0,.08f,.63f,1,.85f);draw(cube,g[0],3.35f,g[1],3.8f,.22f,.22f,0,.08f,.63f,1,.85f);}

    private void kart(float x,float y,float z,float r){draw(cube,x,y+.48f,z,2.3f,.38f,1.35f,r,.08f,.22f,.72f,1);draw(cube,x,y+.70f,z,1.25f,.42f,.72f,r,.95f,.79f,.08f,1);float[][] ws={{-.95f,-.58f},{.95f,-.58f},{-.95f,.58f},{.95f,.58f}};for(float[] o:ws){float[] q=rot(o[0],o[1],r);draw(cylinder,x+q[0],y+.35f,z+q[1],.58f,.28f,.58f,90+r,.04f,.05f,.06f,1);} }
    private void player(float x,float y,float z,float r){float s=(float)Math.sin(walk)*22f;float[] p=base(x,y,z,r);part(cube,p,-.22f,.55f,0,.34f,1.05f,.38f,s,.08f,.11f,.16f,1);part(cube,p,.22f,.55f,0,.34f,1.05f,.38f,-s,.08f,.11f,.16f,1);part(cube,p,0,1.55f,0,1.02f,1.28f,.58f,0,.10f,.22f,.58f,1);part(cube,p,-.68f,1.55f,0,.31f,1.15f,.34f,-s,.12f,.26f,.62f,1);part(cube,p,.68f,1.55f,0,.31f,1.15f,.34f,s,.12f,.26f,.62f,1);head(p,2.55f);}
    private void seated(float x,float y,float z,float r){float[] p=base(x,y,z,r);part(cube,p,0,1.15f,-.1f,.9f,.85f,.52f,0,.10f,.22f,.58f,1);head(p,1.95f);}
    private void head(float[] p,float y){part(sphere,p,0,y,0,.78f,.9f,.76f,0,.97f,.73f,.55f,1);part(cube,p,0,y+.33f,-.1f,.62f,.15f,.58f,0,.72f,.52f,.27f,1);part(cylinder,p,0,y+.45f,0,.88f,.25f,.82f,0,.12f,.34f,.78f,1);part(cube,p,-.2f,y+.37f,.43f,.84f,.09f,.48f,-8,.05f,.12f,.26f,1);part(sphere,p,-.16f,y+.10f,.35f,.12f,.15f,.08f,0,.20f,.55f,.86f,1);part(sphere,p,.16f,y+.10f,.35f,.12f,.15f,.08f,0,.20f,.55f,.86f,1);}

    private void road(float x,float z,float len,float wid,float r){draw(cube,x,-.015f,z,wid,.13f,len,r,.18f,.20f,.22f,1);draw(cube,x,.06f,z,.10f,.035f,len*.82f,r,.95f,.82f,.08f,.95f);}
    private void house(float x,float z,float rr,float gg,float bb){draw(cube,x,1.25f,z,5.4f,2.5f,4.2f,0,.92f,.88f,.78f,1);draw(pyramid,x,2.5f,z,6f,2.2f,4.9f,0,rr,gg,bb,1);draw(cube,x,.95f,z-2.12f,1.1f,1.9f,.1f,0,.24f,.12f,.05f,1);}
    private void tree(float x,float z){draw(cylinder,x,1.25f,z,.55f,2.5f,.55f,0,.38f,.20f,.08f,1);draw(sphere,x,3.1f,z,2.7f,2.3f,2.7f,0,.10f,.48f,.18f,1);}

    private float[] base(float x,float y,float z,float r){float[] m=new float[16];Matrix.setIdentityM(m,0);Matrix.translateM(m,0,x,y,z);Matrix.rotateM(m,0,r,0,1,0);return m;}
    private void part(Mesh me,float[] parent,float x,float y,float z,float sx,float sy,float sz,float rx,float r,float g,float b,float a){float[] m=parent.clone();Matrix.translateM(m,0,x,y,z);if(rx!=0)Matrix.rotateM(m,0,rx,1,0,0);Matrix.scaleM(m,0,sx,sy,sz);drawModel(me,m,r,g,b,a);}
    private void draw(Mesh me,float x,float y,float z,float sx,float sy,float sz,float rY,float r,float g,float b,float a){Matrix.setIdentityM(model,0);Matrix.translateM(model,0,x,y,z);Matrix.rotateM(model,0,rY,0,1,0);Matrix.scaleM(model,0,sx,sy,sz);drawModel(me,model,r,g,b,a);}
    private void drawModel(Mesh me,float[] m,float r,float g,float b,float a){Matrix.multiplyMM(mvp,0,vp,0,m,0);GLES20.glUniformMatrix4fv(uMvp,1,false,mvp,0);GLES20.glUniformMatrix4fv(uModel,1,false,m,0);GLES20.glUniform4f(uColor,r,g,b,a);me.draw(aPos,aNorm);}
    private float[] rot(float x,float z,float y){float a=y*DEG;return new float[]{x*(float)Math.cos(a)+z*(float)Math.sin(a),-x*(float)Math.sin(a)+z*(float)Math.cos(a)};}

    private void show(String s,long ms){message=s;messageUntil=SystemClock.uptimeMillis()+ms;}
    private static float dist2(float a,float b,float c,float d){float x=a-c,z=b-d;return x*x+z*z;}
    private static float clamp(float v,float lo,float hi){return Math.max(lo,Math.min(hi,v));}
    private static float shortest(float a){while(a>180)a-=360;while(a<-180)a+=360;return a;}

    public void setMove(float x,float y){moveX=x;moveY=y;} public void addCamera(float x,float y){camDx+=x;camDy+=y;} public void requestJump(){jumpReq=true;} public void requestAction(){actionReq=true;}
    public void setPaused(boolean p){paused=p;if(p)audio.stopEngine();else if(inKart)audio.startEngine();} public GameSnapshot getSnapshot(){return snap;} public void release(){audio.release();}
    public void resetGame(){stage=0;minerals=tools=coins=checkpoint=0;bestTime=-1;raceTime=0;inKart=false;kartSpeed=0;px=-18;py=0;pz=16;yaw=25;prefs.edit().clear().apply();populate();show("Nová hra – najdi 5 minerálů!",3000);}

    private static int program(String vs,String fs){int v=shader(GLES20.GL_VERTEX_SHADER,vs),f=shader(GLES20.GL_FRAGMENT_SHADER,fs),p=GLES20.glCreateProgram();GLES20.glAttachShader(p,v);GLES20.glAttachShader(p,f);GLES20.glLinkProgram(p);int[] ok=new int[1];GLES20.glGetProgramiv(p,GLES20.GL_LINK_STATUS,ok,0);if(ok[0]==0)throw new RuntimeException(GLES20.glGetProgramInfoLog(p));GLES20.glDeleteShader(v);GLES20.glDeleteShader(f);return p;}
    private static int shader(int type,String src){int s=GLES20.glCreateShader(type);GLES20.glShaderSource(s,src);GLES20.glCompileShader(s);int[] ok=new int[1];GLES20.glGetShaderiv(s,GLES20.GL_COMPILE_STATUS,ok,0);if(ok[0]==0)throw new RuntimeException(GLES20.glGetShaderInfoLog(s));return s;}

    private static final String VS="uniform mat4 uMVP;uniform mat4 uModel;uniform vec3 uLightDir;uniform vec3 uCameraPos;attribute vec3 aPosition;attribute vec3 aNormal;varying float vLight;varying float vDist;void main(){vec4 wp=uModel*vec4(aPosition,1.0);vec3 n=normalize(mat3(uModel)*aNormal);float d=max(dot(n,normalize(uLightDir)),0.0);vLight=0.45+0.70*d;vDist=distance(wp.xyz,uCameraPos);gl_Position=uMVP*vec4(aPosition,1.0);}";
    private static final String FS="precision mediump float;uniform vec4 uColor;uniform vec4 uFogColor;varying float vLight;varying float vDist;void main(){float fog=clamp((vDist-28.0)/35.0,0.0,0.72);vec4 c=vec4(uColor.rgb*vLight,uColor.a);gl_FragColor=mix(c,uFogColor,fog);}";
}
