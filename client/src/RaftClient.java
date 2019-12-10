import java.io.BufferedReader;
import java.io.FileReader;
import java.io.InputStreamReader;
import java.io.IOException;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;
import java.net.MalformedURLException;
import java.rmi.Naming;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;

public class RaftClient {

    private static Map<Long, RaftRMIInterface> idToRaftRMIInterfaceMap = new TreeMap<>();

    private static void loadServersCfg(String filePath) {
        BufferedReader reader;
        try {
            reader = new BufferedReader(new FileReader(filePath));
            String line = reader.readLine();
            while (line != null) {
              String[] tokens = line.split(",");
              Long id = Long.parseLong(tokens[0]);
              String hostname = tokens[1];
              try {
                  RaftRMIInterface raftCmdRMIInterface = (RaftRMIInterface)Naming.lookup("//" + hostname + "/" + id);
                  if (raftCmdRMIInterface != null) {
                      idToRaftRMIInterfaceMap.put(id, raftCmdRMIInterface);
                  }
              } catch (Exception ex) {
                ex.printStackTrace();
                // Not bound. Not adding to list.
              }
              line = reader.readLine();
            }
            reader.close();
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    public static RaftRMIInterface getRandomServerInterface() {
      Random generator = new Random();
      Object[] values = idToRaftRMIInterfaceMap.values().toArray();
      return (RaftRMIInterface)values[generator.nextInt(values.length)];
    }
    
    public static RaftRMIInterface getServerInterfaceById(Long serverId) {
      return idToRaftRMIInterfaceMap.get(serverId);
    }

    public static void main(String[] args) {
        Long leaderId = null;
        loadServersCfg("servers.cfg");
        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
        try {
          String cmd;
          System.out.print("Command: ");
          cmd = reader.readLine();
          while (cmd != null) {
              if (cmd.equals("quit")) {
                  return;
              }
              RaftRMIInterface raftRMIInterface;
              boolean receivedValidResponse = false;
              while (!receivedValidResponse) {
                  if (leaderId == null) {
                          raftRMIInterface = getRandomServerInterface();
                  } else {
                          raftRMIInterface = getServerInterfaceById(leaderId);
                  }
                  try {
                      RaftCmdResult result = raftRMIInterface.processCmd(cmd);
                      if (!result.getAccepted()) {
                          leaderId = result.getLeaderId();
                      } else {
                          System.out.println("Response: " + result.getResponse());
                          receivedValidResponse = true;
                      }
                  } catch (Exception ex) {
                    // Keep trying.
                  }
              }
              System.out.print("Command: ");
              cmd = reader.readLine();
          }
        } catch (IOException ex) {
          ex.printStackTrace();
        } 
    }
}
