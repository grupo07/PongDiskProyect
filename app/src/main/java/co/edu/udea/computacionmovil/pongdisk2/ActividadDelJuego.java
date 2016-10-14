package co.edu.udea.computacionmovil.pongdisk2;

import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.os.PowerManager;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.Window;
import android.view.WindowManager;

public class ActividadDelJuego extends AppCompatActivity {


    private PongView mPongView;
    private AlertDialog mAboutBox;
    protected PowerManager.WakeLock mWakeLock;

    public static final String
            EXTRA_RED_PLAYER = "red-is-player",
            EXTRA_BLUE_PLAYER = "blue-is-player";

    @Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        //setContentView(R.layout.activity_main);
        requestWindowFeature(Window.FEATURE_NO_TITLE);

        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);


        setContentView(R.layout.pong_view);
        mPongView = (PongView) findViewById(R.id.pong);

        Intent i = getIntent();
        Bundle b = i.getExtras();

        mPongView.setPlayerControl(b.getBoolean(EXTRA_RED_PLAYER, false), b.getBoolean(EXTRA_BLUE_PLAYER, false));
        mPongView.update();

        this.setVolumeControlStream(AudioManager.STREAM_MUSIC);

        final PowerManager pm = (PowerManager) this.getSystemService(Context.POWER_SERVICE);
        mWakeLock = pm.newWakeLock(PowerManager.SCREEN_DIM_WAKE_LOCK, "PongDisk");
        mWakeLock.acquire();
    }
    protected void onStop() {
        super.onStop();
        mPongView.stop();
    }

    protected void onResume() {
        super.onResume();
        mPongView.resume();
    }

    protected void onDestroy() {
        super.onDestroy();
        mPongView.release();
        mWakeLock.release();
    }

    public void hideAboutBox() {
        if (mAboutBox != null) {
            mAboutBox.hide();
            mAboutBox = null;
        }
    }


}
