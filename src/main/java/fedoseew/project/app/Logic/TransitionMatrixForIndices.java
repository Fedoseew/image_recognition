package fedoseew.project.app.Logic;

import org.apache.commons.math3.util.CombinatoricsUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;


class TransitionMatrixForIndices {
    private final int Xi;
    private final int Xj;

    private final List<List<Integer>> transitionMatrix;

    public TransitionMatrixForIndices(int Xi, int Xj) {

        transitionMatrix = new ArrayList<>(2);

        transitionMatrix.add(0, new ArrayList<>(2));
        transitionMatrix.add(1, new ArrayList<>(2));

        transitionMatrix.get(0).add(0, 0);
        transitionMatrix.get(0).add(1, 0);
        transitionMatrix.get(1).add(0, 0);
        transitionMatrix.get(1).add(1, 0);

        this.Xi = Xi;
        this.Xj = Xj;
    }

    static void matrixToString(StringBuilder stringBuilder, List<List<Integer>> transitionMatrix) {
        for (int rowIndex = 0; rowIndex < transitionMatrix.size(); rowIndex++) {
            for (int indexInRow = 0; indexInRow < transitionMatrix.get(rowIndex).size(); indexInRow++) {
                int element = transitionMatrix
                        .get(rowIndex)
                        .get(indexInRow);
                stringBuilder
                        .append("(")
                        .append(rowIndex)
                        .append("->")
                        .append(indexInRow)
                        .append(": ")
                        .append(element)
                        .append(") ");
            }
            stringBuilder.append("\n");
        }
    }

    public List<List<Integer>> getTransitionMatrix() {
        return transitionMatrix;
    }

    private double getI0_XiXj() {
        return TransitionMatrix.log2(((double) CombinatoricsUtils.factorial(
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

    private double getI0_XjXi() {
        return TransitionMatrix.log2(((double) CombinatoricsUtils.factorial(
                transitionMatrix.get(0).get(0) + transitionMatrix.get(1).get(0)) /
                (
                        CombinatoricsUtils.factorial(transitionMatrix.get(0).get(0)) *
                                CombinatoricsUtils.factorial(transitionMatrix.get(1).get(0))
                )) *
                ((double) CombinatoricsUtils.factorial(
                        (transitionMatrix.get(0).get(1) + transitionMatrix.get(1).get(1))) /
                        (
                                CombinatoricsUtils.factorial(transitionMatrix.get(0).get(1)) *
                                        CombinatoricsUtils.factorial(transitionMatrix.get(1).get(1))
                        )));
    }

    public double calculateMetricBetweenXiAndXj() {
        BigDecimal bd = new BigDecimal(Double.toString((double) 1 / 2 * (getI0_XiXj() + getI0_XjXi())));
        bd = bd.setScale(2, RoundingMode.HALF_UP);
        return bd.doubleValue();
    }

    @Override
    public String toString() {
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("Transition matrix for X").append(Xi).append("|X").append(Xj).append("\n");
        matrixToString(stringBuilder, transitionMatrix);
        return stringBuilder.toString();
    }
}