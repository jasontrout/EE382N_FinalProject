import model.Host;
import model.RaftServer;
import rmi.RaftRMI;

import java.util.ArrayList;
import java.util.List;

public class Main {

    public static void main(String[] args) {
        //
        List<Host> servers1 = new ArrayList<>();
        List<Host> servers2 = new ArrayList<>();
        List<Host> servers3 = new ArrayList<>();

        Host localhost1 = new Host("localhost", 8080, "1");
        Host localhost2 = new Host("localhost", 8081, "2");
        Host localhost3 = new Host("localhost", 8082 ,"3");
        RaftServer raftServer1 = new RaftServer();
        RaftServer raftServer2 = new RaftServer();
        RaftServer raftServer3 = new RaftServer();
        servers1.add(localhost2);
        servers1.add(localhost3);
        raftServer1.setServersInCluster(localhost1, servers1);

        servers2.add(localhost1);
        servers2.add(localhost3);
        raftServer2.setServersInCluster(localhost2, servers2);

        servers3.add(localhost1);
        servers3.add(localhost2);
        raftServer3.setServersInCluster(localhost3, servers3);


        RaftRMI.getRaftRMI().createRegistry();
        RaftRMI.getRaftRMI().bindHost(raftServer1);
        RaftRMI.getRaftRMI().createRegistry();
        RaftRMI.getRaftRMI().bindHost(raftServer2);
        RaftRMI.getRaftRMI().createRegistry();
        RaftRMI.getRaftRMI().bindHost(raftServer3);
        raftServer1.startElectionTimer();
        raftServer2.startElectionTimer();
        raftServer3.startElectionTimer();

    }
}
