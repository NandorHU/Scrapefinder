package hu.nandor.pokelens.fixture;
import android.app.Activity;
import android.os.Bundle;
import android.widget.Button;
/** Separate UID; only installed in the CI emulator to test real pass-through touches. */
public final class TouchActivity extends Activity {
 private int taps;
 @Override public void onCreate(Bundle state){super.onCreate(state);Button target=new Button(this);target.setText("Fixture taps: 0");target.setTextSize(24);target.setAllCaps(false);target.setOnClickListener(v->target.setText("Fixture taps: "+(++taps)));setContentView(target);}
}
