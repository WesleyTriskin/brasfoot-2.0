package br.com.brasfootmod.patch;

import com.esotericsoftware.reflectasm.shaded.org.objectweb.asm.*;
import java.lang.instrument.ClassFileTransformer;
import java.security.ProtectionDomain;

public class BrasfootClassTransformer implements ClassFileTransformer {

    @Override
    public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined,
                            ProtectionDomain protectionDomain, byte[] classfileBuffer) {
        if (className == null) return null;

        try {
            if ("best/S".equals(className)) {
                return transformBestS(classfileBuffer);
            } else if ("a/ho".equals(className)) {
                return transformAHo(classfileBuffer);
            } else if ("a/cS".equals(className)) {
                return transformACS(classfileBuffer);
            } else if ("b/ar".equals(className)) {
                return transformBAr(classfileBuffer);
            }
        } catch (Throwable t) {
            System.err.println("[MOD PATCH] Erro ao transformar classe " + className + ": " + t.getMessage());
            t.printStackTrace();
        }
        return null;
    }

    /**
     * Sincroniza best.S.aY e best.S.aZ e previne gI == null em aZ
     */
    private byte[] transformBestS(byte[] original) {
        ClassReader cr = new ClassReader(original);
        ClassWriter cw = new ClassWriter(cr, ClassWriter.COMPUTE_MAXS);

        ClassVisitor cv = new ClassVisitor(Opcodes.ASM5, cw) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
                // Adiciona ACC_SYNCHRONIZED em aY(int) e aZ(int)
                if ("aY".equals(name) || "aZ".equals(name)) {
                    access |= Opcodes.ACC_SYNCHRONIZED;
                }
                MethodVisitor mv = super.visitMethod(access, name, desc, signature, exceptions);

                if ("aZ".equals(name) && "(I)V".equals(desc)) {
                    return new MethodVisitor(Opcodes.ASM5, mv) {
                        @Override
                        public void visitFieldInsn(int opcode, String owner, String fieldName, String fieldDesc) {
                            super.visitFieldInsn(opcode, owner, fieldName, fieldDesc);
                            // Sempre que carregar best.S.gI, intercepta com checkGiNotNull
                            if (opcode == Opcodes.GETSTATIC && "best/S".equals(owner) && "gI".equals(fieldName)) {
                                super.visitMethodInsn(Opcodes.INVOKESTATIC, "br/com/brasfootmod/patch/BrasfootPatchHooks", "checkGiNotNull", "(Ljava/lang/Object;)Ljava/lang/Object;", false);
                                super.visitTypeInsn(Opcodes.CHECKCAST, "components/E");
                            }
                        }
                    };
                }
                return mv;
            }
        };

        cr.accept(cv, 0);
        System.out.println("[MOD PATCH] ✅ best.S transformado com sucesso (aY e aZ sincronizados + gI protegido contra NPE)");
        return cw.toByteArray();
    }

    /**
     * Substitui o corpo de a.ho.run() para despachar para a EDT e tratar exceções
     */
    private byte[] transformAHo(byte[] original) {
        ClassReader cr = new ClassReader(original);
        ClassWriter cw = new ClassWriter(cr, ClassWriter.COMPUTE_MAXS);

        ClassVisitor cv = new ClassVisitor(Opcodes.ASM5, cw) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
                if ("run".equals(name) && "()V".equals(desc)) {
                    MethodVisitor mv = super.visitMethod(access, name, desc, signature, exceptions);
                    return new MethodVisitor(Opcodes.ASM5, null) {
                        @Override
                        public void visitCode() {
                            mv.visitCode();
                            mv.visitVarInsn(Opcodes.ALOAD, 0);
                            mv.visitFieldInsn(Opcodes.GETFIELD, "a/ho", "Lt", "La/hk;");
                            mv.visitMethodInsn(Opcodes.INVOKESTATIC, "br/com/brasfootmod/patch/BrasfootPatchHooks", "runHoSafe", "(Ljava/lang/Object;)V", false);
                            mv.visitInsn(Opcodes.RETURN);
                            mv.visitMaxs(2, 1);
                            mv.visitEnd();
                        }
                    };
                }
                return super.visitMethod(access, name, desc, signature, exceptions);
            }
        };

        cr.accept(cv, 0);
        System.out.println("[MOD PATCH] ✅ a.ho transformado com sucesso (runHoSafe com EDT e tratamento de erro ativo)");
        return cw.toByteArray();
    }

    /**
     * Substitui o corpo de a.cS.run() para despachar para a EDT e tratar exceções
     */
    private byte[] transformACS(byte[] original) {
        ClassReader cr = new ClassReader(original);
        ClassWriter cw = new ClassWriter(cr, ClassWriter.COMPUTE_MAXS);

        ClassVisitor cv = new ClassVisitor(Opcodes.ASM5, cw) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
                if ("run".equals(name) && "()V".equals(desc)) {
                    MethodVisitor mv = super.visitMethod(access, name, desc, signature, exceptions);
                    return new MethodVisitor(Opcodes.ASM5, null) {
                        @Override
                        public void visitCode() {
                            mv.visitCode();
                            mv.visitVarInsn(Opcodes.ALOAD, 0);
                            mv.visitFieldInsn(Opcodes.GETFIELD, "a/cS", "DV", "La/cR;");
                            mv.visitMethodInsn(Opcodes.INVOKESTATIC, "br/com/brasfootmod/patch/BrasfootPatchHooks", "runCsSafe", "(Ljava/lang/Object;)V", false);
                            mv.visitInsn(Opcodes.RETURN);
                            mv.visitMaxs(2, 1);
                            mv.visitEnd();
                        }
                    };
                }
                return super.visitMethod(access, name, desc, signature, exceptions);
            }
        };

        cr.accept(cv, 0);
        System.out.println("[MOD PATCH] ✅ a.cS transformado com sucesso (runCsSafe com EDT e tratamento de erro ativo)");
        return cw.toByteArray();
    }

    /**
     * Transforma b.ar (o TableCellRenderer nativo da caixa de mensagens do Brasfoot)
     * para que nunca exiba "Bem vind@ ao " na frente de títulos de mods ("[Scout]...", "[Diretoria]...", etc.)
     */
    private byte[] transformBAr(byte[] original) {
        ClassReader cr = new ClassReader(original);
        ClassWriter cw = new ClassWriter(cr, ClassWriter.COMPUTE_MAXS);

        ClassVisitor cv = new ClassVisitor(Opcodes.ASM5, cw) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
                MethodVisitor mv = super.visitMethod(access, name, desc, signature, exceptions);

                if ("getTableCellRendererComponent".equals(name)) {
                    return new MethodVisitor(Opcodes.ASM5, mv) {
                        @Override
                        public void visitMethodInsn(int opcode, String owner, String methodName, String methodDesc, boolean isInterface) {
                            if (opcode == Opcodes.INVOKEVIRTUAL && "setText".equals(methodName) && "(Ljava/lang/String;)V".equals(methodDesc)) {
                                super.visitMethodInsn(Opcodes.INVOKESTATIC, "br/com/brasfootmod/patch/BrasfootPatchHooks", "filterInboxSubject", "(Ljava/lang/String;)Ljava/lang/String;", false);
                            }
                            super.visitMethodInsn(opcode, owner, methodName, methodDesc, isInterface);
                        }
                    };
                }
                return mv;
            }
        };

        cr.accept(cv, 0);
        System.out.println("[MOD PATCH] ✅ b.ar transformado com sucesso (filtro anti-prefixo de mod na Caixa de Entrada ativo)");
        return cw.toByteArray();
    }
}
