package com.billy.android.register

import com.android.build.api.instrumentation.AsmClassVisitorFactory
import com.android.build.api.instrumentation.ClassContext
import com.android.build.api.instrumentation.ClassData
import com.android.build.api.instrumentation.InstrumentationParameters
import org.gradle.api.provider.ListProperty
import org.gradle.api.tasks.Input
import org.objectweb.asm.*
import org.objectweb.asm.commons.AdviceAdapter

/**
 * ASM 转换工厂类 (专门适配 Gradle 8.x)
 */
abstract class RegisterAsmFactory implements AsmClassVisitorFactory<RegisterAsmParams> {

    @Override
    ClassVisitor createClassVisitor(ClassContext classContext, ClassVisitor nextClassVisitor) {
        return new RegisterClassVisitor(nextClassVisitor, getParameters().getConfigList().get())
    }

    @Override
    boolean isInstrumentable(ClassData classData) {
        // 由于需要动态收集实现类，必须扫描所有相关类
        return true
    }
}

interface RegisterAsmParams extends InstrumentationParameters {
    @Input
    ListProperty<RegisterInfo> getConfigList()
}

/**
 * 核心 ClassVisitor，负责扫描和注入逻辑
 */
class RegisterClassVisitor extends ClassVisitor {
    private List<RegisterInfo> configList
    private String className
    private String superName
    private String[] interfaces
    private int access

    RegisterClassVisitor(ClassVisitor cv, List<RegisterInfo> configList) {
        super(Opcodes.ASM9, cv)
        this.configList = configList
    }

    @Override
    void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
        super.visit(version, access, name, signature, superName, interfaces)
        this.className = name
        this.superName = superName
        this.interfaces = interfaces
        this.access = access

        // 核心逻辑：扫描符合条件的类并记录到 configList
        if (!((access & Opcodes.ACC_ABSTRACT) || (access & Opcodes.ACC_INTERFACE) || !(access & Opcodes.ACC_PUBLIC))) {
            configList.each { info ->
                boolean matched = false
                if (info.interfaceName && interfaces != null) {
                    if (interfaces.contains(info.interfaceName)) {
                        matched = true
                    }
                }
                if (!matched && info.superClassNames && !info.superClassNames.isEmpty()) {
                    if (info.superClassNames.contains(superName)) {
                        matched = true
                    }
                }
                
                if (matched) {
                    println("auto-register found: ${name} (matched ${info.interfaceName ?: info.superClassNames})")
                    // 在 Gradle 运行期间，所有 Variant 的 Instrumentation 是串行的，
                    // 但类处理可能是并行的。不过 RegisterInfo.classList 是线程安全的集合时才稳妥。
                    // 默认 List 可能存在竞态。此处简单追加。
                    synchronized (info.classList) {
                        if (!info.classList.contains(name)) {
                            info.classList.add(name)
                        }
                    }
                }
            }
        }
    }

    @Override
    MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
        MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions)
        
        configList.each { info ->
            // 匹配插入目标类及方法
            if (className == info.initClassName && name == (info.initMethodName ?: "<clinit>")) {
                println("auto-register will inject code into: ${className}.${name}")
                mv = new RegisterMethodAdapter(api, mv, access, name, descriptor, info)
            }
        }
        return mv
    }
}

/**
 * 负责在方法末尾注入 register 调用代码
 */
class RegisterMethodAdapter extends AdviceAdapter {
    private RegisterInfo info

    RegisterMethodAdapter(int api, MethodVisitor mv, int access, String name, String descriptor, RegisterInfo info) {
        super(api, mv, access, name, descriptor)
        this.info = info
    }

    @Override
    protected void onMethodExit(int opcode) {
        if (opcode == RETURN) {
            // 遍历并注入所有收集到的类
            info.classList.each { clazz ->
                println("auto-register injecting: mv.visitTypeInsn(NEW, ${clazz})")
                mv.visitTypeInsn(NEW, clazz)
                mv.visitInsn(DUP)
                mv.visitMethodInsn(INVOKESPECIAL, clazz, "<init>", "()V", false)
                
                boolean isStatic = (methodAccess & ACC_STATIC) != 0
                int invokeOpcode = isStatic ? INVOKESTATIC : INVOKEVIRTUAL
                
                // 注意：由于 Instrumentation 是流式处理，如果注入目标类先被处理，
                // 而实现类后被发现，则可能导致漏注。
                // 传统 autoregister 在 Transform 阶段分两次遍历（扫描->注入）解决了这一问题。
                // 在 Instrumentation 中，可能需要先确保扫描完成。这是一个架构挑战。
                
                String targetClassName = info.registerClassName ?: info.initClassName
                mv.visitMethodInsn(invokeOpcode, targetClassName, info.registerMethodName, "(L${info.interfaceName};)V", false)
            }
        }
    }
}
