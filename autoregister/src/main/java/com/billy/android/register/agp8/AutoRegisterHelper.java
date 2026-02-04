package com.billy.android.register.agp8;

import org.gradle.api.Project;
import java.io.File;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;

/**
 * 文件操作辅助类 (Java 实现 - 彻底移除 intermediates 引用)
 */
public class AutoRegisterHelper {
    public static File getRegisterInfoCacheFile(Project project) {
        return null; // 彻底废弃
    }

    public static File getRegisterCacheFile(Project project) {
        return null; // 彻底废弃
    }

    public static void cacheRegisterHarvest(File cacheFile, String harvests) {
        // 彻底废弃
    }

    public static Map readToMap(File file, Type type) {
        return new HashMap(); // 彻底废弃
    }
}
