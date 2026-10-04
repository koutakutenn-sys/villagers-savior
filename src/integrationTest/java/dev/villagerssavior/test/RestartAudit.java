package dev.villagerssavior.test;
import dev.villagerssavior.*;
import net.minecraft.server.MinecraftServer;
import java.util.*;
import java.nio.file.*;
public final class RestartAudit {
    private static final UUID PLAYER=UUID.fromString("c4e62d5c-3b1c-4209-a24d-38e449a77044");
    private static final UUID VILLAGER=UUID.fromString("e36272dd-7af5-4f44-810f-58a973acda41");
    public static void run(MinecraftServer server){
        var log=new ArrayList<String>();boolean pass=true;
        try{
            var state=SaviorState.get(server.overworld());long now=server.overworld().getGameTime();
            String village=state.village(List.of("restart-test-poi-a","restart-test-poi-b"));
            if(System.getProperty("audit.restart").equals("write")){
                check(state.dailyGrant("kill",PLAYER,VILLAGER,now,3,5)==3,"fresh kill ledger",log);
                check(state.dailyGrant("repair",PLAYER,VILLAGER,now,2,3)==2,"fresh repair ledger",log);
                check(state.golemKill(PLAYER,VILLAGER,now)==1,"first kill history",log);
                check(state.golemKill(PLAYER,VILLAGER,now)==2,"second kill history",log);
                state.cooldown("emergency",PLAYER,village,now);state.cooldown("raid",PLAYER,village,now);
                check(!state.ready("emergency",PLAYER,village,now),"emergency cooldown written",log);
                check(!state.ready("raid",PLAYER,village,now),"raid cooldown written",log);
            }else{
                check(state.dailyGrant("kill",PLAYER,VILLAGER,now,5,5)==2,"kill cap survives full JVM restart",log);
                check(state.dailyGrant("repair",PLAYER,VILLAGER,now,3,3)==1,"repair cap survives full JVM restart",log);
                check(state.golemKill(PLAYER,VILLAGER,now)==3,"rolling golem history survives full JVM restart",log);
                check(!state.ready("emergency",PLAYER,village,now),"emergency cooldown survives full JVM restart",log);
                check(!state.ready("raid",PLAYER,village,now),"raid cooldown survives full JVM restart",log);
                check(state.ready("emergency",UUID.randomUUID(),village,now),"other player remains independent after restart",log);
            }
        }catch(Throwable e){pass=false;log.add("FAIL "+e);e.printStackTrace();}
        pass=pass&&log.stream().noneMatch(s->s.startsWith("FAIL"));
        String report=(pass?"PASS":"FAIL")+": "+log.size()+" restart checks\n"+String.join("\n",log)+"\n";
        try{Files.writeString(Path.of("test-result.txt"),report);}catch(Exception e){throw new RuntimeException(e);}
        System.out.println(report);
    }
    private static void check(boolean b,String label,List<String> log){log.add((b?"PASS ":"FAIL ")+label);}
}
