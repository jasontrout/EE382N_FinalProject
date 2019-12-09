import java.util.TimerTask;

public class ElectionTimeoutTask extends TimerTask {

    private RaftServer server;
  
    public ElectionTimeoutTask(RaftServer server) {
        super();
        this.server = server;
    }
  
    @Override
    public void run() {
        try {
            server.incrementCurrentTerm();
            server.setServerStateTo(RaftServerState.CANDIDATE);
            server.setServerVotedFor(server.getId());
            server.incrementNumVotes();
            server.startElectionTimeoutTimer();
            for (Long serverId : server.getCfg().getInfos().keySet()) {
                if (serverId != server.getId()) {
                    RaftRMIInterface serverInterface = server.getServerInterface(serverId);
                    try {
                        RaftRequestVoteResult result = serverInterface.requestVote(server.getCurrentTerm(), server.getId(), 0L, 0L);
                        if (result.getVoteGranted()) {
                            server.incrementNumVotes();
                            if (server.getNumVotes() == server.getNumSimpleMajority()) {
                                server.setServerStateTo(RaftServerState.LEADER);
                                server.setVotedFor(server.getId());
                                server.stopElectionTimeoutTimer();
                                server.startLeaderHeartbeatsTimer();
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
