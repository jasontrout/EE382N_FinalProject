
import java.net.MalformedURLException;
import java.rmi.Naming;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;

public class RaftServer extends UnicastRemoteObject implements RaftRMIInterface {

    private static final long serialVersionUID = 1L;
    
    private static String id;
    private static String hostname;
    private static int port;
    
    protected RaftServer() throws RemoteException {
        super();
    }

    @Override
    public String hello(String name) throws RemoteException {
        return "Hello, " + name + "!";
    }

    public static void main(String[] args) throws MalformedURLException, RemoteException, NotBoundException {
        
        if (args.length != 1) {
            System.out.println("Usage: RaftServer <id>");
            return;
        }

        id = args[0];
 
        System.out.println("RaftServer " + id + " started!");

     
       
        /* 
        name = args[0];
        hostname = args[1];
        
        System.out.println("Name: " + name);
        System.out.println("Hostname: " + hostname);
        
        
        try {
            Naming.rebind("//" + hostname + "/" + name, new RaftServer());
            System.out.println("Server ready");
        } catch (Exception ex) {
            System.err.println("Server exception: " + ex.toString());
            ex.printStackTrace();
        }
        
        
        RaftRMIInterface lookUp = (RaftRMIInterface)Naming.lookup("//" + hostname + "/" + name);
        String response = lookUp.hello("alice");
        System.out.println("Response: " + response);
       */
    }
}
