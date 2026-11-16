package exp;

public final class LetterGrade {
    private LetterGrade() {}

    /**
     * Maps an integer score to a letter grade using inclusive lower bounds:
     * 90-100 -> 'A', 80-89 -> 'B', 70-79 -> 'C', 60-69 -> 'D', 0-59 -> 'F'.
     * @throws IllegalArgumentException if score < 0 or score > 100
     */
    public static char letterGrade(int score) {
        if (score < 0 || score > 100) {
            throw new IllegalArgumentException("score out of range 0..100");
        }
        if (score >= 90) {
            return 'A';
        }
        if (score >= 80) {
            return 'B';
        }
        if (score >= 70) {
            return 'C';
        }
        if (score >= 60) {
            return 'D';
        }
        return 'F';
    }
}
