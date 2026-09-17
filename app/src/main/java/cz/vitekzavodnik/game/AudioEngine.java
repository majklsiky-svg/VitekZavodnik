package cz.vitekzavodnik.game;

import android.media.AudioManager;
import android.media.ToneGenerator;

/** Lightweight generated sound effects – no external audio assets required. */
public final class AudioEngine {
    private ToneGenerator tone;
    private boolean engineOn = false;
    private long lastEngineTone = 0L;

    public AudioEngine(android.content.Context context) {
        tone = new ToneGenerator(AudioManager.STREAM_MUSIC, 55);
    }

    public void collect(){ if(tone!=null) tone.startTone(ToneGenerator.TONE_PROP_BEEP, 80); }
    public void success(){ if(tone!=null) tone.startTone(ToneGenerator.TONE_PROP_ACK, 180); }
    public void jump(){ if(tone!=null) tone.startTone(ToneGenerator.TONE_PROP_BEEP2, 55); }

    public void startEngine(){ engineOn = true; }
    public void setEnginePitch(float speed01){
        if(!engineOn || tone==null) return;
        long now = android.os.SystemClock.uptimeMillis();
        long gap = (long)(260 - 150 * Math.max(0f, Math.min(1f, speed01)));
        if(now - lastEngineTone >= gap){
            tone.startTone(ToneGenerator.TONE_DTMF_0, 35);
            lastEngineTone = now;
        }
    }
    public void stopEngine(){ engineOn = false; }
    public void release(){ if(tone!=null){ tone.release(); tone=null; } }
}
