import java.util.TimerTask;

public class HeartbeatsTask extends TimerTask {

    private RaftServer server;
  
    public HeartbeatsTask(RaftServer server) {
        super();
        this.server = server;
    }
  
    @Override
    public void run() {
        try {
            if (server.getServerState() == RaftServerState.LEADER) {
                for (Long serverId : server.getCfg().getInfos().keySet()) {
                    if (serverId != server.getId()) {
                        RaftRMIInterface serverInterface = server.getServerInterface(serverId);
                        try {
                            serverInterface.appendEntries(server.getDb().readCurrentTerm().get(), server.getId(), 0L, new RaftEntry[0], 0L);
                        } catch (Exception ex) {
                            // Server is likely not reachable
                        }
                    }
                }
            }
        } catch (Exception ex) {
            server.log("HeartbeatsTask.", ex);
        }
    }
}
