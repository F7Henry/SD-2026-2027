package rep02;

public final class Wire {
    public static final int LIMITE_RETIDOS = 10;
    public static final int T_TCP_MS = 5000;
    public static final int PORTO_BASE = 7000;
    private Wire() { }
    public static String topicIn(int k) { return "rep02-in-p" + k; }
    public static String topicData(int k) { return "rep02-p" + k; }
    public static String in(String sensor, double temp) {
        return "IN;" + sensor + ";" + temp;
    }
}
