package cz.vitekzavodnik.game;

import android.media.AudioManager;
import android.media.ToneGenerator;

/** Safe lightweight generated sound effects. Audio failure never crashes the game. */
public final class AudioEngine {
    private ToneGenerator tone;
    private boolean engineOn = false;
    private long lastEngineTone = 0L;

    public AudioEngine(android.content.Context context) {
        try { tone = new ToneGenerator(AudioManager.STREAM_MUSIC, 45); }
        catch (Throwable ignored) { tone = null; }
    }

    private void play(int kind, int ms){
        try { if(tone!=null) tone.startTone(kind, ms); } catch (Throwable ignored) { }
    }

    public void collect(){ play(ToneGenerator.TONE_PROP_BEEP, 70); }
    public void success(){ play(ToneGenerator.TONE_PROP_ACK, 160); }
    public void jump(){ play(ToneGenerator.TONE_PROP_BEEP2, 50); }

    public void startEngine(){ engineOn = true; }
    public void setEnginePitch(float speed01){
        if(!engineOn || tone==null) return;
        long now = android.os.SystemClock.uptimeMillis();
        long gap = (long)(300 - 160 * Math.max(0f, Math.min(1f, speed01)));
        if(now - lastEngineTone >= gap){
            play(ToneGenerator.TONE_DTMF_0, 25);
            lastEngineTone = now;
        }
    }
    public void stopEngine(){ engineOn = false; }
    public void release(){
        try { if(tone!=null) tone.release(); } catch (Throwable ignored) { }
        tone=null;
    }
}
