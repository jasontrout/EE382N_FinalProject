import java.util.concurrent.atomic.AtomicInteger;
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
            long currentTerm;
            synchronized (server.getLock()) {
                if (server.getHadLeaderActivity().getAndSet(false)) {
                    return;
                }
                if (server.getServerState() == RaftServerState.LEADER) {
                    server.getHadLeaderActivity().set(true);
                    return;
                }
                currentTerm = server.getDb().readCurrentTerm().get() + 1;
                server.getDb().writeCurrentTerm(new AtomicLong(currentTerm));
                server.getDb().writeVotedFor(new AtomicLong(server.getId()));
                server.setServerState(RaftServerState.CANDIDATE);
                server.log("Voted for self."); 
            }
            AtomicInteger numVotes = new AtomicInteger(0);
            int numMajority = (server.getCfg().getNumServers() + 1) / 2;
            for (Long serverId : server.getCfg().getInfos().keySet()) {
                if (serverId != server.getId()) {
                    RaftRMIInterface serverInterface = server.getServerInterface(serverId);
                    try {
                        RaftRequestVoteResult result = serverInterface.requestVote(server.getDb().readCurrentTerm().get(), server.getId(), 0L, 0L);
                        if (result.getTerm() > currentTerm) {
                            server.getDb().writeCurrentTerm(new AtomicLong(result.getTerm()));
                            server.getDb().writeVotedFor(null);
                            server.setServerState(RaftServerState.FOLLOWER); 
                            server.getHadLeaderActivity().set(true);
                            return;
                        }         
                        if (result.getVoteGranted() && numVotes.incrementAndGet() == numMajority) {
                            synchronized (server.getLock()) {
                                if (server.getServerState() != RaftServerState.CANDIDATE || server.getDb().readCurrentTerm().get() != currentTerm) {
                                    return;
                                }
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
