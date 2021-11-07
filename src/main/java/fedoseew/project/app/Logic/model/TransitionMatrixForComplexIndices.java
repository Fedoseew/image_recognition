package fedoseew.project.app.Logic.model;

import fedoseew.project.app.Database.DB_TABLES;
import org.apache.commons.math3.util.CombinatoricsUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

public class TransitionMatrixForComplexIndices {
    private final List<List<Integer>> transitionMatrix;
    private final DB_TABLES table;
    private final String complexIndAlias;

    public String getComplexIndices() {
        return complexIndAlias;
    }

    public TransitionMatrixForComplexIndices(DB_TABLES table, String complexIndAlias) {
        this.table = table;
        this.complexIndAlias = complexIndAlias;
        transitionMatrix = new ArrayList<>();
    }

    public List<List<Integer>> getTransitionMatrix() {
        return transitionMatrix;
    }

    public double getI0_Y() {
        return I0_Y.allInformativeI0_Y.get(table);
    }

    private double getI0_YX() {
        return TransitionMatrix.log2(
                ((double) CombinatoricsUtils.factorial(transitionMatrix.get(0).get(0) + transitionMatrix.get(0).get(1)) /
                        (CombinatoricsUtils.factorial(transitionMatrix.get(0).get(0)) * CombinatoricsUtils.factorial(transitionMatrix.get(0).get(1)))) *

                        ((double) CombinatoricsUtils.factorial((transitionMatrix.get(0).get(2) + transitionMatrix.get(0).get(3))) /
                                (CombinatoricsUtils.factorial(transitionMatrix.get(0).get(2)) * CombinatoricsUtils.factorial(transitionMatrix.get(0).get(3)))) *

                        ((double) CombinatoricsUtils.factorial((transitionMatrix.get(1).get(0) + transitionMatrix.get(1).get(1))) /
                                (CombinatoricsUtils.factorial(transitionMatrix.get(1).get(0)) * CombinatoricsUtils.factorial(transitionMatrix.get(1).get(1)))) *

                        ((double) CombinatoricsUtils.factorial((transitionMatrix.get(1).get(1) + transitionMatrix.get(1).get(2))) /
                                (CombinatoricsUtils.factorial(transitionMatrix.get(1).get(1)) * CombinatoricsUtils.factorial(transitionMatrix.get(1).get(2)))) *

                        ((double) CombinatoricsUtils.factorial((transitionMatrix.get(1).get(2) + transitionMatrix.get(1).get(3))) /
                                (CombinatoricsUtils.factorial(transitionMatrix.get(1).get(2)) * CombinatoricsUtils.factorial(transitionMatrix.get(1).get(3))))
        );
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

    private String printMatrixForComplexIndices() {
        StringBuilder resultString = new StringBuilder();
        List<String> valuesList = List.of("00", "01", "10", "11");
        final int[] rowInd = {0};
        this.getTransitionMatrix().forEach(row -> {
            final int[] colInd = {0};
            row.forEach(columnInRow -> {
                resultString
                        .append(valuesList.get(colInd[0]))
                        .append("->")
                        .append(rowInd[0])
                        .append(": ")
                        .append(columnInRow)
                        .append(", ");
                colInd[0]++;
            });
            rowInd[0]++;
        });
        return resultString.toString();
    }

    @Override
    public String toString() {
        return "\n\nTransition matrix for table [" +
                table +
                "] for indices [" +
                complexIndAlias +
                "]:\n" +
                printMatrixForComplexIndices() +
                "\nInformative: " +
                calculateInformative();
    }
}
