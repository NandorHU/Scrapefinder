package hu.nandor.pokelens;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public class BattlePresenceTest {
    private final List<String> moves=Arrays.asList("tackle","water-gun");
    @Test public void animatedFramesStillConfirmAnUnchangedBattle(){
        BattlePresence state=new BattlePresence();ScanGate gate=new ScanGate();
        for(int read=0;read<8;read++){
            gate.observe(read*10);long token=gate.begin(read*500);assertTrue(token>=0);
            for(int frame=1;frame<10;frame++)gate.observe(read*10+frame);
            assertTrue("Decorative frames must not invalidate the OCR response",gate.finish(token));
            BattlePresence.Mode mode=state.observe(true,"charizard","squirtle",moves);
            assertEquals(read==0?BattlePresence.Mode.VERIFYING:BattlePresence.Mode.READY,mode);
        }
    }
    @Test public void switchingNamesNeedsTwoMatchingReadsAndRouteClearsConfirmation(){
        BattlePresence state=new BattlePresence();
        state.observe(true,"charizard","squirtle",moves);assertEquals(BattlePresence.Mode.READY,state.observe(true,"charizard","squirtle",moves));
        assertEquals(BattlePresence.Mode.VERIFYING,state.observe(true,"swampert","squirtle",moves));
        assertEquals(BattlePresence.Mode.READY,state.observe(true,"swampert","squirtle",moves));
        assertEquals(BattlePresence.Mode.WAITING,state.observe(false,"swampert","squirtle",moves));
        assertEquals(BattlePresence.Mode.VERIFYING,state.observe(true,"swampert","squirtle",moves));
    }
    @Test public void manualNamesDoNotTurnAPathScreenIntoBattle(){
        BattlePresence state=new BattlePresence();assertEquals(BattlePresence.Mode.WAITING,state.observe(false,"ditto","pikachu",Arrays.asList("nuzzle")));
    }
    @Test public void unreadableEnemyDoesNotKeepOldBattleAndMoveOrderingDoesNotResetIt(){
        BattlePresence state=new BattlePresence();state.observe(true,"charizard","squirtle",moves);
        assertEquals(BattlePresence.Mode.READY,state.observe(true,"charizard","squirtle",Arrays.asList("water-gun","tackle")));
        assertEquals(BattlePresence.Mode.UNCERTAIN,state.observe(true,null,"squirtle",moves));
        assertEquals(BattlePresence.Mode.VERIFYING,state.observe(true,"charizard","squirtle",moves));
    }
    @Test public void changedMovesAreConfirmedAndExplicitResetRequiresConfirmationAgain(){
        BattlePresence state=new BattlePresence();state.observe(true,"charizard","squirtle",moves);state.observe(true,"charizard","squirtle",moves);
        assertEquals(BattlePresence.Mode.VERIFYING,state.observe(true,"charizard","squirtle",Arrays.asList("tackle")));
        state.reset();assertEquals(BattlePresence.Mode.WAITING,state.mode());
        assertEquals(BattlePresence.Mode.VERIFYING,state.observe(true,"charizard","squirtle",moves));
    }
}
