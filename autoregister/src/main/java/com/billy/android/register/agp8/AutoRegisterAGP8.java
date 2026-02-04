package com.billy.android.register.agp8;

import com.android.build.api.instrumentation.FramesComputationMode;
import com.android.build.api.instrumentation.InstrumentationScope;
import com.android.build.api.variant.ApplicationAndroidComponentsExtension;
import com.android.build.api.variant.LibraryAndroidComponentsExtension;
import org.gradle.api.Plugin;
import org.gradle.api.Project;

public class AutoRegisterAGP8 implements Plugin<Project> {
    public static final String EXT_NAME = "autoregister";

    @Override
    public void apply(Project project) {
        System.out.println("Applying AutoRegisterAGP8 plugin to " + project.getName());
        
        final AutoRegisterConfig config = project.getExtensions().create(EXT_NAME, AutoRegisterConfig.class);
        config.project = project;

        ApplicationAndroidComponentsExtension appComponents = 
            project.getExtensions().findByType(ApplicationAndroidComponentsExtension.class);
        if (appComponents != null) {
            appComponents.onVariants(appComponents.selector().all(), variant -> {
                config.convertConfig();
                variant.getInstrumentation().transformClassesWith(
                    AutoRegisterAsmFactory.class,
                    InstrumentationScope.ALL,
                    params -> {
                        params.getConfigList().set(config.list);
                        return null;
                    }
                );
                // 开启强制栈帧计算，确保注入后的字节码在 Dex 阶段校验通过
                variant.getInstrumentation().setAsmFramesComputationMode(
                    FramesComputationMode.COMPUTE_FRAMES_FOR_INSTRUMENTED_METHODS
                );
            });
        }

        LibraryAndroidComponentsExtension libComponents = 
            project.getExtensions().findByType(LibraryAndroidComponentsExtension.class);
        if (libComponents != null) {
            libComponents.onVariants(libComponents.selector().all(), variant -> {
                config.convertConfig();
                variant.getInstrumentation().transformClassesWith(
                    AutoRegisterAsmFactory.class,
                    InstrumentationScope.ALL,
                    params -> {
                        params.getConfigList().set(config.list);
                        return null;
                    }
                );
                // 同样为 Library 开启强制栈帧计算
                variant.getInstrumentation().setAsmFramesComputationMode(
                    FramesComputationMode.COMPUTE_FRAMES_FOR_INSTRUMENTED_METHODS
                );
            });
        }
    }
}
