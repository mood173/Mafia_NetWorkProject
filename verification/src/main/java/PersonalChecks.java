import StreamSystem.*;
import com.mafia.common.protocol.*;
import java.util.*;

public final class PersonalChecks {
    static int checks;
    static void check(boolean ok,String label){checks++;if(!ok)throw new AssertionError(label);}
    public static void main(String[] args){flow(); System.out.println("PASS week 10 personal checks: "+checks);}

    static void flow() {
        StreamManager f = new StreamManager();
        check(f.app()==AppStream.main,"initial");
        f.accept(Message.of(MessageType.LOGIN_OK,"A"));check(f.app()==AppStream.Lobby,"login");
        f.accept(Message.of(MessageType.ERROR,"bad"));check(f.app()==AppStream.Lobby,"error stays");
        f.accept(Message.of(MessageType.GAME_START,"A,B,C,D,E,F"));check(f.app()==AppStream.GameScene,"game");
        f.accept(Message.of(MessageType.PHASE_CHANGE,"NIGHT","40"));check(f.game()==gameStream.NIGHT,"phase");
        f.accept(Message.of(MessageType.GAME_END,"CITIZEN","A:MAFIA"));check(f.app()==AppStream.End,"result");
        f.disconnected();check(f.app()==AppStream.main,"disconnect");
    }
}
