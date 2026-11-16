package exp;

public final class TicketPrice {
    private TicketPrice() {}

    /**
     * Ticket price in cents by age (inclusive bounds):
     * ages 0-2 -> 0, ages 3-12 -> 800, ages 13-64 -> 1500, ages 65 and over -> 1000.
     * @throws IllegalArgumentException if age < 0
     */
    public static int ticketPriceCents(int age) {
        if (age < 0) {
            throw new IllegalArgumentException("age must be >= 0");
        }
        if (age <= 2) {
            return 0;
        }
        if (age <= 12) {
            return 800;
        }
        if (age <= 64) {
            return 1500;
        }
        return 1000;
    }
}
