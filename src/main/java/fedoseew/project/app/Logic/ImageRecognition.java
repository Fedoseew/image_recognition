package fedoseew.project.app.Logic;

import fedoseew.project.app.Configurations.ApplicationConfiguration;
import fedoseew.project.app.Database.DB_TABLES;
import fedoseew.project.app.Database.DatabaseUtils;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public class ImageRecognition {

    private final String[] queries = DatabaseUtils.selectAllFromDb();
    DatabaseUtils databaseUtils = DatabaseUtils.createOrGetDatabase();

    /**
     * Распознование образа сравнивая со значениями из БД.
     *
     * @param source   - распарсенный код картинки в двоичном коде.
     * @param settings - настройки (параметры альфа, бетта и гамма).
     * @return Map<DB_TABLES, Integer> - мапу, где ключ - таблица, в которой произошло совпадение, значение - распознанное число.
     * @throws SQLException выкидывается при ошибках соединения или работы с БД.
     */

    public Map<DB_TABLES, Integer> recognition(String source, Object[] settings) throws SQLException {
        int alpha = (int) settings[0];
        int betta = (int) settings[1];
        int gamma = (int) settings[2];
        double minMetric = (double) settings[3];

        Connection connection = databaseUtils.getConnection();
        BinaryCodeComparator binaryCodeComparator = new BinaryCodeComparator();
        Map<DB_TABLES, Integer> result = new HashMap<>();

        if (!connection.isClosed()) {
            // Цикл по таблицам:
            for (int countOfQuery = 0; countOfQuery < queries.length; countOfQuery++) {
                ResultSet rs = databaseUtils.selectQuery(queries[countOfQuery]);
                // Цикл по строкам таблицы и поиск совпадений:
                while (rs.next()) {
                    int conditionResult = binaryCodeComparator.compare(rs.getString(1), source);
                    if (conditionResult > 0) {
                        if (rs.getString(2).equals("FALSE")) {
                            break;
                        } else if (rs.getString(2).equals("TRUE")) {
                            DB_TABLES db_table = DB_TABLES.valueOf(queries[countOfQuery].substring(14));
                            result.put(db_table, countOfQuery);
                            return result;
                        }
                    }
                }
            }
            smartRecognition(alpha, betta, gamma, minMetric);
        }
        return result;
    }

    /**
     * Распознование образа по алгоритму из курсовой.
     *
     * @param alpha - параметр настройки альфа.
     * @param betta - параметр настройки бетта.
     * @param gamma - параметр настройки гамма.
     * @throws SQLException выкидывается при ошибках соединения или работы с БД.
     */
    private void smartRecognition(int alpha, int betta, int gamma, double minMetric) throws SQLException {
        AtomicReference<ResultSet> resultSet = new AtomicReference<>();
        /* Все матрицы переходов: */
        List<List<TransitionMatrix>> allTransitionMatrices = new ArrayList<>();
        /* Информативности признаков (key - columnIndex, value - informative): */
        List<Map<Integer, Double>> informative = new ArrayList<>();
        /* Все признаки (key - номер таблицы (0..9), value - список признаков (key - номер признака, value - признак)): */
        Map<Integer, Map<Integer, String>> indices = new TreeMap<>();
        /* Цикл по таблицам: */
        for (int countOfQuery = 0; countOfQuery < queries.length; countOfQuery++) {
            Map<Integer, Double> tableInformative = new TreeMap<>();
            List<TransitionMatrix> tableTransitionMatrix = new ArrayList<>();
            indices.put(countOfQuery, new TreeMap<>());
            resultSet.set(databaseUtils.selectQuery(queries[countOfQuery]));
            /* Для уменьшения нагрузки кэшируем все строки из столбцов source и isTrue в массивы соответственно: */
            List<String> sourceColumnData = new ArrayList<>();
            List<String> isTrueColumnData = new ArrayList<>();
            while (resultSet.get().next()) {
                sourceColumnData.add(resultSet.get().getString(1));
                isTrueColumnData.add(resultSet.get().getString(2));
            }
            /* Цикл по колонкам таблицы: */
            for (
                    int columnIndex = 0;
                    columnIndex < Math.pow(ApplicationConfiguration.getSizeOfGrid(), 2);
                    columnIndex++
            ) {
                /* Матрица перехода для конкретной таблицы и колонки: */
                DB_TABLES dbTable = DB_TABLES.valueOf(queries[countOfQuery].substring(14));
                TransitionMatrix transitionMatrix = new TransitionMatrix(dbTable, columnIndex + 1);

                /* Массив количества переходов элементов, где индексы идут в следующем соотвествии:
                0->0, 0->1, 1->0, 1->1 : */
                List<Integer> countOfTransitionElement = Arrays.asList(0, 0, 0, 0);
                StringBuilder indicesBuilder = new StringBuilder();

                /* Цикл по строкам: */
                for (
                        int sourceRowIndex = 0, isTrueRowIndex = 0;
                        sourceRowIndex < sourceColumnData.size() && isTrueRowIndex < isTrueColumnData.size();
                        sourceRowIndex++, isTrueRowIndex++
                ) {
                    /* Проверки на соответствие элемента числу 0 или 1 и во что он переходит: */
                    try {
                        indicesBuilder.append(sourceColumnData.get(sourceRowIndex).charAt(columnIndex));
                        checkTransition(isTrueColumnData, countOfTransitionElement, isTrueRowIndex,
                                sourceColumnData, sourceRowIndex, columnIndex);
                    } catch (IndexOutOfBoundsException ignored) {
                    }
                }

                // Добавление признака в массив всех признаков:
                indices.get(countOfQuery).put(columnIndex + 1, indicesBuilder.toString());

                // Инициализация матрицы перехода:
                transitionMatrix.getTransitionMatrix().add(Arrays.asList(countOfTransitionElement.get(0), countOfTransitionElement.get(1)));
                transitionMatrix.getTransitionMatrix().add(Arrays.asList(countOfTransitionElement.get(2), countOfTransitionElement.get(3)));

                tableInformative.put(columnIndex + 1, transitionMatrix.getInformative());
                tableTransitionMatrix.add(transitionMatrix);
            }
            // Добавление информативности и матрицы перехода:
            informative.add(tableInformative);
            allTransitionMatrices.add(tableTransitionMatrix);
        }
        // Откидывание признаков по параметру alpha:
        filteringByAlpha(alpha, allTransitionMatrices, informative);

        /* Расстояния (key - номер таблицы (0..9),
           value - список мап сложных признаков (key - индекс сложного признака, value - расстояние)): */
        Map<Integer, List<Map<String, Double>>> metrics = new TreeMap<>();

        // Расчёт расстояний между признаками: P(Xi, Xj) = 1/2 * [I0(Xi|Xj) + I0(Xj|Xi)]
        calculateMetrics(informative, metrics, indices);

        // Мапа кластеров (key - число (номер таблицы), value - массив из кластеров (кластер - массив из признаков)):
        Map<Integer, List<List<String>>> clusters = new TreeMap<>();

        // Кластеризация признаков:
        clustering(clusters, metrics, minMetric, informative);

        // Сложные признаки (key -> число, value -> (key -> номер признака, value -> alias ("Xi|Xj))):
        Map<Integer, Map<Integer, String>> complexIndices = new TreeMap<>();

        // Формирование и фильтрация по параметру betta сложных признаков:
        createComplexIndices(complexIndices, clusters, betta, metrics);

        // TODO: Переход в новое пространство признаков (параметр gamma):
    }

    //---------------------------------------------Вспомогательные методы---------------------------------------------//

    /**
     * Проверка во что переходит элемент
     *
     * @param isTrueColumnData         - массив значений во что переходит
     * @param countOfTransitionElement - массив счётчиков перехоода
     * @param isTrueRowIndex           - индекс колонки со значениями TRUE/FALSE
     * @param sourceColumnData         - массив значений из колонки source
     * @param sourceRowIndex           - индекс колонки со значениями source
     * @param columnIndex              - индекс колонки
     */
    private void checkTransition(List<String> isTrueColumnData,
                                 List<Integer> countOfTransitionElement, int isTrueRowIndex,
                                 List<String> sourceColumnData, int sourceRowIndex, int columnIndex) {
        if (sourceColumnData.get(sourceRowIndex).charAt(columnIndex) == '0') {
            if (isTrueColumnData.get(isTrueRowIndex).equals("FALSE")) {
                int tmp = countOfTransitionElement.get(0) + 1;
                countOfTransitionElement.set(0, tmp);
            } else if (isTrueColumnData.get(isTrueRowIndex).equals("TRUE")) {
                int tmp = countOfTransitionElement.get(1) + 1;
                countOfTransitionElement.set(1, tmp);
            }
        } else if (sourceColumnData.get(sourceRowIndex).charAt(columnIndex) == '1') {
            if (isTrueColumnData.get(isTrueRowIndex).equals("FALSE")) {
                int tmp = countOfTransitionElement.get(2) + 1;
                countOfTransitionElement.set(2, tmp);
            } else if (isTrueColumnData.get(isTrueRowIndex).equals("TRUE")) {
                int tmp = countOfTransitionElement.get(3) + 1;
                countOfTransitionElement.set(3, tmp);
            }
        }
    }

    /**
     * Фильтрация признаков (по информативности) по параметру alpha
     *
     * @param alpha                 - параметр alpha
     * @param allTransitionMatrices - все матрицы перехода
     * @param informative           - все информативности
     */
    private void filteringByAlpha(int alpha, List<List<TransitionMatrix>> allTransitionMatrices,
                                  List<Map<Integer, Double>> informative) {
        double alphaProcent = allTransitionMatrices
                .get(1)
                .get(0)
                .getI0_Y()
                * ((double) alpha) / 100;

        List.copyOf(informative).forEach(tableInformative ->
                Map.copyOf(tableInformative).forEach((column, informativeOfColumn) -> {
                    if (informativeOfColumn < alphaProcent) {
                        tableInformative.remove(column);
                    }
                }));
    }

    /**
     * Расчёт расстояний между признаками
     *
     * @param informative - все информативности
     * @param metrics     - расстояния
     * @param indices     - признаки
     */
    private void calculateMetrics(List<Map<Integer, Double>> informative, Map<Integer, List<Map<String, Double>>> metrics,
                                  Map<Integer, Map<Integer, String>> indices) {
        var ref = new Object() {
            int countOfNumber = 0;
        };

        StringBuilder columnsBuilder = new StringBuilder();
        informative.forEach(tableInformative -> {
            metrics.put(ref.countOfNumber, new ArrayList<>());
            tableInformative.forEach((column1, informativeOfColumn1) -> {
                tableInformative.forEach((column2, informativeOfColumn2) -> {
                    if (!column1.equals(column2)) {
                        // Проверка на дубликаты (HINT: признак X1X2 == X2X1):
                        AtomicBoolean duplicates = new AtomicBoolean(false);
                        metrics.forEach((key, value) -> value.forEach(x -> {
                            boolean conditional = x.containsKey("X" + column1 + "|" + "X" + column2)
                                    || x.containsKey("X" + column2 + "|" + "X" + column1);
                            if (conditional) {
                                duplicates.set(true);
                            }
                        }));

                        if (!duplicates.get()) {
                            // Расчёт по формуле, добавление в map с метриками и формирование сложных признаков:
                            columnsBuilder.append("X").append(column1).append("|X").append(column2);
                            TransitionMatrixForIndices transitionMatrix = new TransitionMatrixForIndices(column1, column2);
                            String firstSource = indices.get(ref.countOfNumber).get(column1);
                            String secondSource = indices.get(ref.countOfNumber).get(column2);
                            for (int i = 0; i < firstSource.length(); i++) {
                                if (firstSource.charAt(i) == '0') {
                                    if (secondSource.charAt(i) == '0') {
                                        int old = transitionMatrix.getTransitionMatrix().get(0).get(0);
                                        transitionMatrix.getTransitionMatrix().get(0).set(0, old + 1);
                                    } else if (secondSource.charAt(i) == '1') {
                                        int old = transitionMatrix.getTransitionMatrix().get(0).get(1);
                                        transitionMatrix.getTransitionMatrix().get(0).set(1, old + 1);
                                    }
                                } else if (firstSource.charAt(i) == '1') {
                                    if (secondSource.charAt(i) == '0') {
                                        int old = transitionMatrix.getTransitionMatrix().get(1).get(0);
                                        transitionMatrix.getTransitionMatrix().get(1).set(0, old + 1);
                                    } else if (secondSource.charAt(i) == '1') {
                                        int old = transitionMatrix.getTransitionMatrix().get(1).get(1);
                                        transitionMatrix.getTransitionMatrix().get(1).set(1, old + 1);
                                    }
                                }
                            }
                            metrics.get(ref.countOfNumber).add(Collections.singletonMap(
                                    columnsBuilder.toString(),
                                    transitionMatrix.calculateMetricBetweenXiAndXj()));
                            columnsBuilder.delete(0, columnsBuilder.length());
                        }
                    }
                });
            });
            ref.countOfNumber++;
        });
    }

    /**
     * Кластеризация признаков
     *
     * @param clusters             - массив кластеров для сохранения данных
     * @param metrics              - расстояния между признаками
     * @param maxMetricCoefficient - коэффициент попадания в кластер (берётся от максимального расстояния)
     * @param informative          - информативности признаков
     */
    private void clustering(Map<Integer, List<List<String>>> clusters, Map<Integer, List<Map<String, Double>>> metrics,
                            double maxMetricCoefficient, List<Map<Integer, Double>> informative) {
        metrics.forEach((number, listOfIndices) -> {
            clusters.put(number, new ArrayList<>());
            AtomicReference<Double> maxValue = new AtomicReference<>(0.0);
            listOfIndices.forEach(stringDoubleMap -> {
                stringDoubleMap.forEach((key, value) -> {
                    // Находим максимальное расстояние между признаками:
                    if (value > maxValue.get()) {
                        maxValue.set(value);
                    }
                });
            });
            // Ставим максимально допустимое расстояние между признаками в одном кластере равное 30% от максимального расстояния:
            double maxMetric = maxValue.get() * maxMetricCoefficient;
            // Оставшиеся после откидывания по параметру alpha признаки:
            Map<Integer, List<String>> indices = new TreeMap<>();
            indices.put(number, new ArrayList<>());
            informative.get(number).forEach((ind, inf) -> {
                indices.get(number).add("X" + ind);
            });
            // Пока есть неотобранные в кластеры признаки:
            int i = 0;
            while (indices.get(number).size() > 0) {
                int finalI = i;
                try {
                    clusters.get(number).get(finalI);
                } catch (IndexOutOfBoundsException e) {
                    // Берем самый первый признак из списка оставшихся признаков, добавляем в кластер, удаляем из списка:
                    Map.copyOf(indices)
                            .get(number)
                            .stream()
                            .findFirst()
                            .ifPresent(Xi -> {
                                clusters.get(number).add(new ArrayList<>());
                                clusters.get(number).get(finalI).add(Xi);
                                indices.get(number).remove(Xi);
                            });
                }
                try {
                    AtomicInteger sizeOfCluster = new AtomicInteger(clusters.get(number).get(i).size());
                    AtomicBoolean isIndicesAdded = new AtomicBoolean(false);
                    // Пробегаем динамически кластер, одновременно добавляя в него новые признаки:
                    for (int indInCluster = 0; indInCluster < sizeOfCluster.get(); indInCluster++) {
                        for (Map<String, Double> map : listOfIndices) {
                            int finalI1 = i;
                            int finalIndInCluster = indInCluster;
                            map.forEach((metric, value) -> {
                                if (!clusters.get(number).get(finalI1).contains(metric.split("\\|")[1])
                                        && clusters.get(number).get(finalI).get(finalIndInCluster).equals(metric.split("\\|")[0])
                                        && value < maxMetric
                                        && indices.get(number).contains(metric.split("\\|")[1])
                                ) {
                                    clusters.get(number).get(finalI1).add(metric.split("\\|")[1]);
                                    indices.get(number).remove(metric.split("\\|")[1]);
                                    sizeOfCluster.set(clusters.get(number).get(finalI1).size());
                                    isIndicesAdded.set(true);
                                }
                            });
                        }
                    }
                    if (!isIndicesAdded.get()) {
                        Map.copyOf(indices)
                                .get(number)
                                .stream()
                                .findFirst()
                                .ifPresent(Xi -> {
                                    clusters.get(number).add(new ArrayList<>());
                                    clusters.get(number).get(finalI + 1).add(Xi);
                                    indices.get(number).remove(Xi);
                                });
                    }
                    i++;
                } catch (IndexOutOfBoundsException ignored) {
                }
            }
        });
    }

    /**
     * Формирование сложных признаков и их отсеивание по параметру betta
     *
     * @param complexIndices - переменная, в которую будем складывать сложные признаки
     * @param clusters       - кластеры с признаками
     * @param betta          - параметр betta
     * @param metrics        - расстояния между признаками
     */
    private void createComplexIndices(Map<Integer, Map<Integer, String>> complexIndices,
                                      Map<Integer, List<List<String>>> clusters, double betta,
                                      Map<Integer, List<Map<String, Double>>> metrics) {
        clusters.forEach((number, clustersForNumber) -> {
            Set<String> complexIndicesForNumber = new HashSet<>();
            complexIndices.put(number, new HashMap<>());
            if (metrics.get(number).size() != 0) {
                clustersForNumber.forEach(cluster -> {
                    cluster.forEach(simpleInd -> {
                        clustersForNumber.forEach(otherCluster -> {
                            if (!cluster.equals(otherCluster)) {
                                otherCluster.forEach(otherSimpleInd -> {
                                    String complexInd = metrics.get(number).stream().anyMatch(map -> map.containsKey(simpleInd + "|" + otherSimpleInd))
                                            ? simpleInd + "|" + otherSimpleInd
                                            : otherSimpleInd + "|" + simpleInd;
                                    if (!complexIndicesForNumber.contains(complexInd)
                                            && metrics.get(number).stream().anyMatch(stringDoubleMap ->
                                            stringDoubleMap.get(complexInd) != null && stringDoubleMap.get(complexInd) >= betta)) {
                                        complexIndicesForNumber.add(complexInd);
                                        complexIndices.get(number).put(complexIndicesForNumber.size(), complexInd);
                                    }
                                });
                            }
                        });
                    });
                });
            }
        });
    }
}
