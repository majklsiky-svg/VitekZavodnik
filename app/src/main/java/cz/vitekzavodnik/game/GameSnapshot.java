package cz.vitekzavodnik.game;

public final class GameSnapshot {
    public final int stage;
    public final int minerals;
    public final int tools;
    public final int coins;
    public final boolean inKart;
    public final int checkpoint;
    public final int checkpointCount;
    public final float raceTime;
    public final float bestTime;
    public final String message;

    public GameSnapshot(int stage, int minerals, int tools, int coins, boolean inKart,
                        int checkpoint, int checkpointCount, float raceTime, float bestTime, String message) {
        this.stage = stage;
        this.minerals = minerals;
        this.tools = tools;
        this.coins = coins;
        this.inKart = inKart;
        this.checkpoint = checkpoint;
        this.checkpointCount = checkpointCount;
        this.raceTime = raceTime;
        this.bestTime = bestTime;
        this.message = message == null ? "" : message;
    }

    public String questText() {
        if (stage == 0) return "Úkol: Najdi minerály  " + minerals + "/5";
        if (stage == 1) return "Úkol: Najdi díly pro mechanika  " + tools + "/3";
        if (stage == 2 && !inKart) return "Úkol: Vrať se k motokáře a stiskni AKCE";
        if (stage == 2) return "Závod: kontrolní bod " + Math.min(checkpoint + 1, checkpointCount) + "/" + checkpointCount;
        return "Hotovo! Prozkoumávej svět a sbírej mince.";
    }
}
