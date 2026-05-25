package backend.optimize;

import java.util.ArrayList;
import java.util.List;

public class ReplayResult {
    public int windowCount;
    public int tableCount;
    public List<ReplaySnapshot> snapshots = new ArrayList<>();

    public void addSnapshot(ReplaySnapshot snapshot) {
        snapshots.add(snapshot.copy());
    }
}
