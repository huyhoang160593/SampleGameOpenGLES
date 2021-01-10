package com.example.samplegamefix;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Context;
import android.graphics.Rect;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.WindowManager;

import com.example.samplegamefix.sprites.TextureSprite;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;

public class GameActivity extends AppCompatActivity implements ViewTreeObserver.OnGlobalLayoutListener {

    private MediaPlayer FXPlayer;

    public void playSound(Context context, int _id, boolean loppingSet)
    {
        if(FXPlayer != null)
        {
            FXPlayer.stop();
            FXPlayer.release();
        }
        FXPlayer = MediaPlayer.create(context, _id);
        if(FXPlayer != null)
            FXPlayer.start();
        FXPlayer.setLooping(loppingSet);
    }

    @BindView(R.id.root_layout)
    View _root;

    @BindView(R.id.game_view)
    GameGLSurfaceView _gameGLSurfaceView;

    @Override
    protected void onCreate (Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_fullscreen);

        ButterKnife.bind(this);

        _root.getViewTreeObserver().addOnGlobalLayoutListener(this);
        FXPlayer = new MediaPlayer();
        playSound(this,R.raw.theworldrevolvingmusicbox,true);
    }

    @Override
    protected void onResume ()
    {
        super.onResume();
        _gameGLSurfaceView.onResume();

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        FXPlayer.start();
        toggleFullscreen(true);
    }

    @Override
    protected void onPause ()
    {
        _gameGLSurfaceView.onPause();

        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        FXPlayer.pause();
        super.onPause();
    }

    @Override
    protected void onDestroy ()
    {
        TextureSprite.clearTextureCache();
        _gameGLSurfaceView.exitGame();
        super.onDestroy();
    }

    @OnClick(R.id.game_view)
    public void clickGame()
    {
        _gameGLSurfaceView.startGame();
    }

    /**
     * when the view is laid out, figure out where the icons are
     * and tell the opengl code about that location so it can convert it to opengl coordinates
     */
    @Override
    public void onGlobalLayout ()
    {
        _gameGLSurfaceView.setViewLocations(getViewLocations());
    }

    private Map<Integer, Rect> getViewLocations ()
    {
        Map<Integer, Rect> locations = new HashMap<>();
        ArrayList<View> views = getAllChildren(_root);
        for (View view : views)
        {
            locations.put(view.getId(), getLocationInsideRoot(view));
        }
        return locations;
    }

    private ArrayList<View> getAllChildren (View v)
    {

        if (!(v instanceof ViewGroup))
        {
            ArrayList<View> viewArrayList = new ArrayList<>();
            viewArrayList.add(v);
            return viewArrayList;
        }

        ArrayList<View> result = new ArrayList<>();

        ViewGroup viewGroup = (ViewGroup) v;
        for (int i = 0; i < viewGroup.getChildCount(); i++)
        {

            View child = viewGroup.getChildAt(i);

            ArrayList<View> viewArrayList = new ArrayList<>();
            viewArrayList.add(v);
            viewArrayList.addAll(getAllChildren(child));

            result.addAll(viewArrayList);
        }
        return result;
    }

    private Rect getLocationInsideRoot (View view)
    {
        Rect rootLocation = new Rect();
        _root.getGlobalVisibleRect(rootLocation);

        Rect r = new Rect();
        view.getGlobalVisibleRect(r);
        r.left -= rootLocation.left;
        r.top -= rootLocation.top;
        r.right -= rootLocation.left;
        r.bottom -= rootLocation.top;
        return r;
    }

    private void toggleFullscreen (boolean fullScreen)
    {
        if (fullScreen)
        {
            getWindow().getDecorView()
                    .setSystemUiVisibility(
                            View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                                    | View.SYSTEM_UI_FLAG_FULLSCREEN
                                    | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        }
        else
        {
            getWindow().getDecorView()
                    .setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE);
        }
    }
}
