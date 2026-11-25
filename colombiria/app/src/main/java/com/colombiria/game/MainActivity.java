package com.colombiria.game;

import android.app.Activity;
import android.os.Bundle;
import android.opengl.GLSurfaceView;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;

public final class MainActivity extends Activity {
    private WorldRenderer renderer;
    private long lastTap;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        FrameLayout root = new FrameLayout(this);
        GLSurfaceView surface = new GLSurfaceView(this);
        surface.setEGLContextClientVersion(2);
        renderer = new WorldRenderer();
        surface.setRenderer(renderer);
        surface.setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY);
        root.addView(surface, new FrameLayout.LayoutParams(-1, -1));

        TextView hud = new TextView(this);
        hud.setText("COLOMBIRIA  •  BETA 1     ATX-01     Toca para acelerar");
        hud.setTextColor(0xffffffff);
        hud.setTextSize(13);
        hud.setPadding(22, 16, 22, 16);
        hud.setBackgroundColor(0x660b1720);
        root.addView(hud);
        surface.setOnTouchListener((v, e) -> {
            if (e.getAction() == MotionEvent.ACTION_DOWN) {
                renderer.setThrottle(true);
                lastTap = System.currentTimeMillis();
                return true;
            }
            if (e.getAction() == MotionEvent.ACTION_UP || e.getAction() == MotionEvent.ACTION_CANCEL) {
                renderer.setThrottle(false);
                return true;
            }
            if (e.getAction() == MotionEvent.ACTION_MOVE) {
                renderer.setSteering((e.getX() / Math.max(1f, v.getWidth()) - .5f) * 2f);
                return true;
            }
            return true;
        });
        setContentView(root);
    }

    @Override protected void onPause() { super.onPause(); }
}
