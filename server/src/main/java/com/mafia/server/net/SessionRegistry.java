package com.mafia.server.net;
import java.util.concurrent.ConcurrentHashMap;
public final class SessionRegistry {
    private final ConcurrentHashMap<String,Session> sessions=new ConcurrentHashMap<>();
    public boolean register(String name,Session session) { return sessions.putIfAbsent(name,session)==null; }
    public void remove(String name,Session session) { if(name!=null) sessions.remove(name,session); }
}
