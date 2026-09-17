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

/**
 * Vítek závodník 2.0 renderer.
 *
 * A lightweight semi-realistic OpenGL ES 2.0 renderer built for Android phones.
 * It uses smooth procedural meshes, directional lighting, specular highlights,
 * fog, soft fake shadows, detailed character/kart geometry and a denser world.
 */
public final class UltraRenderer implements GLSurfaceView.Renderer {
    private static final float DEG = (float) (Math.PI / 180.0);

    private final SharedPreferences prefs;
    private final AudioEngine audio;
    private final List<Item> items = new ArrayList<>();

    private int program;
    private int aPos, aNormal;
    private int uMvp, uModel, uColor, uLightDir, uCamPos, uFogColor, uShine;
    private boolean glReady;

    private Mesh cube, sphere, cylinder, roof;

    private final float[] proj = new float[16];
    private final float[] view = new float[16];
    private final float[] vp = new float[16];
    private final float[] model = new float[16];
    private final float[] mvp = new float[16];

    private volatile float moveX, moveY, camDx, camDy;
    private volatile boolean jumpReq, actionReq, paused = true;

    private float px = -18f, py = 0f, pz = 16f;
    private float yaw = 25f, vY = 0f, walk = 0f;
    private float camYaw = 35f, camPitch = 17f, camDist = 8.6f;
    private boolean grounded = true, inKart = false;
    private float kartSpeed = 0f, raceTime = 0f, bestTime = -1f;
    private int stage = 0, minerals = 0, tools = 0, coins = 0, checkpoint = 0;
    private long lastMs, messageUntil;
    private String message = "";

    private volatile GameSnapshot snapshot = new GameSnapshot(0,0,0,0,false,0,8,0,-1,"Vítej!");

    private static final class Item {
        static final int MINERAL = 0, TOOL = 1, COIN = 2;
        final int type;
        final float x, z;
        boolean active = true;
        Item(int type, float x, float z){ this.type=type; this.x=x; this.z=z; }
    }

    private static final float[][] GATES = {
            {-12,10},{-2,8},{10,5},{17,-3},{12,-14},{0,-18},{-13,-12},{-20,0}
    };

    public UltraRenderer(Context context){
        Context app = context.getApplicationContext();
        prefs = app.getSharedPreferences("vitek_zavodnik_save", Context.MODE_PRIVATE);
        audio = new AudioEngine(app);
        stage = prefs.getInt("stage",0);
        coins = prefs.getInt("coins",0);
        bestTime = prefs.getFloat("best",-1f);
        if(stage>=1) minerals=5;
        if(stage>=2) tools=3;
        populate();
        show("Najdi 5 minerálů!",3000);
    }

    private void populate(){
        items.clear();
        float[][] ms={{-14,4},{-7,-5},{2,-12},{13,-9},{16,10}};
        for(float[] p:ms) items.add(new Item(Item.MINERAL,p[0],p[1]));
        float[][] ts={{-4,15},{8,13},{18,2}};
        for(float[] p:ts) items.add(new Item(Item.TOOL,p[0],p[1]));
        for(int i=0;i<32;i++){
            double a=i*1.31;
            float r=6f+(i%6)*3.0f;
            items.add(new Item(Item.COIN,(float)Math.cos(a)*r,(float)Math.sin(a)*r));
        }
        if(stage>=1) for(Item i:items) if(i.type==Item.MINERAL) i.active=false;
        if(stage>=2) for(Item i:items) if(i.type==Item.TOOL) i.active=false;
        updateSnapshot(SystemClock.uptimeMillis());
    }

    @Override public void onSurfaceCreated(GL10 gl, EGLConfig cfg){
        try{
            GLES20.glClearColor(.47f,.72f,.93f,1f);
            GLES20.glEnable(GLES20.GL_DEPTH_TEST);
            GLES20.glDepthFunc(GLES20.GL_LEQUAL);
            GLES20.glEnable(GLES20.GL_BLEND);
            GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA,GLES20.GL_ONE_MINUS_SRC_ALPHA);
            GLES20.glDisable(GLES20.GL_CULL_FACE);

            program=program(VS,FS);
            if(program==0){ glReady=false; return; }
            aPos=GLES20.glGetAttribLocation(program,"aPosition");
            aNormal=GLES20.glGetAttribLocation(program,"aNormal");
            uMvp=GLES20.glGetUniformLocation(program,"uMVP");
            uModel=GLES20.glGetUniformLocation(program,"uModel");
            uColor=GLES20.glGetUniformLocation(program,"uColor");
            uLightDir=GLES20.glGetUniformLocation(program,"uLightDir");
            uCamPos=GLES20.glGetUniformLocation(program,"uCameraPos");
            uFogColor=GLES20.glGetUniformLocation(program,"uFogColor");
            uShine=GLES20.glGetUniformLocation(program,"uShine");

            cube=Mesh.cube();
            sphere=Mesh.sphere(12,18);
            cylinder=Mesh.cylinder(18);
            roof=Mesh.roof();
            glReady=aPos>=0 && aNormal>=0 && uMvp>=0 && uModel>=0 && uColor>=0;
            lastMs=SystemClock.uptimeMillis();
        }catch(Throwable t){ glReady=false; }
    }

    @Override public void onSurfaceChanged(GL10 gl,int w,int h){
        try{
            int hh=Math.max(1,h);
            GLES20.glViewport(0,0,w,hh);
            Matrix.perspectiveM(proj,0,55f,(float)w/hh,.1f,150f);
        }catch(Throwable t){ glReady=false; }
    }

    @Override public void onDrawFrame(GL10 gl){
        try{
            long now=SystemClock.uptimeMillis();
            if(lastMs==0)lastMs=now;
            float dt=Math.min(.033f,Math.max(.001f,(now-lastMs)/1000f));
            lastMs=now;
            if(!paused) update(dt,now);

            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT|GLES20.GL_DEPTH_BUFFER_BIT);
            if(!glReady || program==0) return;
            GLES20.glUseProgram(program);

            float cr=camYaw*DEG, pr=camPitch*DEG;
            float targetY=py+(inKart?1.05f:1.75f);
            float ex=px-(float)Math.sin(cr)*camDist*(float)Math.cos(pr);
            float ez=pz-(float)Math.cos(cr)*camDist*(float)Math.cos(pr);
            float ey=targetY+camDist*(float)Math.sin(pr);
            Matrix.setLookAtM(view,0,ex,ey,ez,px,targetY,pz,0,1,0);
            Matrix.multiplyMM(vp,0,proj,0,view,0);

            GLES20.glUniform3f(uLightDir,-.42f,.84f,.31f);
            GLES20.glUniform3f(uCamPos,ex,ey,ez);
            GLES20.glUniform4f(uFogColor,.47f,.72f,.93f,1f);

            drawWorld(now);
            drawCollectibles(now);
            if(inKart){
                drawKart(px,py,pz,yaw,true);
                drawSeatedPlayer(px,py,pz,yaw);
            }else{
                drawPlayer(px,py,pz,yaw);
                drawKart(-18,0,16,25,false);
            }
            if(stage==2 && inKart) drawCheckpoint(now);
        }catch(Throwable t){
            // Keep the app alive even on quirky GPU drivers.
        }
    }

    private void update(float dt,long now){
        camYaw+=camDx*.18f;
        camPitch=clamp(camPitch+camDy*.12f,8f,34f);
        camDx=camDy=0f;

        if(actionReq){ actionReq=false; action(); }

        if(!inKart){
            float mag=(float)Math.sqrt(moveX*moveX+moveY*moveY);
            if(mag>.08f){
                // Correct camera-relative direct joystick mapping:
                // right = right on screen, up = forward on screen.
                float mx=moveX/Math.max(1f,mag);
                float fw=-moveY/Math.max(1f,mag);
                float a=camYaw*DEG;
                float dx=mx*(float)Math.cos(a)+fw*(float)Math.sin(a);
                float dz=-mx*(float)Math.sin(a)+fw*(float)Math.cos(a);
                px+=dx*5.3f*dt;
                pz+=dz*5.3f*dt;
                yaw=(float)Math.toDegrees(Math.atan2(dx,dz));
                walk+=dt*8.8f*mag;
            }
            if(jumpReq&&grounded){ grounded=false; vY=5.5f; audio.jump(); }
            jumpReq=false;
            if(!grounded){
                vY-=12.5f*dt; py+=vY*dt;
                if(py<=0){py=0;vY=0;grounded=true;}
            }
        }else{
            jumpReq=false;
            float throttle=clamp(-moveY,-1,1);
            float steer=clamp(moveX,-1,1);
            kartSpeed+=throttle*(throttle>=0?9.8f:12.5f)*dt;
            kartSpeed*=(float)Math.pow(.46f,dt);
            kartSpeed=clamp(kartSpeed,-3.2f,11.5f);
            if(Math.abs(kartSpeed)>.12f)
                yaw+=steer*(57f+Math.abs(kartSpeed)*2.1f)*dt*Math.signum(kartSpeed);
            float a=yaw*DEG;
            px+=(float)Math.sin(a)*kartSpeed*dt;
            pz+=(float)Math.cos(a)*kartSpeed*dt;
            camYaw+=shortest(yaw-camYaw)*dt*1.25f;
            audio.setEnginePitch(Math.abs(kartSpeed)/11.5f);
            if(stage==2){
                raceTime+=dt;
                if(raceTime>75f){ show("Čas vypršel – zkus to znovu!",2500); resetRace(); }
            }
        }

        px=clamp(px,-29f,29f);
        pz=clamp(pz,-29f,29f);
        collect();
        if(stage==2&&inKart) race();
        updateSnapshot(now);
    }

    private void action(){
        float d=dist2(px,pz,-18,16);
        if(!inKart&&stage>=2&&d<10f){
            inKart=true;py=0;kartSpeed=0;audio.startEngine();
            if(stage==2){checkpoint=0;raceTime=0;show("Závod! Projeď 8 modrými bránami.",2500);} return;
        }
        if(inKart&&stage>=3){inKart=false;kartSpeed=0;audio.stopEngine();show("Volná jízda dokončena.",1500);}
    }

    private void collect(){
        for(Item i:items){
            if(!i.active)continue;
            if(i.type==Item.MINERAL&&stage!=0)continue;
            if(i.type==Item.TOOL&&stage!=1)continue;
            if(dist2(px,pz,i.x,i.z)>2.1f)continue;
            i.active=false;audio.collect();
            if(i.type==Item.MINERAL){
                minerals++;show("Minerál "+minerals+"/5",1000);
                if(minerals>=5){stage=1;save();audio.success();show("Super! Najdi 3 díly pro mechanika.",2900);}
            }else if(i.type==Item.TOOL){
                tools++;show("Díl "+tools+"/3",1000);
                if(tools>=3){stage=2;save();audio.success();show("Motokára čeká v depu!",2900);}
            }else{
                coins++;prefs.edit().putInt("coins",coins).apply();
            }
        }
    }

    private void race(){
        if(checkpoint>=GATES.length)return;
        float[] g=GATES[checkpoint];
        if(dist2(px,pz,g[0],g[1])<9f){
            checkpoint++;audio.collect();
            if(checkpoint>=GATES.length){
                stage=3;inKart=false;audio.stopEngine();
                if(bestTime<0||raceTime<bestTime)bestTime=raceTime;
                prefs.edit().putInt("stage",stage).putFloat("best",bestTime).putInt("coins",coins).apply();
                audio.success();show("CÍL! Výborná jízda!",4300);
            }else show("Brána "+checkpoint+"/8",850);
        }
    }

    private void resetRace(){checkpoint=0;raceTime=0;px=-18;pz=16;yaw=25;kartSpeed=0;}
    private void save(){prefs.edit().putInt("stage",stage).putInt("coins",coins).apply();}
    private void updateSnapshot(long now){snapshot=new GameSnapshot(stage,minerals,tools,coins,inKart,checkpoint,GATES.length,raceTime,bestTime,now<messageUntil?message:"");}

    // WORLD -----------------------------------------------------------------

    private void drawWorld(long now){
        // Ground layers
        draw(cube,0,-.62f,0,66,1,66,0,0,0,.18f,.47f,.21f,1,.1f);
        draw(cube,0,-.54f,0,61,0.08f,61,0,0,0,.27f,.62f,.29f,1,.1f);

        // Distant hills for a more natural horizon
        for(int i=0;i<18;i++){
            double a=i*.35;
            float r=43f+(i%4)*3.2f;
            float x=(float)Math.cos(a)*r,z=(float)Math.sin(a)*r;
            draw(sphere,x,3.5f,z,8f,5f,8f,0,0,0,.20f+.015f*(i%3),.42f,.23f,1,.05f);
        }

        drawRoad(0,0,6.8f,55,0);
        drawRoad(0,0,48,5.8f,90);
        drawRoad(-15,7,26,4.2f,34);
        drawRoad(15,-8,24,4.2f,-38);

        // Depot apron and pit lane
        draw(cube,-18,-.015f,14,11.5f,.09f,8.5f,0,0,0,.13f,.15f,.18f,1,.25f);
        drawPitCurbs();

        drawDepot();
        drawGrandstand();
        drawHouse(-20,-12,.76f,.26f,.14f,0);
        drawHouse(18,-12,.12f,.48f,.82f,1);
        drawHouse(18,13,.88f,.57f,.10f,2);
        drawHouse(-3,-20,.66f,.28f,.62f,3);

        // Tree belt
        for(int i=-27;i<=27;i+=6){
            tree(i,-28,1f+((i+30)%5)*.03f);
            tree(i,28,.95f+((i+12)%4)*.04f);
            if(i%12==0){tree(-28,i,1.04f);tree(28,i,.98f);}
        }

        // Quarry / rocks
        for(int i=0;i<18;i++){
            double a=i*.71;
            float rr=21+(i%4)*2.4f;
            float x=(float)Math.cos(a)*rr,z=(float)Math.sin(a)*rr;
            draw(sphere,x,.7f,z,1.5f+(i%3)*.4f,.8f+(i%2)*.3f,1.4f+(i%4)*.25f,i*17,0,0,.39f,.36f,.31f,1,.05f);
        }

        // Banners, cones, safety barriers
        for(int i=-20;i<=20;i+=8){ banner(i,-4.6f,0); banner(i,4.6f,180); }
        for(int i=-20;i<=20;i+=5) cone(i,7.1f);
        barrier(-5,-6.2f,10,0);
        barrier(10,6.2f,9,180);

        // Sun disc (far object)
        draw(sphere,34,28,-45,4.5f,4.5f,4.5f,0,0,0,1.0f,.86f,.46f,1,.4f);
    }

    private void drawRoad(float x,float z,float sx,float sz,float rot){
        draw(cube,x,-.025f,z,sx,.08f,sz,rot,0,0,.12f,.14f,.17f,1,.28f);
        // road shoulders
        if(Math.abs(rot)<45){
            draw(cube,x-3.6f,.015f,z,.18f,.10f,sz,rot,0,0,.75f,.76f,.72f,1,.1f);
            draw(cube,x+3.6f,.015f,z,.18f,.10f,sz,rot,0,0,.75f,.76f,.72f,1,.1f);
            for(int i=-5;i<=5;i++){
                float zz=z+i*4.6f;
                draw(cube,x,.035f,zz,.15f,.035f,1.9f,rot,0,0,.96f,.77f,.06f,1,.15f);
            }
        }else{
            draw(cube,x,.015f,z-3.1f,sx,.10f,.18f,rot,0,0,.75f,.76f,.72f,1,.1f);
            draw(cube,x,.015f,z+3.1f,sx,.10f,.18f,rot,0,0,.75f,.76f,.72f,1,.1f);
            for(int i=-5;i<=5;i++){
                float xx=x+i*4.4f;
                draw(cube,xx,.035f,z,1.9f,.035f,.15f,rot,0,0,.96f,.77f,.06f,1,.15f);
            }
        }
    }

    private void drawPitCurbs(){
        for(int i=0;i<18;i++){
            float zz=6.5f+i*.85f;
            float r=(i%2==0)?1f:.95f, g=(i%2==0)?.12f:.95f, b=(i%2==0)?.08f:.95f;
            draw(cube,-24.0f,.08f,zz,.28f,.12f,.45f,0,0,0,r,g,b,1,.05f);
        }
    }

    private void drawDepot(){
        // structure
        draw(cube,-18,1.55f,20.5f,10.8f,3.0f,5.8f,0,0,0,.065f,.085f,.11f,1,.5f);
        draw(cube,-18,3.25f,20.5f,11.2f,.32f,6.1f,0,0,0,.025f,.035f,.05f,1,.65f);
        // blue facade
        draw(cube,-18,3.05f,17.47f,10.4f,.65f,.12f,0,0,0,.025f,.23f,.67f,1,.65f);
        draw(cube,-18,3.05f,17.34f,6.4f,.14f,.04f,0,0,0,1f,.80f,.04f,1,.35f);
        // garage bays
        for(int i=0;i<3;i++){
            float bx=-21.4f+i*3.4f;
            draw(cube,bx,1.35f,17.42f,2.75f,2.35f,.16f,0,0,0,.018f,.022f,.028f,1,.7f);
            draw(cube,bx,2.72f,17.25f,2.9f,.28f,.10f,0,0,0,.08f,.33f,.82f,1,.65f);
            draw(cube,bx,1.55f,17.20f,.08f,1.9f,.04f,0,0,0,.85f,.87f,.90f,1,.35f);
        }
        // tyres / crates
        for(int i=0;i<5;i++){
            draw(cylinder,-23.2f+i*.62f,.40f,15.4f,.48f,.28f,.48f,0,90,0,.025f,.028f,.032f,1,.75f);
        }
        draw(cube,-13.5f,.50f,15.6f,1.45f,1.0f,1.05f,0,0,0,.84f,.29f,.035f,1,.3f);
        draw(cube,-13.5f,1.05f,15.6f,1.20f,.08f,.85f,0,0,0,.98f,.78f,.04f,1,.25f);
    }

    private void drawGrandstand(){
        float x=21,z=20;
        draw(cube,x,.45f,z,8.5f,.8f,4.0f,-8,0,0,.12f,.15f,.20f,1,.35f);
        for(int i=0;i<5;i++){
            float rr=(i%2==0)?.05f:.95f, gg=(i%2==0)?.30f:.76f, bb=(i%2==0)?.76f:.05f;
            draw(cube,x,.8f+i*.42f,z+1.3f-i*.65f,8.0f,.20f,.58f,-8,0,0,rr,gg,bb,1,.25f);
        }
        draw(cube,x,3.1f,z-1.9f,9.2f,.18f,.20f,-8,0,0,.05f,.06f,.08f,1,.5f);
        for(int i=-4;i<=4;i+=2) draw(cylinder,x+i,1.6f,z-1.75f,.10f,3.1f,.10f,0,0,0,.22f,.24f,.27f,1,.3f);
    }

    private void drawHouse(float x,float z,float r,float g,float b,int variant){
        draw(cube,x,1.25f,z,4.6f,2.5f,3.8f,0,0,0,r,g,b,1,.22f);
        draw(roof,x,3.10f,z,5.0f,1.7f,4.2f,0,0,0,.25f,.12f,.07f,1,.18f);
        draw(cube,x,.98f,z-1.93f,1.05f,1.95f,.13f,0,0,0,.12f,.18f,.26f,1,.45f);
        for(int s=-1;s<=1;s+=2){
            draw(cube,x+s*1.35f,1.65f,z-1.95f,.86f,.76f,.10f,0,0,0,.30f,.67f,.92f,1,.65f);
            draw(cube,x+s*1.35f,1.65f,z-2.01f,.07f,.80f,.025f,0,0,0,.92f,.94f,.96f,1,.25f);
            draw(cube,x+s*1.35f,1.65f,z-2.01f,.90f,.07f,.025f,0,0,0,.92f,.94f,.96f,1,.25f);
        }
        if(variant%2==0) draw(cube,x,2.73f,z-2.02f,2.3f,.26f,.10f,0,0,0,.96f,.77f,.05f,1,.3f);
    }

    private void tree(float x,float z,float s){
        draw(cylinder,x,.95f*s,z,.38f*s,1.9f*s,.38f*s,0,0,0,.28f,.15f,.065f,1,.08f);
        draw(sphere,x,2.60f*s,z,1.55f*s,1.70f*s,1.55f*s,0,0,0,.07f,.37f,.13f,1,.03f);
        draw(sphere,x-.85f*s,2.45f*s,z+.15f,1.15f*s,1.28f*s,1.15f*s,0,0,0,.09f,.45f,.16f,1,.03f);
        draw(sphere,x+.82f*s,2.48f*s,z-.12f,1.10f*s,1.22f*s,1.10f*s,0,0,0,.055f,.31f,.11f,1,.03f);
    }

    private void banner(float x,float z,float rot){
        draw(cylinder,x-.95f,.9f,z,.10f,1.8f,.10f,rot,0,0,.10f,.11f,.13f,1,.25f);
        draw(cylinder,x+.95f,.9f,z,.10f,1.8f,.10f,rot,0,0,.10f,.11f,.13f,1,.25f);
        draw(cube,x,1.45f,z,1.9f,.62f,.08f,rot,0,0,.04f,.27f,.75f,1,.55f);
        draw(cube,x,1.45f,z-.05f,1.28f,.11f,.03f,rot,0,0,.98f,.79f,.04f,1,.28f);
    }

    private void cone(float x,float z){
        draw(cylinder,x,.08f,z,.50f,.10f,.50f,0,0,0,.05f,.05f,.055f,1,.4f);
        draw(cylinder,x,.46f,z,.27f,.72f,.27f,0,0,0,.95f,.25f,.02f,1,.18f);
        draw(cylinder,x,.52f,z,.285f,.14f,.285f,0,0,0,.94f,.94f,.94f,1,.2f);
    }

    private void barrier(float x,float z,int count,float rot){
        for(int i=0;i<count;i++){
            float xx=x+(rot==0?i*1.1f:0), zz=z+(rot==0?0:i*1.1f);
            float r=(i%2==0)?.95f:.92f, g=(i%2==0)?.08f:.92f, b=(i%2==0)?.06f:.92f;
            draw(cube,xx,.34f,zz,1.05f,.65f,.30f,rot,0,0,r,g,b,1,.15f);
        }
    }

    // COLLECTIBLES -----------------------------------------------------------

    private void drawCollectibles(long now){
        float bob=(float)Math.sin(now*.004)*.16f;
        for(Item i:items){
            if(!i.active)continue;
            if(i.type==Item.MINERAL&&stage!=0)continue;
            if(i.type==Item.TOOL&&stage!=1)continue;
            shadow(i.x,.03f,i.z,.75f,.55f);
            if(i.type==Item.MINERAL){
                draw(sphere,i.x,.72f+bob,i.z,.48f,1.08f,.48f,now*.06f,18,0,.47f,.16f,.82f,1,.55f);
                draw(sphere,i.x,.98f+bob,i.z,.28f,.62f,.28f,-now*.08f,0,25,.75f,.44f,.96f,1,.65f);
            }else if(i.type==Item.TOOL){
                draw(cube,i.x,.62f+bob,i.z,1.05f,.20f,.20f,now*.04f,0,0,.72f,.75f,.80f,1,.6f);
                draw(cube,i.x,.62f+bob,i.z,.20f,1.0f,.20f,now*.04f,0,0,.18f,.20f,.23f,1,.35f);
            }else{
                draw(cylinder,i.x,.58f+bob,i.z,.52f,.10f,.52f,now*.10f,90,0,.98f,.72f,.025f,1,.85f);
                draw(cylinder,i.x,.58f+bob,i.z,.29f,.12f,.29f,now*.10f,90,0,.11f,.15f,.22f,1,.65f);
            }
        }
    }

    private void drawCheckpoint(long now){
        if(checkpoint>=GATES.length)return;
        float[] g=GATES[checkpoint];
        float pulse=.78f+.18f*(float)Math.sin(now*.006);
        float blue=.72f+.20f*pulse;
        draw(cube,g[0]-1.8f,1.7f,g[1],.22f,3.4f,.22f,0,0,0,.03f,blue,1f,1,.75f);
        draw(cube,g[0]+1.8f,1.7f,g[1],.22f,3.4f,.22f,0,0,0,.03f,blue,1f,1,.75f);
        draw(cube,g[0],3.35f,g[1],3.8f,.22f,.22f,0,0,0,.03f,blue,1f,1,.75f);
        draw(cube,g[0],3.67f,g[1],2.1f,.15f,.15f,0,0,0,.98f,.78f,.04f,1,.45f);
    }

    // CHARACTER --------------------------------------------------------------

    private void drawPlayer(float x,float y,float z,float r){
        shadow(x,y+.025f,z,1.0f,.62f);
        float swing=(float)Math.sin(walk)*22f;

        // legs
        part(cylinder,x,y,z,r,-.22f,.63f,0,.23f,1.02f,.23f,swing,0,.96f,.75f,.57f,1,.18f);
        part(cylinder,x,y,z,r, .22f,.63f,0,.23f,1.02f,.23f,-swing,0,.96f,.75f,.57f,1,.18f);
        // shorts
        part(cube,x,y,z,r,-.26f,1.17f,0,.48f,.54f,.58f,0,0,.035f,.045f,.065f,1,.25f);
        part(cube,x,y,z,r, .26f,1.17f,0,.48f,.54f,.58f,0,0,.035f,.045f,.065f,1,.25f);
        // shoes
        part(cube,x,y,z,r,-.22f,.10f,-.15f,.42f,.22f,.72f,0,0,.055f,.08f,.12f,1,.38f);
        part(cube,x,y,z,r, .22f,.10f,-.15f,.42f,.22f,.72f,0,0,.055f,.08f,.12f,1,.38f);
        part(cube,x,y,z,r,-.22f,.13f,-.47f,.35f,.10f,.22f,0,0,.95f,.76f,.035f,1,.45f);
        part(cube,x,y,z,r, .22f,.13f,-.47f,.35f,.10f,.22f,0,0,.95f,.76f,.035f,1,.45f);

        // torso – rounded child proportions
        part(sphere,x,y,z,r,0,1.76f,0,.92f,1.12f,.52f,0,0,.06f,.24f,.63f,1,.48f);
        // black side panels
        part(cube,x,y,z,r,-.66f,1.75f,.02f,.20f,.98f,.46f,0,0,.025f,.035f,.055f,1,.30f);
        part(cube,x,y,z,r, .66f,1.75f,.02f,.20f,.98f,.46f,0,0,.025f,.035f,.055f,1,.30f);
        // yellow racing stripe
        part(cube,x,y,z,r,0,1.83f,-.49f,.84f,.10f,.035f,-12,0,.98f,.80f,.035f,1,.42f);

        // arms
        part(cylinder,x,y,z,r,-.74f,1.72f,0,.19f,.92f,.19f,-swing,0,.95f,.74f,.56f,1,.14f);
        part(cylinder,x,y,z,r, .74f,1.72f,0,.19f,.92f,.19f, swing,0,.95f,.74f,.56f,1,.14f);
        part(sphere,x,y,z,r,-.74f,1.23f,.02f,.24f,.24f,.24f,0,0,.97f,.77f,.59f,1,.22f);
        part(sphere,x,y,z,r, .74f,1.23f,.02f,.24f,.24f,.24f,0,0,.97f,.77f,.59f,1,.22f);

        // neck and head
        part(cylinder,x,y,z,r,0,2.38f,0,.26f,.28f,.26f,0,0,.96f,.75f,.58f,1,.12f);
        part(sphere,x,y,z,r,0,2.88f,0,.75f,.90f,.72f,0,0,.98f,.80f,.63f,1,.20f);
        // ears
        part(sphere,x,y,z,r,-.72f,2.88f,0,.14f,.22f,.12f,0,0,.97f,.76f,.59f,1,.15f);
        part(sphere,x,y,z,r, .72f,2.88f,0,.14f,.22f,.12f,0,0,.97f,.76f,.59f,1,.15f);
        // nose
        part(sphere,x,y,z,r,0,2.84f,-.69f,.11f,.14f,.11f,0,0,.97f,.76f,.59f,1,.18f);
        // eyes whites and blue irises
        eye(x,y,z,r,-.26f,2.96f,-.63f);
        eye(x,y,z,r, .26f,2.96f,-.63f);
        // subtle mouth
        part(cube,x,y,z,r,0,2.55f,-.69f,.24f,.035f,.035f,0,0,.48f,.15f,.12f,1,.2f);

        // hair peeking out
        for(int i=-2;i<=2;i++){
            part(sphere,x,y,z,r,i*.23f,3.42f,-.08f,.22f,.15f,.20f,0,0,.76f,.62f,.28f,1,.12f);
        }
        // cap
        part(sphere,x,y,z,r,0,3.38f,0,.86f,.30f,.78f,0,0,.025f,.28f,.80f,1,.55f);
        part(cube,x,y,z,r,0,3.27f,-.70f,1.22f,.10f,.42f,0,0,.018f,.12f,.34f,1,.65f);
        part(cube,x,y,z,r,0,3.45f,-.02f,.66f,.05f,.68f,0,0,.98f,.80f,.035f,1,.35f);
    }

    private void eye(float x,float y,float z,float r,float ox,float oy,float oz){
        part(sphere,x,y,z,r,ox,oy,oz,.105f,.105f,.075f,0,0,.97f,.98f,1f,1,.65f);
        part(sphere,x,y,z,r,ox,oy-.005f,oz-.065f,.055f,.055f,.045f,0,0,.12f,.43f,.82f,1,.75f);
        part(sphere,x,y,z,r,ox,oy-.005f,oz-.096f,.025f,.025f,.022f,0,0,.01f,.015f,.02f,1,.2f);
    }

    private void drawSeatedPlayer(float x,float y,float z,float r){
        part(sphere,x,y,z,r,0,1.20f,.10f,.85f,.92f,.48f,0,0,.06f,.24f,.63f,1,.48f);
        part(cube,x,y,z,r,0,1.28f,-.39f,.76f,.09f,.03f,-12,0,.98f,.80f,.035f,1,.42f);
        part(sphere,x,y,z,r,0,2.03f,.02f,.70f,.82f,.68f,0,0,.98f,.80f,.63f,1,.20f);
        eye(x,y,z,r,-.24f,2.10f,-.58f);
        eye(x,y,z,r, .24f,2.10f,-.58f);
        for(int i=-2;i<=2;i++) part(sphere,x,y,z,r,i*.20f,2.52f,-.06f,.20f,.13f,.18f,0,0,.76f,.62f,.28f,1,.12f);
        part(sphere,x,y,z,r,0,2.50f,0,.80f,.27f,.72f,0,0,.025f,.28f,.80f,1,.55f);
        part(cube,x,y,z,r,0,2.40f,-.63f,1.12f,.09f,.38f,0,0,.018f,.12f,.34f,1,.65f);
        // bent arms to wheel
        part(cylinder,x,y,z,r,-.57f,1.42f,-.34f,.18f,.70f,.18f,-48,0,.95f,.74f,.56f,1,.14f);
        part(cylinder,x,y,z,r, .57f,1.42f,-.34f,.18f,.70f,.18f,-48,0,.95f,.74f,.56f,1,.14f);
    }

    // KART -------------------------------------------------------------------

    private void drawKart(float x,float y,float z,float r,boolean moving){
        shadow(x,y+.025f,z,1.55f,.86f);
        // chassis / floor
        part(cube,x,y,z,r,0,.38f,0,2.62f,.26f,1.50f,0,0,.035f,.055f,.095f,1,.65f);
        // front fairing rounded with sphere + shell
        part(sphere,x,y,z,r,0,.68f,-.78f,1.20f,.46f,.72f,0,0,.035f,.24f,.76f,1,.78f);
        part(cube,x,y,z,r,0,.62f,-1.18f,1.18f,.36f,.44f,-14,0,.96f,.79f,.035f,1,.62f);
        // number plate
        part(cube,x,y,z,r,0,.84f,-1.40f,.56f,.07f,.30f,-16,0,.04f,.05f,.07f,1,.72f);
        // side pods
        part(sphere,x,y,z,r,-1.30f,.50f,-.12f,.72f,.34f,1.06f,0,0,.04f,.22f,.68f,1,.72f);
        part(sphere,x,y,z,r, 1.30f,.50f,-.12f,.72f,.34f,1.06f,0,0,.04f,.22f,.68f,1,.72f);
        part(cube,x,y,z,r,-1.30f,.53f,-.20f,.78f,.07f,.62f,0,0,.97f,.79f,.035f,1,.45f);
        part(cube,x,y,z,r, 1.30f,.53f,-.20f,.78f,.07f,.62f,0,0,.97f,.79f,.035f,1,.45f);
        // rear body and seat
        part(cube,x,y,z,r,0,.67f,.64f,1.15f,.50f,.78f,0,0,.035f,.055f,.085f,1,.55f);
        part(sphere,x,y,z,r,0,.95f,.54f,.78f,.78f,.55f,0,0,.025f,.028f,.035f,1,.2f);
        // steering wheel and column
        part(cylinder,x,y,z,r,0,1.16f,-.30f,.10f,.52f,.10f,-55,0,.14f,.15f,.17f,1,.55f);
        part(cylinder,x,y,z,r,0,1.42f,-.56f,.48f,.09f,.48f,-55,90,.025f,.028f,.032f,1,.65f);
        part(cylinder,x,y,z,r,0,1.42f,-.56f,.26f,.11f,.26f,-55,90,.97f,.79f,.035f,1,.5f);
        // rear wing
        part(cube,x,y,z,r,0,1.20f,1.18f,2.40f,.15f,.32f,0,0,.025f,.14f,.48f,1,.72f);
        part(cube,x,y,z,r,0,1.33f,1.19f,1.42f,.065f,.11f,0,0,.98f,.79f,.035f,1,.5f);
        // bumpers
        part(cylinder,x,y,z,r,0,.30f,-1.56f,.09f,2.30f,.09f,90,0,.06f,.07f,.08f,1,.55f);
        part(cylinder,x,y,z,r,0,.32f,1.52f,.09f,2.18f,.09f,90,0,.06f,.07f,.08f,1,.55f);
        // wheels
        wheel(x,y,z,r,-1.42f,.38f,-.86f,moving);
        wheel(x,y,z,r, 1.42f,.38f,-.86f,moving);
        wheel(x,y,z,r,-1.42f,.38f, .88f,moving);
        wheel(x,y,z,r, 1.42f,.38f, .88f,moving);
    }

    private void wheel(float x,float y,float z,float r,float ox,float oy,float oz,boolean moving){
        part(cylinder,x,y,z,r,ox,oy,oz,.56f,.30f,.56f,90,90,.018f,.020f,.024f,1,.22f);
        part(cylinder,x,y,z,r,ox,oy,oz,.31f,.32f,.31f,90,90,.94f,.68f,.025f,1,.78f);
        part(cylinder,x,y,z,r,ox,oy,oz,.14f,.34f,.14f,90,90,.12f,.13f,.15f,1,.65f);
    }

    // DRAW HELPERS -----------------------------------------------------------

    private void shadow(float x,float y,float z,float sx,float sz){
        draw(sphere,x,y,z,sx,.035f,sz,0,0,0,.015f,.018f,.022f,.30f,.0f);
    }

    private void part(Mesh me,float x,float y,float z,float parentYaw,
                      float ox,float oy,float oz,float sx,float sy,float sz,
                      float rotX,float rotZ,float r,float g,float b,float a,float shine){
        float ang=parentYaw*DEG;
        float rx=ox*(float)Math.cos(ang)+oz*(float)Math.sin(ang);
        float rz=-ox*(float)Math.sin(ang)+oz*(float)Math.cos(ang);
        Matrix.setIdentityM(model,0);
        Matrix.translateM(model,0,x+rx,y+oy,z+rz);
        Matrix.rotateM(model,0,parentYaw,0,1,0);
        if(rotX!=0)Matrix.rotateM(model,0,rotX,1,0,0);
        if(rotZ!=0)Matrix.rotateM(model,0,rotZ,0,0,1);
        Matrix.scaleM(model,0,sx,sy,sz);
        drawModel(me,r,g,b,a,shine);
    }

    private void draw(Mesh me,float x,float y,float z,float sx,float sy,float sz,
                      float ry,float rx,float rz,float r,float g,float b,float a,float shine){
        Matrix.setIdentityM(model,0);
        Matrix.translateM(model,0,x,y,z);
        if(ry!=0)Matrix.rotateM(model,0,ry,0,1,0);
        if(rx!=0)Matrix.rotateM(model,0,rx,1,0,0);
        if(rz!=0)Matrix.rotateM(model,0,rz,0,0,1);
        Matrix.scaleM(model,0,sx,sy,sz);
        drawModel(me,r,g,b,a,shine);
    }

    private void drawModel(Mesh me,float r,float g,float b,float a,float shine){
        if(me==null)return;
        Matrix.multiplyMM(mvp,0,vp,0,model,0);
        GLES20.glUniformMatrix4fv(uMvp,1,false,mvp,0);
        GLES20.glUniformMatrix4fv(uModel,1,false,model,0);
        GLES20.glUniform4f(uColor,r,g,b,a);
        GLES20.glUniform1f(uShine,shine);
        me.draw(aPos,aNormal);
    }

    private void show(String s,long ms){message=s;messageUntil=SystemClock.uptimeMillis()+ms;}
    public void setMove(float x,float y){moveX=x;moveY=y;}
    public void addCamera(float x,float y){camDx+=x;camDy+=y;}
    public void requestJump(){jumpReq=true;}
    public void requestAction(){actionReq=true;}
    public void setPaused(boolean p){paused=p;if(p)audio.stopEngine();else if(inKart)audio.startEngine();}
    public GameSnapshot getSnapshot(){return snapshot;}
    public void release(){audio.release();}
    public void resetGame(){
        stage=0;minerals=tools=coins=checkpoint=0;bestTime=-1;raceTime=0;inKart=false;kartSpeed=0;
        px=-18;py=0;pz=16;yaw=25;prefs.edit().clear().apply();populate();show("Nová hra – najdi 5 minerálů!",3000);
    }

    private static float dist2(float a,float b,float c,float d){float x=a-c,z=b-d;return x*x+z*z;}
    private static float clamp(float v,float lo,float hi){return Math.max(lo,Math.min(hi,v));}
    private static float shortest(float a){while(a>180)a-=360;while(a<-180)a+=360;return a;}

    private static int program(String vs,String fs){
        try{
            int v=shader(GLES20.GL_VERTEX_SHADER,vs),f=shader(GLES20.GL_FRAGMENT_SHADER,fs);
            if(v==0||f==0)return 0;
            int p=GLES20.glCreateProgram();if(p==0)return 0;
            GLES20.glAttachShader(p,v);GLES20.glAttachShader(p,f);GLES20.glLinkProgram(p);
            int[] ok=new int[1];GLES20.glGetProgramiv(p,GLES20.GL_LINK_STATUS,ok,0);
            GLES20.glDeleteShader(v);GLES20.glDeleteShader(f);
            if(ok[0]==0){GLES20.glDeleteProgram(p);return 0;}return p;
        }catch(Throwable t){return 0;}
    }

    private static int shader(int type,String src){
        try{
            int s=GLES20.glCreateShader(type);if(s==0)return 0;
            GLES20.glShaderSource(s,src);GLES20.glCompileShader(s);
            int[] ok=new int[1];GLES20.glGetShaderiv(s,GLES20.GL_COMPILE_STATUS,ok,0);
            if(ok[0]==0){GLES20.glDeleteShader(s);return 0;}return s;
        }catch(Throwable t){return 0;}
    }

    private static final String VS=
            "uniform mat4 uMVP;"+
            "uniform mat4 uModel;"+
            "attribute vec3 aPosition;"+
            "attribute vec3 aNormal;"+
            "varying vec3 vWorld;"+
            "varying vec3 vNormal;"+
            "void main(){"+
            "vec4 w=uModel*vec4(aPosition,1.0);"+
            "vWorld=w.xyz;"+
            "vNormal=normalize(mat3(uModel)*aNormal);"+
            "gl_Position=uMVP*vec4(aPosition,1.0);"+
            "}";

    private static final String FS=
            "precision mediump float;"+
            "uniform vec4 uColor;"+
            "uniform vec3 uLightDir;"+
            "uniform vec3 uCameraPos;"+
            "uniform vec4 uFogColor;"+
            "uniform float uShine;"+
            "varying vec3 vWorld;"+
            "varying vec3 vNormal;"+
            "void main(){"+
            "vec3 n=normalize(vNormal);"+
            "vec3 l=normalize(uLightDir);"+
            "float ndl=max(dot(n,l),0.0);"+
            "float hemi=0.44+0.16*(n.y*0.5+0.5);"+
            "vec3 v=normalize(uCameraPos-vWorld);"+
            "vec3 h=normalize(l+v);"+
            "float spec=pow(max(dot(n,h),0.0),18.0)*uShine;"+
            "vec3 base=uColor.rgb*(hemi+0.58*ndl)+vec3(spec);"+
            "float d=distance(vWorld,uCameraPos);"+
            "float fog=clamp((d-32.0)/48.0,0.0,0.78);"+
            "gl_FragColor=mix(vec4(base,uColor.a),uFogColor,fog);"+
            "}";

    private static final class Mesh{
        private final FloatBuffer v;
        private final int count;
        Mesh(float[] a){v=ByteBuffer.allocateDirect(a.length*4).order(ByteOrder.nativeOrder()).asFloatBuffer();v.put(a).position(0);count=a.length/6;}
        void draw(int pos,int norm){
            v.position(0);GLES20.glVertexAttribPointer(pos,3,GLES20.GL_FLOAT,false,24,v);GLES20.glEnableVertexAttribArray(pos);
            v.position(3);GLES20.glVertexAttribPointer(norm,3,GLES20.GL_FLOAT,false,24,v);GLES20.glEnableVertexAttribArray(norm);
            GLES20.glDrawArrays(GLES20.GL_TRIANGLES,0,count);
            GLES20.glDisableVertexAttribArray(pos);GLES20.glDisableVertexAttribArray(norm);
        }

        static Mesh cube(){
            ArrayList<Float> o=new ArrayList<>();
            face(o,0,0,1, new float[][]{{-.5f,-.5f,.5f},{.5f,-.5f,.5f},{.5f,.5f,.5f},{-.5f,.5f,.5f}});
            face(o,0,0,-1,new float[][]{{.5f,-.5f,-.5f},{-.5f,-.5f,-.5f},{-.5f,.5f,-.5f},{.5f,.5f,-.5f}});
            face(o,-1,0,0,new float[][]{{-.5f,-.5f,-.5f},{-.5f,-.5f,.5f},{-.5f,.5f,.5f},{-.5f,.5f,-.5f}});
            face(o,1,0,0,new float[][]{{.5f,-.5f,.5f},{.5f,-.5f,-.5f},{.5f,.5f,-.5f},{.5f,.5f,.5f}});
            face(o,0,1,0,new float[][]{{-.5f,.5f,.5f},{.5f,.5f,.5f},{.5f,.5f,-.5f},{-.5f,.5f,-.5f}});
            face(o,0,-1,0,new float[][]{{-.5f,-.5f,-.5f},{.5f,-.5f,-.5f},{.5f,-.5f,.5f},{-.5f,-.5f,.5f}});
            return new Mesh(toArray(o));
        }

        static Mesh sphere(int stacks,int slices){
            ArrayList<Float> o=new ArrayList<>();
            for(int i=0;i<stacks;i++){
                float p0=(i/(float)stacks-.5f)*(float)Math.PI;
                float p1=((i+1)/(float)stacks-.5f)*(float)Math.PI;
                for(int j=0;j<slices;j++){
                    float t0=j*(float)Math.PI*2f/slices;
                    float t1=(j+1)*(float)Math.PI*2f/slices;
                    sv(o,p0,t0);sv(o,p0,t1);sv(o,p1,t1);
                    sv(o,p0,t0);sv(o,p1,t1);sv(o,p1,t0);
                }
            }
            return new Mesh(toArray(o));
        }

        static Mesh cylinder(int slices){
            ArrayList<Float> o=new ArrayList<>();
            for(int i=0;i<slices;i++){
                float a0=i*(float)Math.PI*2f/slices,a1=(i+1)*(float)Math.PI*2f/slices;
                float x0=(float)Math.sin(a0)*.5f,z0=(float)Math.cos(a0)*.5f;
                float x1=(float)Math.sin(a1)*.5f,z1=(float)Math.cos(a1)*.5f;
                cv(o,x0,-.5f,z0,x0*2,0,z0*2);cv(o,x1,-.5f,z1,x1*2,0,z1*2);cv(o,x1,.5f,z1,x1*2,0,z1*2);
                cv(o,x0,-.5f,z0,x0*2,0,z0*2);cv(o,x1,.5f,z1,x1*2,0,z1*2);cv(o,x0,.5f,z0,x0*2,0,z0*2);
                cv(o,0,.5f,0,0,1,0);cv(o,x1,.5f,z1,0,1,0);cv(o,x0,.5f,z0,0,1,0);
                cv(o,0,-.5f,0,0,-1,0);cv(o,x0,-.5f,z0,0,-1,0);cv(o,x1,-.5f,z1,0,-1,0);
            }
            return new Mesh(toArray(o));
        }

        static Mesh roof(){
            ArrayList<Float> o=new ArrayList<>();
            // front triangle
            tri(o,-.5f,-.5f,-.5f, .5f,-.5f,-.5f, 0,.5f,-.5f, 0,0,-1);
            // back triangle
            tri(o,.5f,-.5f,.5f, -.5f,-.5f,.5f, 0,.5f,.5f, 0,0,1);
            // left slope
            quad(o,-.5f,-.5f,.5f,-.5f,-.5f,-.5f,0,.5f,-.5f,0,.5f,.5f,-.7f,.7f,0);
            // right slope
            quad(o,.5f,-.5f,-.5f,.5f,-.5f,.5f,0,.5f,.5f,0,.5f,-.5f,.7f,.7f,0);
            // bottom
            quad(o,-.5f,-.5f,-.5f,.5f,-.5f,-.5f,.5f,-.5f,.5f,-.5f,-.5f,.5f,0,-1,0);
            return new Mesh(toArray(o));
        }

        private static void face(ArrayList<Float> o,float nx,float ny,float nz,float[][] p){
            put(o,p[0],nx,ny,nz);put(o,p[1],nx,ny,nz);put(o,p[2],nx,ny,nz);
            put(o,p[0],nx,ny,nz);put(o,p[2],nx,ny,nz);put(o,p[3],nx,ny,nz);
        }
        private static void put(ArrayList<Float> o,float[] p,float nx,float ny,float nz){cv(o,p[0],p[1],p[2],nx,ny,nz);}
        private static void sv(ArrayList<Float> o,float p,float t){
            float cp=(float)Math.cos(p),x=cp*(float)Math.sin(t),y=(float)Math.sin(p),z=cp*(float)Math.cos(t);
            cv(o,x*.5f,y*.5f,z*.5f,x,y,z);
        }
        private static void cv(ArrayList<Float> o,float x,float y,float z,float nx,float ny,float nz){o.add(x);o.add(y);o.add(z);o.add(nx);o.add(ny);o.add(nz);}
        private static void tri(ArrayList<Float> o,float ax,float ay,float az,float bx,float by,float bz,float cx,float cy,float cz,float nx,float ny,float nz){cv(o,ax,ay,az,nx,ny,nz);cv(o,bx,by,bz,nx,ny,nz);cv(o,cx,cy,cz,nx,ny,nz);}
        private static void quad(ArrayList<Float> o,float ax,float ay,float az,float bx,float by,float bz,float cx,float cy,float cz,float dx,float dy,float dz,float nx,float ny,float nz){tri(o,ax,ay,az,bx,by,bz,cx,cy,cz,nx,ny,nz);tri(o,ax,ay,az,cx,cy,cz,dx,dy,dz,nx,ny,nz);}
        private static float[] toArray(ArrayList<Float> o){float[] a=new float[o.size()];for(int i=0;i<a.length;i++)a[i]=o.get(i);return a;}
    }
}
