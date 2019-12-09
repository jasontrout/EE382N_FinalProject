import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.Map;
import java.util.TreeMap;

class RaftServersCfg {

    private Map<Long, RaftServerInfo> idToRaftServerInfoMap = new TreeMap<>();

    private void load(String filePath) {
        BufferedReader reader;
        try {
            reader = new BufferedReader(new FileReader(filePath));
            String line = reader.readLine();
            while (line != null) {
              String[] tokens = line.split(",");
              Long id = Long.parseLong(tokens[0]);
              String hostname = tokens[1];
              Integer port = Integer.parseInt(tokens[2]);
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

    public RaftServerInfo getInfoById(Long id) {
        return idToRaftServerInfoMap.get(id);
    }

    public Map<Long, RaftServerInfo> getInfos() {
      Map<Long, RaftServerInfo> map = new TreeMap<>();
      map.putAll(idToRaftServerInfoMap);
      return map;
    }

    public int getNumServers() {
        return idToRaftServerInfoMap.size();
    }
}
