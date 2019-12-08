import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.Map;
import java.util.TreeMap;

class RaftServersCfg {

    private Map<Integer, RaftServerInfo> idToRaftServerInfoMap = new TreeMap<>();

    private void load(String filePath) {
        BufferedReader reader;
        try {
            reader = new BufferedReader(new FileReader(filePath));
            String line = reader.readLine();
            while (line != null) {
              String[] tokens = line.split(",");
              int id = Integer.parseInt(tokens[0]);
              String hostname = tokens[1];
              int port = Integer.parseInt(tokens[2]);
              RaftServerInfo info = new RaftServerInfo(id, hostname, port);
              idToRaftServerInfoMap.put(id, info);
              line = reader.readLine();
            }
            reader.close();
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    public RaftServersCfg(String filePath) {
        load(filePath);
    }

    public RaftServerInfo getInfoById(int id) {
      return idToRaftServerInfoMap.get(id);
    }

    public Map<Integer, RaftServerInfo> getInfos() {
      Map<Integer, RaftServerInfo> map = new TreeMap<>();
      map.putAll(idToRaftServerInfoMap);
      return map;
    }
}
