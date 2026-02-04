package com.billy.android.register.agp8;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class AutoRegisterInfo implements Serializable {
    public String id = ""; // 唯一标识，用于跨任务查找 classList
    public String interfaceName = "";
    public List<String> superClassNames = new ArrayList<>();
    public String initClassName = "";
    public String initMethodName = "";
    public String registerClassName = "";
    public String registerMethodName = "";
    public List<String> include = new ArrayList<>();
    public List<String> exclude = new ArrayList<>();
    
    // 静态全局缓存，确保跨 AsmClassVisitorFactory 实例能够共享扫描状态
    public static final Map<String, List<String>> GLOBAL_CLASS_LISTS = new ConcurrentHashMap<>();

    public void init() {
        interfaceName = convertDotToSlash(interfaceName);
        initClassName = convertDotToSlash(initClassName);
        if (initMethodName == null || initMethodName.isEmpty()) initMethodName = "<clinit>";
        registerClassName = convertDotToSlash(registerClassName);
        if (registerClassName == null || registerClassName.isEmpty()) registerClassName = initClassName;
        
        superClassNames = convertList(superClassNames);
        include = convertList(include);
        exclude = convertList(exclude);
        
        // 初始化全局缓存
        if (id != null && !id.isEmpty()) {
            GLOBAL_CLASS_LISTS.putIfAbsent(id, Collections.synchronizedList(new ArrayList<>()));
        }
    }

    public List<String> getClassList() {
        return GLOBAL_CLASS_LISTS.getOrDefault(id, new ArrayList<>());
    }

    private List<String> convertList(List<String> ori) {
        if (ori == null) return new ArrayList<>();
        List<String> res = new ArrayList<>();
        for (String s : ori) {
            res.add(convertDotToSlash(s));
        }
        return res;
    }

    private String convertDotToSlash(String str) {
        return str != null ? str.replace('.', '/').intern() : str;
    }

    public boolean validate() {
        return interfaceName != null && !interfaceName.isEmpty() && initClassName != null && !initClassName.isEmpty();
    }

    public boolean isExclude(String className) {
        if (exclude == null || exclude.isEmpty()) return false;
        for (String ex : exclude) {
            if (className.contains(ex)) return true;
        }
        return false;
    }

    public boolean isInclude(String className) {
        if (include == null || include.isEmpty()) return true;
        for (String inc : include) {
            if (className.contains(inc)) return true;
        }
        return false;
    }
}
