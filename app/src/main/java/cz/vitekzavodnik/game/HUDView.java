package cz.vitekzavodnik.game;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;

public final class HUDView extends View {
    public interface Listener { void onPauseRequested(); }
    private final SafeGameRenderer renderer;
    private final Listener listener;
    private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float d;
    private int joyPointer=-1,camPointer=-1;
    private float joyBaseX,joyBaseY,joyX,joyY,camLastX,camLastY,joyRadius;
    private final RectF pauseRect=new RectF();

    public HUDView(Context context, SafeGameRenderer renderer, Listener listener){
        super(context);this.renderer=renderer;this.listener=listener;d=getResources().getDisplayMetrics().density;
        p.setTypeface(android.graphics.Typeface.create("sans",android.graphics.Typeface.BOLD));setWillNotDraw(false);
    }
    @Override protected void onDraw(Canvas c){
        super.onDraw(c);int w=getWidth(),h=getHeight();
        joyRadius=58*d;joyBaseX=82*d;joyBaseY=h-76*d;if(joyPointer<0){joyX=joyBaseX;joyY=joyBaseY;}
        GameSnapshot s=renderer.getSnapshot();
        p.setColor(Color.argb(150,10,20,25));c.drawRoundRect(16*d,14*d,285*d,68*d,16*d,16*d,p);
        p.setTextAlign(Paint.Align.LEFT);p.setColor(Color.WHITE);p.setTextSize(17*d);c.drawText("Vítek – Lovec minerálů",31*d,38*d,p);
        p.setTextSize(12*d);p.setColor(Color.rgb(220,235,220));c.drawText("Nalezeno  "+s.minerals+" / 5",31*d,58*d,p);
        float pr=42*d;pauseRect.set(w-pr-14*d,14*d,w-14*d,14*d+pr);p.setColor(Color.argb(155,10,20,25));c.drawRoundRect(pauseRect,14*d,14*d,p);p.setColor(Color.WHITE);p.setStrokeWidth(3*d);c.drawLine(w-39*d,27*d,w-39*d,44*d,p);c.drawLine(w-27*d,27*d,w-27*d,44*d,p);
        p.setColor(Color.argb(40,255,255,255));c.drawCircle(joyBaseX,joyBaseY,joyRadius,p);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(2*d);p.setColor(Color.argb(100,255,255,255));c.drawCircle(joyBaseX,joyBaseY,joyRadius,p);p.setStyle(Paint.Style.FILL);p.setColor(Color.argb(145,255,255,255));c.drawCircle(joyX,joyY,22*d,p);
        if(!s.message.isEmpty()){p.setTextAlign(Paint.Align.CENTER);p.setTextSize(12*d);p.setColor(Color.argb(135,10,20,25));float mw=Math.min(w-60*d,350*d);c.drawRoundRect(w/2f-mw/2,16*d,w/2f+mw/2,52*d,12*d,12*d,p);p.setColor(Color.WHITE);c.drawText(s.message,w/2f,39*d,p);}
        postInvalidateOnAnimation();
    }
    @Override public boolean onTouchEvent(MotionEvent e){
        int action=e.getActionMasked(),idx=e.getActionIndex();
        if(action==MotionEvent.ACTION_DOWN||action==MotionEvent.ACTION_POINTER_DOWN){int id=e.getPointerId(idx);float x=e.getX(idx),y=e.getY(idx);if(pauseRect.contains(x,y)){if(listener!=null)listener.onPauseRequested();return true;}if(x<getWidth()*.40f&&y>getHeight()*.42f&&joyPointer<0){joyPointer=id;updateJoy(x,y);}else if(camPointer<0){camPointer=id;camLastX=x;camLastY=y;}}
        else if(action==MotionEvent.ACTION_MOVE){if(joyPointer>=0){int i=e.findPointerIndex(joyPointer);if(i>=0)updateJoy(e.getX(i),e.getY(i));}if(camPointer>=0){int i=e.findPointerIndex(camPointer);if(i>=0){float x=e.getX(i),y=e.getY(i);renderer.addCamera(x-camLastX,y-camLastY);camLastX=x;camLastY=y;}}}
        else if(action==MotionEvent.ACTION_UP||action==MotionEvent.ACTION_POINTER_UP||action==MotionEvent.ACTION_CANCEL){int id=e.getPointerId(idx);if(id==joyPointer||action==MotionEvent.ACTION_CANCEL){joyPointer=-1;joyX=joyBaseX;joyY=joyBaseY;renderer.setMove(0,0);}if(id==camPointer||action==MotionEvent.ACTION_CANCEL)camPointer=-1;}
        return true;
    }
    private void updateJoy(float x,float y){float dx=x-joyBaseX,dy=y-joyBaseY,len=(float)Math.sqrt(dx*dx+dy*dy);if(len>joyRadius){dx*=joyRadius/len;dy*=joyRadius/len;}joyX=joyBaseX+dx;joyY=joyBaseY+dy;renderer.setMove(dx/joyRadius,dy/joyRadius);}
}
