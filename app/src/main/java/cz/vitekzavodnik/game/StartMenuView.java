package cz.vitekzavodnik.game;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.MotionEvent;
import android.view.View;

public final class StartMenuView extends View {
    public interface Listener { void onPlay(); void onNewGame(); }

    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float d;
    private final Listener listener;
    private final RectF play = new RectF(), fresh = new RectF();
    private boolean pausedMenu = false;

    public StartMenuView(Context context, Listener listener){
        super(context);
        this.listener = listener;
        d = getResources().getDisplayMetrics().density;
        p.setTypeface(android.graphics.Typeface.create("sans", android.graphics.Typeface.BOLD));
        setClickable(true);
    }

    public void setPausedMenu(boolean paused){ pausedMenu=paused; invalidate(); }

    @Override protected void onDraw(Canvas c){
        int w=getWidth(), h=getHeight();
        p.setShader(new LinearGradient(0,0,0,h, Color.rgb(75,179,239), Color.rgb(19,55,104), Shader.TileMode.CLAMP));
        c.drawRect(0,0,w,h,p); p.setShader(null);
        p.setColor(Color.rgb(255,220,95)); c.drawCircle(w*0.82f,h*0.18f,54*d,p);
        Path hills=new Path(); hills.moveTo(0,h*0.62f); hills.cubicTo(w*.18f,h*.42f,w*.32f,h*.69f,w*.47f,h*.53f); hills.cubicTo(w*.63f,h*.37f,w*.78f,h*.63f,w,h*.44f); hills.lineTo(w,h); hills.lineTo(0,h); hills.close();
        p.setColor(Color.rgb(70,142,82)); c.drawPath(hills,p);
        Path track=new Path(); track.moveTo(w*.35f,h); track.cubicTo(w*.4f,h*.78f,w*.58f,h*.77f,w*.65f,h*.58f); track.cubicTo(w*.72f,h*.42f,w*.67f,h*.32f,w*.72f,h*.24f); track.lineTo(w*.82f,h*.24f); track.cubicTo(w*.77f,h*.34f,w*.83f,h*.46f,w*.76f,h*.62f); track.cubicTo(w*.68f,h*.84f,w*.55f,h*.86f,w*.5f,h); track.close();
        p.setColor(Color.rgb(54,61,73)); c.drawPath(track,p);
        p.setColor(Color.rgb(255,214,49)); p.setStrokeWidth(5*d); p.setStyle(Paint.Style.STROKE);
        Path stripe=new Path(); stripe.moveTo(w*.43f,h); stripe.cubicTo(w*.48f,h*.80f,w*.60f,h*.78f,w*.68f,h*.58f); stripe.cubicTo(w*.75f,h*.42f,w*.72f,h*.33f,w*.76f,h*.25f); c.drawPath(stripe,p); p.setStyle(Paint.Style.FILL);
        float cx=w*.67f, cy=h*.43f;
        p.setColor(Color.rgb(246,201,157)); c.drawCircle(cx,cy,47*d,p);
        p.setColor(Color.rgb(246,215,82)); c.drawOval(new RectF(cx-42*d,cy-49*d,cx+36*d,cy-18*d),p);
        p.setColor(Color.rgb(24,105,211)); c.drawOval(new RectF(cx-51*d,cy-58*d,cx+39*d,cy-31*d),p); c.drawRect(cx+22*d,cy-45*d,cx+72*d,cy-36*d,p);
        p.setColor(Color.WHITE); c.drawCircle(cx-17*d,cy-4*d,7*d,p); c.drawCircle(cx+16*d,cy-4*d,7*d,p);
        p.setColor(Color.rgb(28,92,170)); c.drawCircle(cx-17*d,cy-4*d,3.4f*d,p); c.drawCircle(cx+16*d,cy-4*d,3.4f*d,p);
        p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(3*d); p.setColor(Color.rgb(120,66,45)); c.drawArc(new RectF(cx-18*d,cy+10*d,cx+20*d,cy+34*d),15,150,false,p); p.setStyle(Paint.Style.FILL);
        p.setColor(Color.rgb(17,91,190)); c.drawRoundRect(new RectF(cx-53*d,cy+38*d,cx+55*d,cy+146*d),18*d,18*d,p);
        p.setColor(Color.rgb(250,210,33)); c.drawRect(cx-43*d,cy+58*d,cx+45*d,cy+72*d,p);
        p.setShader(new LinearGradient(0,0,w*.55f,0,Color.argb(210,5,17,32),Color.argb(10,5,17,32),Shader.TileMode.CLAMP)); c.drawRect(0,0,w*.7f,h,p); p.setShader(null);
        float left=46*d;
        p.setTextAlign(Paint.Align.LEFT); p.setColor(Color.WHITE); p.setTextSize(42*d); c.drawText("VÍTEK",left,72*d,p);
        p.setColor(Color.rgb(255,213,45)); p.setTextSize(34*d); c.drawText("ZÁVODNÍK",left,111*d,p);
        p.setColor(Color.argb(220,255,255,255)); p.setTextSize(13*d); c.drawText("Velké dobrodružství začíná!",left,137*d,p);
        if(pausedMenu){ p.setColor(Color.WHITE); p.setTextSize(16*d); c.drawText("HRA POZASTAVENA",left,h-180*d,p); }
        float bw=190*d,bh=54*d;
        play.set(left,h-142*d,left+bw,h-142*d+bh);
        fresh.set(left,h-76*d,left+bw,h-76*d+45*d);
        drawButton(c,play,pausedMenu?"POKRAČOVAT":"HRÁT",Color.rgb(19,100,196),16*d);
        drawButton(c,fresh,"NOVÁ HRA",Color.rgb(238,181,17),14*d);
        p.setTextSize(10*d); p.setColor(Color.argb(190,255,255,255)); p.setTextAlign(Paint.Align.LEFT); c.drawText("Android • OpenGL ES • bez internetu",left,h-10*d,p);
    }

    private void drawButton(Canvas c,RectF r,String text,int color,float textSize){
        p.setColor(Color.argb(245,Color.red(color),Color.green(color),Color.blue(color))); c.drawRoundRect(r,18*d,18*d,p);
        p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(2*d); p.setColor(Color.argb(190,255,255,255)); c.drawRoundRect(r,18*d,18*d,p); p.setStyle(Paint.Style.FILL);
        p.setTextAlign(Paint.Align.CENTER); p.setTextSize(textSize); p.setColor(Color.WHITE); c.drawText(text,r.centerX(),r.centerY()+5*d,p);
    }

    @Override public boolean onTouchEvent(MotionEvent e){
        if(e.getAction()==MotionEvent.ACTION_UP){ float x=e.getX(),y=e.getY(); if(play.contains(x,y)){ if(listener!=null)listener.onPlay(); return true; } if(fresh.contains(x,y)){ if(listener!=null)listener.onNewGame(); return true; } }
        return true;
    }
}
