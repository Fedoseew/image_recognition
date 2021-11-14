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
import org.springframework.lang.NonNull;
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
    private Object[] data;
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
        // Подгрузка данных
        Object[] loadData = data == null ? loadData(alpha, betta, gamma, minMetric) : data;
        Map<Integer, String> userSourceToNewSpace = userSourceToNewSpace(source, (Map<Integer, Map<String, String>>) loadData[2]);

        // Подсчёт совпадений:
        Map<Integer, Map<Integer, Integer>> countOfTransition = calculateCountOfTransitions((Map<Integer, List<String>>) loadData[1], userSourceToNewSpace);

        // Вычисление результата распознавания:
        Pair<Integer, Integer> result = calculateResult((Map<Integer, List<String>>) loadData[0], countOfTransition);

        System.out.println("Result is " + result);
    }

    public Object[] loadData(int alpha, int betta, int gamma, double minMetric) throws SQLException {
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

        // Сложные признаки (key -> число, value -> (key -> alias ("Xi1|Xi2|Xi3|Xi4|Xi5), value - бинарный кортеж сложного признака)):
        Map<Integer, Map<String, String>> complexIndices = new HashMap<>();

        // ФОрмирование и фильтрация сложных признаков по параметру betta:
        Map<Integer, List<TransitionMatrixForComplexIndices>> transitionMatricesForComplexIndices =
                createComplexIndices(complexIndices, clusters, metrics, indices, isTrueDataCache, betta);

        // Переход в новое пространтсво по параметру gamma:
        Map<Integer, Map<String, String>> complexIndicesNewSpace = convertComplexIndicesToNewSpace(transitionMatricesForComplexIndices, gamma);

        Map<Integer, List<String>> indicesNewSpace = convertIndicesToNewSpace(sourcesDataCache, complexIndicesNewSpace);
        data = new Object[] {isTrueDataCache, indicesNewSpace, complexIndicesNewSpace};

        return data;
    }


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
        Map<String, Integer> map = Map.of("000", 0, "001", 1, "010", 2, "011", 3, "100", 4, "101", 5, "110", 6, "111", 7);
        Map<Integer, String> userSourceNewSpace = new LinkedHashMap<>();
        complexIndicesNewSpace.forEach((number, mapOfIndicesWithNewValue) -> {
            StringBuilder sb = new StringBuilder();
            mapOfIndicesWithNewValue.forEach((indAlias, newValue) -> {
                String[] split = indAlias.split("\\|");
                String first = String.valueOf(source.charAt(Integer.parseInt(split[0].split("X")[1]) - 1));
                String second = String.valueOf(source.charAt(Integer.parseInt(split[1].split("X")[1]) - 1));
                String third = String.valueOf(source.charAt(Integer.parseInt(split[2].split("X")[1]) - 1));
                int index = map.get(first + second + third);
                char charAt = newValue.charAt(index);
                sb.append(charAt);
            });
            userSourceNewSpace.put(number, sb.toString());
        });
        return userSourceNewSpace;
    }

    private Map<Integer, List<String>> convertIndicesToNewSpace(Map<Integer, List<String>> sourcesDataCache, Map<Integer, Map<String, String>> complexIndicesNewSpace) {
        Map<Integer, List<String>> indicesNewSpace = new LinkedHashMap<>();
        Map<String, Integer> map = Map.of("000", 0, "001", 1, "010", 2, "011", 3, "100", 4, "101", 5, "110", 6, "111", 7);
        complexIndicesNewSpace.forEach((number, mapOfIndicesWithNewValue) -> {
            indicesNewSpace.put(number, new ArrayList<>());
            List<String> sourcesForNumber = sourcesDataCache.get(number);
            sourcesForNumber.forEach(source -> {
                StringBuilder sb = new StringBuilder();
                mapOfIndicesWithNewValue.forEach((indAlias, newValue) -> {
                    String[] split = indAlias.split("\\|");
                    String first = String.valueOf(source.charAt(Integer.parseInt(split[0].split("X")[1]) - 1));
                    String second = String.valueOf(source.charAt(Integer.parseInt(split[1].split("X")[1]) - 1));
                    String third = String.valueOf(source.charAt(Integer.parseInt(split[2].split("X")[1]) - 1));
                    int index = map.get(first + second + third);
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
        for (int chInd = 0, isTrueColumnInd = 0; chInd < indValue.length(); chInd += 6, isTrueColumnInd++) {
            String value = "" +
                    indValue.charAt(chInd) +
                    indValue.charAt(chInd + 1) +
                    indValue.charAt(chInd + 2) +
                    indValue.charAt(chInd + 3) +
                    indValue.charAt(chInd + 4);
            int tmpCount = 0;
            int ind = 0;
            switch (value) {
                case "00000":
                    tmpCount = Boolean.parseBoolean(isTrueForNumberData.get(isTrueColumnInd))
                            ? countOfTransitionElement.get(0) + 1
                            : countOfTransitionElement.get(32) + 1;
                    ind = Boolean.parseBoolean(isTrueForNumberData.get(isTrueColumnInd)) ? 0 : 32;
                    break;
                case "00001":
                    tmpCount = Boolean.parseBoolean(isTrueForNumberData.get(isTrueColumnInd))
                            ? countOfTransitionElement.get(1) + 1
                            : countOfTransitionElement.get(33) + 1;
                    ind = Boolean.parseBoolean(isTrueForNumberData.get(isTrueColumnInd)) ? 1 : 33;
                    break;
                case "00010":
                    tmpCount = Boolean.parseBoolean(isTrueForNumberData.get(isTrueColumnInd))
                            ? countOfTransitionElement.get(2) + 1
                            : countOfTransitionElement.get(34) + 1;
                    ind = Boolean.parseBoolean(isTrueForNumberData.get(isTrueColumnInd)) ? 2 : 34;
                    break;
                case "00011":
                    tmpCount = Boolean.parseBoolean(isTrueForNumberData.get(isTrueColumnInd))
                            ? countOfTransitionElement.get(3) + 1
                            : countOfTransitionElement.get(35) + 1;
                    ind = Boolean.parseBoolean(isTrueForNumberData.get(isTrueColumnInd)) ? 3 : 35;
                    break;
                case "00100":
                    tmpCount = Boolean.parseBoolean(isTrueForNumberData.get(isTrueColumnInd))
                            ? countOfTransitionElement.get(4) + 1
                            : countOfTransitionElement.get(36) + 1;
                    ind = Boolean.parseBoolean(isTrueForNumberData.get(isTrueColumnInd)) ? 4 : 36;
                    break;
                case "00101":
                    tmpCount = Boolean.parseBoolean(isTrueForNumberData.get(isTrueColumnInd))
                            ? countOfTransitionElement.get(5) + 1
                            : countOfTransitionElement.get(37) + 1;
                    ind = Boolean.parseBoolean(isTrueForNumberData.get(isTrueColumnInd)) ? 5 : 37;
                    break;
                case "000110":
                    tmpCount = Boolean.parseBoolean(isTrueForNumberData.get(isTrueColumnInd))
                            ? countOfTransitionElement.get(6) + 1
                            : countOfTransitionElement.get(38) + 1;
                    ind = Boolean.parseBoolean(isTrueForNumberData.get(isTrueColumnInd)) ? 6 : 38;
                    break;
                case "00111":
                    tmpCount = Boolean.parseBoolean(isTrueForNumberData.get(isTrueColumnInd))
                            ? countOfTransitionElement.get(7) + 1
                            : countOfTransitionElement.get(39) + 1;
                    ind = Boolean.parseBoolean(isTrueForNumberData.get(isTrueColumnInd)) ? 7 : 39;
                    break;
                case "01000":
                    tmpCount = Boolean.parseBoolean(isTrueForNumberData.get(isTrueColumnInd))
                            ? countOfTransitionElement.get(8) + 1
                            : countOfTransitionElement.get(40) + 1;
                    ind = Boolean.parseBoolean(isTrueForNumberData.get(isTrueColumnInd)) ? 8 : 40;
                    break;
                case "01001":
                    tmpCount = Boolean.parseBoolean(isTrueForNumberData.get(isTrueColumnInd))
                            ? countOfTransitionElement.get(9) + 1
                            : countOfTransitionElement.get(41) + 1;
                    ind = Boolean.parseBoolean(isTrueForNumberData.get(isTrueColumnInd)) ? 9 : 41;
                    break;
                case "01010":
                    tmpCount = Boolean.parseBoolean(isTrueForNumberData.get(isTrueColumnInd))
                            ? countOfTransitionElement.get(10) + 1
                            : countOfTransitionElement.get(42) + 1;
                    ind = Boolean.parseBoolean(isTrueForNumberData.get(isTrueColumnInd)) ? 10 : 42;
                    break;
                case "01011":
                    tmpCount = Boolean.parseBoolean(isTrueForNumberData.get(isTrueColumnInd))
                            ? countOfTransitionElement.get(11) + 1
                            : countOfTransitionElement.get(43) + 1;
                    ind = Boolean.parseBoolean(isTrueForNumberData.get(isTrueColumnInd)) ? 11 : 43;
                    break;
                case "01100":
                    tmpCount = Boolean.parseBoolean(isTrueForNumberData.get(isTrueColumnInd))
                            ? countOfTransitionElement.get(12) + 1
                            : countOfTransitionElement.get(44) + 1;
                    ind = Boolean.parseBoolean(isTrueForNumberData.get(isTrueColumnInd)) ? 12 : 44;
                    break;
                case "01101":
                    tmpCount = Boolean.parseBoolean(isTrueForNumberData.get(isTrueColumnInd))
                            ? countOfTransitionElement.get(13) + 1
                            : countOfTransitionElement.get(45) + 1;
                    ind = Boolean.parseBoolean(isTrueForNumberData.get(isTrueColumnInd)) ? 13 : 45;
                    break;
                case "01110":
                    tmpCount = Boolean.parseBoolean(isTrueForNumberData.get(isTrueColumnInd))
                            ? countOfTransitionElement.get(14) + 1
                            : countOfTransitionElement.get(46) + 1;
                    ind = Boolean.parseBoolean(isTrueForNumberData.get(isTrueColumnInd)) ? 14 : 46;
                    break;
                case "01111":
                    tmpCount = Boolean.parseBoolean(isTrueForNumberData.get(isTrueColumnInd))
                            ? countOfTransitionElement.get(15) + 1
                            : countOfTransitionElement.get(47) + 1;
                    ind = Boolean.parseBoolean(isTrueForNumberData.get(isTrueColumnInd)) ? 15 : 47;
                    break;
                case "10000":
                    tmpCount = Boolean.parseBoolean(isTrueForNumberData.get(isTrueColumnInd))
                            ? countOfTransitionElement.get(16) + 1
                            : countOfTransitionElement.get(48) + 1;
                    ind = Boolean.parseBoolean(isTrueForNumberData.get(isTrueColumnInd)) ? 16 : 48;
                    break;
                case "10001":
                    tmpCount = Boolean.parseBoolean(isTrueForNumberData.get(isTrueColumnInd))
                            ? countOfTransitionElement.get(17) + 1
                            : countOfTransitionElement.get(49) + 1;
                    ind = Boolean.parseBoolean(isTrueForNumberData.get(isTrueColumnInd)) ? 17 : 49;
                    break;
                case "10010":
                    tmpCount = Boolean.parseBoolean(isTrueForNumberData.get(isTrueColumnInd))
                            ? countOfTransitionElement.get(18) + 1
                            : countOfTransitionElement.get(50) + 1;
                    ind = Boolean.parseBoolean(isTrueForNumberData.get(isTrueColumnInd)) ? 18 : 50;
                    break;
                case "10011":
                    tmpCount = Boolean.parseBoolean(isTrueForNumberData.get(isTrueColumnInd))
                            ? countOfTransitionElement.get(19) + 1
                            : countOfTransitionElement.get(51) + 1;
                    ind = Boolean.parseBoolean(isTrueForNumberData.get(isTrueColumnInd)) ? 19 : 51;
                    break;
                case "10100":
                    tmpCount = Boolean.parseBoolean(isTrueForNumberData.get(isTrueColumnInd))
                            ? countOfTransitionElement.get(20) + 1
                            : countOfTransitionElement.get(52) + 1;
                    ind = Boolean.parseBoolean(isTrueForNumberData.get(isTrueColumnInd)) ? 20 : 52;
                    break;
                case "10101":
                    tmpCount = Boolean.parseBoolean(isTrueForNumberData.get(isTrueColumnInd))
                            ? countOfTransitionElement.get(21) + 1
                            : countOfTransitionElement.get(53) + 1;
                    ind = Boolean.parseBoolean(isTrueForNumberData.get(isTrueColumnInd)) ? 21 : 53;
                    break;
                case "10110":
                    tmpCount = Boolean.parseBoolean(isTrueForNumberData.get(isTrueColumnInd))
                            ? countOfTransitionElement.get(22) + 1
                            : countOfTransitionElement.get(54) + 1;
                    ind = Boolean.parseBoolean(isTrueForNumberData.get(isTrueColumnInd)) ? 22 : 54;
                    break;
                case "10111":
                    tmpCount = Boolean.parseBoolean(isTrueForNumberData.get(isTrueColumnInd))
                            ? countOfTransitionElement.get(23) + 1
                            : countOfTransitionElement.get(55) + 1;
                    ind = Boolean.parseBoolean(isTrueForNumberData.get(isTrueColumnInd)) ? 23 : 55;
                    break;
                case "11000":
                    tmpCount = Boolean.parseBoolean(isTrueForNumberData.get(isTrueColumnInd))
                            ? countOfTransitionElement.get(24) + 1
                            : countOfTransitionElement.get(56) + 1;
                    ind = Boolean.parseBoolean(isTrueForNumberData.get(isTrueColumnInd)) ? 24 : 56;
                    break;
                case "11001":
                    tmpCount = Boolean.parseBoolean(isTrueForNumberData.get(isTrueColumnInd))
                            ? countOfTransitionElement.get(25) + 1
                            : countOfTransitionElement.get(57) + 1;
                    ind = Boolean.parseBoolean(isTrueForNumberData.get(isTrueColumnInd)) ? 25 : 57;
                    break;
                case "11010":
                    tmpCount = Boolean.parseBoolean(isTrueForNumberData.get(isTrueColumnInd))
                            ? countOfTransitionElement.get(26) + 1
                            : countOfTransitionElement.get(58) + 1;
                    ind = Boolean.parseBoolean(isTrueForNumberData.get(isTrueColumnInd)) ? 26 : 58;
                    break;
                case "11011":
                    tmpCount = Boolean.parseBoolean(isTrueForNumberData.get(isTrueColumnInd))
                            ? countOfTransitionElement.get(27) + 1
                            : countOfTransitionElement.get(59) + 1;
                    ind = Boolean.parseBoolean(isTrueForNumberData.get(isTrueColumnInd)) ? 27 : 59;
                    break;
                case "11100":
                    tmpCount = Boolean.parseBoolean(isTrueForNumberData.get(isTrueColumnInd))
                            ? countOfTransitionElement.get(28) + 1
                            : countOfTransitionElement.get(60) + 1;
                    ind = Boolean.parseBoolean(isTrueForNumberData.get(isTrueColumnInd)) ? 28 : 60;
                    break;
                case "11101":
                    tmpCount = Boolean.parseBoolean(isTrueForNumberData.get(isTrueColumnInd))
                            ? countOfTransitionElement.get(29) + 1
                            : countOfTransitionElement.get(61) + 1;
                    ind = Boolean.parseBoolean(isTrueForNumberData.get(isTrueColumnInd)) ? 29 : 61;
                    break;
                case "11110":
                    tmpCount = Boolean.parseBoolean(isTrueForNumberData.get(isTrueColumnInd))
                            ? countOfTransitionElement.get(30) + 1
                            : countOfTransitionElement.get(62) + 1;
                    ind = Boolean.parseBoolean(isTrueForNumberData.get(isTrueColumnInd)) ? 30 : 62;
                    break;
                case "11111":
                    tmpCount = Boolean.parseBoolean(isTrueForNumberData.get(isTrueColumnInd))
                            ? countOfTransitionElement.get(31) + 1
                            : countOfTransitionElement.get(63) + 1;
                    ind = Boolean.parseBoolean(isTrueForNumberData.get(isTrueColumnInd)) ? 31 : 63;
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
     *  @param complexIndices  переменная, в которую будем складывать сложные признаки
     * @param clusters        кластеры с признаками
     * @param metrics         расстояния между признаками
     * @return
     */
    private Map<Integer, List<TransitionMatrixForComplexIndices>> createComplexIndices(Map<Integer, Map<String, String>> complexIndices,
                                                                                       Map<Integer, List<List<String>>> clusters,
                                                                                       Map<Integer, List<Map<String, Double>>> metrics,
                                                                                       Map<Integer, Map<Integer, String>> indices,
                                                                                       Map<Integer, List<String>> isTrueDataCache,
                                                                                       double betta) {
        Map<Integer, List<TransitionMatrixForComplexIndices>> resultMap = new LinkedHashMap<>();
        clusters.forEach((number, clustersForNumber) -> {
            Set<String> complexIndicesForNumber = new HashSet<>();
            complexIndices.put(number, new HashMap<>());
            if (metrics.get(number).size() != 0) {
                for (List<String> firstCluster : clustersForNumber) {
                    for (List<String> secondCluster : clustersForNumber) {
                        if (firstCluster.equals(secondCluster)) continue;
                        for (List<String> thirdCluster : clustersForNumber) {
                            if (firstCluster.equals(thirdCluster) || secondCluster.equals(thirdCluster)) continue;
                            for (List<String> fourthCluster : clustersForNumber) {
                                if (firstCluster.equals(fourthCluster) || secondCluster.equals(fourthCluster)
                                        || thirdCluster.equals(fourthCluster)) continue;
                                for (List<String> fiveCluster : clustersForNumber) {
                                    if (firstCluster.equals(fiveCluster) || secondCluster.equals(firstCluster)
                                            || thirdCluster.equals(fiveCluster) || fourthCluster.equals(firstCluster)) continue;
                                    firstCluster.forEach(firstInd -> {
                                        secondCluster.forEach(secondInd -> {
                                            thirdCluster.forEach(thirdInd -> {
                                                fourthCluster.forEach(fourthInd -> {
                                                    fiveCluster.forEach(fiveInd -> {
                                                        if (!objectsNotEquals(firstInd, secondInd, thirdInd, fourthInd, fiveInd)) {
                                                            String complexInd = firstInd + "|" + secondInd + "|" + thirdInd + "|" + fourthInd + "|" + fiveInd;
                                                            if (complexIndNotExist(complexIndicesForNumber, firstInd, secondInd, thirdInd, fourthInd, fiveInd)) {
                                                                complexIndicesForNumber.add(complexInd);
                                                                complexIndices.get(number).put(
                                                                        complexInd,
                                                                        ComplexIndGenerator.generateComplexIndValue(
                                                                                complexInd, indices.get(number)
                                                                        )
                                                                );
                                                            }
                                                        }
                                                    });
                                                });
                                            });
                                        });
                                    });
                                }
                            }
                        }
                    }
                }
            }
            List<TransitionMatrixForComplexIndices> transitionMatricesForComplexIndices = new ArrayList<>();
            resultMap.put(number, transitionMatricesForComplexIndices);
            for (Map.Entry<String, String> entry : Map.copyOf(complexIndices.get(number)).entrySet()) {
                String alias = entry.getKey();
                String indValue = entry.getValue();
                TransitionMatrixForComplexIndices matrix = new TransitionMatrixForComplexIndices(DB_TABLES.values()[number], alias);
            /*
            Массив количества переходов элементов, где индексы идут в следующем соотвествии:
             [00000->0], [00001->0], [00010->0], [00011->0], ..., [00000->1], [00001->1], ..., [10000->0], [10001->0], ..., [10000->1], ..., [11111->1]
            */
                List<Integer> countOfTransitionElement = Arrays.asList(
                        0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                        0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0
                );
                List<String> isTrueForNumberData = isTrueDataCache.get(number);

                checkTransitionForComplexIndices(isTrueForNumberData, indValue, countOfTransitionElement);

                matrix.getTransitionMatrix().add(Arrays.asList(
                        countOfTransitionElement.get(0), countOfTransitionElement.get(1),
                        countOfTransitionElement.get(2), countOfTransitionElement.get(3),
                        countOfTransitionElement.get(4), countOfTransitionElement.get(5),
                        countOfTransitionElement.get(6), countOfTransitionElement.get(7),
                        countOfTransitionElement.get(8), countOfTransitionElement.get(9),
                        countOfTransitionElement.get(10), countOfTransitionElement.get(11),
                        countOfTransitionElement.get(12), countOfTransitionElement.get(13),
                        countOfTransitionElement.get(14), countOfTransitionElement.get(15),
                        countOfTransitionElement.get(16), countOfTransitionElement.get(17),
                        countOfTransitionElement.get(18), countOfTransitionElement.get(19),
                        countOfTransitionElement.get(20), countOfTransitionElement.get(21),
                        countOfTransitionElement.get(22), countOfTransitionElement.get(23),
                        countOfTransitionElement.get(24), countOfTransitionElement.get(25),
                        countOfTransitionElement.get(26), countOfTransitionElement.get(27),
                        countOfTransitionElement.get(28), countOfTransitionElement.get(29),
                        countOfTransitionElement.get(30), countOfTransitionElement.get(31)
                ));

                matrix.getTransitionMatrix().add(Arrays.asList(
                        countOfTransitionElement.get(32), countOfTransitionElement.get(33),
                        countOfTransitionElement.get(34), countOfTransitionElement.get(35),
                        countOfTransitionElement.get(36), countOfTransitionElement.get(37),
                        countOfTransitionElement.get(38), countOfTransitionElement.get(39),
                        countOfTransitionElement.get(40), countOfTransitionElement.get(41),
                        countOfTransitionElement.get(42), countOfTransitionElement.get(43),
                        countOfTransitionElement.get(44), countOfTransitionElement.get(45),
                        countOfTransitionElement.get(46), countOfTransitionElement.get(47),
                        countOfTransitionElement.get(48), countOfTransitionElement.get(49),
                        countOfTransitionElement.get(50), countOfTransitionElement.get(51),
                        countOfTransitionElement.get(52), countOfTransitionElement.get(53),
                        countOfTransitionElement.get(54), countOfTransitionElement.get(55),
                        countOfTransitionElement.get(56), countOfTransitionElement.get(57),
                        countOfTransitionElement.get(58), countOfTransitionElement.get(59),
                        countOfTransitionElement.get(60), countOfTransitionElement.get(61),
                        countOfTransitionElement.get(62), countOfTransitionElement.get(63)

                ));

                if (matrix.getInformative() < I0_Y.allInformativeI0_Y.get(DB_TABLES.values()[number]) * betta / 100) {
                    complexIndices.get(number).remove(alias);
                }
                transitionMatricesForComplexIndices.add(matrix);
            }
        });
        return resultMap;
    }

    private boolean objectsNotEquals(
            Object first,
            Object second,
            Object third,
            Object fourth,
            Object five
    ) {
        return !first.equals(second) &&
                !first.equals(third) &&
                !first.equals(fourth) &&
                !first.equals(five) &&
                !second.equals(third) &&
                !second.equals(fourth) &&
                !second.equals(five) &&
                !third.equals(fourth) &&
                !third.equals(five) &&
                !fourth.equals(five);
    }

    @Nullable
    private String permutation(@NonNull String prefix, @NonNull String str) {
        int n = str.length();
        if (n == 0) return prefix;
        else {
            for (int i = 0; i < n; i++)
                permutation(prefix + str.charAt(i), str.substring(0, i) + str.substring(i+1, n));
        }
        return null;
    }

    private boolean complexIndNotExist(Set<String> complexIndices, String... indices) {
        for (int i1 = 0; i1 < 5; i1++) {
            for (int i2 = 0; i2 < 5; i2++) {
                if (i1 == i2) continue;
                for (int i3 = 0; i3 < 5; i3++) {
                    if (i1 == i3 || i2 == i3) continue;
                    for (int i4 = 0; i4 < 5; i4++) {
                        if (i1 == i4 || i2 == i4 || i3 == i4) continue;
                        for (int i5 = 0; i5 < 5; i5++) {
                            if (i1 == i5 || i2 == i5 || i3 == i5 || i4 == i5) continue;
                            String complexInd = indices[i1] + "|" + indices[i2] + "|" + indices[i3] + "|" + indices[i4] + "|" + indices[i5];
                            if (complexIndices.contains(complexInd)) {
                                return false;
                            }
                        }
                    }
                }
            }
        }
        return true;
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
            Массив количества переходов элементов, где индексы идут в следующем соотвествии:
             [00000->0], [00001->0], [00010->0], [00011->0], ..., [00000->1], [00001->1], ..., [10000->0], [10001->0], ..., [10000->1], ..., [11111->1]
            */
                List<Integer> countOfTransitionElement = Arrays.asList(
                        0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                        0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0
                );
                List<String> isTrueForNumberData = isTrueDataCache.get(number);

                checkTransitionForComplexIndices(isTrueForNumberData, indValue, countOfTransitionElement);

                matrix.getTransitionMatrix().add(Arrays.asList(
                        countOfTransitionElement.get(0), countOfTransitionElement.get(1),
                        countOfTransitionElement.get(2), countOfTransitionElement.get(3),
                        countOfTransitionElement.get(4), countOfTransitionElement.get(5),
                        countOfTransitionElement.get(6), countOfTransitionElement.get(7),
                        countOfTransitionElement.get(8), countOfTransitionElement.get(9),
                        countOfTransitionElement.get(10), countOfTransitionElement.get(11),
                        countOfTransitionElement.get(12), countOfTransitionElement.get(13),
                        countOfTransitionElement.get(14), countOfTransitionElement.get(15),
                        countOfTransitionElement.get(16), countOfTransitionElement.get(17),
                        countOfTransitionElement.get(18), countOfTransitionElement.get(19),
                        countOfTransitionElement.get(20), countOfTransitionElement.get(21),
                        countOfTransitionElement.get(22), countOfTransitionElement.get(23),
                        countOfTransitionElement.get(24), countOfTransitionElement.get(25),
                        countOfTransitionElement.get(26), countOfTransitionElement.get(27),
                        countOfTransitionElement.get(28), countOfTransitionElement.get(29),
                        countOfTransitionElement.get(30), countOfTransitionElement.get(31)
                        ));

                matrix.getTransitionMatrix().add(Arrays.asList(
                        countOfTransitionElement.get(32), countOfTransitionElement.get(33),
                        countOfTransitionElement.get(34), countOfTransitionElement.get(35),
                        countOfTransitionElement.get(36), countOfTransitionElement.get(37),
                        countOfTransitionElement.get(38), countOfTransitionElement.get(39),
                        countOfTransitionElement.get(40), countOfTransitionElement.get(41),
                        countOfTransitionElement.get(42), countOfTransitionElement.get(43),
                        countOfTransitionElement.get(44), countOfTransitionElement.get(45),
                        countOfTransitionElement.get(46), countOfTransitionElement.get(47),
                        countOfTransitionElement.get(48), countOfTransitionElement.get(49),
                        countOfTransitionElement.get(50), countOfTransitionElement.get(51),
                        countOfTransitionElement.get(52), countOfTransitionElement.get(53),
                        countOfTransitionElement.get(54), countOfTransitionElement.get(55),
                        countOfTransitionElement.get(56), countOfTransitionElement.get(57),
                        countOfTransitionElement.get(58), countOfTransitionElement.get(59),
                        countOfTransitionElement.get(60), countOfTransitionElement.get(61)

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
