package com.mafia.common.protocol;

import java.util.Arrays;
import java.util.List;

/** 10주차 규격을 유지하고 누락 필드를 거절한다. 마지막 필드의 | 보존은 CHAT만 허용. */
public final class MessageCodec {
    public static final int MAX_LENGTH = 8192;

    public String encode(Message message, Direction direction) {
        if(message.fields().size()!=message.type().fields(direction))
            throw new IllegalArgumentException("방향 또는 필드 수 오류: "+message.type());
        return encode(message);
    }

    public String encode(Message message) {
        int count = message.fields().size();
        if (count != message.type().fields(Direction.CLIENT_TO_SERVER)
                && count != message.type().fields(Direction.SERVER_TO_CLIENT))
            throw new IllegalArgumentException("필드 수 오류: " + message.type());
        for (int i=0; i<count; i++) {
            String f=message.field(i);
            if (f.indexOf('\n')>=0 || f.indexOf('\r')>=0)
                throw new IllegalArgumentException("개행 불가");
            if (f.contains("|") && !(message.type()==MessageType.CHAT && i==count-1))
                throw new IllegalArgumentException("구분 문자 불가");
        }
        String line=message.type().name() + (count==0 ? "" : "|" + String.join("|",message.fields()));
        if(line.length()>MAX_LENGTH) throw new IllegalArgumentException("메시지가 너무 김");
        return line;
    }

    public Message decode(String line, Direction direction) {
        if(line==null || line.isBlank() || line.length()>MAX_LENGTH || line.indexOf('\n')>=0 || line.indexOf('\r')>=0)
            throw new IllegalArgumentException("잘못된 메시지 길이 또는 개행");
        String[] head=line.split("\\|",2);
        MessageType type;
        try { type=MessageType.valueOf(head[0]); }
        catch(IllegalArgumentException e) { throw new IllegalArgumentException("모르는 메시지 종류"); }
        int expected=type.fields(direction);
        if(expected<0) throw new IllegalArgumentException("보낼 수 없는 방향: "+type);
        if(expected==0) {
            if(head.length!=1) throw new IllegalArgumentException("불필요한 필드");
            return new Message(type,List.of());
        }
        if(head.length<2) throw new IllegalArgumentException("필드 누락");
        String[] fields=head[1].split("\\|",type==MessageType.CHAT ? expected : -1);
        if(fields.length!=expected) throw new IllegalArgumentException("필드 수 오류: "+type);
        return new Message(type,Arrays.asList(fields));
    }
}
