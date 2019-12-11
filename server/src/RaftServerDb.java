import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.atomic.AtomicLong;
import java.util.List;
import java.util.ArrayList;

public class RaftServerDb {

    private String varsDbFilePath;
    private String logsDbFilePath;

    private void createVarsDb() throws IOException {
        File file = new File(varsDbFilePath);
        FileOutputStream fos = new FileOutputStream(file);
        BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(fos));
        bw.write("0");
        bw.newLine();
        bw.write("null");
        bw.newLine();
        bw.close();
    }

    private void createLogsDb() throws IOException {
        File file = new File(logsDbFilePath);
        FileOutputStream fos = new FileOutputStream(file);
        BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(fos));
        bw.close();
    }

    public RaftServerDb(Long serverId) throws IOException {
        varsDbFilePath = serverId + ".vars.db";
        File varsDbFile = new File(varsDbFilePath);
        if (!varsDbFile.exists()) {
            createVarsDb();
        }
        logsDbFilePath = serverId + ".logs.db";
        File logsDbFile = new File(logsDbFilePath);
        if (!logsDbFile.exists()) {
            createLogsDb();
        }
    }

    public synchronized AtomicLong readCurrentTerm() throws IOException {
        AtomicLong currentTerm;
        BufferedReader reader;
        reader = new BufferedReader(new FileReader(varsDbFilePath));
        String line = reader.readLine();
        reader.close();
        if (line.equals("null")) {
            currentTerm = null; 
        } else {
            currentTerm = new AtomicLong(Long.parseLong(line));
        }
        return currentTerm;
    }
  
    public synchronized void writeCurrentTerm(AtomicLong currentTerm) throws IOException {
        AtomicLong votedFor = readVotedFor();
        File file = new File(varsDbFilePath);
        FileOutputStream fos = new FileOutputStream(file);
        BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(fos));
        if (currentTerm == null) {
            bw.write("null");
        } else {
            bw.write(currentTerm.toString());
        }
        bw.newLine();
        if (votedFor == null) {
            bw.write("null");
        } else {
            bw.write(votedFor.toString());
        }
        bw.newLine();
        bw.close();
    }

    public synchronized AtomicLong readVotedFor() throws IOException {
        AtomicLong votedFor;
        BufferedReader reader;
        reader = new BufferedReader(new FileReader(varsDbFilePath));
        String line;
        line = reader.readLine();
        line = reader.readLine();
        reader.close();
        if (line.equals("null")) {
            votedFor = null; 
        } else {
            votedFor = new AtomicLong(Long.parseLong(line));
        }
        return votedFor;
    }
  
    public synchronized void writeVotedFor(AtomicLong votedFor) throws IOException {
        AtomicLong currentTerm = readCurrentTerm();
        File file = new File(varsDbFilePath);
        FileOutputStream fos = new FileOutputStream(file);
        BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(fos));
        if (currentTerm == null) {
            bw.write("null");
        } else {
            bw.write(currentTerm.toString());
        }
        bw.newLine();
        if (votedFor == null) {
            bw.write("null");
        } else {
            bw.write(votedFor.toString());
        }
        bw.newLine();
        bw.close();
    }

    public synchronized void writeEntry(RaftEntry entry) throws IOException {
        String line = entry.getIndex() + "#" + entry.getTerm() + "#" + entry.getCommand() + "\n";
        Files.write(Paths.get(logsDbFilePath), line.getBytes(), StandardOpenOption.APPEND);
    }

    public synchronized RaftEntry readEntry(long index) throws IOException {
        RaftEntry entry = null;
        Path path = Paths.get(logsDbFilePath);
        long lineCount = Files.lines(path).count();
        if (index < lineCount) {
            BufferedReader reader;
            long currentIndex = 0;
            reader = new BufferedReader(new FileReader(logsDbFilePath));
            String line = reader.readLine();
            while (line != null) {
                if (index == currentIndex) {
                    String[] tokens = line.split("#");
                    long readIndex = Long.parseLong(tokens[0]);
                    long readTerm = Long.parseLong(tokens[1]);
                    String readCommand = tokens[2];
                    entry = new RaftEntry(readIndex, readTerm, readCommand);
                    break;
                } 
                line = reader.readLine();
                currentIndex++;
            }
            reader.close();
        }
        return entry;
    }

    public synchronized RaftEntry[] readEntries(long startIndex) throws IOException {
        List<RaftEntry> entries = new ArrayList<>();
        Path path = Paths.get(logsDbFilePath);
        long lineCount = Files.lines(path).count();
        if (startIndex < lineCount) {
            BufferedReader reader;
            long currentIndex = 0;
            reader = new BufferedReader(new FileReader(logsDbFilePath));
            String line = reader.readLine();
            while (line != null) {
                if (currentIndex >= startIndex) {
                    String[] tokens = line.split("#");
                    long index = Long.parseLong(tokens[0]);
                    long term = Long.parseLong(tokens[1]);
                    String command = tokens[2];
                    entries.add(new RaftEntry(index, term, command));
                } 
                line = reader.readLine();
                currentIndex++;
            }
            reader.close();
        }
        return entries.toArray(new RaftEntry[entries.size()]);
    }
}
