import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.Thread;
import java.net.MalformedURLException;
import java.rmi.Naming;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.Map;
import java.util.Random;
import java.util.Timer;
import java.util.TimerTask;
import java.util.TreeMap;

public class RaftServer extends UnicastRemoteObject implements RaftRMIInterface {

    private static final long serialVersionUID = 1L;

    private static final int ELECTION_TIMEOUT_MIN_MILLIS = 1500;
    private static final int ELECTION_TIMEOUT_MAX_MILLIS = 3000;

    private static final int LEADER_HEARTBEATS_TIMER_MILLIS = 500;

    private static Long currentTerm;
    private static Long votedFor;
    private static RaftEntry[] log;

    private RaftServersCfg cfg;
    
    private Long id;
    private Long commitIndex;
    private Long lastApplied;
    private Long[] nextIndex;
    private Long[] matchIndex;

    private Long serverVotedFor;
    private int numSimpleMajority;
    private int numVotes;

    private Map<Long, Boolean> idToServerStateMap = new TreeMap<>();
    private Map<Long, RaftRMIInterface> idToServerInterfaceMap = new TreeMap<>();
    private boolean clusterFormed = false;
    private RaftServerState serverState = RaftServerState.FOLLOWER;

    private Timer electionTimeoutTimer;
    private ElectionTimeoutTask electionTimeoutTask;

    private Timer leaderHeartbeatsTimer;
    private LeaderHeartbeatsTask leaderHeartbeatsTask;

    protected RaftServer(Long id, RaftServersCfg cfg) throws RemoteException {
        super();
        this.id = id;
        this.cfg = cfg;
        currentTerm = 0L;
        votedFor = null;
    }

    // Get current server Id.
    public Long getId() {
        return id;
    }

    // Get config.
    public RaftServersCfg getCfg() {
        return cfg;
    }

    // Gets the current term.
    public static Long getCurrentTerm() { 
        return currentTerm; 
    }
  
    public static void setVotedFor(Long votedFor) {
        RaftServer.votedFor = votedFor;
    }

    // Increments the current term.
    public static void incrementCurrentTerm() {
        currentTerm++;
    }
  
    // Get number of votes this server has. 
    public int getNumVotes() {  
        return numVotes;
    }
  
    // Get server interface by ID.
    public synchronized RaftRMIInterface getServerInterface(Long serverId) {
        return idToServerInterfaceMap.get(serverId);
    }

    // Get number of a simple majority.
    public int getNumSimpleMajority() {
        return numSimpleMajority;
    }

    // Increment number of votes.
    public void incrementNumVotes() { 
        numVotes++;
    }

    // Sets the server state to FOLLOWER, CANDIDATE, or LEADER.
    public void setServerStateTo(RaftServerState serverState) {
        numVotes = 0;
        serverVotedFor = null;
        this.serverState = serverState;
        log("I am a " + this.serverState + ".");
    }
 
    // Sets the local voted for variable.
    public void setServerVotedFor(Long serverVotedFor) {
        this.serverVotedFor = serverVotedFor;
    }

    // Stop election timeout timer.
    public void stopElectionTimeoutTimer() {
        if (electionTimeoutTask != null) {
            electionTimeoutTask.cancel();
        } 
        if (electionTimeoutTimer != null) {
            electionTimeoutTimer.cancel();
        }
    }

    // Start election timeout timer.
    public void startElectionTimeoutTimer() {
        stopElectionTimeoutTimer();
        electionTimeoutTimer = new Timer();
        int electionTimeoutMillis = new Random().nextInt((ELECTION_TIMEOUT_MAX_MILLIS - ELECTION_TIMEOUT_MIN_MILLIS) + 1)  + ELECTION_TIMEOUT_MIN_MILLIS;
        electionTimeoutTimer.schedule(new ElectionTimeoutTask(this), electionTimeoutMillis);
    }

    // Stop leader heartbeats timer.
    public void stopLeaderHeartbeatsTimer() {
        if (leaderHeartbeatsTask != null) { 
            leaderHeartbeatsTask.cancel();
        }
        if (leaderHeartbeatsTimer != null) {
            leaderHeartbeatsTimer.cancel();
        }
    }

    // Start leader heartbeats timer.
    public void startLeaderHeartbeatsTimer() {
        stopLeaderHeartbeatsTimer();
        leaderHeartbeatsTimer = new Timer();
        leaderHeartbeatsTimer.schedule(new LeaderHeartbeatsTask(this), 0, LEADER_HEARTBEATS_TIMER_MILLIS);
    }
   
    public void init() throws MalformedURLException, RemoteException, NotBoundException {

        // Number of servers that make up the simple majority.
        numSimpleMajority = cfg.getNumServers() / 2 + 1;

        Naming.rebind("//" + cfg.getInfoById(id).getHostname() + "/" + id, this);
        log("Server started.");

        // Add this server to the other servers to form a cluster.  
        for (Long serverId : cfg.getInfos().keySet()) {
            RaftRMIInterface serverInterface = (RaftRMIInterface)Naming.lookup("//" + cfg.getInfoById(serverId).getHostname() + "/" + serverId);
            idToServerInterfaceMap.put(serverId, serverInterface);
        }


        // Add server to other servers' cluster lists.
        for (Long serverId : cfg.getInfos().keySet()) {
            if (serverId != id) {
                idToServerInterfaceMap.get(serverId).addServerToCluster(id);
            }
        }
      
        // Handle shutdown.
        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                try {
                    stopElectionTimeoutTimer();
                    stopLeaderHeartbeatsTimer();
                } catch (Exception ex) {
                    log("Handle shutdown.", ex);
                }
          }
        });
    }

    public static void main(String[] args) throws MalformedURLException, RemoteException, NotBoundException {
        if (args.length != 1) {
            System.out.println("Usage: RaftServer <id>");
            return;
        }
        Long initId = Long.parseLong(args[0]);
        RaftServersCfg initCfg = new RaftServersCfg("servers.cfg");
        try {
            String hostname = initCfg.getInfoById(initId).getHostname();
            int port = initCfg.getInfoById(initId).getPort();
            RaftServer server = new RaftServer(initId, initCfg);
            server.init();
        } catch (Exception ex) {
            StringWriter sw = new StringWriter();
            ex.printStackTrace(new PrintWriter(sw));
            System.err.println("RaftServer[ " + initId + "] Server exception: " + sw.toString());
        }
    }

    @Override
    public synchronized void addServerToCluster(Long id) throws RemoteException {
      idToServerStateMap.put(id, true);
      if (cfg.getNumServers() - 1 == idToServerStateMap.size()) {
        clusterFormed = true;
        log("Cluster is formed. I am a " + serverState + ".");
        startElectionTimeoutTimer();
      }
    }

    @Override
    public RaftRequestVoteResult requestVote(Long term, Long candidateId, Long lastLogIndex, Long lastLogTerm) throws RemoteException {
        boolean voteGranted = false;
        if (clusterFormed) {
            if (term < currentTerm) {
                voteGranted = false;
            } else {
                if ((votedFor == null || votedFor == candidateId) && (lastLogIndex >= 0)) {
                    // candidate's log is at least as up-to-date as receiver's log
                    voteGranted = true;
                }
            }
        }
        log("Received requestVote. Term= " + term + "; candidateId=" + candidateId + "; lastLogIndex=" + lastLogIndex + "; lastLogTerm=" + lastLogTerm + "; voteGranted=" + voteGranted);
        return new RaftRequestVoteResult(currentTerm, voteGranted);
    }

    @Override
    public RaftAppendEntriesResult appendEntries(Long term, Long leaderId, Long prevLogIndex, RaftEntry[] entries, Long leaderCommit) throws RemoteException {
      if (clusterFormed) {
         if (entries.length == 0) {
             switch (serverState) {
                 case FOLLOWER:
                     startElectionTimeoutTimer();
                     break;
                 case CANDIDATE:
                     setServerStateTo(RaftServerState.FOLLOWER);
                     startElectionTimeoutTimer();
                 case LEADER:
                     stopElectionTimeoutTimer();
                     break;
             } 
         }
      }
      return null;
    }

    // Log message. 
    public void log(String msg) {
        System.out.println("\nRaftServer[" +id + "] " + msg + "\n");
    }

    // Log message with exception.
    public void log(String msg, Exception ex) {
        StringWriter sw = new StringWriter();
        ex.printStackTrace(new PrintWriter(sw));
        log(msg + ":" + sw.toString());
    }
    
}
