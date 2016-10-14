package co.edu.udea.computacionmovil.pongdisk2;

import android.os.Bundle;
import android.preference.PreferenceActivity;

/**
 * Created by estudiantelis on 13/10/16.
 */
public class PongPreferencias extends PreferenceActivity {
    @Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        addPreferencesFromResource(R.xml.preferences);
    }
}