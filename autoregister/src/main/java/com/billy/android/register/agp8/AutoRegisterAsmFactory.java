package com.billy.android.register.agp8;

import com.android.build.api.instrumentation.AsmClassVisitorFactory;
import com.android.build.api.instrumentation.ClassContext;
import com.android.build.api.instrumentation.ClassData;
import com.android.build.api.instrumentation.InstrumentationParameters;

import org.gradle.api.provider.ListProperty;
import org.gradle.api.tasks.Input;
import org.objectweb.asm.*;

import java.util.List;

public abstract class AutoRegisterAsmFactory implements AsmClassVisitorFactory<AutoRegisterAsmParams> {
    @Override
    public ClassVisitor createClassVisitor(ClassContext classContext, ClassVisitor nextClassVisitor) {
        AutoRegisterAsmParams params = getParameters().get();
        return new RegisterClassVisitor(nextClassVisitor, params.getConfigList().get());
    }

    @Override
    public boolean isInstrumentable(ClassData classData) {
        String className = classData.getClassName();
        if (className.startsWith("android.") ||
                className.startsWith("com.android.") ||
                className.startsWith("com.google.") ||
                className.startsWith("org.jetbrains.") ||
                className.startsWith("kotlin.") ||
                className.startsWith("android.support.")) {
            return false;
        }

        AutoRegisterAsmParams params = getParameters().get();
        List<AutoRegisterInfo> configList = params.getConfigList().get();
        String slashClassName = className.replace('.', '/');


        for (AutoRegisterInfo info : configList) {
            // 只要是注入目标类，必须拦截
            if (slashClassName.equals(info.initClassName)) {
                return true;
            }
            // 只要在 include 范围内，就拦截（具体的 exclude 过滤下沉到 visit 阶段处理）
            if (info.isInclude(slashClassName)) {
                return true;
            }
        }

        return false;
    }
}

interface AutoRegisterAsmParams extends InstrumentationParameters {
    @Input
    ListProperty<AutoRegisterInfo> getConfigList();
}

class RegisterClassVisitor extends ClassVisitor {
    private List<AutoRegisterInfo> configList;
    private String className;

    public RegisterClassVisitor(ClassVisitor cv, List<AutoRegisterInfo> configList) {
        super(Opcodes.ASM9, cv);
        this.configList = configList;
    }

    @Override
    public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
        super.visit(version, access, name, signature, superName, interfaces);
        this.className = name;

        // 阶段 1：扫描探测
        if ((access & Opcodes.ACC_PUBLIC) != 0 && (access & (Opcodes.ACC_ABSTRACT | Opcodes.ACC_INTERFACE)) == 0) {
            for (AutoRegisterInfo info : configList) {
                if (info.isExclude(name) || !info.isInclude(name)) continue;

                boolean matched = false;
                // 检查接口实现
                if (info.interfaceName != null && !info.interfaceName.isEmpty() && interfaces != null) {
                    for (String itf : interfaces) {
                        if (itf.equals(info.interfaceName)) {
                            matched = true;
                            break;
                        }
                    }
                }
                // 检查父类继承
                if (!matched && info.superClassNames != null && !info.superClassNames.isEmpty()) {
                    if (info.superClassNames.contains(superName)) {
                        matched = true;
                    }
                }

                if (matched) {
                    List<String> list = AutoRegisterInfo.GLOBAL_CLASS_LISTS.get(info.id);
                    if (list != null) {
                        synchronized (list) {
                            if (!list.contains(name)) {
                                list.add(name);
                                System.out.println("[AutoRegister] 扫描到组件: " + name + " -> 对应接口: " + info.interfaceName);
                            }
                        }
                    }
                }
            }
        }
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
        MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
        if (mv == null) return null;

        // 阶段 2：代码注入
        for (AutoRegisterInfo info : configList) {
            if (className != null && className.equals(info.initClassName) && name.equals(info.initMethodName)) {
                System.out.println("命中注入目标方法: " + className + "." + name);
                return new RegisterMethodVisitor(Opcodes.ASM9, mv, access, info);
            }
        }
        return mv;
    }
}

class RegisterMethodVisitor extends MethodVisitor {
    private AutoRegisterInfo info;
    private int access;

    public RegisterMethodVisitor(int api, MethodVisitor mv, int access, AutoRegisterInfo info) {
        super(api, mv);
        this.info = info;
        this.access = access;
    }

    @Override
    public void visitInsn(int opcode) {
        System.out.println("开始注入: " + opcode);

        if (opcode >= Opcodes.IRETURN && opcode <= Opcodes.RETURN) {
            // 从全局缓存中拉取扫描到的组件类
            List<String> classList = AutoRegisterInfo.GLOBAL_CLASS_LISTS.get(info.id);
            if (classList != null) {
                synchronized (classList) {
                    for (String clazz : classList) {

                        boolean isMethodStatic = (access & Opcodes.ACC_STATIC) != 0;
                        if (!isMethodStatic) {
                            mv.visitVarInsn(Opcodes.ALOAD, 0);
                        }
                        mv.visitTypeInsn(Opcodes.NEW, clazz);
                        mv.visitInsn(Opcodes.DUP);
                        mv.visitMethodInsn(Opcodes.INVOKESPECIAL, clazz, "<init>", "()V", false);

                        int invokeOpcode = isMethodStatic ? Opcodes.INVOKESTATIC : Opcodes.INVOKEVIRTUAL;
                        String targetClassName = (info.registerClassName != null && !info.registerClassName.isEmpty()) ?
                                info.registerClassName : info.initClassName;
                        String descriptor = "(L" + info.interfaceName + ";)V";

                        mv.visitMethodInsn(invokeOpcode, targetClassName, info.registerMethodName, descriptor, false);
                    }
                }
            }
        }
        super.visitInsn(opcode);
    }
}
