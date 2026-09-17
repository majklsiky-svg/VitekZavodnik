package cz.vitekzavodnik.game;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.MotionEvent;
import android.view.View;

import java.util.Locale;

public final class HUDView extends View {
    public interface Listener { void onPauseRequested(); }

    private final PremiumGameRenderer renderer;
    private final Listener listener;
    private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float d;
    private int joyPointer=-1,camPointer=-1;
    private float joyBaseX,joyBaseY,joyX,joyY,camLastX,camLastY,joyRadius;
    private final RectF pauseRect=new RectF(),jumpRect=new RectF(),actionRect=new RectF();

    public HUDView(Context context, PremiumGameRenderer renderer, Listener listener){
        super(context);this.renderer=renderer;this.listener=listener;d=getResources().getDisplayMetrics().density;
        p.setTypeface(android.graphics.Typeface.create("sans",android.graphics.Typeface.BOLD));setWillNotDraw(false);
    }

    @Override protected void onDraw(Canvas c){
        super.onDraw(c);int w=getWidth(),h=getHeight();
        joyRadius=70*d;joyBaseX=98*d;joyBaseY=h-96*d;if(joyPointer<0){joyX=joyBaseX;joyY=joyBaseY;}
        GameSnapshot s=renderer.getSnapshot();

        // quest card
        p.setShader(new LinearGradient(18*d,16*d,Math.min(w*.66f,610*d),84*d,Color.argb(235,8,24,48),Color.argb(220,17,72,128),Shader.TileMode.CLAMP));
        c.drawRoundRect(18*d,16*d,Math.min(w*.66f,610*d),86*d,20*d,20*d,p);p.setShader(null);
        p.setColor(Color.rgb(255,215,38));c.drawRoundRect(18*d,16*d,25*d,86*d,20*d,20*d,p);
        p.setTextAlign(Paint.Align.LEFT);p.setColor(Color.WHITE);p.setTextSize(18*d);c.drawText(s.questText(),38*d,46*d,p);
        p.setColor(Color.rgb(255,220,55));p.setTextSize(13*d);c.drawText("★  MINCE  "+s.coins,38*d,70*d,p);

        if(s.inKart){
            p.setColor(Color.argb(230,7,19,38));c.drawRoundRect(w/2f-105*d,14*d,w/2f+105*d,75*d,18*d,18*d,p);
            p.setColor(Color.rgb(255,215,38));c.drawRoundRect(w/2f-105*d,14*d,w/2f-96*d,75*d,18*d,18*d,p);
            p.setTextAlign(Paint.Align.CENTER);p.setColor(Color.WHITE);p.setTextSize(22*d);c.drawText(String.format(Locale.US,"%.1f s",s.raceTime),w/2f,50*d,p);
            p.setTextSize(10*d);p.setColor(Color.rgb(170,210,255));c.drawText("ZÁVODNÍ ČAS",w/2f,67*d,p);
        }else if(s.bestTime>0&&s.stage>=3){
            p.setTextAlign(Paint.Align.CENTER);p.setColor(Color.WHITE);p.setTextSize(14*d);c.drawText(String.format(Locale.US,"Rekord %.1f s",s.bestTime),w/2f,42*d,p);
        }

        float pr=48*d;pauseRect.set(w-pr-14*d,14*d,w-14*d,14*d+pr);
        p.setColor(Color.argb(225,8,24,48));c.drawRoundRect(pauseRect,16*d,16*d,p);p.setColor(Color.rgb(255,215,38));p.setStrokeWidth(4*d);
        c.drawLine(w-44*d,29*d,w-44*d,49*d,p);c.drawLine(w-29*d,29*d,w-29*d,49*d,p);

        // joystick
        p.setColor(Color.argb(70,255,255,255));c.drawCircle(joyBaseX,joyBaseY,joyRadius,p);
        p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(3*d);p.setColor(Color.argb(170,120,190,255));c.drawCircle(joyBaseX,joyBaseY,joyRadius,p);
        p.setStrokeWidth(2*d);p.setColor(Color.argb(90,255,215,40));c.drawCircle(joyBaseX,joyBaseY,joyRadius-10*d,p);p.setStyle(Paint.Style.FILL);
        p.setColor(Color.argb(235,8,32,66));c.drawCircle(joyX,joyY,32*d,p);p.setColor(Color.rgb(255,215,38));c.drawCircle(joyX,joyY,10*d,p);

        float by=h-92*d;jumpRect.set(w-134*d,by-49*d,w-34*d,by+49*d);actionRect.set(w-270*d,by-43*d,w-162*d,by+43*d);
        if(!s.inKart){button(c,jumpRect,"SKOK",Color.rgb(17,95,190));button(c,actionRect,"AKCE",Color.rgb(235,170,15));}
        else if(s.stage>=3)button(c,actionRect,"VYSTOUPIT",Color.rgb(235,170,15));

        if(!s.message.isEmpty()){
            p.setTextAlign(Paint.Align.CENTER);p.setTextSize(18*d);p.setColor(Color.argb(235,5,18,35));float mw=Math.min(w-50*d,540*d);
            c.drawRoundRect(w/2f-mw/2,92*d,w/2f+mw/2,146*d,16*d,16*d,p);p.setColor(Color.rgb(255,220,55));c.drawText(s.message,w/2f,126*d,p);
        }
        postInvalidateOnAnimation();
    }

    private void button(Canvas c,RectF r,String text,int color){
        p.setColor(Color.argb(235,5,18,35));c.drawRoundRect(r,30*d,30*d,p);
        RectF inner=new RectF(r.left+4*d,r.top+4*d,r.right-4*d,r.bottom-4*d);p.setColor(color);c.drawRoundRect(inner,27*d,27*d,p);
        p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(2*d);p.setColor(Color.argb(190,255,255,255));c.drawRoundRect(inner,27*d,27*d,p);p.setStyle(Paint.Style.FILL);
        p.setColor(Color.WHITE);p.setTextSize((text.length()>6?12:15)*d);p.setTextAlign(Paint.Align.CENTER);c.drawText(text,r.centerX(),r.centerY()+5*d,p);
    }

    @Override public boolean onTouchEvent(MotionEvent e){
        int action=e.getActionMasked(),idx=e.getActionIndex();
        if(action==MotionEvent.ACTION_DOWN||action==MotionEvent.ACTION_POINTER_DOWN){
            int id=e.getPointerId(idx);float x=e.getX(idx),y=e.getY(idx);
            if(pauseRect.contains(x,y)){if(listener!=null)listener.onPauseRequested();return true;}
            GameSnapshot s=renderer.getSnapshot();if(!s.inKart&&jumpRect.contains(x,y)){renderer.requestJump();return true;}if(actionRect.contains(x,y)){renderer.requestAction();return true;}
            if(x<getWidth()*.42f&&y>getHeight()*.38f&&joyPointer<0){joyPointer=id;updateJoy(x,y);}else if(camPointer<0){camPointer=id;camLastX=x;camLastY=y;}
        }else if(action==MotionEvent.ACTION_MOVE){
            if(joyPointer>=0){int i=e.findPointerIndex(joyPointer);if(i>=0)updateJoy(e.getX(i),e.getY(i));}
            if(camPointer>=0){int i=e.findPointerIndex(camPointer);if(i>=0){float x=e.getX(i),y=e.getY(i);renderer.addCamera(x-camLastX,y-camLastY);camLastX=x;camLastY=y;}}
        }else if(action==MotionEvent.ACTION_UP||action==MotionEvent.ACTION_POINTER_UP||action==MotionEvent.ACTION_CANCEL){
            int id=e.getPointerId(idx);if(id==joyPointer||action==MotionEvent.ACTION_CANCEL){joyPointer=-1;joyX=joyBaseX;joyY=joyBaseY;renderer.setMove(0,0);}if(id==camPointer||action==MotionEvent.ACTION_CANCEL)camPointer=-1;
        }
        return true;
    }

    private void updateJoy(float x,float y){
        float dx=x-joyBaseX,dy=y-joyBaseY,len=(float)Math.sqrt(dx*dx+dy*dy);if(len>joyRadius){dx*=joyRadius/len;dy*=joyRadius/len;}
        joyX=joyBaseX+dx;joyY=joyBaseY+dy;
        // direct control: the direction of the thumb equals the direction of travel
        renderer.setMove(dx/joyRadius,dy/joyRadius);
    }
}