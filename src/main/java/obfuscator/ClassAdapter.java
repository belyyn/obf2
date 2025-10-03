package obfuscator;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * ClassVisitor adapted to return obfuscated code of the visited class.
 */
public class ClassAdapter extends ClassVisitor {
    /**
     * Highest level package of this class. In other words, the largest local namespace, which contains this class.
     */
    private String pkg;
    /**
     * Binary name of this class, as provided by the method visit of ClassVisitor.
     */
    private String currentClass;
    private boolean isInnerClass = false;

    /**
     * Initiates constructor of the super class ClassVisitor.
     *
     * @param classVisitor the visitor passed to the field cv inherited from ClassVisitor.
     */
    public ClassAdapter(ClassVisitor classVisitor) {
        super(Opcodes.ASM9, classVisitor);
    }

    /**
     * Renames name, superName, interfaces of this class.
     *
     * @param version    the class version. The minor version is stored in the 16 most significant bits,
     *                   and the major version in the 16 least significant bits.
     * @param access     the class's access flags (see {@link Opcodes}). This parameter also indicates if
     *                   the class is deprecated {@link Opcodes#ACC_DEPRECATED} or a record {@link
     *                   Opcodes#ACC_RECORD}.
     * @param name       the internal name of the class.
     * @param signature  the signature of this class. May be {@literal null} if the class is not a
     *                   generic one, and does not extend or implement generic classes or interfaces.
     * @param superName  the internal of name of the super class.
     *                   For interfaces, the super class is {@link Object}. May be {@literal null}, but only for the
     *                   {@link Object} class.
     * @param interfaces the internal names of the class's interfaces. May be {@literal null}.
     */
    @Override
    public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
        this.pkg = name.substring(0, name.indexOf('/'));
        this.currentClass = name;
        if (interfaces != null)
            for (int i = 0; i < interfaces.length; i++)
                interfaces[i] = interfaces[i].startsWith(pkg)
                        ? NameFactory.obfuscateName(interfaces[i])
                        : interfaces[i];

        super.visit(version, access,
                NameFactory.obfuscateName(name),
                signature,
                superName.startsWith(pkg)
                        ? NameFactory.obfuscateName(superName)
                        : superName,
                interfaces);

    }


    /**
     * Renames fields and their descriptors.
     *
     * @param access     the field's access flags (see {@link Opcodes}). This parameter also indicates if
     *                   the field is synthetic and/or deprecated.
     * @param name       the field's name.
     * @param descriptor the field's descriptor.
     * @param signature  the field's signature. May be {@literal null} if the field's type does not use
     *                   generic types.
     * @param value      the field's initial value. This parameter, which may be {@literal null} if the
     *                   field does not have an initial value, must be an {@link Integer}, a {@link Float}, a {@link
     *                   Long}, a {@link Double} or a {@link String} (for {@code int}, {@code float}, {@code long}
     *                   or {@code String} fields respectively). <i>This parameter is only used for static
     *                   fields</i>. Its value is ignored for non-static fields, which must be initialized through
     *                   bytecode instructions in constructors or methods.
     * @return field with obfuscated name and descriptor.
     */
    @Override
    public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
        return super.visitField(access, NameFactory.obfuscateName(name), filterDescriptor(descriptor), signature, value);
    }

    /**
     * Renames methods, their descriptors and class members within the scope of this method.
     * Access value of the constructor indicates whether the class is nested or not.
     *
     * @param access     the method's access flags (see {@link Opcodes}). This parameter also indicates if
     *                   the method is synthetic and/or deprecated.
     * @param name       the method's name.
     * @param desc       the method's descriptor.
     * @param signature  the method's signature. May be {@literal null} if the method parameters,
     *                   return type and exceptions do not use generic types.
     * @param exceptions the internal names of the method's exception classes. May be {@literal null}.
     * @return method with obfuscated signature and body.
     */
    @Override
    public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
        if (exceptions != null)
            for (int i = 0; i < exceptions.length; i++)
                exceptions[i] = exceptions[i].startsWith(pkg)
                        ? NameFactory.obfuscateName(exceptions[i])
                        : exceptions[i];
        if (access == 2 && name.equals("<init>")) {
            this.isInnerClass = true;
        }

        MethodVisitor mv = cv.visitMethod(isInnerClass ? Opcodes.ACC_PUBLIC : access, name,
                filterDescriptor(desc), signature, exceptions);
        return new MethodAdapter(mv, pkg, currentClass);
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

    /**
     * @return the package of this class (see {@link ClassAdapter#pkg}).
     */
    public String getPackage() {
        return pkg;
    }
}
