package fedoseew.project.app.Logic;

import fedoseew.project.app.Database.DB_TABLES;
import org.apache.commons.math3.util.CombinatoricsUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

class InformativeY {
    protected static Double I0_Y;
    protected static DB_TABLES table;
}

public class TransitionMatrix {

    private final List<List<Integer>> transitionMatrix;
    private final DB_TABLES table;
    private final int columnIndex;


    public TransitionMatrix(DB_TABLES table, int columnIndex) {

        this.table = table;
        this.columnIndex = columnIndex;
        transitionMatrix = new ArrayList<>();
    }

    // HINT: log2(x) = log10(x) / log10(2)
    public static double log2(double n) {
        return Math.log10(n) / Math.log10(2);
    }

    public List<List<Integer>> getTransitionMatrix() {

        return transitionMatrix;
    }

    public double getI0_Y() {
        if (
                InformativeY.I0_Y == null
                        || InformativeY.table == null
                        || !InformativeY.table.equals(table)
        ) {

            InformativeY.table = table;

            AtomicInteger sumOfAllElements = new AtomicInteger();
            transitionMatrix.forEach(row -> row.forEach(sumOfAllElements::addAndGet));

            InformativeY.I0_Y = (double) CombinatoricsUtils.factorial(sumOfAllElements.get()) /
                    (CombinatoricsUtils.factorial(
                            (transitionMatrix.get(0).get(0) + transitionMatrix.get(1).get(0))) *
                            CombinatoricsUtils.factorial(
                                    (transitionMatrix.get(0).get(1) + transitionMatrix.get(1).get(1))));

        }
        return log2(InformativeY.I0_Y);
    }

    private double getI0_YX() {
        return log2(((double) CombinatoricsUtils.factorial(
                transitionMatrix.get(0).get(0) + transitionMatrix.get(0).get(1)) /
                (
                        CombinatoricsUtils.factorial(transitionMatrix.get(0).get(0)) *
                                CombinatoricsUtils.factorial(transitionMatrix.get(0).get(1))
                )) *
                ((double) CombinatoricsUtils.factorial(
                        (transitionMatrix.get(1).get(0) + transitionMatrix.get(1).get(1))) /
                        (
                                CombinatoricsUtils.factorial(transitionMatrix.get(1).get(0)) *
                                        CombinatoricsUtils.factorial(transitionMatrix.get(1).get(1))
                        )));
    }

    private double calculateInformative() {

        double informative0_YX = getI0_YX();
        double informative0_Y = getI0_Y();

        // HINT: I0(X:Y) = I0(Y) – I0(Y|X) = I0(X) – I0(X|Y)
        double informative = informative0_Y - informative0_YX;

        BigDecimal bd = new BigDecimal(Double.toString(informative));
        bd = bd.setScale(2, RoundingMode.HALF_UP);

        return bd.doubleValue();
    }

    public double getInformative() {
        return calculateInformative();
    }

    @Override
    public String toString() {

        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("\nTransition matrix for table [").append(table)
                .append("] for column [").append(columnIndex).append("]:\n");

        TransitionMatrixForIndices.matrixToString(stringBuilder, transitionMatrix);

        stringBuilder.append("Informative: ").append(calculateInformative());

        return stringBuilder.toString();
    }
}
