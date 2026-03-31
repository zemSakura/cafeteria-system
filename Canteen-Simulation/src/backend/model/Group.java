package backend.model;

import java.util.ArrayList;
import java.util.List;

/**
 * 到达小组
 * 同一 groupId 的学生可以被封装成一个组
 */
public class Group {
    private int groupId;
    private int size;
    private long arrivalTime;
    private List<Student> members;

    public Group(int groupId, long arrivalTime, List<Student> members) {
        this.groupId = groupId;
        this.arrivalTime = arrivalTime;
        this.members = new ArrayList<>(members);
        this.size = members.size();
    }

    public int getGroupId() { return groupId; }
    public int getSize() { return size; }
    public long getArrivalTime() { return arrivalTime; }
    public List<Student> getMembers() { return new ArrayList<>(members); }

    @Override
    public String toString() {
        return "Group{" +
                "groupId=" + groupId +
                ", size=" + size +
                ", arrivalTime=" + arrivalTime +
                ", membersCount=" + members.size() +
                '}';
    }
}