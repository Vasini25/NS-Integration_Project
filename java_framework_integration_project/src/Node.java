public class Node {
    private int id;
    private int nextHop;

    private int lastHeard;


    public Node(int id) {
        this.id = id;
    }

    public int getId(){return id;}

    public int getNextHop(){return nextHop;}

    public int getLastHeard(){return lastHeard;}

    public void setNextHop(int nextHop){this.nextHop = nextHop;}


    public void setLastHeard(int lastHeard){this.lastHeard = lastHeard;}
}
