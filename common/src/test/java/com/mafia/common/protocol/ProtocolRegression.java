package com.mafia.common.protocol;
public final class ProtocolRegression {
    private static void check(boolean b) { if(!b) throw new AssertionError(); }
    private static void rejects(Runnable r) { try { r.run(); } catch(IllegalArgumentException e) { return; } throw new AssertionError("invalid message accepted"); }
    @org.junit.jupiter.api.Test
    public void regression() throws Exception { main(new String[0]); }
    public static void main(String[] args) {
        MessageCodec c=new MessageCodec();
        for(MessageType t:MessageType.values()) for(Direction d:Direction.values()) if(t.usedIn(d)) {
            String[] fields=new String[t.fields(d)]; java.util.Arrays.fill(fields,"");
            Message m=Message.of(t,fields); check(m.equals(c.decode(c.encode(m,d),d)));
        }
        check(c.decode("CHAT|user|a|b",Direction.SERVER_TO_CLIENT).field(1).equals("a|b"));
        check(c.decode("VOTE|",Direction.CLIENT_TO_SERVER).fields().size()==1);
        rejects(()->c.decode("LOGIN",Direction.CLIENT_TO_SERVER));
        rejects(()->c.decode("LOGIN|a|b",Direction.CLIENT_TO_SERVER));
        rejects(()->c.decode("READY|true",Direction.SERVER_TO_CLIENT));
        rejects(()->c.decode("LEAVE_ROOM|",Direction.CLIENT_TO_SERVER));
        rejects(()->c.encode(Message.of(MessageType.LOGIN_OK,"a"),Direction.CLIENT_TO_SERVER));
        rejects(()->c.encode(Message.of(MessageType.CHAT,"a\nb")));
        rejects(()->c.decode("CHAT|"+"a".repeat(8192),Direction.CLIENT_TO_SERVER));
        System.out.println("PASS: protocol directions, fields, empty tails, chat separators and bounds");
    }
}
