package cz.vitekzavodnik.game;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.widget.FrameLayout;

public final class MainActivity extends Activity implements HUDView.Listener, StartMenuView.Listener {
    private GameSurfaceView gameView;
    private HUDView hudView;
    private StartMenuView menuView;
    private boolean menuVisible=true;

    @Override protected void onCreate(Bundle savedInstanceState){
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,WindowManager.LayoutParams.FLAG_FULLSCREEN);
        hideSystemUi();

        FrameLayout root=new FrameLayout(this);
        gameView=new GameSurfaceView(this);
        hudView=new HUDView(this,gameView.renderer(),this);
        menuView=new StartMenuView(this,this);

        root.addView(gameView,new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT,FrameLayout.LayoutParams.MATCH_PARENT));
        root.addView(hudView,new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT,FrameLayout.LayoutParams.MATCH_PARENT));
        root.addView(menuView,new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT,FrameLayout.LayoutParams.MATCH_PARENT));
        hudView.setVisibility(View.GONE);
        setContentView(root);
        gameView.setGamePaused(true);
    }

    private void hideSystemUi(){
        try {
            if(android.os.Build.VERSION.SDK_INT>=30){
                WindowInsetsController c=getWindow().getInsetsController();
                if(c!=null){
                    c.hide(WindowInsets.Type.statusBars()|WindowInsets.Type.navigationBars());
                    c.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
                }
            }else{
                getWindow().getDecorView().setSystemUiVisibility(
                        View.SYSTEM_UI_FLAG_FULLSCREEN|View.SYSTEM_UI_FLAG_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY|View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN|View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
            }
        } catch (Throwable ignored) { }
    }

    @Override public void onPlay(){
        menuVisible=false;
        if(menuView!=null) menuView.setVisibility(View.GONE);
        if(hudView!=null) hudView.setVisibility(View.VISIBLE);
        if(gameView!=null) gameView.setGamePaused(false);
        hideSystemUi();
    }

    @Override public void onNewGame(){
        if(gameView!=null) gameView.resetGame();
        menuVisible=false;
        if(menuView!=null) menuView.setVisibility(View.GONE);
        if(hudView!=null) hudView.setVisibility(View.VISIBLE);
        if(gameView!=null) gameView.setGamePaused(false);
        hideSystemUi();
    }

    @Override public void onPauseRequested(){
        menuVisible=true;
        if(menuView!=null){ menuView.setPausedMenu(true); menuView.setVisibility(View.VISIBLE); }
        if(hudView!=null) hudView.setVisibility(View.GONE);
        if(gameView!=null) gameView.setGamePaused(true);
    }

    @Override public void onBackPressed(){
        if(!menuVisible) onPauseRequested(); else super.onBackPressed();
    }

    @Override protected void onResume(){
        super.onResume();
        if(gameView!=null){
            try { gameView.onResume(); } catch(Throwable ignored) { }
            if(menuVisible) gameView.setGamePaused(true);
        }
        hideSystemUi();
    }

    @Override protected void onPause(){
        if(gameView!=null){
            gameView.setGamePaused(true);
            try { gameView.onPause(); } catch(Throwable ignored) { }
        }
        super.onPause();
    }

    @Override protected void onDestroy(){
        if(gameView!=null) gameView.releaseGame();
        super.onDestroy();
    }
}
