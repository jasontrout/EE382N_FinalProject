import java.util.ArrayList;
import java.util.List;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicLong;
import java.util.Map;
import java.util.TreeMap;

public class HeartbeatsTask extends TimerTask {

    private RaftServer server;

    public HeartbeatsTask(RaftServer server) {
        super();
        this.server = server;
    }
  
    @Override
    public void run() {
        try {
            for (Long serverId : server.getCfg().getInfos().keySet()) {
                if (serverId != server.getId()) {
                    long currentTerm;
                    long prevLogIndex;
                    long prevLogTerm;
                    final List<RaftEntry> entries;
                    final long lastIndex;
                    long commitIndex;
                    synchronized (server.getLock()) {
                        if (server.getServerState() != RaftServerState.LEADER) {
                            return;
                        }
                        server.getHadLeaderActivity().set(true);
                        currentTerm = server.getDb().readCurrentTerm().get();
                        prevLogIndex = server.getNextIndicies().get(serverId) - 1;
                        RaftEntry prevLogEntry = server.getDb().readEntry(prevLogIndex);
                        if (prevLogEntry == null) {
                            prevLogTerm = -1;
                        } else {
                            prevLogTerm = prevLogEntry.getTerm();
                        }
                        if (prevLogIndex >= -1) {
                            entries = server.getDb().readEntries(prevLogIndex + 1);
                        } else {
                            entries = new ArrayList<>();
                        }
                       commitIndex = server.getCommitIndex();
                       RaftEntry lastLogEntry = server.getDb().readLastEntry();
                       lastIndex = (lastLogEntry != null) ? lastLogEntry.getIndex() : -1;
                    }
                    new Thread() {
                        @Override 
                        public void run() {
                            RaftRMIInterface serverInterface = server.getServerInterface(serverId);
                            try {   
                                RaftAppendEntriesResult result = serverInterface.appendEntries(server.getDb().readCurrentTerm().get(), server.getId(), prevLogIndex, prevLogTerm, entries.toArray(new RaftEntry[entries.size()]), commitIndex);
                                if (result.getTerm() != currentTerm) {
                                    if (result.getTerm() > currentTerm) {
                                        server.getDb().writeCurrentTerm(new AtomicLong(result.getTerm()));
                                        server.getDb().writeVotedFor(null);
                                        server.setServerState(RaftServerState.FOLLOWER); 
                                    }
                                    return;
                                 } 
                                 synchronized (server.getLock()) {
                                     if (server.getServerState() != RaftServerState.LEADER) {
                                         return;
                                     }
                                     if (!result.getSuccess()) {
                                       server.getNextIndicies().put(serverId, prevLogIndex - 1);
                                     } else {
                                       server.getNextIndicies().put(serverId, lastIndex + 1);
                                       server.getMatchIndicies().put(serverId, lastIndex);
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
