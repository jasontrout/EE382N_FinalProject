import java.net.MalformedURLException;
import java.rmi.Naming;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;

public class RaftServer extends UnicastRemoteObject implements RaftRMIInterface {

    private static final long serialVersionUID = 1L;
    
    private int id;
 
    private RaftServersCfg cfg;
    
    protected RaftServer() throws RemoteException {
        super();
        this.id = id;
    }

    @Override
    public String hello(String name) throws RemoteException {
        return "Hello, " + name + "!";
    }

    private void init(String[] args) {
        if (args.length != 1) {
            System.out.println("Usage: RaftServer <id>");
            return;
        }
        id = Integer.parseInt(args[0]);
     
        cfg = new RaftServersCfg("servers.cfg");
  
        System.out.println("\nRaft Server[" + id + "]  Started. Listening on " + cfg.getInfoById(id).getHostname() + ":" + cfg.getInfoById(id).getPort() + "\n");
    }

    public static void main(String[] args) throws MalformedURLException, RemoteException, NotBoundException {
        
        RaftServer server = new RaftServer();
        server.init(args);
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
