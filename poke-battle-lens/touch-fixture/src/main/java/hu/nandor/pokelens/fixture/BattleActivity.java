package hu.nandor.pokelens.fixture;
import android.app.Activity;
import android.os.Bundle;
import android.graphics.*;
import android.view.View;
/** Animated game surface: tap cycles battle, changed battle, route, neutral/status battle. */
public final class BattleActivity extends Activity {
 @Override public void onCreate(Bundle state){super.onCreate(state);setContentView(new Scene(this));}
 private static final class Scene extends View {
  final Paint ink=new Paint(Paint.ANTI_ALIAS_FLAG);int phase;
  Scene(Activity context){super(context);setOnClickListener(v->{phase=(phase+1)%4;invalidate();});}
  @Override protected void onDraw(Canvas canvas){int pulse=(int)((android.os.SystemClock.uptimeMillis()/40)%25);canvas.drawColor(phase==0?Color.WHITE:Color.rgb(230+pulse,250-pulse,235));ink.setColor(Color.BLACK);ink.setTypeface(Typeface.DEFAULT_BOLD);ink.setTextSize(getWidth()*.037f);
   if(phase==2){canvas.drawText("CHOOSE A PATH",getWidth()*.08f,getHeight()*.23f,ink);canvas.drawText("LEFT",getWidth()*.08f,getHeight()*.64f,ink);canvas.drawText("RIGHT",getWidth()*.57f,getHeight()*.64f,ink);canvas.drawText("CONTINUE",getWidth()*.08f,getHeight()*.74f,ink);postInvalidateDelayed(40);return;}
   canvas.drawText(phase==1?"SWAMPERT":phase==3?"DITTO":"CHARIZARD",getWidth()*.08f,getHeight()*.23f,ink);canvas.drawText(phase==1?"VENUSAUR":phase==3?"PIKACHU":"SQUIRTLE",getWidth()*.55f,getHeight()*.40f,ink);
   String[] moves=phase==1?new String[]{"ENERGY BALL","THUNDERBOLT","TACKLE","TAIL WHIP"}:phase==3?new String[]{"CHARM","NASTY PLOT","PLAY NICE","NUZZLE"}:new String[]{"ROCK SLIDE","WATER GUN","TACKLE","TAIL WHIP"};
   for(int i=0;i<4;i++)canvas.drawText(moves[i],getWidth()*(i%2==0?.08f:.57f),getHeight()*(i<2?.64f:.74f),ink);
   if(phase!=0)postInvalidateDelayed(40);
  }
 }
}
