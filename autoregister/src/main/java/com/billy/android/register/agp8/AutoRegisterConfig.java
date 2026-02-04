package com.billy.android.register.agp8;

import org.gradle.api.Project;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class AutoRegisterConfig implements Serializable {
    public List<AutoRegisterInfo> registerInfo = new ArrayList<>();
    public boolean cacheEnabled = true;
    public transient Project project;
    public List<AutoRegisterInfo> list = new ArrayList<>();

    public void convertConfig() {
        list.clear();
        if (registerInfo != null) {
            for (Object obj : registerInfo) {
                if (obj instanceof java.util.Map) {
                    java.util.Map map = (java.util.Map) obj;
                    AutoRegisterInfo info = new AutoRegisterInfo();
                    info.interfaceName = (String) map.get("scanInterface");
                    Object superClassNames = map.get("scanSuperClasses");
                    if (superClassNames instanceof List) {
                        info.superClassNames = (List<String>) superClassNames;
                    } else if (superClassNames instanceof String) {
                        info.superClassNames.add((String) superClassNames);
                    }
                    info.initClassName = (String) map.get("codeInsertToClassName");
                    info.initMethodName = (String) map.get("codeInsertToMethodName");
                    info.registerClassName = (String) map.get("registerClassName");
                    info.registerMethodName = (String) map.get("registerMethodName");
                    Object exclude = map.get("exclude");
                    if (exclude instanceof List) {
                        info.exclude = (List<String>) exclude;
                    }
                    Object include = map.get("include");
                    if (include instanceof List) {
                        info.include = (List<String>) include;
                    }
                    
                    // 生成唯一 ID
                    info.id = (info.interfaceName + "|" + info.initClassName + "|" + info.initMethodName).hashCode() + "";
                    
                    info.init();
                    if (info.validate()) {
                        list.add(info);
                    }
                }
            }
        }
        
        // 关键补丁：在每一轮构建开始时，清除当前配置对应的全局缓存
        for (AutoRegisterInfo info : list) {
            AutoRegisterInfo.GLOBAL_CLASS_LISTS.remove(info.id);
            info.init(); // 重新创建缓存列表
        }
    }
}
