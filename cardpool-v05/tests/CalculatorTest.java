import com.example.cardpool.Calculator;

public class CalculatorTest {
    public static void main(String[] args) {
        Calculator.Result r = Calculator.calculate(10, 3, 4, 120, 25, 5);
        if (r.remaining != 3) throw new AssertionError("Remaining copies mismatch");
        if (Math.abs(r.slotProbability - 0.00625) > 1e-12) throw new AssertionError("Single slot mismatch");
        if (Math.abs(r.fiveSlotProbability - (1-Math.pow(1-0.00625, 5))) > 1e-12) {
            throw new AssertionError("Five slots mismatch");
        }
        boolean caught = false;
        try { Calculator.calculate(10, 9, 2, 120, 25, 5); }
        catch (IllegalArgumentException e) { caught = true; }
        if (!caught) throw new AssertionError("Must reject impossible numbers");
        System.out.println("PASSED: arithmetic and invalid input tests; example probability = " +
                (100 * r.fiveSlotProbability) + "%");
    }
}
