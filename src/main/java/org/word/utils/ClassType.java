package org.word.utils;

import java.util.Map;

public class ClassType {
    public static boolean isJavaClass(Class<?> clz) {
        return clz != null && clz.getClassLoader() == null;
    }

    public static boolean isMap(Object clz) {
        return clz instanceof Map;
    }
}