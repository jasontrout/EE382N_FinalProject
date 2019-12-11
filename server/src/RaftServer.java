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

    private static final int ELECTION_SCHEDULE_DELAY_MIN_MILLIS = 1800; // Should be 150ms, but using 1s for the demo.
    private static final int ELECTION_SCHEDULE_DELAY_MAX_MILLIS = 4000; // Should be 300ms, but using 3s for the demo.

    private static final int HEARTBEATS_SCHEDULE_DELAY_MILLIS = 600;

    private Object lock = new Object();

    private RaftServersCfg cfg;
    private RaftServerDb db;

    private Long id;

    private AtomicBoolean hadLeaderActivity = new AtomicBoolean(false);

    private Map<Long, Boolean> idToServerStateMap = new TreeMap<>();
    private Map<Long, RaftRMIInterface> idToServerInterfaceMap = new TreeMap<>();
    private RaftServerState serverState = RaftServerState.FOLLOWER;

    private ScheduledExecutorService readyScheduler = Executors.newScheduledThreadPool(1);
    private ScheduledFuture<?> readyFuture;
    private TimerTask readyTask;

    private ScheduledExecutorService electionScheduler = Executors.newScheduledThreadPool(1);
    private ScheduledFuture<?> electionFuture;
    private ElectionTask electionTask;

    private ScheduledExecutorService heartbeatsScheduler = Executors.newScheduledThreadPool(1);
    private ScheduledFuture<?> heartbeatsFuture;
    private HeartbeatsTask hearbeatsTask;


    private boolean clusterFormed = false;
    private boolean serverAddedToOtherServers = false;

    protected RaftServer(Long id, RaftServersCfg cfg, RaftServerDb db) throws RemoteException, IOException {
        super();
        this.id = id;
        this.cfg = cfg;
        this.db = db;

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
        long currentTerm = db.readCurrentTerm().get();
        boolean voteGranted = false;
        String votedForStr = "";
        synchronized (lock) {
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
                 if (votedFor == null || votedFor.get() == candidateId) {
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
    public RaftAppendEntriesResult appendEntries(Long term, Long leaderId, Long prevLogIndex, RaftEntry[] entries, Long leaderCommit) throws RemoteException, IOException {
        long currentTerm = db.readCurrentTerm().get();
        boolean success = false;
        synchronized (lock) {
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
                success = true;
            }
        }
        return new RaftAppendEntriesResult(currentTerm, success);
    }

    @Override
    public RaftCmdResult processCmd(String cmd) throws RemoteException, IOException {
      log("Processing cmd " + cmd);
      if (serverState == RaftServerState.LEADER) {
        log("I am the leader. Processing cmd " + cmd);
        return new RaftCmdResult(true, id, "Command processed.");
      } else {
        long lastKnownLeaderId = db.readVotedFor().get();
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
