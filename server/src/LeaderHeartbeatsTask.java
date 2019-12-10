import java.util.TimerTask;

public class LeaderHeartbeatsTask extends TimerTask {

    private RaftServer server;
  
    public LeaderHeartbeatsTask(RaftServer server) {
        super();
        this.server = server;
    }
  
    @Override
    public void run() {
        try {
            for (Long serverId : server.getCfg().getInfos().keySet()) {
                if (serverId != server.getId()) {
                    RaftRMIInterface serverInterface = server.getServerInterface(serverId);
                    try {
                        serverInterface.appendEntries(server.getCurrentTerm(), server.getId(), 0L, new RaftEntry[0], 0L);
                    } catch (Exception ex) {
                        // Server is likely not reachable
                    }
                }
            }
        } catch (Exception ex) {
            server.log("LeaderHeartbeatsTask.", ex);
        }
    }
}
