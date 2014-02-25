
public class MotionVector {

    private int dx = 0;
    private int dy = 0;

    public MotionVector(int dx, int dy) {
        this.dx = dx;
        this.dy = dy;
    }

    public int getTotalMotionValue() {
        return (int) Math.round(Math.sqrt(Math.pow(dx, 2) + Math.pow(dy, 2)));
    }
}

