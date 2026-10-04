package hu.nandor.pokelens.fixture;
import android.app.Activity;
import android.os.Bundle;
import android.graphics.*;
import android.view.View;
/** Static game surface in another process: tap changes enemy and the whole move menu. */
public final class BattleActivity extends Activity {
 @Override public void onCreate(Bundle state){super.onCreate(state);setContentView(new Scene(this));}
 private static final class Scene extends View {
  final Paint ink=new Paint(Paint.ANTI_ALIAS_FLAG);boolean switched;
  Scene(Activity context){super(context);setOnClickListener(v->{switched=!switched;invalidate();});}
  @Override protected void onDraw(Canvas canvas){canvas.drawColor(Color.WHITE);ink.setColor(Color.BLACK);ink.setTypeface(Typeface.DEFAULT_BOLD);ink.setTextSize(getWidth()*.037f);
   canvas.drawText(switched?"SWAMPERT":"CHARIZARD",getWidth()*.08f,getHeight()*.23f,ink);canvas.drawText(switched?"VENUSAUR":"SQUIRTLE",getWidth()*.55f,getHeight()*.40f,ink);
   String[] moves=switched?new String[]{"ENERGY BALL","THUNDERBOLT","TACKLE","TAIL WHIP"}:new String[]{"ROCK SLIDE","WATER GUN","TACKLE","TAIL WHIP"};
   for(int i=0;i<4;i++)canvas.drawText(moves[i],getWidth()*(i%2==0?.08f:.57f),getHeight()*(i<2?.64f:.74f),ink);
  }
 }
}
