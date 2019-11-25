
import java.net.MalformedURLException;
import java.rmi.Naming;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;

public class RaftServer extends UnicastRemoteObject implements RaftRMIInterface {

    private static final long serialVersionUID = 1L;

    protected RaftServer() throws RemoteException {
        super();
    }

    @Override
    public String hello(String name) throws RemoteException {
        return "Hello, " + name + "!";
    }

    public static void main(String[] args) throws MalformedURLException, RemoteException, NotBoundException {
        
        try {
            Naming.rebind("//127.0.0.1/RaftServer", new RaftServer());
            System.out.println("Server ready");
        } catch (Exception ex) {
            System.err.println("Server exception: " + ex.toString());
            ex.printStackTrace();
        }
        
        
        RaftRMIInterface lookUp = (RaftRMIInterface)Naming.lookup("//127.0.0.1/RaftServer");
        String response = lookUp.hello("alice");
        System.out.println("Response: " + response);
    }
}
