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
    public static String reg(String host, int port, SensorRecord r) {
        return "REG;" + host + ";" + port + ";" + r.toLine();
    }
    public static String type(String msg) {
        String value = msg.trim();
        int index = value.indexOf(';');
        if (index <= 0) throw new IllegalArgumentException("Mensagem sem tipo");
        return value.substring(0, index);
    }
    public static String body(String msg) {
        String value = msg.trim();
        int index = value.indexOf(';');
        if (index < 0) throw new IllegalArgumentException("Mensagem sem corpo");
        return value.substring(index + 1);
    }
    public static String[] regParts(String msg) {
        String[] fields = msg.trim().split(";", 4);
        if (fields.length != 4 || !fields[0].equals("REG")) {
            throw new IllegalArgumentException("REG mal formado");
        }
        return new String[] {fields[1], fields[2], fields[3]};
    }
}
