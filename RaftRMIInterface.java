
import java.rmi.Remote;
import java.rmi.RemoteException;

public interface RaftRMIInterface extends Remote {

    public String hello(String name) throws RemoteException;
}
