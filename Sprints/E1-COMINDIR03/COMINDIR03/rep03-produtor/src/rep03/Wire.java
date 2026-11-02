package rep03;

public final class Wire {
    public static final int LIMITE_RETIDOS = 10;
    public static final int T_TCP_MS = 5000;
    public static final int PORTO_BASE = 7000;
    public static final double LIMIAR_ALARME = 28.0;
    private Wire() { }
    private static final String TOPIC_NAMESPACE = "rep03-v1-";
    public static String topicIn(int k) { return TOPIC_NAMESPACE + "in-p" + k; }
    public static String topicData(int k) { return TOPIC_NAMESPACE + "p" + k; }
    public static String in(String sensor, double temp) {
        return in(sensor, temp, 0, new VectorClock(3));
    }
    public static String in(String sensor, double temp, long lamport,
                            VectorClock vector) {
        return "IN;" + sensor + ";" + temp + ";" + lamport + ";" + vector;
    }
}
