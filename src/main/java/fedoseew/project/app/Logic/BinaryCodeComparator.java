package fedoseew.project.app.Logic;

import java.util.Comparator;

public class BinaryCodeComparator implements Comparator<String> {

    @Override
    public int compare(String sourceFromDataBase, String source) {
        if(sourceFromDataBase.length() != source.length()) {
            return -1;
        } else {
            for (int i = 0; i < sourceFromDataBase.length(); i++) {
                if (sourceFromDataBase.charAt(i) != source.charAt(i)) {
                    return 0;
                }
            }
        }
        return 1;
    }
}
