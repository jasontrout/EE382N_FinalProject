import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.Thread;
import java.net.MalformedURLException;
import java.rmi.Naming;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
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

    private static final int READY_SCHEDULE_DELAY_MILLIS = 5;

    private static final int ELECTION_SCHEDULE_DELAY_MIN_MILLIS = 1800; // Should be 150ms, but using 1.8s for the demo.
    private static final int ELECTION_SCHEDULE_DELAY_MAX_MILLIS = 4000; // Should be 300ms, but using 4s for the demo.

    private static final int HEARTBEATS_SCHEDULE_DELAY_MILLIS = 600; // Should be 50ms, but using 600ms for the demo.

    private Object lock = new Object();

    // Used for server configurations.
    private RaftServersCfg cfg;

    // Used for persistent storage (currentTerm, votedFor, logs).
    private RaftServerDb db;

    // Index of highest log entry known to be committed. 
    private long commitIndex = 0;

    // Index of highest log entry applied to state machine.
    private long lastApplied = 0;

    // Server ID.
    private Long id;

    // Server State (FOLLOWER, CANDIDATE, LEADER).
    private RaftServerState serverState = RaftServerState.FOLLOWER;

    // Stores whether leader activity has been detected.
    private AtomicBoolean hadLeaderActivity = new AtomicBoolean(false);

    // Maps of server ID to server objects.
    private Map<Long, Boolean> idToServerStateMap = new TreeMap<>();
    private Map<Long, RaftRMIInterface> idToServerInterfaceMap = new TreeMap<>();
    private Map<Long, Long> nextIndicies = new TreeMap<>();
    private Map<Long, Long> matchIndicies = new TreeMap<>();

    // Ready scheduler. For cluster formation.
    private ScheduledExecutorService readyScheduler = Executors.newScheduledThreadPool(1);
    private ScheduledFuture<?> readyFuture;
    private TimerTask readyTask;

    // Election scheduler for elections.
    private ScheduledExecutorService electionScheduler = Executors.newScheduledThreadPool(1);
    private ScheduledFuture<?> electionFuture;
    private ElectionTask electionTask;
 
    // Heartbeat scheduler for producing heartbeats.
    private ScheduledExecutorService heartbeatsScheduler = Executors.newScheduledThreadPool(1);
    private ScheduledFuture<?> heartbeatsFuture;
    private HeartbeatsTask hearbeatsTask;

    // Cluster formation state variables.
    private boolean clusterFormed = false;
    private boolean serverAddedToOtherServers = false;

    // Value to be set.
    private long value;

    protected RaftServer(Long id, RaftServersCfg cfg, RaftServerDb db) throws RemoteException, IOException {
        super();
        this.id = id;
        this.cfg = cfg;
        this.db = db;

        // Get last log index.
        long lastLogIndex = -1;
        RaftEntry lastLogEntry = db.readLastEntry();
        if (lastLogEntry != null) {
            lastLogIndex = lastLogEntry.getIndex();
        }

        // Initialize next indicies. 
	for (Long serverId : cfg.getInfos().keySet()) {
            if (serverId != id) {
                nextIndicies.put(serverId, lastLogIndex + 1);
            }
        }

        // Initialize match indicies.
        for (Long serverId : cfg.getInfos().keySet()) {
            if (serverId != id) {
                matchIndicies.put(serverId, 0L);
            }
        }

        log("Server started.");

        readyFuture = readyScheduler.scheduleAtFixedRate(new TimerTask() { 
            @Override
            public void run() {
                if (clusterFormed && serverAddedToOtherServers) {
                    log("Cluster is formed. I am a " + serverState + ".");

                    int electionScheduleDelayMillis = new Random().nextInt((ELECTION_SCHEDULE_DELAY_MAX_MILLIS - ELECTION_SCHEDULE_DELAY_MIN_MILLIS) + 1)  + ELECTION_SCHEDULE_DELAY_MIN_MILLIS;
                    electionFuture = electionScheduler.scheduleAtFixedRate(new ElectionTask(RaftServer.this), electionScheduleDelayMillis, electionScheduleDelayMillis, TimeUnit.MILLISECONDS);

                    heartbeatsFuture = heartbeatsScheduler.scheduleAtFixedRate(new HeartbeatsTask(RaftServer.this), 0, HEARTBEATS_SCHEDULE_DELAY_MILLIS, TimeUnit.MILLISECONDS);

                    readyFuture.cancel(true);
                    readyScheduler.shutdown();
                }
            }
        }, 0, READY_SCHEDULE_DELAY_MILLIS, TimeUnit.MILLISECONDS);

        Timer timer = new Timer();
        timer.schedule(new TimerTask() {
            @Override
            public void run() { 

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
		    if (serverId != id) {
			serversToAddToSet.add(serverId);
		    }
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
		serverAddedToOtherServers = true;
           }
        }, 5);
       

      
        // Handle shutdown.
        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                try {
                    electionFuture.cancel(true);
                    electionScheduler.shutdown();
                    heartbeatsFuture.cancel(true);
                    heartbeatsScheduler.shutdown();
                } catch (Exception ex) {
                    log("Handle shutdown.", ex);
                }
          }
        });
    }

    public void setValue(long value) {
        synchronized (lock) {
            this.value = value;
            log("Value set to " + value);
        }
    }

    public long getCommitIndex() {
        return commitIndex;
    }
  
    public void setCommitIndex(long commitIndex) {
        this.commitIndex = commitIndex;
    }
  
    public long getLastApplied() { 
        return lastApplied;
    }

    public void setLastApplied(long lastApplied) { 
        this.lastApplied = lastApplied;
    }

    public Map<Long, Long> getNextIndicies() {
        return nextIndicies;
    }

    public Map<Long, Long> getMatchIndicies() {
        return matchIndicies;
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

    // Get whether had leader activity.
    public AtomicBoolean getHadLeaderActivity() {
        return hadLeaderActivity;
    }

    // Get lock.
    public Object getLock() {
        return lock; 
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
            String bindStr = "//" + initCfg.getInfoById(initId).getHostname() + "/" + initId;
            Naming.rebind(bindStr, server);
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
      }
    }

    @Override
    public RaftRequestVoteResult requestVote(Long term, Long candidateId, Long lastLogIndex, Long lastLogTerm) throws RemoteException, IOException {
        long currentTerm;
        long currentLastLogIndex;
        long currentLastLogTerm;
        boolean voteGranted = false;
        String votedForStr = "";
        synchronized (lock) {
            currentTerm = db.readCurrentTerm().get();
            RaftEntry currentLastLogEntry = db.readLastEntry();
            if (currentLastLogEntry == null) {
                currentLastLogIndex = -1;
                currentLastLogTerm = -1;
            } else {
                currentLastLogIndex = currentLastLogEntry.getIndex();
                currentLastLogTerm = currentLastLogEntry.getTerm();
            }
            if (term > currentTerm) {
                db.writeCurrentTerm(new AtomicLong(term));
                currentTerm = db.readCurrentTerm().get();
                db.writeVotedFor(null);
                setServerState(RaftServerState.FOLLOWER);
                hadLeaderActivity.set(true);
            }
            AtomicLong votedFor = db.readVotedFor();
            if (term < currentTerm) {
                 voteGranted = false;
            } else {
                 if ((votedFor == null || votedFor.get() == candidateId) && 
                     (currentLastLogTerm < lastLogTerm || (currentLastLogTerm == lastLogTerm && currentLastLogIndex <= lastLogIndex))) {
                     voteGranted = true;
                     votedFor = new AtomicLong(candidateId);
                     db.writeVotedFor(new AtomicLong(candidateId));
                 }
            }
            votedForStr = (votedFor == null) ? "null" : votedFor.toString();
        }
        log("RequestVote. Term=" + term + "; currentTerm=" + currentTerm + "; candidateId=" + candidateId + "; lastLogIndex=" + lastLogIndex + "; lastLogTerm=" + lastLogTerm + "; votedFor=" + votedForStr + "; voteGranted=" + voteGranted);
        return new RaftRequestVoteResult(currentTerm, voteGranted);
    }

    @Override
    public RaftAppendEntriesResult appendEntries(Long term, Long leaderId, Long prevLogIndex, Long prevLogTerm, RaftEntry[] entries, Long leaderCommit) throws RemoteException, IOException {
        long currentTerm ;
        boolean success = false;
        synchronized (lock) {
            currentTerm = db.readCurrentTerm().get();
            if (term > currentTerm) {
                db.writeCurrentTerm(new AtomicLong(term));
                currentTerm = db.readCurrentTerm().get();
                setServerState(RaftServerState.FOLLOWER);
                hadLeaderActivity.set(true);
            }
            if (term == currentTerm) {
                db.writeVotedFor(new AtomicLong(leaderId));
                hadLeaderActivity.set(true);
            }
            if (term < currentTerm) {
                success = false;
            } else {
                for (RaftEntry entry : entries) {
                    db.writeEntry(entry);
                }
                RaftEntry entry = db.readEntry(prevLogIndex);
                if (prevLogIndex == -1 || entry != null) { 
                    success = true;
                }
            }
        }
        String entriesStr = "";
        for (RaftEntry entry : entries) {
            entriesStr += (entry.getIndex() + " " + entry.getTerm() + " " + entry.getCommand() + "\n");
        }
        if (entries.length > 0) {
            log("AppendEntries: Term= " + term + "; LeaderId=" + leaderId + "; prevLogIndex=" + prevLogIndex + "; entries=" + entriesStr + "; leaderCommit=" + leaderCommit);
        }

        synchronized (lock) {
            if (prevLogIndex == -1) { 
                for (RaftEntry entry : entries) {
                    db.writeEntry(entry);
                    commitIndex++;
               }
           }
           for (long i = lastApplied; i < leaderCommit; i++) {
               RaftEntry commitEntry = db.readEntry(i+1);
               log("Commit entry " + commitEntry.getCommand());
               lastApplied = commitEntry.getIndex();
           }
        }
        return new RaftAppendEntriesResult(currentTerm, success);
    }

    @Override
    public RaftCmdResult processCmd(String cmd) throws RemoteException, IOException {
        long currentTerm;
        boolean success;
        long lastLogIndex = 0;
        long entryIndex;
        long entryTerm;
        log("Processing cmd " + cmd);
        synchronized (lock) {
             currentTerm = db.readCurrentTerm().get();
            if (serverState == RaftServerState.LEADER) {
                log("I am the leader. Processing cmd " + cmd);
                RaftEntry lastLogEntry = db.readLastEntry();
                if (lastLogEntry != null) {
                    lastLogIndex = lastLogEntry.getIndex();
                }
                entryIndex = lastLogIndex + 1;
                entryTerm = currentTerm;
                db.writeEntry(new RaftEntry(lastLogIndex + 1, currentTerm, cmd));
            } else {
                long lastKnownLeaderId = db.readVotedFor().get();
                log("I am not the leader. Last known leader is " + lastKnownLeaderId);
                return new RaftCmdResult(false, lastKnownLeaderId, null);
            }
        }
        while (true) {
            try {
                int numMatches;
                synchronized (matchIndicies) {
                    numMatches = 0;
                    for (Long serverId : matchIndicies.keySet()) {
                        if (matchIndicies.get(serverId) >= entryIndex) {
                            numMatches++;
                        }
                    }
                }
                if (numMatches >= ((cfg.getNumServers() + 1) / 2)) {
                    log("Committing " + cmd + " to the state machine.");
                    if (entryIndex > commitIndex) { 
                        commitIndex = entryIndex;
                    } 
                    if (entryIndex > lastApplied) {
                        lastApplied = entryIndex;
                    }
                    break;
                }
                Thread.sleep(100);
            } catch (Exception ex) {}
        }
        return new RaftCmdResult(true, id, "Command processed.");
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
