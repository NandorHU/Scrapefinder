package hu.nandor.pokelens;

/** Pure rules: type effectiveness is distinct from damage. PokeAPI type IDs are 1..18. */
public final class BattleMath {
    private BattleMath() {}
    public static double effectiveness(int moveType, int[] defendingTypes, int generation, int[][] chart) {
        double result = 1;
        for (int defending : defendingTypes) {
            double factor = chart[moveType - 1][defending - 1] / 100.0;
            if (generation <= 5 && defending == 9 && (moveType == 8 || moveType == 17)) factor = .5;
            if (generation == 1) {
                if (moveType == 8 && defending == 14) factor = 0;
                if (moveType == 7 && defending == 4) factor = 2;
                if (moveType == 4 && defending == 7) factor = 2;
                if (moveType == 15 && defending == 10) factor = 1;
            }
            result *= factor;
        }
        return result;
    }
    public static double stab(int moveType, int[] ownTypes) {
        for (int t : ownTypes) if (t == moveType) return 1.5;
        return 1;
    }
    public static int damageClass(int type, int storedClass, int generation) {
        if (storedClass == 1 || generation >= 4) return storedClass;
        return type == 10 || type == 11 || type == 12 || type == 13 || type == 14 || type == 15 || type == 16 || type == 17 ? 3 : 2;
    }
    public static double comparison(int power, double effectiveness, double stab) {
        return Math.max(0, power) * effectiveness * stab;
    }
}
