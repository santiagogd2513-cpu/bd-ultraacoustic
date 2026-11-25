package com.colombiria.game;

import android.app.Activity;
import android.os.Bundle;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.MotionEvent;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.view.View;

public final class MainActivity extends Activity implements SurfaceHolder.Callback {
    static { System.loadLibrary("colombiria_vulkan"); }
    private native void nativeStart(Surface surface);
    private native void nativeStop();
    private native void nativeSetThrottle(boolean enabled);
    private native void nativeSetSteering(float value);
    private SurfaceView surfaceView;
    private boolean surfaceReady;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        FrameLayout root = new FrameLayout(this);
        surfaceView = new SurfaceView(this);
        surfaceView.getHolder().addCallback(this);
        root.addView(surfaceView, new FrameLayout.LayoutParams(-1, -1));

        TextView hud = new TextView(this);
        hud.setText("COLOMBIRIA  •  GRAPHICS ENGINE / VULKAN");
        hud.setTextColor(0xffffffff);
        hud.setTextSize(13);
        hud.setShadowLayer(3f, 1f, 2f, 0xff10202a);
        hud.setPadding(22, 16, 22, 16);
        hud.setBackgroundColor(0x660b1720);
        root.addView(hud);

        surfaceView.setOnTouchListener((v, e) -> {
            if (e.getAction() == MotionEvent.ACTION_DOWN) {
                nativeSetThrottle(true);
                return true;
            }
            if (e.getAction() == MotionEvent.ACTION_UP || e.getAction() == MotionEvent.ACTION_CANCEL) {
                nativeSetThrottle(false);
                return true;
            }
            if (e.getAction() == MotionEvent.ACTION_MOVE) {
                nativeSetSteering((e.getX() / Math.max(1f, v.getWidth()) - .5f) * 2f);
                return true;
            }
            return true;
        });
        setContentView(root);
    }

    @Override public void surfaceCreated(SurfaceHolder holder) {
        surfaceReady = true;
        nativeStart(holder.getSurface());
    }
    @Override public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) { }
    @Override public void surfaceDestroyed(SurfaceHolder holder) {
        surfaceReady = false;
        nativeStop();
    }
    @Override protected void onPause() {
        if (surfaceReady) nativeStop();
        super.onPause();
    }
    @Override protected void onDestroy() {
        nativeStop();
        super.onDestroy();
    }
}
