package exp;

public final class Withdraw {
    private Withdraw() {}

    /**
     * Returns the new balance after withdrawing amount from balance (both in cents).
     * @throws IllegalArgumentException if amount <= 0 or balance < 0
     * @throws IllegalStateException if amount is greater than balance
     */
    public static int withdraw(int balance, int amount) {
        if (amount <= 0 || balance < 0) {
            throw new IllegalArgumentException("invalid balance or amount");
        }
        if (amount > balance) {
            throw new IllegalStateException("insufficient funds");
        }
        return balance - amount;
    }
}
