package rmi;

import model.Host;
import model.RaftMessage;
import model.RaftServer;

import java.rmi.AlreadyBoundException;
import java.rmi.NoSuchObjectException;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;

public class RaftRMI {

    //can only start a Registry in the localhost, can't connect to 0.0.0.0. It should only be "localhost".
    //install a security manager for RMI if the peer is using the codebase facility to supply classes to you dynamically.
    //heartbeat - reset heartbeat - on the leader side, slave get function from leader
    //getRegistry call does not actually make a connection to the remote host. It simply creates a local reference to the remote
    //  registry and will succeed even if no registry is running on the remote host.
    private final String hostname = "localhost";
    private static int port = 8080;

    private static RaftRMI raftRMI;

    public static RaftRMI getRaftRMI() {
        if(raftRMI == null){
            raftRMI = new RaftRMI();
        }
        return raftRMI;
    }

    public int createRegistry() {
        boolean created = false;
        while(!created){
            try {
                LocateRegistry.createRegistry(port);
                created = true;
            } catch (RemoteException e) {
                System.out.println("port "+port+" is occupied");
                port++;
            }
        }
        return port;
    }

    public void bindHost(RaftServer raftServer){
        try {
            RaftRMIInterface stub = (RaftRMIInterface) UnicastRemoteObject.exportObject(raftServer, 0);
            Registry registry = LocateRegistry.getRegistry(port);
            registry.bind("rmi://" + hostname + "/" + port + "/" + raftServer.getRaftServerId(), stub);
        } catch (RemoteException| AlreadyBoundException e) {
            System.out.println("RaftRMI(): bindHost exception");
        }
    }

    public void unbindHost(RaftServer raftServer){

        try {
            UnicastRemoteObject.unexportObject(raftServer, true);
            Registry registry = LocateRegistry.getRegistry(port);
            registry.unbind("rmi://" + hostname + "/" + port + "/" + raftServer.getRaftServerId());
        } catch (NoSuchObjectException e) {
            e.printStackTrace();
        } catch (RemoteException e) {
            e.printStackTrace();
        } catch (NotBoundException e) {
            e.printStackTrace();
        }

    }

    //raft related function
    public RaftMessage requestVote(Host host, long term, int logLastIndex, long logLastTerm, String voteFor) throws Exception {
        //Thread.sleep();

        try {
            System.out.println("RaftRMI: request vote for " + voteFor);
            Registry registry = LocateRegistry.getRegistry(host.getAddress(), host.getPort());
            System.out.println("RaftRMI: id is " + host.getId());
            RaftRMIInterface stub = (RaftRMIInterface) registry.lookup("rmi://" + host.getAddress() + "/" + host.getPort() + "/" + host.getId());
            return stub.requestVote(term, voteFor, logLastIndex, logLastTerm);
        } catch (NotBoundException e) {
            System.out.println("RaftRMI(): requestVote exception");
        }
        return null;
    }

    public RaftMessage appendEntry(Host host, long term, int logCurrentIndex, long logCurrentTerm, String leaderId, boolean isHeartBeats){

        try {
            Registry registry = LocateRegistry.getRegistry(host.getAddress(), host.getPort());
            RaftRMIInterface stub = (RaftRMIInterface) registry.lookup("rmi://" + host.getAddress() + "/" + host.getPort() + "/" + host.getId());
            return stub.appendEntry(term, leaderId, logCurrentIndex, logCurrentTerm, isHeartBeats);
        } catch (RemoteException e) {
            System.out.println("RaftRMI(): appendEntry exception");
        } catch (NotBoundException e) {
            System.out.println("RaftRMI(): appendEntry exception");
        }
        return null;
    }
}
