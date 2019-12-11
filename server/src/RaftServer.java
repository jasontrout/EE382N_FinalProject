import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.Thread;
import java.net.MalformedURLException;
import java.rmi.Naming;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.TreeMap;
import java.util.TreeSet;

public class RaftServer extends UnicastRemoteObject implements RaftRMIInterface {

    private static final long serialVersionUID = 1L;

    private static final int ELECTION_TIMER_MIN_MILLIS = 1500; // Make 1.5s due to logging.
    private static final int ELECTION_TIMER_MAX_MILLIS = 3000; // Make 3.0s due to logging.

    private static final int LEADER_HEARTBEATS_TIMER_MILLIS = 500; // Make 0.5s due to logging.

    private Object lock = new Object();

    private RaftServersCfg cfg;
    private RaftServerDb db;

    private Long id;
    private AtomicInteger numVotes = new AtomicInteger(0);
    private int numMajority;

    private AtomicBoolean hadLeaderActivity = new AtomicBoolean(false);

    
    private Long lastKnownLeaderId = null;


    private Map<Long, Boolean> idToServerStateMap = new TreeMap<>();
    private Map<Long, RaftRMIInterface> idToServerInterfaceMap = new TreeMap<>();
    private boolean clusterFormed = false;
    private RaftServerState serverState = RaftServerState.FOLLOWER;

    private Timer electionTimer;
    private ElectionTask electionTask;

    private Timer leaderHeartbeatsTimer;
    private HeartbeatsTask leaderHeartbeatsTask;

    protected RaftServer(Long id, RaftServersCfg cfg, RaftServerDb db) throws RemoteException, IOException {
        super();
        this.id = id;
        this.cfg = cfg;
        this.db = db;
    }

    // Get current server Id.
    public Long getId() {
        return id;
    }

    // Get db.
    public RaftServerDb getDb() {
        return db;
    }

    // Get config.
    public RaftServersCfg getCfg() {
        return cfg;
    }

    // Get number of votes this server has. 
    public AtomicInteger getNumVotes() {  
        return numVotes;
    }

    // Get current server state.
    public RaftServerState getServerState() {
        return serverState;
    }

    // Set current server state.
    public void setServerState(RaftServerState serverState) {
        this.serverState = serverState; 
        log("I am a " + this.serverState);
    }
  
    // Get server interface by ID.
    public synchronized RaftRMIInterface getServerInterface(Long serverId) {
        return idToServerInterfaceMap.get(serverId);
    }

    // Get number of majority.
    public int getNumMajority() {
        return numMajority;
    }

    // Get whether had leader activity.
    public AtomicBoolean hadLeaderActivity() {
        return hadLeaderActivity;
    }

    // Get lock.
    public Object getLock() {
        return lock; 
    } 

    // Start election timer.
    public void startElectionTimer() {
        electionTimer = new Timer();
        int electionTimerMillis = new Random().nextInt((ELECTION_TIMER_MAX_MILLIS - ELECTION_TIMER_MIN_MILLIS) + 1)  + ELECTION_TIMER_MIN_MILLIS;
        electionTimer.schedule(new ElectionTask(this), electionTimerMillis);
    }

    // Stop election timer.
    public void stopElectionTimer() {
        if (electionTask != null) {
            electionTask.cancel();
        }
        if (electionTimer != null) {
            electionTimer.cancel();
        }
    }

    // Start leader heartbeats timer.
    public void startHeartbeatsTimer() {
        leaderHeartbeatsTimer = new Timer();
        leaderHeartbeatsTimer.schedule(new HeartbeatsTask(this), 0, LEADER_HEARTBEATS_TIMER_MILLIS);
    }

    // Stop leader heartbeats timer.
    public void stopHeartbeatsTimer() {
        if (leaderHeartbeatsTask != null) { 
            leaderHeartbeatsTask.cancel();
        }
        if (leaderHeartbeatsTimer != null) {
            leaderHeartbeatsTimer.cancel();
        }
    }

   
    public void init() throws MalformedURLException, RemoteException, NotBoundException {

        // Number of servers that make up the majority.
        numMajority = cfg.getNumServers() / 2 + 1;

        Naming.rebind("//" + cfg.getInfoById(id).getHostname() + "/" + id, this);
        log("Server started.");

        // Add server interfaces to the other servers.
        Set<Long> serverInterfacesToAddSet = new TreeSet<>();
        for (Long serverId : cfg.getInfos().keySet()) {
            serverInterfacesToAddSet.add(serverId);
        }
        Set<Long> serverInterfacesAddedSet = new TreeSet<>();
        while (serverInterfacesToAddSet.size() > 0) {
            for (Long serverId : serverInterfacesToAddSet) {
                try {
                    RaftRMIInterface serverInterface = (RaftRMIInterface)Naming.lookup("//" + cfg.getInfoById(serverId).getHostname() + "/" + serverId);
                    idToServerInterfaceMap.put(serverId, serverInterface);
                    serverInterfacesAddedSet.add(serverId);
                } catch (Exception ex) {
                    // Could not add server interface. Keeping trying.
                }
            }
            for (Long serverId : serverInterfacesAddedSet) {
              serverInterfacesToAddSet.remove(serverId);
            }
        } 

        // Form cluster.
        Set<Long> serversToAddToSet = new TreeSet<>();
        for (Long serverId : cfg.getInfos().keySet()) {
            serversToAddToSet.add(serverId);
        }
        Set<Long> serversAddedSet = new TreeSet<>();
        while (serversToAddToSet.size() > 0) {
            for (Long serverId : serversToAddToSet) {
                try {
                    idToServerInterfaceMap.get(serverId).addServerToCluster(id);
                    serversAddedSet.add(serverId);
                } catch (Exception ex) {
                    // Could not add server interface. Keeping trying.
                }
            }
            for (Long serverId : serversAddedSet) {
              serversToAddToSet.remove(serverId);
            }
        } 
      
        // Handle shutdown.
        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                try {
                    stopElectionTimer();
                    stopHeartbeatsTimer();
                } catch (Exception ex) {
                    log("Handle shutdown.", ex);
                }
          }
        });
    }

    public static void main(String[] args) throws IOException, MalformedURLException, RemoteException, NotBoundException {
        if (args.length != 1) {
            System.out.println("Usage: RaftServer <id>");
            return;
        }
        Long initId = Long.parseLong(args[0]);
        RaftServersCfg initCfg = new RaftServersCfg("servers.cfg");
        RaftServerDb initDb = new RaftServerDb(initId);
        try {
            String hostname = initCfg.getInfoById(initId).getHostname();
            RaftServer server = new RaftServer(initId, initCfg, initDb);
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
        startElectionTimer();
        startHeartbeatsTimer();
      }
    }


    @Override
    public RaftRequestVoteResult requestVote(Long term, Long candidateId, Long lastLogIndex, Long lastLogTerm) throws RemoteException, IOException {
        if (clusterFormed) {
            synchronized (lock) {
                long currentTerm = db.readCurrentTerm().get();
                if (term > currentTerm) {
                    db.writeCurrentTerm(new AtomicLong(term));
                    db.writeVotedFor(null);
                    serverState = RaftServerState.FOLLOWER;
                }
            }
            long currentTerm;
            AtomicLong votedFor = null;
            boolean voteGranted = false;
            synchronized (lock) {
                 currentTerm = db.readCurrentTerm().get();
                 votedFor = db.readVotedFor();
                if (term == currentTerm && (votedFor == null || votedFor.get() == candidateId)) {
                    voteGranted = true;
                    db.writeVotedFor(new AtomicLong(candidateId));
                    hadLeaderActivity.set(true);
                }
            } 
            log("Received requestVote. Term=" + term + "; candidateId=" + candidateId + "; lastLogIndex=" + lastLogIndex + "; lastLogTerm=" + lastLogTerm + "; voteGranted=" + voteGranted);
            return new RaftRequestVoteResult(currentTerm, voteGranted);
        }
        return new RaftRequestVoteResult(db.readCurrentTerm().get(), false);
    }

    @Override
    public RaftAppendEntriesResult appendEntries(Long term, Long leaderId, Long prevLogIndex, RaftEntry[] entries, Long leaderCommit) throws RemoteException, IOException {
        if (clusterFormed) {
            synchronized (lock) {
                long currentTerm = db.readCurrentTerm().get();
                if (term > currentTerm) {
                    db.writeCurrentTerm(new AtomicLong(term));
                    db.writeVotedFor(null);
                    serverState = RaftServerState.FOLLOWER;
                }
            }
            boolean success = false;
            long currentTerm;
            synchronized (lock) {
                currentTerm = db.readCurrentTerm().get();
                if (term < currentTerm) {
                    success = false;
                }
            }
            hadLeaderActivity.set(true);
            return new RaftAppendEntriesResult(currentTerm, success);
        } 
        return new RaftAppendEntriesResult(db.readCurrentTerm().get(), false);
    }

    @Override
    public RaftCmdResult processCmd(String cmd) throws RemoteException {
      log("Processing cmd " + cmd);
      if (serverState == RaftServerState.LEADER) {
        log("I am the leader. Processing cmd " + cmd);
        return new RaftCmdResult(true, id, "Command processed.");
      } else {
        log("I am not the leader. Last known leader is " + lastKnownLeaderId);
        return new RaftCmdResult(false, lastKnownLeaderId, null);
      }
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
