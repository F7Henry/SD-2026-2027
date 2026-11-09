package raft04;

public final class VoteReply {
    public final long term;
    public final int voter;
    public final int candidate;
    public final boolean granted;

    public VoteReply(long term, int voter, int candidate, boolean granted) {
        this.term = term;
        this.voter = voter;
        this.candidate = candidate;
        this.granted = granted;
    }

    // VOTO;<termo>;<eleitor>;<candidato>;<S|N>
    public String encode() {
        return "VOTO;" + term + ";" + voter + ";" + candidate + ";" + (granted ? "S" : "N");
    }

    public static VoteReply decode(String message) {
        String[] f = message.trim().split(";", -1);
        if (f.length != 5 || !"VOTO".equals(f[0])
                || !(f[4].equals("S") || f[4].equals("N"))) {
            throw new IllegalArgumentException("VOTO mal formado");
        }
        return new VoteReply(Long.parseLong(f[1]), Integer.parseInt(f[2]),
                Integer.parseInt(f[3]), f[4].equals("S"));
    }
}