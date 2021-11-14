package fedoseew.project.app.Logic;

import fedoseew.project.app.Configurations.ApplicationConfiguration;
import fedoseew.project.app.Database.DB_TABLES;
import fedoseew.project.app.Database.DatabaseUtils;
import fedoseew.project.app.Logic.model.I0_Y;
import fedoseew.project.app.Logic.model.TransitionMatrix;
import fedoseew.project.app.Logic.model.TransitionMatrixForComplexIndices;
import fedoseew.project.app.Logic.model.TransitionMatrixForIndices;
import fedoseew.project.app.Logic.utils.BinaryCodeComparator;
import fedoseew.project.app.Logic.utils.ComplexIndGenerator;
import org.apache.commons.math3.util.Pair;
import org.springframework.lang.Nullable;

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
     * @param source    распаршенный код картинки в двоичном коде.
     * @param settings  настройки (параметры альфа, бетта и гамма).
     * @return Map<DB_TABLES, Integer>  мапа, где ключ - таблица, в которой произошло совпадение, значение - распознанное число.
     * @throws SQLException выкидывается при ошибках соединения или работы с БД.
     */

    public Map<DB_TABLES, Integer> recognition(String source, Object[] settings) throws SQLException {
        int alpha = (int) settings[0];
        int betta = (int) settings[1];
        int gamma = (int) settings[2];
        double minMetric = (double) settings[3];

        Connection connection = databaseUtils.getConnection();
        BinaryCodeComparator binaryCodeComparator = new BinaryCodeComparator();
        Map<DB_TABLES, Integer> result = new LinkedHashMap<>();

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
            smartRecognition(source, alpha, betta, gamma, minMetric);
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
    private void smartRecognition(String source, int alpha, int betta, int gamma, double minMetric) throws SQLException {
        // Будем кешировать значения колонок source и isTrue для каждой из таблиц:
        Map<Integer, List<String>> sourcesDataCache = new TreeMap<>();
        Map<Integer, List<String>> isTrueDataCache = new TreeMap<>();

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
                sourcesDataCache.put(countOfQuery, sourceColumnData);
                isTrueDataCache.put(countOfQuery, isTrueColumnData);
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
                for (int sourceRowIndex = 0, isTrueRowIndex = 0;
                     sourceRowIndex < sourceColumnData.size() && isTrueRowIndex < isTrueColumnData.size();
                     sourceRowIndex++, isTrueRowIndex++) {
                    /* Проверки на соответствие элемента числу 0 или 1 и во что он переходит: */
                    try {
                        indicesBuilder.append(sourceColumnData.get(sourceRowIndex).charAt(columnIndex));
                        checkTransition(isTrueColumnData, countOfTransitionElement, isTrueRowIndex, sourceColumnData, sourceRowIndex, columnIndex);
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

        // Сложные признаки (key -> число, value -> (key -> alias ("Xi|Xj), value - бинарный кортеж сложного признака)):
        Map<Integer, Map<String, String>> complexIndices = new TreeMap<>();

        // Формирование сложных признаков:
        createBinaryComplexIndices(complexIndices, clusters, metrics, indices);

        // Фильтрация сложных признаков по параметру betta:
        Map<Integer, List<TransitionMatrixForComplexIndices>> transitionMatricesForComplexIndices =
                filteringComplexIndicesByBetta(complexIndices, isTrueDataCache, betta);

        // TODO: Создание тройных (или более) сложных признаков из тех, которые отсеялись по betta

        // Переход в новое пространтсво по параметру gamma:
        Map<Integer, Map<String, String>> complexIndicesNewSpace = convertComplexIndicesToNewSpace(transitionMatricesForComplexIndices, gamma);
        Map<Integer, List<String>> indicesNewSpace = convertIndicesToNewSpace(sourcesDataCache, complexIndicesNewSpace);
        Map<Integer, String> userSourceToNewSpace = userSourceToNewSpace(source, complexIndicesNewSpace);

        // Подсчёт совпадений:
        Map<Integer, Map<Integer, Integer>> countOfTransition = calculateCountOfTransitions(indicesNewSpace, userSourceToNewSpace);

        // Вычисление результата распознавания:
        Pair<Integer, Integer> result = calculateResult(isTrueDataCache, countOfTransition);

        System.out.println("Result is " + result);
    }

    @Nullable
    private Pair<Integer, Integer> calculateResult(
            Map<Integer, List<String>> isTrueDataCache,
            Map<Integer, Map<Integer, Integer>> countOfTransition
    ) {
        Map<Integer, List<Map<String, Integer>>> counts = new LinkedHashMap<>(
                Map.of(
                        0, new ArrayList<>(List.of(Map.of("TRUE", 0), Map.of("FALSE", 0))),
                        1, new ArrayList<>(List.of(Map.of("TRUE", 0), Map.of("FALSE", 0))),
                        2, new ArrayList<>(List.of(Map.of("TRUE", 0), Map.of("FALSE", 0))),
                        3, new ArrayList<>(List.of(Map.of("TRUE", 0), Map.of("FALSE", 0))),
                        4, new ArrayList<>(List.of(Map.of("TRUE", 0), Map.of("FALSE", 0))),
                        5, new ArrayList<>(List.of(Map.of("TRUE", 0), Map.of("FALSE", 0))),
                        6, new ArrayList<>(List.of(Map.of("TRUE", 0), Map.of("FALSE", 0))),
                        7, new ArrayList<>(List.of(Map.of("TRUE", 0), Map.of("FALSE", 0))),
                        8, new ArrayList<>(List.of(Map.of("TRUE", 0), Map.of("FALSE", 0))),
                        9, new ArrayList<>(List.of(Map.of("TRUE", 0), Map.of("FALSE", 0)))
                )
        );

        countOfTransition.forEach((number, mapOfTransitions) -> mapOfTransitions.forEach((numberOfObject, count) -> {
                if (isTrueDataCache.get(number).get(numberOfObject).equals("TRUE")) {
                   counts.get(number).set(0, Map.of("TRUE", count));
                } else {
                    counts.get(number).set(1, Map.of("FALSE", count));
                }
        }));

        Map<Integer, Integer> maxTrueCount = new LinkedHashMap<>(Map.of(0, 0, 1, 0, 2, 0, 3, 0, 4, 0, 5, 0, 6, 0, 7, 0, 8, 0, 9, 0));
        Map<Integer, Integer> maxFalseCount = new LinkedHashMap<>(Map.of(0, 0, 1, 0, 2, 0, 3, 0, 4, 0, 5, 0, 6, 0, 7, 0, 8, 0, 9, 0));

        counts.forEach((number, list) -> {
                Integer oldValue = maxTrueCount.get(number);
                maxTrueCount.put(number, list.get(0).get("TRUE") + oldValue);
        });

        counts.forEach((number, list) -> {
                Integer oldValue = maxFalseCount.get(number);
                maxFalseCount.put(number, list.get(1).get("FALSE") + oldValue);
        });

        AtomicBoolean flag = new AtomicBoolean(true);
        Optional<Map.Entry<Integer, Integer>> maxTrue = maxTrueCount.entrySet().stream().max(Comparator.comparingInt(Map.Entry::getValue));
        Optional<Map.Entry<Integer, Integer>> maxFalse = maxFalse = maxFalseCount.entrySet().stream().max(Comparator.comparingInt(Map.Entry::getValue));
        /*while (flag.get()) {
            maxTrue = maxTrueCount.entrySet().stream().max(Comparator.comparingInt(Map.Entry::getValue));
            maxFalse = maxFalseCount.entrySet().stream().max(Comparator.comparingInt(Map.Entry::getValue));
            Optional<Map.Entry<Integer, Integer>> finalMaxTrue = maxTrue;
            maxFalse.ifPresent(pair -> {
                if (finalMaxTrue.isPresent() && finalMaxTrue.get().getKey().equals(pair.getKey())) {
                    maxTrueCount.remove(pair.getKey());
                } else {
                    flag.set(false);
                }
            });
        }*/
        Map<Integer, Integer> resultMap = new LinkedHashMap<>();
        counts.forEach((number, list) -> {
            int subtraction = list.get(0).get("TRUE") - list.get(1).get("FALSE");
            resultMap.put(number, subtraction);
        });

        Map.Entry<Integer, Integer> entry = resultMap.entrySet().stream()
                .filter(e -> e.getValue() != 0)
                .max(Comparator.comparingInt(Map.Entry::getValue))
                .get();
        return Pair.create(entry.getKey(), entry.getValue());
    }

    private Map<Integer, Map<Integer, Integer>> calculateCountOfTransitions(
            Map<Integer, List<String>> indicesNewSpace,
            Map<Integer, String> userSourceToNewSpace
    ) {
        Map<Integer, Map<Integer, Integer>> countOfTransition = new HashMap<>();
        userSourceToNewSpace.forEach((number, value) -> {
            countOfTransition.put(number, new HashMap<>());
            List<String> list = indicesNewSpace.get(number);
            for (int ind = 0; ind < list.size(); ind++) {
                    AtomicInteger count = new AtomicInteger();
                    for (int i = 0; i < list.get(ind).length(); i++) {
                        if (value.charAt(i) != '-' && list.get(ind).charAt(i) != '-' && value.charAt(i) == list.get(ind).charAt(i)) {
                            count.getAndIncrement();
                        }
                    }
                    countOfTransition.get(number).put(ind, count.get());
                }
        });
        return countOfTransition;
    }

    private Map<Integer, String> userSourceToNewSpace(String source, Map<Integer, Map<String, String>> complexIndicesNewSpace) {
        Map<String, Integer> map = Map.of("00", 0, "01", 1, "10", 2, "11", 3);
        Map<Integer, String> userSourceNewSpace = new LinkedHashMap<>();
        complexIndicesNewSpace.forEach((number, mapOfIndicesWithNewValue) -> {
            StringBuilder sb = new StringBuilder();
            mapOfIndicesWithNewValue.forEach((indAlias, newValue) -> {
                String[] split = indAlias.split("\\|");
                String first = String.valueOf(source.charAt(Integer.parseInt(split[0].split("X")[1]) - 1));
                String second = String.valueOf(source.charAt(Integer.parseInt(split[1].split("X")[1]) - 1));
                int index = map.get(first + second);
                char charAt = newValue.charAt(index);
                sb.append(charAt);
            });
            userSourceNewSpace.put(number, sb.toString());
        });
        return userSourceNewSpace;
    }

    private Map<Integer, List<String>> convertIndicesToNewSpace(Map<Integer, List<String>> sourcesDataCache, Map<Integer, Map<String, String>> complexIndicesNewSpace) {
        Map<Integer, List<String>> indicesNewSpace = new LinkedHashMap<>();
        Map<String, Integer> map = Map.of("00", 0, "01", 1, "10", 2, "11", 3);
        complexIndicesNewSpace.forEach((number, mapOfIndicesWithNewValue) -> {
            indicesNewSpace.put(number, new ArrayList<>());
            List<String> sourcesForNumber = sourcesDataCache.get(number);
            sourcesForNumber.forEach(source -> {
                StringBuilder sb = new StringBuilder();
                mapOfIndicesWithNewValue.forEach((indAlias, newValue) -> {
                    String[] split = indAlias.split("\\|");
                    String first = String.valueOf(source.charAt(Integer.parseInt(split[0].split("X")[1]) - 1));
                    String second = String.valueOf(source.charAt(Integer.parseInt(split[1].split("X")[1]) - 1));
                    int index = map.get(first + second);
                    char charAt = newValue.charAt(index);
                    sb.append(charAt);
                });
                indicesNewSpace.get(number).add(sb.toString());
            });
        });
        return indicesNewSpace;
    }

    //---------------------------------------------Вспомогательные методы---------------------------------------------//

    /**
     * Проверка во что переходит элемент
     *
     * @param isTrueColumnData          массив значений во что переходит
     * @param countOfTransitionElement  массив счётчиков перехоода
     * @param isTrueRowIndex            индекс колонки со значениями TRUE/FALSE
     * @param sourceColumnData          массив значений из колонки source
     * @param sourceRowIndex            индекс колонки со значениями source
     * @param columnIndex               индекс колонки
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
     * Проверка во что переходит сложный признак
     */
    private void checkTransitionForComplexIndices(List<String> isTrueForNumberData, String indValue, List<Integer> countOfTransitionElement) {
        for (int chInd = 0, isTrueColumnInd = 0; chInd < indValue.length(); chInd += 3, isTrueColumnInd++) {
            String value = "" + indValue.charAt(chInd) + indValue.charAt(chInd + 1);
            int tmpCount = 0;
            int ind = 0;
            switch (value) {
                case "00":
                    tmpCount = Boolean.parseBoolean(isTrueForNumberData.get(isTrueColumnInd))
                            ? countOfTransitionElement.get(0) + 1
                            : countOfTransitionElement.get(4) + 1;
                    ind = Boolean.parseBoolean(isTrueForNumberData.get(isTrueColumnInd)) ? 0 : 4;
                    break;
                case "01":
                    tmpCount = Boolean.parseBoolean(isTrueForNumberData.get(isTrueColumnInd))
                            ? countOfTransitionElement.get(1) + 1
                            : countOfTransitionElement.get(5) + 1;
                    ind = Boolean.parseBoolean(isTrueForNumberData.get(isTrueColumnInd)) ? 1 : 5;
                    break;
                case "10":
                    tmpCount = Boolean.parseBoolean(isTrueForNumberData.get(isTrueColumnInd))
                            ? countOfTransitionElement.get(2) + 1
                            : countOfTransitionElement.get(6) + 1;
                    ind = Boolean.parseBoolean(isTrueForNumberData.get(isTrueColumnInd)) ? 2 : 6;
                    break;
                case "11":
                    tmpCount = Boolean.parseBoolean(isTrueForNumberData.get(isTrueColumnInd))
                            ? countOfTransitionElement.get(3) + 1
                            : countOfTransitionElement.get(7) + 1;
                    ind = Boolean.parseBoolean(isTrueForNumberData.get(isTrueColumnInd)) ? 3 : 7;
                    break;
            }
            countOfTransitionElement.set(ind, tmpCount);
        }
    }

    /**
     * Фильтрация признаков (по информативности) по параметру alpha
     *
     * @param alpha                  параметр alpha
     * @param allTransitionMatrices  все матрицы перехода
     * @param informative            все информативности
     */
    private void filteringByAlpha(int alpha, List<List<TransitionMatrix>> allTransitionMatrices,
                                  List<Map<Integer, Double>> informative) {
        double alphaPercent = allTransitionMatrices
                .get(1)
                .get(0)
                .getI0_Y()
                * ((double) alpha) / 100;

        List.copyOf(informative).forEach(tableInformative ->
                Map.copyOf(tableInformative).forEach((column, informativeOfColumn) -> {
                    if (informativeOfColumn < alphaPercent) {
                        tableInformative.remove(column);
                    }
                }));
    }

    /**
     * Расчёт расстояний между признаками
     *
     * @param informative  все информативности
     * @param metrics      расстояния
     * @param indices      признаки
     */
    private void calculateMetrics(List<Map<Integer, Double>> informative, Map<Integer, List<Map<String, Double>>> metrics,
                                  Map<Integer, Map<Integer, String>> indices) {
        var ref = new Object() {
            int countOfNumber = 0;
        };

        StringBuilder columnsBuilder = new StringBuilder();
        informative.forEach(tableInformative -> {
            metrics.put(ref.countOfNumber, new ArrayList<>());
            tableInformative.forEach((column1, informativeOfColumn1) -> tableInformative.forEach((column2, informativeOfColumn2) -> {
                if (!column1.equals(column2)) {
                    // Проверка на дубликаты (HINT: признак X1X2 == X2X1):
                    AtomicBoolean duplicates = new AtomicBoolean(false);
                    metrics.get(ref.countOfNumber).forEach(x -> {
                        boolean conditional = x.containsKey("X" + column1 + "|" + "X" + column2)
                                || x.containsKey("X" + column2 + "|" + "X" + column1);
                        if (conditional) {
                            duplicates.set(true);
                        }
                    });

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
            }));
            ref.countOfNumber++;
        });
    }

    /**
     * Кластеризация признаков
     *
     * @param clusters              массив кластеров для сохранения данных
     * @param metrics               расстояния между признаками
     * @param maxMetricCoefficient  коэффициент попадания в кластер (берётся от максимального расстояния)
     * @param informative           информативности признаков
     */
    @SuppressWarnings("ResultOfMethodCallIgnored")
    private void clustering(Map<Integer, List<List<String>>> clusters, Map<Integer, List<Map<String, Double>>> metrics,
                            double maxMetricCoefficient, List<Map<Integer, Double>> informative) {
        metrics.forEach((number, listOfIndices) -> {
            clusters.put(number, new ArrayList<>());
            AtomicReference<Double> maxValue = new AtomicReference<>(0.0);
            listOfIndices.forEach(stringDoubleMap -> stringDoubleMap.forEach((key, value) -> {
                // Находим максимальное расстояние между признаками:
                if (value > maxValue.get()) {
                    maxValue.set(value);
                }
            }));
            // Ставим максимально допустимое расстояние между признаками в одном кластере равное 30% от максимального расстояния:
            double maxMetric = maxValue.get() * maxMetricCoefficient;
            // Оставшиеся после откидывания по параметру alpha признаки:
            Map<Integer, List<String>> indices = new TreeMap<>();
            indices.put(number, new ArrayList<>());
            informative.get(number).forEach((ind, inf) -> indices.get(number).add("X" + ind));
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
     * Формирование двойных сложных признаков
     *
     * @param complexIndices  переменная, в которую будем складывать сложные признаки
     * @param clusters        кластеры с признаками
     * @param metrics         расстояния между признаками
     */
    private void createBinaryComplexIndices(Map<Integer, Map<String, String>> complexIndices,
                                            Map<Integer, List<List<String>>> clusters,
                                            Map<Integer, List<Map<String, Double>>> metrics,
                                            Map<Integer, Map<Integer, String>> indices) {
        clusters.forEach((number, clustersForNumber) -> {
            Set<String> complexIndicesForNumber = new LinkedHashSet<>();
            complexIndices.put(number, new LinkedHashMap<>());
            if (metrics.get(number).size() != 0) {
                for (List<String> cluster : clustersForNumber) {
                   cluster.forEach(simpleInd -> clustersForNumber.forEach(otherCluster -> {
                        if (!cluster.equals(otherCluster)) {
                            otherCluster.forEach(otherSimpleInd -> {
                                String complexInd = metrics.get(number)
                                        .stream()
                                        .anyMatch(map -> map.containsKey(simpleInd + "|" + otherSimpleInd))
                                        ? simpleInd + "|" + otherSimpleInd
                                        : otherSimpleInd + "|" + simpleInd;

                                if (!complexIndicesForNumber.contains(complexInd)) {
                                    complexIndicesForNumber.add(complexInd);
                                    complexIndices.get(number).put(
                                            complexInd,
                                            ComplexIndGenerator.generateComplexIndValue(complexInd, indices.get(number))
                                    );
                                }
                            });
                        }
                    }));
                }
            }
        });
    }

    /**
     * Фильтрация сложных признаков по параметру betta:
     *
     * @param complexIndices  сложные признаки
     * @param isTrueDataCache  isTrue колонка
     * @param betta  параметр betta
     */
    private Map<Integer, List<TransitionMatrixForComplexIndices>> filteringComplexIndicesByBetta(Map<Integer, Map<String, String>> complexIndices, Map<Integer, List<String>> isTrueDataCache, double betta) {
        Map<Integer, Map<String, String>> filteringResult = new LinkedHashMap<>(complexIndices);
        Map<Integer, List<TransitionMatrixForComplexIndices>> resultMap = new LinkedHashMap<>();
        filteringResult.forEach((number, indMap) -> {
            List<TransitionMatrixForComplexIndices> transitionMatricesForComplexIndices = new ArrayList<>();
            resultMap.put(number, transitionMatricesForComplexIndices);
            for (Map.Entry<String, String> entry : Map.copyOf(indMap).entrySet()) {
                String alias = entry.getKey();
                String indValue = entry.getValue();
                TransitionMatrixForComplexIndices matrix = new TransitionMatrixForComplexIndices(DB_TABLES.values()[number], alias);
            /*
            Массив количества переходов элементов, где индексы идут в следующем соотвествии: [00->0], [01->0], [10->0], [11->0], [00->1], [01->1], [10->1], [11->1]
            */
                List<Integer> countOfTransitionElement = Arrays.asList(0, 0, 0, 0, 0, 0, 0, 0);
                List<String> isTrueForNumberData = isTrueDataCache.get(number);

                checkTransitionForComplexIndices(isTrueForNumberData, indValue, countOfTransitionElement);

                matrix.getTransitionMatrix().add(Arrays.asList(
                        countOfTransitionElement.get(0), countOfTransitionElement.get(1),
                        countOfTransitionElement.get(2), countOfTransitionElement.get(3)
                ));

                matrix.getTransitionMatrix().add(Arrays.asList(
                        countOfTransitionElement.get(4), countOfTransitionElement.get(5),
                        countOfTransitionElement.get(6), countOfTransitionElement.get(7)
                ));

                if (matrix.getInformative() < I0_Y.allInformativeI0_Y.get(DB_TABLES.values()[number]) * betta / 100) {
                    complexIndices.get(number).remove(alias);
                }
                transitionMatricesForComplexIndices.add(matrix);
            }
        });
        return resultMap;
    }
    private Map<Integer, Map<String, String>> convertComplexIndicesToNewSpace(
            Map<Integer, List<TransitionMatrixForComplexIndices>> transitionMatricesForComplexIndices, int gamma) {
        Map<Integer, Map<String, String>> newSpace = new LinkedHashMap<>();
        transitionMatricesForComplexIndices.forEach((number, listOfTransitionMatrices) -> {
            newSpace.put(number, new LinkedHashMap<>());
            listOfTransitionMatrices.forEach(matrix -> {
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < matrix.getTransitionMatrix().get(0).size(); i++) {
                    Integer firstValue = matrix.getTransitionMatrix().get(0).get(i);
                    Integer secondValue = matrix.getTransitionMatrix().get(1).get(i);
                    int result = firstValue - secondValue;
                    if (result > 0) {
                        if (result >= gamma) {
                            sb.append(1);
                        } else {
                            sb.append(0);
                        }
                    } else if (result < 0) {
                        if (Math.abs(result) >= gamma) {
                            sb.append(1);
                        } else {
                            sb.append(0);
                        }
                    } else {
                        sb.append("-");
                    }
                }
                newSpace.get(number).put(matrix.getComplexIndices(), sb.toString());
            });
        });
        return newSpace;
    }
}
