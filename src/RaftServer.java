import java.net.MalformedURLException;
import java.rmi.Naming;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.Map;
import java.util.TreeMap;

public class RaftServer extends UnicastRemoteObject implements RaftRMIInterface {

    private static final long serialVersionUID = 1L;

    private static Long currentTerm;
    private static Long votedFor;
    private static RaftEntry[] log;

    private RaftServersCfg cfg;
    
    private Long id;
    private Long commitIndex;
    private Long lastApplied;
    private Long[] nextIndex;
    private Long[] matchIndex;

    private Map<Long, Boolean> idToServerStateMap = new TreeMap<>();
    private Map<Long, RaftRMIInterface> idToServerInterfaceMap = new TreeMap<>();
    private boolean clusterFormed = false;
 
    protected RaftServer(Long id, RaftServersCfg cfg) throws RemoteException {
        super();
        this.id = id;
        this.cfg = cfg;
        currentTerm = 0L;
        votedFor = null;
    }

    public void init() throws MalformedURLException, RemoteException, NotBoundException {
        log("Server started.");
        // Add this server to the other servers to form a cluster.  
        for (Long serverId : cfg.getInfos().keySet()) {
            RaftRMIInterface serverInterface = (RaftRMIInterface)Naming.lookup("//" + cfg.getInfoById(serverId).getHostname() + "/" + serverId);
            idToServerInterfaceMap.put(serverId, serverInterface);
            serverInterface.addServerToCluster(id); 
        } 
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
            Naming.rebind("//" + hostname + "/" + initId, server);
            server.init();
        } catch (Exception ex) {
            System.err.println("Server exception: " + ex.toString());
            ex.printStackTrace();
        }
    }

    @Override
    public synchronized void addServerToCluster(Long id) throws RemoteException {
      idToServerStateMap.put(id, true);
      if (cfg.getNumServers() == idToServerStateMap.size()) {
        clusterFormed = true;
        log("Cluster formed!");
      }
    }

    @Override
    public RaftRequestVoteResult requestVote(Long term, Long candidateId, Long lastLogIndex, Long lastLogTerm) throws RemoteException {
      if (clusterFormed) {
          log("Request vote!\n");
      }
      return null;
    }

    @Override
    public RaftAppendEntriesResult appendEntries(Long term, Long leaderId, Long prevLogIndex, RaftEntry[] entries, Long leaderCommit) throws RemoteException {
      if (clusterFormed) {
           log("Append entries!\n");
      }
      return null;
    }
 
    private void log(String msg) {
      System.out.println("\nRaftServer[ " +id + "] " + msg + "\n");
    }
    
}
