import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.jar.*;
import org.objectweb.asm.*;
import org.objectweb.asm.Opcodes;

/**
 * Rewrites Java 9+ StringConcatFactory invokedynamic calls to
 * Java 7 StringBuilder chains, and downgrades class file version to 52 (Java 8).
 */
public class BytecodeRewriter {

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("Usage: java BytecodeRewriter <jar-file>");
            System.exit(1);
        }
        Path jarPath = Paths.get(args[0]);
        Path tmpPath = jarPath.resolveSibling(jarPath.getFileName() + ".tmp");

        int classesProcessed = 0;
        int concatReplaced = 0;

        try (JarFile jarIn = new JarFile(jarPath.toFile());
             JarOutputStream jarOut = new JarOutputStream(new FileOutputStream(tmpPath.toFile()))) {

            Enumeration<JarEntry> entries = jarIn.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                InputStream is = jarIn.getInputStream(entry);
                byte[] data;

                if (entry.getName().endsWith(".class")) {
                    data = readAllBytes(is);
                    ClassReader cr = new ClassReader(data);
                    ClassWriter cw = new LenientClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);

                    ConcatRewriter rewriter = new ConcatRewriter(cw);
                    try {
                        cr.accept(rewriter, ClassReader.SKIP_FRAMES);
                        data = cw.toByteArray();
                        concatReplaced += rewriter.concatCount;
                    } catch (Exception e) {
                        // If ASM fails, just downgrade the version
                        System.err.println("WARN: ASM failed on " + entry.getName() + ": " + e.getMessage());
                        data = downgradeVersion(data);
                    }
                    // Always patch Record -> Object in the output bytes
                    data = patchRecordTo(data, "java/lang/Record", "java/lang/Object");
                    classesProcessed++;
                } else {
                    data = readAllBytes(is);
                }

                jarOut.putNextEntry(new JarEntry(entry.getName()));
                jarOut.write(data);
                jarOut.closeEntry();
            }
        }

        Files.move(tmpPath, jarPath, StandardCopyOption.REPLACE_EXISTING);
        long sizeMb = Files.size(jarPath);
        System.out.println("Processed " + classesProcessed + " classes");
        System.out.println("Replaced " + concatReplaced + " StringConcatFactory calls");
        System.out.println("Output: " + jarPath + " (" + (sizeMb / 1024) + " KB)");
    }

    static byte[] downgradeVersion(byte[] data) {
        if (data.length >= 8 && data[0] == (byte)0xCA && data[1] == (byte)0xFE) {
            int major = (data[6] & 0xFF) * 256 + (data[7] & 0xFF);
            if (major > 52) {
                data[6] = 0;
                data[7] = 52;
            }
        }
        // Also patch java/lang/Record -> java/lang/Object in constant pool
        data = patchRecordTo(data, "java/lang/Record", "java/lang/Object");
        return data;
    }

    static byte[] patchRecordTo(byte[] data, String from, String to) {
        byte[] fromBytes = from.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] toBytes = to.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        // Constant pool UTF8 entries are: tag(1) + length(2) + bytes
        // tag=1 for UTF8. We search for: 0x00 len_hi len_low then the string
        // But we need to be careful about length field
        if (fromBytes.length != toBytes.length) {
            // java/lang/Record (16) and java/lang/Object (16) are same length
            return data;
        }
        int count = 0;
        for (int i = 0; i < data.length - 2 - fromBytes.length; i++) {
            // Check for CONSTANT_Utf8 tag=1 followed by 2-byte length
            if (data[i] == 1) {
                int len = ((data[i+1] & 0xFF) << 8) | (data[i+2] & 0xFF);
                if (len == fromBytes.length) {
                    boolean match = true;
                    for (int j = 0; j < fromBytes.length; j++) {
                        if (data[i+3+j] != fromBytes[j]) {
                            match = false;
                            break;
                        }
                    }
                    if (match) {
                        for (int j = 0; j < toBytes.length; j++) {
                            data[i+3+j] = toBytes[j];
                        }
                        count++;
                    }
                }
            }
        }
        if (count > 0) {
            System.out.println("  Patched " + count + " x Record -> Object");
        }
        return data;
    }

    static byte[] readAllBytes(InputStream is) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int n;
        while ((n = is.read(buf)) != -1) bos.write(buf, 0, n);
        return bos.toByteArray();
    }

    /**
     * ClassWriter that does not crash when types are missing from classpath.
     */
    static class LenientClassWriter extends ClassWriter {
        LenientClassWriter(int flags) {
            super(flags);
        }

        @Override
        protected String getCommonSuperClass(String type1, String type2) {
            try {
                return super.getCommonSuperClass(type1, type2);
            } catch (RuntimeException e) {
                return "java/lang/Object";
            }
        }
    }

    /**
     * ClassVisitor that rewrites StringConcatFactory invokedynamic to StringBuilder chains.
     */
    static class ConcatRewriter extends ClassVisitor {
        int concatCount = 0;

        ConcatRewriter(ClassVisitor cv) {
            super(Opcodes.ASM9, cv);
        }

        @Override
        public void visit(int version, int access, String name, String signature,
                          String superName, String[] interfaces) {
            // Downgrade class version to 52 (Java 8)
            if (version > 52) version = 52;
            // Patch Record extends -> Object extends (Record not in Java 8)
            if ("java/lang/Record".equals(superName)) {
                superName = "java/lang/Object";
            }
            super.visit(version, access, name, signature, superName, interfaces);
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String desc,
                                         String signature, String[] exceptions) {
            MethodVisitor mv = super.visitMethod(access, name, desc, signature, exceptions);
            return new MethodRewriter(mv, this);
        }

        @Override
        public RecordComponentVisitor visitRecordComponent(String name, String descriptor, String signature) {
            // Suppress Record attribute - Java 8 does not understand it
            return null;
        }
    }

    /**
     * MethodVisitor that replaces StringConcatFactory invokedynamic with StringBuilder.
     */
    static class MethodRewriter extends MethodVisitor {
        private final ConcatRewriter parent;
        private int tempSlotBase = -1;
        private boolean isFirstVisitCode = true;

        MethodRewriter(MethodVisitor mv, ConcatRewriter parent) {
            super(Opcodes.ASM9, mv);
            this.parent = parent;
        }

        @Override
        public void visitCode() {
            super.visitCode();
            isFirstVisitCode = false;
        }

        @Override
        public void visitMaxs(int maxStack, int maxLocals) {
            super.visitMaxs(maxStack, maxLocals);
        }

        @Override
        public void visitInvokeDynamicInsn(String name, String descriptor,
                                            Handle bootstrapMethodHandle,
                                            Object... bootstrapMethodArguments) {
            // Check if this is StringConcatFactory
            if (!isStringConcatFactory(bootstrapMethodHandle)) {
                super.visitInvokeDynamicInsn(name, descriptor, bootstrapMethodHandle, bootstrapMethodArguments);
                return;
            }

            parent.concatCount++;

            // Parse the descriptor to get argument types
            Type[] argTypes = Type.getArgumentTypes(descriptor);

            // Get the recipe string
            String recipe = null;
            if ("makeConcatWithConstants".equals(name) && bootstrapMethodArguments.length > 0) {
                Object first = bootstrapMethodArguments[0];
                if (first instanceof String) {
                    recipe = (String) first;
                }
            }

            if (recipe == null) {
                // makeConcat without recipe - just concatenate all args
                recipe = buildDefaultRecipe(argTypes.length);
            }

            // Allocate temp local variable slots above current max
            // We need one slot per argument (but long/double need 2)
            int slotBase = tempSlotBase;
            if (slotBase < 0) {
                // Estimate: use a high slot number to avoid conflicts
                // In practice, we'll just use slots after what ASM reports
                slotBase = 256; // safe high number
                tempSlotBase = slotBase;
            }

            // Save args from stack to locals (in reverse order since stack is LIFO)
            int slot = slotBase;
            int[] argSlots = new int[argTypes.length];
            for (int i = argTypes.length - 1; i >= 0; i--) {
                if (argTypes[i].getSize() == 2) {
                    slot += (slot % 2 != 0) ? 1 : 0; // align to even for long/double
                }
                argSlots[i] = slot;
                slot += argTypes[i].getSize();
            }
            // Store in reverse order
            for (int i = argTypes.length - 1; i >= 0; i--) {
                storeArg(argTypes[i], argSlots[i]);
            }

            // Create new StringBuilder
            mv.visitTypeInsn(Opcodes.NEW, "java/lang/StringBuilder");
            mv.visitInsn(Opcodes.DUP);
            mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/StringBuilder", "<init>", "()V", false);

            // Parse recipe and emit appends
            int argIndex = 0;
            int pos = 0;
            while (pos < recipe.length()) {
                int nextArg = recipe.indexOf('\u0001', pos);
                if (nextArg == -1) {
                    // Rest is literal
                    String literal = recipe.substring(pos);
                    if (!literal.isEmpty()) {
                        emitAppend(literal);
                    }
                    break;
                } else {
                    // Literal before argument
                    if (nextArg > pos) {
                        emitAppend(recipe.substring(pos, nextArg));
                    }
                    // Append the argument
                    if (argIndex < argTypes.length) {
                        loadArg(argTypes[argIndex], argSlots[argIndex]);
                        emitAppendTyped(argTypes[argIndex]);
                        argIndex++;
                    }
                    pos = nextArg + 1;
                }
            }

            // Call toString()
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/StringBuilder", "toString",
                    "()Ljava/lang/String;", false);
        }

        private boolean isStringConcatFactory(Handle handle) {
            return handle != null &&
                   handle.getOwner().equals("java/lang/invoke/StringConcatFactory") &&
                   (handle.getName().equals("makeConcat") || handle.getName().equals("makeConcatWithConstants"));
        }

        private String buildDefaultRecipe(int argCount) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < argCount; i++) {
                if (i > 0) sb.append('\u0001');
                sb.append('\u0001');
            }
            return sb.toString();
        }

        private void emitAppend(String literal) {
            mv.visitLdcInsn(literal);
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/StringBuilder", "append",
                    "(Ljava/lang/String;)Ljava/lang/StringBuilder;", false);
        }

        private void emitAppendTyped(Type type) {
            String desc;
            switch (type.getSort()) {
                case Type.BOOLEAN: desc = "(Z)"; break;
                case Type.CHAR:    desc = "(C)"; break;
                case Type.BYTE:    desc = "(B)"; break;
                case Type.SHORT:   desc = "(S)"; break;
                case Type.INT:     desc = "(I)"; break;
                case Type.LONG:    desc = "(J)"; break;
                case Type.FLOAT:   desc = "(F)"; break;
                case Type.DOUBLE:  desc = "(D)"; break;
                default:           desc = "(Ljava/lang/Object;)"; break;
            }
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/StringBuilder", "append",
                    desc + "Ljava/lang/StringBuilder;", false);
        }

        private void storeArg(Type type, int slot) {
            switch (type.getSort()) {
                case Type.BOOLEAN:
                case Type.CHAR:
                case Type.BYTE:
                case Type.SHORT:
                case Type.INT:
                    mv.visitVarInsn(Opcodes.ISTORE, slot);
                    break;
                case Type.LONG:
                    mv.visitVarInsn(Opcodes.LSTORE, slot);
                    break;
                case Type.FLOAT:
                    mv.visitVarInsn(Opcodes.FSTORE, slot);
                    break;
                case Type.DOUBLE:
                    mv.visitVarInsn(Opcodes.DSTORE, slot);
                    break;
                default:
                    mv.visitVarInsn(Opcodes.ASTORE, slot);
                    break;
            }
        }

        private void loadArg(Type type, int slot) {
            switch (type.getSort()) {
                case Type.BOOLEAN:
                case Type.CHAR:
                case Type.BYTE:
                case Type.SHORT:
                case Type.INT:
                    mv.visitVarInsn(Opcodes.ILOAD, slot);
                    break;
                case Type.LONG:
                    mv.visitVarInsn(Opcodes.LLOAD, slot);
                    break;
                case Type.FLOAT:
                    mv.visitVarInsn(Opcodes.FLOAD, slot);
                    break;
                case Type.DOUBLE:
                    mv.visitVarInsn(Opcodes.DLOAD, slot);
                    break;
                default:
                    mv.visitVarInsn(Opcodes.ALOAD, slot);
                    break;
            }
        }
    }
}
