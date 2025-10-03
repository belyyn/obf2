package obfuscator;

import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Handle;


import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


/**
 * MethodVisitor adapted to return obfuscated code of the visited method.
 */
public class MethodAdapter extends MethodVisitor {
    /**
     * Highest level package of this class. In other words, the largest local namespace, which contains this class.
     */
    private final String pkg;
    /**
     * Binary name of this class, as provided by the method visit of ClassVisitor.
     */
    private final String currentClass;

    /**
     * Initiates constructor of the super class MethodVisitor.
     *
     * @param mv           MethodVisitor passed to the inherited field mv.
     * @param pkg          Highest level package of this class. In other words, the largest local namespace, which contains this class.
     * @param currentClass Binary name of this class, as provided by the method visit of ClassVisitor.
     */
    public MethodAdapter(MethodVisitor mv, String pkg, String currentClass) {
        super(Opcodes.ASM9, mv);
        this.pkg = pkg;
        this.currentClass = currentClass;
    }

    /**
     * Renames lambda function calls, their descriptors and owners.
     *
     * @param name                     the method's name.
     * @param descriptor               the method's descriptor.
     * @param bootstrapMethodHandle    the bootstrap method.
     * @param bootstrapMethodArguments the bootstrap method constant arguments. Only inspects
     *                                 {@link Handle}  values. This method is allowed to modify
     *                                 the content of the array so a caller should expect that this array may change.
     */
    @Override
    public void visitInvokeDynamicInsn(String name, String descriptor, Handle bootstrapMethodHandle, Object... bootstrapMethodArguments) {
        if (bootstrapMethodHandle != null)
            for (int i = 0; i < bootstrapMethodArguments.length; i++)
                if (bootstrapMethodArguments[i] instanceof Handle oldHandle) {
                    if (oldHandle.getOwner().startsWith(pkg))
                        bootstrapMethodArguments[i] = new Handle(oldHandle.getTag(),
                                NameFactory.obfuscateName(oldHandle.getOwner()),
                                filterSynthetic(oldHandle.getName()),
                                filterDescriptor(oldHandle.getDesc()),
                                oldHandle.isInterface());
                    else bootstrapMethodArguments[i] = new Handle(oldHandle.getTag(),
                            oldHandle.getOwner(),
                            oldHandle.getName(),
                            filterDescriptor(oldHandle.getDesc()),
                            oldHandle.isInterface());
                }
        super.visitInvokeDynamicInsn(name, filterDescriptor(descriptor), bootstrapMethodHandle, bootstrapMethodArguments);
    }

    /**
     * Renames types used in type instructions. Types are fully qualified names.
     *
     * @param opcode the opcode of the type instruction to be visited. This opcode is either NEW,
     *               ANEWARRAY, CHECKCAST or INSTANCEOF.
     * @param type   the operand of the instruction to be visited. This operand must be the internal
     *               name of an object or array class.
     */
    @Override
    public void visitTypeInsn(int opcode, String type) {
        super.visitTypeInsn(opcode, type.startsWith(pkg)
                ? NameFactory.obfuscateName(type)
                : type);
    }

    /**
     * Renames method instruction calls' names, owners, descriptors.
     * Only obfuscates method instructions belonging to this package.
     *
     * @param opcode      the opcode of the type instruction to be visited. This opcode is either
     *                    INVOKEVIRTUAL, INVOKESPECIAL, INVOKESTATIC or INVOKEINTERFACE.
     * @param owner       the internal name of the method's owner class.
     * @param name        the method's name.
     * @param descriptor  the method's descriptor.
     * @param isInterface if the method's owner class is an interface.
     */
    @Override
    public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
        if (!owner.startsWith(pkg)) { // Checking if method belongs to outside package
            super.visitMethodInsn(opcode, owner, name, filterDescriptor(descriptor), isInterface);
            return;
        }

        super.visitMethodInsn(opcode,
                NameFactory.obfuscateName(owner),
                filterSynthetic(name),
                filterDescriptor(descriptor),
                isInterface);
    }

    /**
     * Renames field instruction calls' names, owners, descriptors.
     * Only obfuscates field instructions belonging to this package.
     * Finds inherited fields used in this class.
     *
     * @param opcode     the opcode of the type instruction to be visited. This opcode is either
     *                   GETSTATIC, PUTSTATIC, GETFIELD or PUTFIELD.
     * @param owner      the internal name of the field's owner class.
     * @param name       the field's name.
     * @param descriptor the field's descriptor.
     */
    @Override
    public void visitFieldInsn(int opcode, String owner, String name, String descriptor) {
        if (!owner.startsWith(pkg)) { // Checking if field belongs to outside package
            super.visitFieldInsn(opcode, owner, name, filterDescriptor(descriptor));
            return;
        }
        if (owner.equals(currentClass) && !NameFactory.getNameMap().containsKey(name)) { // if the field wasn't obfuscated in ClassAdapter.visitField
            super.visitFieldInsn(opcode,                                                 // then it's an inherited field
                    NameFactory.obfuscateName(owner),
                    name,
                    filterDescriptor(descriptor));
            NameFactory.getNameMap().put(name, name);
            return;
        }
        super.visitFieldInsn(opcode,
                NameFactory.obfuscateName(owner),
                filterSynthetic(name),
                filterDescriptor(descriptor));
    }

    /**
     * Renames variables inside catch.
     * Breaks on long names(?).
     *
     * @param start   the beginning of the exception handler's scope (inclusive).
     * @param end     the end of the exception handler's scope (exclusive).
     * @param handler the beginning of the exception handler's code.
     * @param type    the internal name of the type of exceptions handled by the handler, or {@literal null} to catch any exceptions (for "finally"
     *                blocks).
     */
    @Override
    public void visitTryCatchBlock(Label start, Label end, Label handler, String type) {
        super.visitTryCatchBlock(start, end, handler,
                type.startsWith(pkg)
                        ? NameFactory.obfuscateName(type)
                        : type);
    }


    /**
     * Renames local variable names and descriptors.
     *
     * @param name      the name of a local variable.
     * @param desc      the type descriptor of this local variable.
     * @param signature the type signature of this local variable. May be {@literal null} if the local
     *                  variable type does not use generic types.
     * @param start     the first instruction corresponding to the scope of this local variable
     *                  (inclusive).
     * @param end       the last instruction corresponding to the scope of this local variable (exclusive).
     * @param index     the local variable's index.
     */
    @Override
    public void visitLocalVariable(String name, String desc, String signature, Label start, Label end, int index) {
        super.visitLocalVariable(NameFactory.obfuscateName(name), filterDescriptor(desc), signature, start, end, index);
    }

    /**
     * Uses regex to obfuscate the binary name within synthetic methods.
     *
     * @param syntheticMethodName method to be filtered.
     * @return an obfuscated method.
     */
    private String filterSynthetic(String syntheticMethodName) {
        if (List.of("<init>", "<clinit>", "main").contains(syntheticMethodName))
            return syntheticMethodName;

        if (!syntheticMethodName.contains("$"))
            return NameFactory.obfuscateName(syntheticMethodName);

        Matcher m = (Pattern.compile("\\$(.*?)\\$")).matcher(syntheticMethodName); // non-greedy regex

        while (m.find()) {
            String methodName = m.group(1);
            if (methodName.contains(".") || Objects.equals(methodName,"")) // account for anonymous and local classes
                continue;
            syntheticMethodName = syntheticMethodName.replace(methodName, NameFactory.obfuscateName(methodName));
        }

        return syntheticMethodName;
    }

    /**
     * Uses regex to obfuscate the binary name within the descriptor.
     *
     * @param desc descriptor to be filtered. Do not confuse descriptor with type (fully qualified name of the class).
     * @return an obfuscated descriptor if not passed a primitive or a descriptor of an outside package class. Otherwise, same, unchanged descriptor.
     */
    private String filterDescriptor(String desc) {
        Matcher m = (Pattern.compile("L(.*?);")).matcher(desc);

        while (m.find()) {
            String classDescriptor = m.group(1);
            if (!classDescriptor.startsWith(pkg)) continue;
            desc = desc.replace(classDescriptor, NameFactory.obfuscateName(classDescriptor));
        }

        return desc;
    }
}
