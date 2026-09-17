package cz.vitekzavodnik.game;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;

import java.util.Locale;

public final class HUDView extends View {
    public interface Listener { void onPauseRequested(); }

    private final GameRenderer renderer;
    private final Listener listener;
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float d;

    private int joyPointer=-1, camPointer=-1;
    private float joyBaseX, joyBaseY, joyX, joyY;
    private float camLastX, camLastY;
    private float joyRadius;
    private final RectF pauseRect = new RectF();
    private final RectF jumpRect = new RectF();
    private final RectF actionRect = new RectF();

    public HUDView(Context context, GameRenderer renderer, Listener listener) {
        super(context);
        this.renderer=renderer; this.listener=listener;
        d=getResources().getDisplayMetrics().density;
        setLayerType(View.LAYER_TYPE_SOFTWARE,null);
        p.setTypeface(android.graphics.Typeface.create("sans", android.graphics.Typeface.BOLD));
    }

    @Override protected void onDraw(Canvas c) {
        super.onDraw(c);
        int w=getWidth(), h=getHeight();
        joyRadius=68*d; joyBaseX=94*d; joyBaseY=h-92*d;
        if(joyPointer<0){ joyX=joyBaseX; joyY=joyBaseY; }

        GameSnapshot s=renderer.getSnapshot();

        p.setColor(Color.argb(205,14,25,45));
        c.drawRoundRect(18*d,16*d,Math.min(w*0.64f,600*d),82*d,18*d,18*d,p);
        p.setColor(Color.WHITE); p.setTextSize(18*d); p.setTextAlign(Paint.Align.LEFT);
        c.drawText(s.questText(),34*d,45*d,p);
        p.setColor(Color.rgb(255,226,43)); p.setTextSize(13*d);
        c.drawText("★ Mince: "+s.coins,34*d,68*d,p);

        if(s.inKart){
            p.setColor(Color.argb(210,10,18,33));
            c.drawRoundRect(w/2f-90*d,16*d,w/2f+90*d,72*d,16*d,16*d,p);
            p.setTextAlign(Paint.Align.CENTER); p.setColor(Color.WHITE); p.setTextSize(22*d);
            c.drawText(String.format(Locale.US,"%.1f s",s.raceTime),w/2f,51*d,p);
        } else if(s.bestTime>0 && s.stage>=3){
            p.setTextAlign(Paint.Align.CENTER); p.setColor(Color.WHITE); p.setTextSize(14*d);
            c.drawText(String.format(Locale.US,"Nejlepší čas %.1f s",s.bestTime),w/2f,42*d,p);
        }

        float pr=46*d; pauseRect.set(w-pr-12*d,14*d,w-12*d,14*d+pr);
        p.setColor(Color.argb(195,14,25,45)); c.drawRoundRect(pauseRect,14*d,14*d,p);
        p.setColor(Color.WHITE); p.setStrokeWidth(4*d);
        c.drawLine(w-42*d,28*d,w-42*d,48*d,p); c.drawLine(w-28*d,28*d,w-28*d,48*d,p);

        p.setColor(Color.argb(90,255,255,255)); c.drawCircle(joyBaseX,joyBaseY,joyRadius,p);
        p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(3*d); p.setColor(Color.argb(160,255,255,255)); c.drawCircle(joyBaseX,joyBaseY,joyRadius,p);
        p.setStyle(Paint.Style.FILL); p.setColor(Color.argb(190,20,37,66)); c.drawCircle(joyX,joyY,31*d,p);

        float by=h-88*d;
        jumpRect.set(w-132*d,by-48*d,w-34*d,by+48*d);
        actionRect.set(w-262*d,by-42*d,w-158*d,by+42*d);
        if(!s.inKart){
            button(c,jumpRect,"SKOK",Color.argb(210,16,94,168));
            button(c,actionRect,"AKCE",Color.argb(220,240,184,16));
        } else if(s.stage>=3){
            button(c,actionRect,"VYSTOUPIT",Color.argb(220,240,184,16));
        }

        if(!s.message.isEmpty()){
            p.setTextAlign(Paint.Align.CENTER); p.setTextSize(18*d); p.setColor(Color.argb(225,6,17,30));
            float mw=Math.min(w-50*d,520*d);
            c.drawRoundRect(w/2f-mw/2,90*d,w/2f+mw/2,142*d,15*d,15*d,p);
            p.setColor(Color.WHITE); c.drawText(s.message,w/2f,123*d,p);
        }
        invalidate();
    }

    private void button(Canvas c,RectF r,String text,int color){
        p.setColor(color); c.drawRoundRect(r,28*d,28*d,p);
        p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(2*d); p.setColor(Color.argb(180,255,255,255)); c.drawRoundRect(r,28*d,28*d,p); p.setStyle(Paint.Style.FILL);
        p.setColor(Color.WHITE); p.setTextSize((text.length()>6?12:15)*d); p.setTextAlign(Paint.Align.CENTER);
        c.drawText(text,r.centerX(),r.centerY()+5*d,p);
    }

    @Override public boolean onTouchEvent(MotionEvent e){
        int action=e.getActionMasked();
        int idx=e.getActionIndex();
        if(action==MotionEvent.ACTION_DOWN || action==MotionEvent.ACTION_POINTER_DOWN){
            int id=e.getPointerId(idx); float x=e.getX(idx), y=e.getY(idx);
            if(pauseRect.contains(x,y)){ if(listener!=null)listener.onPauseRequested(); return true; }
            GameSnapshot s=renderer.getSnapshot();
            if(!s.inKart && jumpRect.contains(x,y)){ renderer.requestJump(); return true; }
            if(actionRect.contains(x,y)){ renderer.requestAction(); return true; }
            if(x<getWidth()*0.42f && y>getHeight()*0.40f && joyPointer<0){ joyPointer=id; updateJoy(x,y); }
            else if(camPointer<0){ camPointer=id; camLastX=x; camLastY=y; }
        } else if(action==MotionEvent.ACTION_MOVE){
            if(joyPointer>=0){ int i=e.findPointerIndex(joyPointer); if(i>=0) updateJoy(e.getX(i),e.getY(i)); }
            if(camPointer>=0){ int i=e.findPointerIndex(camPointer); if(i>=0){ float x=e.getX(i),y=e.getY(i); renderer.addCamera(x-camLastX,y-camLastY); camLastX=x;camLastY=y; } }
        } else if(action==MotionEvent.ACTION_UP || action==MotionEvent.ACTION_POINTER_UP || action==MotionEvent.ACTION_CANCEL){
            int id=e.getPointerId(idx);
            if(id==joyPointer || action==MotionEvent.ACTION_CANCEL){ joyPointer=-1; joyX=joyBaseX;joyY=joyBaseY; renderer.setMove(0,0); }
            if(id==camPointer || action==MotionEvent.ACTION_CANCEL) camPointer=-1;
        }
        return true;
    }

    private void updateJoy(float x,float y){
        float dx=x-joyBaseX, dy=y-joyBaseY;
        float len=(float)Math.sqrt(dx*dx+dy*dy);
        if(len>joyRadius){ dx*=joyRadius/len; dy*=joyRadius/len; }
        joyX=joyBaseX+dx; joyY=joyBaseY+dy;
        renderer.setMove(dx/joyRadius,dy/joyRadius);
    }
}
