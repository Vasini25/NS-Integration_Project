public class Node {
    private int id;
    private int distance;
    private int nextHop;


    public Node(int id) {
        this.id = id;
    }

    public int getId(){return id;}

    public int getDistance(){return distance;}

    public int getNextHop(){return nextHop;}

    public void setNextHop(int nextHop){this.nextHop = nextHop;}

    public void setDistance(int distance){this.distance = distance;}
}
