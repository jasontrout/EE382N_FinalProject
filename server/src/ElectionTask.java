import java.util.concurrent.atomic.AtomicLong;
import java.util.TimerTask;

public class ElectionTask extends TimerTask {

    private RaftServer server;
  
    public ElectionTask(RaftServer server) {
        super();
        this.server = server;
    }
  
    @Override
    public void run() {
        try {
            if (server.getServerState() == RaftServerState.LEADER) {
                return;
            }
            synchronized (server.getLock()) {
                AtomicLong currentTerm = server.getDb().readCurrentTerm();
                server.getDb().writeCurrentTerm(currentTerm);
                server.setServerState(RaftServerState.CANDIDATE);
                server.getDb().writeVotedFor(new AtomicLong(server.getId()));
                server.getNumVotes().incrementAndGet();
            }
            server.log("Voted for self."); 
            for (Long serverId : server.getCfg().getInfos().keySet()) {
                if (serverId != server.getId()) {
                    RaftRMIInterface serverInterface = server.getServerInterface(serverId);
                    try {
                        RaftRequestVoteResult result = serverInterface.requestVote(server.getDb().readCurrentTerm().get(), server.getId(), 0L, 0L);
                        if (result.getVoteGranted()) {
                            if (server.getNumVotes().incrementAndGet() == server.getNumMajority()) {
                                server.setServerState(RaftServerState.LEADER);
                            }
                        }
                    } catch (Exception ex) {
                        // Server is likely not reachable.
                    }
                }
            }
        } catch (Exception ex) {
            server.log("ElectionTimeTask.", ex);
        }
    }
}
