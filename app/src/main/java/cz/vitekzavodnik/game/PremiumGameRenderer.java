package cz.vitekzavodnik.game;

import android.content.Context;
import android.content.SharedPreferences;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;
import android.os.SystemClock;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.List;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

/** Richer stylised 3D renderer, still lightweight enough for phones. */
public final class PremiumGameRenderer implements GLSurfaceView.Renderer {
    private static final float DEG=(float)(Math.PI/180.0);
    private final SharedPreferences prefs;
    private final AudioEngine audio;
    private final List<Item> items=new ArrayList<>();
    private int program,aPos,uMvp,uColor;
    private Mesh cube,sphere,cylinder;
    private boolean glReady;
    private final float[] proj=new float[16],view=new float[16],vp=new float[16],model=new float[16],mvp=new float[16];
    private volatile float moveX,moveY,camDx,camDy;
    private volatile boolean jumpReq,actionReq,paused=true;
    private float px=-18,py=0,pz=16,yaw=25,vY=0,walk=0,camYaw=35,camPitch=18,camDist=8.3f;
    private boolean grounded=true,inKart=false;
    private float kartSpeed=0,raceTime=0,bestTime=-1;
    private int stage=0,minerals=0,tools=0,coins=0,checkpoint=0;
    private long lastMs,messageUntil;
    private String message="";
    private volatile GameSnapshot snap=new GameSnapshot(0,0,0,0,false,0,8,0,-1,"Vítej!");

    private static final class Item{
        static final int MINERAL=0,TOOL=1,COIN=2;
        final int type; final float x,z; boolean active=true;
        Item(int t,float x,float z){type=t;this.x=x;this.z=z;}
    }
    private static final float[][] GATES={{-12,10},{-2,8},{10,5},{17,-3},{12,-14},{0,-18},{-13,-12},{-20,0}};

    public PremiumGameRenderer(Context c){
        Context app=c.getApplicationContext();
        prefs=app.getSharedPreferences("vitek_zavodnik_save",Context.MODE_PRIVATE);
        audio=new AudioEngine(app);
        stage=prefs.getInt("stage",0); coins=prefs.getInt("coins",0); bestTime=prefs.getFloat("best",-1f);
        if(stage>=1)minerals=5; if(stage>=2)tools=3;
        populate(); show("Najdi 5 minerálů!",3200);
    }

    private void populate(){
        items.clear();
        float[][] ms={{-14,4},{-7,-5},{2,-12},{13,-9},{16,10}};
        for(float[] p:ms)items.add(new Item(Item.MINERAL,p[0],p[1]));
        float[][] ts={{-4,15},{8,13},{18,2}};
        for(float[] p:ts)items.add(new Item(Item.TOOL,p[0],p[1]));
        for(int i=0;i<28;i++){double a=i*1.37;float r=6+(i%6)*3.1f;items.add(new Item(Item.COIN,(float)Math.cos(a)*r,(float)Math.sin(a)*r));}
        if(stage>=1)for(Item i:items)if(i.type==Item.MINERAL)i.active=false;
        if(stage>=2)for(Item i:items)if(i.type==Item.TOOL)i.active=false;
        updateSnap(SystemClock.uptimeMillis());
    }

    @Override public void onSurfaceCreated(GL10 gl,EGLConfig cfg){
        try{
            GLES20.glClearColor(.47f,.74f,.94f,1); GLES20.glEnable(GLES20.GL_DEPTH_TEST); GLES20.glDisable(GLES20.GL_CULL_FACE);
            GLES20.glEnable(GLES20.GL_BLEND); GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA,GLES20.GL_ONE_MINUS_SRC_ALPHA);
            program=buildProgram(VS,FS); if(program==0)return;
            aPos=GLES20.glGetAttribLocation(program,"aPosition");uMvp=GLES20.glGetUniformLocation(program,"uMVP");uColor=GLES20.glGetUniformLocation(program,"uColor");
            cube=Mesh.cube();sphere=Mesh.sphere(9,14);cylinder=Mesh.cylinder(14);glReady=aPos>=0&&uMvp>=0&&uColor>=0;lastMs=SystemClock.uptimeMillis();
        }catch(Throwable ignored){glReady=false;}
    }
    @Override public void onSurfaceChanged(GL10 gl,int w,int h){try{h=Math.max(1,h);GLES20.glViewport(0,0,w,h);Matrix.perspectiveM(proj,0,55,(float)w/h,.1f,130);}catch(Throwable ignored){glReady=false;}}
    @Override public void onDrawFrame(GL10 gl){
        try{
            long now=SystemClock.uptimeMillis();if(lastMs==0)lastMs=now;float dt=Math.min(.033f,Math.max(.001f,(now-lastMs)/1000f));lastMs=now;if(!paused)update(dt,now);
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT|GLES20.GL_DEPTH_BUFFER_BIT);if(!glReady)return;GLES20.glUseProgram(program);
            float cr=camYaw*DEG,pr=camPitch*DEG,ty=py+(inKart?1.05f:1.7f);
            float ex=px-(float)Math.sin(cr)*camDist*(float)Math.cos(pr),ez=pz-(float)Math.cos(cr)*camDist*(float)Math.cos(pr),ey=ty+camDist*(float)Math.sin(pr);
            Matrix.setLookAtM(view,0,ex,ey,ez,px,ty,pz,0,1,0);Matrix.multiplyMM(vp,0,proj,0,view,0);
            world(now);collectibles(now);
            if(inKart){kart(px,py,pz,yaw);seated(px,py,pz,yaw);}else{player(px,py,pz,yaw);kart(-18,0,16,25);}
            if(stage==2&&inKart)gate(now);
        }catch(Throwable ignored){glReady=false;}
    }

    private void update(float dt,long now){
        camYaw+=camDx*.18f;camPitch=clamp(camPitch+camDy*.12f,8,34);camDx=camDy=0;
        if(actionReq){actionReq=false;action();}
        if(!inKart){
            float mag=(float)Math.sqrt(moveX*moveX+moveY*moveY);
            if(mag>.08f){
                // joystick is intentionally direct: drag right -> move right, drag up -> move forward
                float mx=moveX/Math.max(1f,mag),fw=-moveY/Math.max(1f,mag),a=camYaw*DEG;
                float dx=mx*(float)Math.cos(a)+fw*(float)Math.sin(a),dz=-mx*(float)Math.sin(a)+fw*(float)Math.cos(a);
                px+=dx*5.3f*dt;pz+=dz*5.3f*dt;yaw=(float)Math.toDegrees(Math.atan2(dx,dz));walk+=dt*8.5f*mag;
            }
            if(jumpReq&&grounded){grounded=false;vY=5.5f;audio.jump();}jumpReq=false;
            if(!grounded){vY-=12.5f*dt;py+=vY*dt;if(py<=0){py=0;vY=0;grounded=true;}}
        }else{
            jumpReq=false;float th=clamp(-moveY,-1,1),st=clamp(moveX,-1,1);
            kartSpeed+=th*(th>=0?9.5f:12f)*dt;kartSpeed*=(float)Math.pow(.46f,dt);kartSpeed=clamp(kartSpeed,-3.2f,11.2f);
            if(Math.abs(kartSpeed)>.12f)yaw+=st*(56+Math.abs(kartSpeed)*2)*dt*Math.signum(kartSpeed);
            float a=yaw*DEG;px+=(float)Math.sin(a)*kartSpeed*dt;pz+=(float)Math.cos(a)*kartSpeed*dt;camYaw+=shortest(yaw-camYaw)*dt*1.25f;audio.setEnginePitch(Math.abs(kartSpeed)/11.2f);
            if(stage==2){raceTime+=dt;if(raceTime>75){show("Čas vypršel – zkus to znovu!",2600);resetRace();}}
        }
        px=clamp(px,-29,29);pz=clamp(pz,-29,29);collect();if(stage==2&&inKart)race();updateSnap(now);
    }

    private void action(){
        if(!inKart&&stage>=2&&dist2(px,pz,-18,16)<10){inKart=true;py=0;kartSpeed=0;audio.startEngine();if(stage==2){checkpoint=0;raceTime=0;show("Závod! Projeď 8 modrými bránami.",2500);}return;}
        if(inKart&&stage>=3){inKart=false;kartSpeed=0;audio.stopEngine();show("Volná jízda dokončena.",1500);}
    }
    private void collect(){
        for(Item i:items){if(!i.active)continue;if(i.type==Item.MINERAL&&stage!=0)continue;if(i.type==Item.TOOL&&stage!=1)continue;if(dist2(px,pz,i.x,i.z)>2.1f)continue;
            i.active=false;audio.collect();
            if(i.type==Item.MINERAL){minerals++;show("Minerál "+minerals+"/5",1100);if(minerals>=5){stage=1;save();audio.success();show("Super! Najdi 3 díly pro mechanika.",3000);}}
            else if(i.type==Item.TOOL){tools++;show("Díl "+tools+"/3",1100);if(tools>=3){stage=2;save();audio.success();show("Motokára čeká v depu!",3000);}}
            else{coins++;prefs.edit().putInt("coins",coins).apply();}
        }
    }
    private void race(){if(checkpoint>=GATES.length)return;float[] g=GATES[checkpoint];if(dist2(px,pz,g[0],g[1])<9){checkpoint++;audio.collect();if(checkpoint>=GATES.length){stage=3;inKart=false;audio.stopEngine();if(bestTime<0||raceTime<bestTime)bestTime=raceTime;prefs.edit().putInt("stage",stage).putFloat("best",bestTime).putInt("coins",coins).apply();audio.success();show("CÍL! Výborná jízda!",4500);}else show("Brána "+checkpoint+"/8",900);}}
    private void resetRace(){checkpoint=0;raceTime=0;px=-18;pz=16;yaw=25;kartSpeed=0;}
    private void save(){prefs.edit().putInt("stage",stage).putInt("coins",coins).apply();}
    private void updateSnap(long now){snap=new GameSnapshot(stage,minerals,tools,coins,inKart,checkpoint,GATES.length,raceTime,bestTime,now<messageUntil?message:"");}

    private void world(long now){
        box(0,-.58f,0,64,1,64,0,.22f,.58f,.27f,1);box(0,-.49f,0,60,.08f,60,0,.28f,.66f,.31f,1);
        road(0,0,6.7f,55,0);road(0,0,48,5.7f,90);road(-15,7,26,4.1f,34);road(15,-8,24,4.1f,-38);
        box(-18,-.01f,14,11,.08f,8,0,.17f,.19f,.22f,1);
        house(-20,-12,.76f,.25f,.15f);house(18,-12,.12f,.48f,.82f);house(18,13,.90f,.60f,.12f);house(-3,-20,.68f,.28f,.62f);
        depot();stand();
        for(int i=-27;i<=27;i+=6){tree(i,-28,1);tree(i,28,1);if(i%12==0){tree(-28,i,1.05f);tree(28,i,.95f);}}
        for(int i=0;i<14;i++){double a=i*.58;float r=31+(i%4)*2.6f;box((float)Math.cos(a)*r,1.2f,(float)Math.sin(a)*r,3.4f,2.5f+(i%3),3.4f,i*11,.43f,.39f,.34f,1);}
        for(int i=-20;i<=20;i+=8){banner(i,-4.5f);banner(i,4.5f);}for(int i=-20;i<=20;i+=5)cone(i,7.1f);
    }
    private void road(float x,float z,float sx,float sz,float rot){
        box(x,-.02f,z,sx,.08f,sz,rot,.17f,.19f,.22f,1);
        int n=Math.max(3,(int)(Math.max(sx,sz)/5));for(int i=-n/2;i<=n/2;i++){float t=i*4.8f;if(Math.abs(rot)<45)box(x,.035f,z+t,.18f,.035f,2,rot,.98f,.80f,.08f,1);else box(x+t,.035f,z,2,.035f,.18f,rot,.98f,.80f,.08f,1);}
    }
    private void depot(){
        box(-18,1.55f,20.5f,10.5f,3,5.8f,0,.08f,.12f,.17f,1);box(-18,3.25f,20.5f,11,.35f,6.2f,0,.04f,.07f,.11f,1);
        for(int i=0;i<3;i++){float bx=-21.4f+i*3.4f;box(bx,1.45f,17.55f,2.8f,2.6f,.18f,0,.035f,.045f,.065f,1);box(bx,2.95f,17.35f,2.9f,.38f,.18f,0,.10f,.43f,.95f,1);}
        box(-18,3.25f,17.20f,8.5f,.70f,.12f,0,.08f,.30f,.80f,1);box(-18,3.25f,17.08f,5.8f,.18f,.05f,0,.98f,.82f,.08f,1);
        for(int i=0;i<4;i++)mesh(cylinder,-23+i*.85f,.42f,15.4f,.55f,.35f,.55f,0,90,0,.03f,.04f,.05f,1);
        box(-13.3f,.45f,15.4f,1.4f,.9f,1,0,.88f,.34f,.06f,1);
    }
    private void stand(){float x=21,z=20;box(x,.45f,z,8.5f,.8f,4,-8,.18f,.22f,.28f,1);for(int i=0;i<4;i++)box(x,.8f+i*.45f,z+1.2f-i*.75f,8,.25f,.65f,-8,i%2==0?.08f:.95f,i%2==0?.35f:.80f,i%2==0?.82f:.08f,1);box(x,3,z-1.8f,9,.22f,.22f,-8,.10f,.12f,.16f,1);}
    private void banner(float x,float z){box(x-.9f,.9f,z,.12f,1.8f,.12f,0,.12f,.14f,.17f,1);box(x+.9f,.9f,z,.12f,1.8f,.12f,0,.12f,.14f,.17f,1);box(x,1.5f,z,1.8f,.62f,.08f,0,.08f,.34f,.86f,1);box(x,1.5f,z-.05f,1.2f,.12f,.04f,0,.98f,.82f,.08f,1);}
    private void cone(float x,float z){mesh(cylinder,x,.10f,z,.50f,.10f,.50f,0,0,0,.05f,.05f,.06f,1);box(x,.52f,z,.35f,.80f,.35f,45,1,.34f,.04f,1);box(x,.58f,z,.38f,.13f,.38f,45,.96f,.96f,.96f,1);}
    private void house(float x,float z,float r,float g,float b){box(x,1.25f,z,4.6f,2.5f,3.8f,0,r,g,b,1);box(x,2.72f,z,5,.38f,4.2f,0,.20f,.12f,.08f,1);box(x,.95f,z-1.93f,1.05f,1.9f,.15f,0,.16f,.23f,.32f,1);for(int i=-1;i<=1;i+=2){box(x+i*1.35f,1.65f,z-1.94f,.85f,.75f,.13f,0,.42f,.78f,.96f,1);box(x+i*1.35f,1.65f,z-2.02f,.08f,.78f,.03f,0,.95f,.95f,.95f,1);}}
    private void tree(float x,float z,float s){mesh(cylinder,x,1*s,z,.42f*s,1.9f*s,.42f*s,0,0,0,.34f,.19f,.08f,1);mesh(sphere,x,2.75f*s,z,1.55f*s,1.65f*s,1.55f*s,0,0,0,.10f,.48f,.18f,1);mesh(sphere,x-.85f*s,2.45f*s,z+.2f,1.15f*s,1.25f*s,1.15f*s,0,0,0,.12f,.55f,.20f,1);mesh(sphere,x+.82f*s,2.50f*s,z-.1f,1.1f*s,1.2f*s,1.1f*s,0,0,0,.08f,.42f,.15f,1);}

    private void collectibles(long now){float bob=(float)Math.sin(now*.004)*.18f;for(Item i:items){if(!i.active)continue;if(i.type==Item.MINERAL&&stage!=0)continue;if(i.type==Item.TOOL&&stage!=1)continue;shadow(i.x,.03f,i.z,.75f,.45f);if(i.type==Item.MINERAL){box(i.x,.72f+bob,i.z,.58f,1.18f,.58f,now*.06f,.62f,.28f,.90f,1);box(i.x,1.02f+bob,i.z,.30f,.65f,.30f,-now*.08f,.78f,.55f,1,1);}else if(i.type==Item.TOOL){box(i.x,.65f+bob,i.z,1,.26f,.26f,now*.04f,.82f,.84f,.88f,1);box(i.x,.65f+bob,i.z,.26f,1,.26f,now*.04f,.20f,.24f,.28f,1);}else{mesh(cylinder,i.x,.58f+bob,i.z,.53f,.12f,.53f,now*.10f,90,0,.98f,.76f,.04f,1);mesh(cylinder,i.x,.58f+bob,i.z,.31f,.14f,.31f,now*.10f,90,0,.18f,.23f,.32f,1);}}}
    private void gate(long now){if(checkpoint>=GATES.length)return;float[] g=GATES[checkpoint];float pulse=.72f+.22f*(float)Math.sin(now*.006);box(g[0]-1.8f,1.7f,g[1],.22f,3.4f,.22f,0,.08f,.52f+pulse*.25f,1,1);box(g[0]+1.8f,1.7f,g[1],.22f,3.4f,.22f,0,.08f,.52f+pulse*.25f,1,1);box(g[0],3.35f,g[1],3.8f,.22f,.22f,0,.08f,.52f+pulse*.25f,1,1);box(g[0],3.65f,g[1],2,.18f,.18f,0,.98f,.82f,.08f,1);}

    private void player(float x,float y,float z,float r){
        shadow(x,y+.02f,z,.95f,.58f);float s=(float)Math.sin(walk)*20;
        part(cube,x,y,z,r,-.22f,.55f,0,.30f,1,.34f,s,.07f,.09f,.13f,1);part(cube,x,y,z,r,.22f,.55f,0,.30f,1,.34f,-s,.07f,.09f,.13f,1);
        part(cube,x,y,z,r,-.22f,.10f,.12f,.42f,.23f,.72f,0,.08f,.12f,.18f,1);part(cube,x,y,z,r,.22f,.10f,.12f,.42f,.23f,.72f,0,.08f,.12f,.18f,1);
        part(cube,x,y,z,r,0,1.06f,0,.92f,.52f,.60f,0,.05f,.07f,.10f,1);part(cube,x,y,z,r,0,1.72f,0,1.04f,1.18f,.60f,0,.10f,.30f,.72f,1);part(cube,x,y,z,r,0,1.82f,-.31f,.92f,.16f,.10f,0,.97f,.82f,.06f,1);
        part(cube,x,y,z,r,-.70f,1.72f,0,.28f,1.02f,.30f,-s,.08f,.25f,.62f,1);part(cube,x,y,z,r,.70f,1.72f,0,.28f,1.02f,.30f,s,.08f,.25f,.62f,1);
        part(sphere,x,y,z,r,0,2.80f,0,.83f,.95f,.80f,0,.98f,.80f,.64f,1);part(sphere,x,y,z,r,-.78f,2.80f,0,.17f,.23f,.14f,0,.97f,.75f,.58f,1);part(sphere,x,y,z,r,.78f,2.80f,0,.17f,.23f,.14f,0,.97f,.75f,.58f,1);
        part(sphere,x,y,z,r,-.27f,2.86f,-.71f,.09f,.09f,.08f,0,.18f,.52f,.90f,1);part(sphere,x,y,z,r,.27f,2.86f,-.71f,.09f,.09f,.08f,0,.18f,.52f,.90f,1);
        part(sphere,x,y,z,r,0,3.35f,0,.92f,.32f,.82f,0,.08f,.36f,.86f,1);part(cube,x,y,z,r,0,3.24f,-.72f,1.30f,.12f,.48f,0,.05f,.18f,.42f,1);part(cube,x,y,z,r,0,3.42f,-.06f,.75f,.08f,.74f,0,.98f,.82f,.07f,1);
    }
    private void seated(float x,float y,float z,float r){part(cube,x,y,z,r,0,1.15f,0,.96f,1.02f,.55f,0,.10f,.30f,.72f,1);part(cube,x,y,z,r,0,1.32f,-.30f,.86f,.13f,.08f,0,.98f,.82f,.07f,1);part(sphere,x,y,z,r,0,2.02f,0,.78f,.88f,.75f,0,.98f,.80f,.64f,1);part(sphere,x,y,z,r,0,2.50f,0,.88f,.28f,.78f,0,.08f,.36f,.86f,1);part(cube,x,y,z,r,0,2.40f,-.66f,1.22f,.11f,.42f,0,.05f,.18f,.42f,1);}
    private void kart(float x,float y,float z,float r){
        shadow(x,y+.02f,z,1.50f,.82f);part(cube,x,y,z,r,0,.42f,0,2.55f,.30f,1.45f,0,.05f,.08f,.13f,1);part(cube,x,y,z,r,0,.60f,-.55f,1.75f,.42f,.70f,-8,.08f,.30f,.78f,1);part(cube,x,y,z,r,0,.67f,-1.02f,1.20f,.40f,.45f,-16,.96f,.80f,.06f,1);
        part(cube,x,y,z,r,-1.32f,.47f,-.18f,.72f,.32f,1.08f,0,.06f,.26f,.72f,1);part(cube,x,y,z,r,1.32f,.47f,-.18f,.72f,.32f,1.08f,0,.06f,.26f,.72f,1);part(cube,x,y,z,r,-1.35f,.52f,-.18f,.76f,.09f,.62f,0,.98f,.80f,.05f,1);part(cube,x,y,z,r,1.35f,.52f,-.18f,.76f,.09f,.62f,0,.98f,.80f,.05f,1);
        part(cube,x,y,z,r,0,.93f,.48f,.92f,.84f,.62f,-10,.06f,.07f,.09f,1);part(cube,x,y,z,r,0,1.22f,1.18f,2.35f,.18f,.34f,0,.05f,.18f,.52f,1);part(cube,x,y,z,r,0,1.35f,1.19f,1.45f,.08f,.12f,0,.98f,.80f,.06f,1);
        wheel(x,y,z,r,-1.38f,.38f,-.86f);wheel(x,y,z,r,1.38f,.38f,-.86f);wheel(x,y,z,r,-1.38f,.38f,.88f);wheel(x,y,z,r,1.38f,.38f,.88f);
    }
    private void wheel(float x,float y,float z,float r,float ox,float oy,float oz){part(cylinder,x,y,z,r,ox,oy,oz,.57f,.27f,.57f,90,.025f,.028f,.032f,1);part(cylinder,x,y,z,r,ox,oy,oz,.30f,.29f,.30f,90,.95f,.72f,.05f,1);part(cylinder,x,y,z,r,ox,oy,oz,.13f,.31f,.13f,90,.12f,.13f,.15f,1);}

    private void shadow(float x,float y,float z,float sx,float sz){box(x,y,z,sx,.025f,sz,0,.02f,.025f,.03f,.28f);}
    private void part(Mesh me,float x,float y,float z,float parentYaw,float ox,float oy,float oz,float sx,float sy,float sz,float rx,float r,float g,float b,float a){float ang=parentYaw*DEG,xx=ox*(float)Math.cos(ang)+oz*(float)Math.sin(ang),zz=-ox*(float)Math.sin(ang)+oz*(float)Math.cos(ang);Matrix.setIdentityM(model,0);Matrix.translateM(model,0,x+xx,y+oy,z+zz);Matrix.rotateM(model,0,parentYaw,0,1,0);if(rx!=0)Matrix.rotateM(model,0,rx,1,0,0);Matrix.scaleM(model,0,sx,sy,sz);draw(me,r,g,b,a);}
    private void box(float x,float y,float z,float sx,float sy,float sz,float ry,float r,float g,float b,float a){mesh(cube,x,y,z,sx,sy,sz,ry,0,0,r,g,b,a);}
    private void mesh(Mesh me,float x,float y,float z,float sx,float sy,float sz,float ry,float rx,float rz,float r,float g,float b,float a){Matrix.setIdentityM(model,0);Matrix.translateM(model,0,x,y,z);if(ry!=0)Matrix.rotateM(model,0,ry,0,1,0);if(rx!=0)Matrix.rotateM(model,0,rx,1,0,0);if(rz!=0)Matrix.rotateM(model,0,rz,0,0,1);Matrix.scaleM(model,0,sx,sy,sz);draw(me,r,g,b,a);}
    private void draw(Mesh me,float r,float g,float b,float a){if(me==null)return;Matrix.multiplyMM(mvp,0,vp,0,model,0);GLES20.glUniformMatrix4fv(uMvp,1,false,mvp,0);GLES20.glUniform4f(uColor,r,g,b,a);me.draw(aPos);}

    private void show(String s,long ms){message=s;messageUntil=SystemClock.uptimeMillis()+ms;}
    public void setMove(float x,float y){moveX=x;moveY=y;} public void addCamera(float x,float y){camDx+=x;camDy+=y;} public void requestJump(){jumpReq=true;} public void requestAction(){actionReq=true;}
    public void setPaused(boolean p){paused=p;if(p)audio.stopEngine();else if(inKart)audio.startEngine();} public GameSnapshot getSnapshot(){return snap;} public void release(){audio.release();}
    public void resetGame(){stage=0;minerals=tools=coins=checkpoint=0;bestTime=-1;raceTime=0;inKart=false;kartSpeed=0;px=-18;py=0;pz=16;yaw=25;prefs.edit().clear().apply();populate();show("Nová hra – najdi 5 minerálů!",3000);}
    private static float dist2(float a,float b,float c,float d){float x=a-c,z=b-d;return x*x+z*z;} private static float clamp(float v,float lo,float hi){return Math.max(lo,Math.min(hi,v));} private static float shortest(float a){while(a>180)a-=360;while(a<-180)a+=360;return a;}

    private static int buildProgram(String vs,String fs){try{int v=compile(GLES20.GL_VERTEX_SHADER,vs),f=compile(GLES20.GL_FRAGMENT_SHADER,fs);if(v==0||f==0)return 0;int p=GLES20.glCreateProgram();GLES20.glAttachShader(p,v);GLES20.glAttachShader(p,f);GLES20.glLinkProgram(p);int[] ok=new int[1];GLES20.glGetProgramiv(p,GLES20.GL_LINK_STATUS,ok,0);GLES20.glDeleteShader(v);GLES20.glDeleteShader(f);if(ok[0]==0){GLES20.glDeleteProgram(p);return 0;}return p;}catch(Throwable t){return 0;}}
    private static int compile(int type,String src){try{int s=GLES20.glCreateShader(type);GLES20.glShaderSource(s,src);GLES20.glCompileShader(s);int[] ok=new int[1];GLES20.glGetShaderiv(s,GLES20.GL_COMPILE_STATUS,ok,0);if(ok[0]==0){GLES20.glDeleteShader(s);return 0;}return s;}catch(Throwable t){return 0;}}
    private static final String VS="uniform mat4 uMVP;attribute vec3 aPosition;void main(){gl_Position=uMVP*vec4(aPosition,1.0);}";
    private static final String FS="precision mediump float;uniform vec4 uColor;void main(){gl_FragColor=uColor;}";

    private static final class Mesh{
        final FloatBuffer v;final int n;
        Mesh(float[] a){v=ByteBuffer.allocateDirect(a.length*4).order(ByteOrder.nativeOrder()).asFloatBuffer();v.put(a).position(0);n=a.length/3;}
        void draw(int p){v.position(0);GLES20.glVertexAttribPointer(p,3,GLES20.GL_FLOAT,false,12,v);GLES20.glEnableVertexAttribArray(p);GLES20.glDrawArrays(GLES20.GL_TRIANGLES,0,n);GLES20.glDisableVertexAttribArray(p);}
        static Mesh cube(){float n=-.5f,p=.5f;return new Mesh(new float[]{n,n,p,p,n,p,p,p,p,n,n,p,p,p,p,n,p,p,p,n,n,n,n,n,n,p,n,p,n,n,n,p,n,p,p,n,n,n,n,n,n,p,n,p,p,n,n,n,n,p,p,n,p,n,p,n,n,p,n,n,p,p,n,p,n,p,n,p,n,p,p,p,n,p,p,p,p,p,p,p,p,n,n,p,p,p,p,n,n,p,n,n,n,n,p,n,n,p,n,p,n,p,n,n,n,p,n,p,n,n,p});}
        static Mesh sphere(int st,int sl){ArrayList<Float> o=new ArrayList<>();for(int i=0;i<st;i++){float p0=((float)i/st-.5f)*(float)Math.PI,p1=((float)(i+1)/st-.5f)*(float)Math.PI;for(int j=0;j<sl;j++){float t0=(float)j/sl*(float)Math.PI*2,t1=(float)(j+1)/sl*(float)Math.PI*2;sv(o,p0,t0);sv(o,p0,t1);sv(o,p1,t1);sv(o,p0,t0);sv(o,p1,t1);sv(o,p1,t0);}}float[] a=new float[o.size()];for(int i=0;i<a.length;i++)a[i]=o.get(i);return new Mesh(a);}static void sv(ArrayList<Float>o,float p,float t){float c=(float)Math.cos(p);o.add(c*(float)Math.sin(t)*.5f);o.add((float)Math.sin(p)*.5f);o.add(c*(float)Math.cos(t)*.5f);}
        static Mesh cylinder(int sl){ArrayList<Float>o=new ArrayList<>();for(int i=0;i<sl;i++){float a0=i*(float)Math.PI*2/sl,a1=(i+1)*(float)Math.PI*2/sl,x0=(float)Math.sin(a0)*.5f,z0=(float)Math.cos(a0)*.5f,x1=(float)Math.sin(a1)*.5f,z1=(float)Math.cos(a1)*.5f;av(o,x0,-.5f,z0);av(o,x1,-.5f,z1);av(o,x1,.5f,z1);av(o,x0,-.5f,z0);av(o,x1,.5f,z1);av(o,x0,.5f,z0);av(o,0,.5f,0);av(o,x1,.5f,z1);av(o,x0,.5f,z0);av(o,0,-.5f,0);av(o,x0,-.5f,z0);av(o,x1,-.5f,z1);}float[]a=new float[o.size()];for(int i=0;i<a.length;i++)a[i]=o.get(i);return new Mesh(a);}static void av(ArrayList<Float>o,float x,float y,float z){o.add(x);o.add(y);o.add(z);}
    }
}
