package co.edu.udea.computacionmovil.pongdisk2;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;

/**
 * Created by estudiantelis on 13/10/16.
 */
public class PongDisk extends Activity {

    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        setContentView(R.layout.act_title);
        setListeners();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.game_menu, menu);
        return true;
    }

    public boolean onOptionsItemSelected(MenuItem item) {
        switch(item.getItemId()) {
            case R.id.menu_preferences:
                Intent i = new Intent(this, PongPreferencias.class);
                startActivity(i);
                break;
        }
        return false;
    }


    protected void setListeners () {
        this.findViewById(R.id.title_btnNoPlayer)
                .setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startGame(false,false);
                    }
                });

        this.findViewById(R.id.title_btnOnePlayer)
                .setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startGame(false,true);
                    }
                });

        this.findViewById(R.id.title_btnTwoPlayer)
                .setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startGame(true,true);
                    }
                });
    }

    protected void startGame(boolean redPlayer, boolean bluePlayer) {
        Intent i = new Intent(this, ActividadDelJuego.class);
        i.putExtra(ActividadDelJuego.EXTRA_BLUE_PLAYER, bluePlayer);
        i.putExtra(ActividadDelJuego.EXTRA_RED_PLAYER, redPlayer);
        startActivity(i);
    }


    public static final String
            PREF_BALL_SPEED = "ball_speed",
            PREF_STRATEGY = "strategy",
            PREF_LIVES = "lives",
            PREF_HANDICAP = "handicap",
            PREF_MUTED = "muted";

    public static final String
            KEY_AI_STRATEGY = "key_ai_strategy";
}