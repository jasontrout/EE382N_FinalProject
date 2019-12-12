import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicLong;

public class HeartbeatsTask extends TimerTask {

    private RaftServer server;
  
    public HeartbeatsTask(RaftServer server) {
        super();
        this.server = server;
    }
  
    @Override
    public void run() {
        try {
            long currentTerm;
            synchronized (server.getLock()) {
                if (server.getServerState() != RaftServerState.LEADER) {
                    return;
                }
                server.getHadLeaderActivity().set(true);
                currentTerm = server.getDb().readCurrentTerm().get();
            }
            for (Long serverId : server.getCfg().getInfos().keySet()) {
                if (serverId != server.getId()) {
                    new Thread() {
                        @Override 
                        public void run() {
                            RaftRMIInterface serverInterface = server.getServerInterface(serverId);
                            try {   
                                RaftAppendEntriesResult result = serverInterface.appendEntries(server.getDb().readCurrentTerm().get(), server.getId(), 0L, new RaftEntry[0], 0L);
                                if (result.getTerm() != currentTerm) {
                                    if (result.getTerm() > currentTerm) {
                                        server.getDb().writeCurrentTerm(new AtomicLong(result.getTerm()));
                                        server.getDb().writeVotedFor(null);
                                        server.setServerState(RaftServerState.FOLLOWER); 
                                        server.getHadLeaderActivity().set(true);
                                    }
                                    return;
                                }         
                                synchronized (server.getLock()) {
                                    if (server.getServerState() != RaftServerState.LEADER) {
                                        return;
                                    }
                                    if (currentTerm != server.getDb().readCurrentTerm().get()) {
                                        return;
                                     }
                                }
                            } catch (Exception ex) {
                                // Server is likely not reachable
                            }
                        }
                    }.start();
                }
            }
        } catch (Exception ex) {
            server.log("HeartbeatsTask.", ex);
        }
    }
}
