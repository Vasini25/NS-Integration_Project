public class Node {
    private int id;
    private int distance;
    private int nextHop;

    private int lastHeard;


    public Node(int id) {
        this.id = id;
    }

    public int getId(){return id;}

    public int getDistance(){return distance;}

    public int getNextHop(){return nextHop;}

    public int getLastHeard(){return lastHeard;}

    public void setNextHop(int nextHop){this.nextHop = nextHop;}

    public void setDistance(int distance){this.distance = distance;}

    public void setLastHeard(int lastHeard){this.lastHeard = lastHeard;}
}
