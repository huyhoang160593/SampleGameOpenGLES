package com.example.samplegamefix;

import android.content.Context;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.opengl.GLSurfaceView;
import android.util.AttributeSet;
import android.util.Log;

import com.example.samplegamefix.engine.GameEngine;

import java.util.Map;

public class GameGLSurfaceView extends GLSurfaceView
{
    private GameGLRenderer _renderer;
    private GameEngine _engine;
    private Log Logging;

    public GameGLSurfaceView (Context context)
    {
        super(context);
        init();
    }

    public GameGLSurfaceView (Context context, AttributeSet attrs)
    {
        super(context, attrs);
        init();
    }

    private void init ()
    {
        Logging.d("Initiate","init");

        setEGLContextClientVersion(2);

//        setEGLConfigChooser(false);
//        getHolder().setFormat(PixelFormat.RGBA_8888);
        setEGLConfigChooser(8, 8, 8, 8, 16, 0);
        getHolder().setFormat(PixelFormat.TRANSLUCENT);
        setBackgroundResource(R.drawable.skybackground1);
        setZOrderOnTop(true);

        if (!isInEditMode())
        {
            _renderer = new GameGLRenderer();
            setRenderer(_renderer);
            setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY);
        }

        _engine = new GameEngine(getContext());
        _renderer.setGameEngine(_engine);
    }

    public void exitGame ()
    {
        // run this on the glthread
        queueEvent(new Runnable()
        {
            @Override
            public void run ()
            {
                _engine.destroy();
            }
        });
    }

    public void setViewLocations (final Map<Integer, Rect> viewLocations)
    {
        // run this on the glthread
        queueEvent(new Runnable()
        {
            @Override
            public void run ()
            {
                _engine.setViewLocations(viewLocations);
            }
        });
    }

    public void startGame ()
    {
        // run this on the glthread
        queueEvent(new Runnable()
        {
            @Override
            public void run ()
            {
                _engine.startGame();
            }
        });
    }
}

