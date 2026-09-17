package cz.vitekzavodnik.game;

import android.content.Context;
import android.opengl.GLSurfaceView;

public final class GameSurfaceView extends GLSurfaceView {
    private final GameRenderer renderer;

    public GameSurfaceView(Context context) {
        super(context);
        setEGLContextClientVersion(2);
        setPreserveEGLContextOnPause(true);
        renderer = new GameRenderer(context);
        setRenderer(renderer);
        setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY);
    }

    public GameRenderer renderer(){ return renderer; }

    public void resetGame(){ queueEvent(renderer::resetGame); }
    public void setGamePaused(boolean paused){ queueEvent(() -> renderer.setPaused(paused)); }
    public void releaseGame(){ queueEvent(renderer::release); }
}
