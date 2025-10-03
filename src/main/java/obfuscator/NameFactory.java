package obfuscator;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Utility class that manages all name obfuscation operations.
 */
public class NameFactory {
    /**
     * The maximum number of names that can be mapped per one execution.
     */
    private static int maxObfuscatedNames; // default: 2^12 = 4096; 12 character long strings.
    /**
     * The output path for obfuscated classes files.
     */
    private static String outputPath; // default: working directory / inside the executable .jar file.
    /**
     * The mapping of obfuscated names.
     */
    private static final Map<String, String> nameMap = new HashMap<>();
    /**
     * Iterator over shuffled uppercase i and lowercase L sequence strings
     * of size {@link NameFactory#maxObfuscatedNames}.
     */
    private static Iterator<String> obfuscatedNames;
    /**
     * Logger for this class.
     */
    private static final Logger logger = LogManager.getLogger();


    /**
     * Initializes the NameFactory by generating {@link NameFactory#maxObfuscatedNames} of names and shuffling them.
     *
     * @param outputPath         path to which classes will be written.
     * @param maxObfuscatedNames the maximum number of names that can be mapped per one execution.
     * @return false if {@link NameFactory#outputPath} is not a valid path. True otherwise.
     */
    public static boolean initialize(String outputPath, int maxObfuscatedNames) {
        NameFactory.maxObfuscatedNames = maxObfuscatedNames;
        NameFactory.outputPath = outputPath.isEmpty() ? "" : outputPath + System.getProperty("file.separator");

        Path path = Path.of(outputPath);
        if (!(Files.exists(path) && Files.isDirectory(path))) {
            logger.error("Output path is not a valid directory.");
            return false;
        }

        List<Integer> numbers = IntStream.range(0, maxObfuscatedNames)
                .boxed()
                .collect(Collectors.toList()); // Should be mutable to shuffle (achieved by using .collect())
        Collections.shuffle(numbers);

        obfuscatedNames = numbers.stream()
                .map(NameFactory::intToBitString)
                .toList()
                .iterator();

        return true;
    }

    /**
     * Converts integers to uppercase i and lowercase L sequence strings.
     *
     * @param integer integer to be converted.
     * @return x - long sequence of uppercase i and lowercase L, where a positive integer x satisfies
     * 2^x >= {@link NameFactory#maxObfuscatedNames}.
     */
    private static String intToBitString(int integer) {
        return String.format("%" + (int) Math.ceil(Math.log(maxObfuscatedNames) / Math.log(2)) + "s",
                        Integer.toBinaryString(integer)
                                .replace('0', 'I')
                                .replace('1', 'l'))
                .replace(' ', 'I');
    }

    /**
     * Determines the interaction between nameMap and oldName.
     *
     * @param oldName name to be obfuscated.
     * @return mapped obfuscated name if not a utility name.
     */
    public static String obfuscateName(String oldName) {
        if (oldName == null || Objects.equals(oldName, "this") || oldName.contains("this$")|| Objects.equals(oldName, "<init>"))
            return oldName;

        if (nameMap.containsKey(oldName)) return nameMap.get(oldName);

        if (!obfuscatedNames.hasNext()) {
            logger.error("Number of names to obfuscate exceeds " + maxObfuscatedNames +
                    ". Use -max argument to provide more names.");
            throw new IndexOutOfBoundsException("Amount of provided names exceeds the argument given in -max."); // https://docs.oracle.com/javase/tutorial/essential/exceptions/runtime.html
        }

        String obfuscatedName = obfuscatedNames.next();
        nameMap.put(oldName, obfuscatedName);
        return obfuscatedName;
    }

    /**
     * Obfuscates a class using class visitor.
     * Renames methods and obfuscates inner classes using Tree API of the ASM library.
     * Inner classes are turned into public classes with public methods.
     *
     * @param inputClass the full name of the class to be obfuscated.
     *                   For example: for a class named "MyClass" in "me.domain.project" package
     *                   the full name will be <i>me/domain/project/MyClass</i>.
     */
    public static void obfuscateClass(String inputClass) {
        try {
            ClassReader classReader = new ClassReader(inputClass); // Can only read from the working directory
            ClassNode classNode = new ClassNode();
            ClassAdapter adapter = new ClassAdapter(classNode);

            classReader.accept(adapter, 0);

            // Rename methods with Tree API
            for (MethodNode method : classNode.methods)
                if (isSuperMethod(method, classNode))
                    nameMap.put(method.name, method.name);
                else
                    method.name = filterSynthetic(method.name);

            // Remove source file information
            removeMetaData(classNode);

            // Write to output path
            writeClassToFile(classNode);

            // Obfuscate inner classes
            if (classNode.nestMembers != null)
                for (String memberName : classNode.nestMembers)
                    if (memberName.startsWith(adapter.getPackage())) obfuscateClass(memberName);

        } catch (IOException e) {
            logger.error(e + ": " + inputClass); // ClassReader() or .close()
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Writes to a new file without passing ClassReader to ClassWriter.
     * This ensures that constant pool will be written fresh, not copied from the read class.
     *
     * @param classNode the class to be written to file.
     * @throws IOException for any exception occurred while writing the class to file.
     */
    // Separated from obfuscateClass if creating synthetic classes is implemented in future
    private static void writeClassToFile(ClassNode classNode) throws IOException {
        ClassWriter classWriter = new ClassWriter(ClassWriter.COMPUTE_FRAMES);

        classNode.accept(classWriter);

        FileOutputStream fos = new FileOutputStream(outputPath + classNode.name + ".class"); // FileNotFoundException is handled in initialization()
        fos.write(classWriter.toByteArray());
        fos.close();
    }

    /**
     * Determines if a method is inherited.
     *
     * @param method method to be checked for inheritance.
     * @param node   class containing the method.
     * @return true if the same method is found in a super class, or super class' super class, and so on.
     * False otherwise.
     */
    private static boolean isSuperMethod(MethodNode method, ClassNode node) {
        if (node.superName == null)
            return false;
        boolean exists = false;
        ClassNode cn;
        try {
            ClassReader cr = new ClassReader(node.superName);
            cn = new ClassNode();
            cr.accept(cn, 0);
        } catch (IOException e) {
            logger.error("Couldn't read super class of " + node.name); // May or may not matter
            return false;                                              // Obfuscation can still be successful
        }
        for (MethodNode superMethod : cn.methods) {
            boolean sameSignature = Objects.equals(superMethod.signature, method.signature);
            if (superMethod.name.equals(method.name) && sameSignature) {
                exists = true;
                break;
            }
        }
        return exists || isSuperMethod(method, cn);
    }


    /**
     * Removes metadata from the source file.
     * This is not necessary for the obfuscator to work,
     * but this is an easy way to further hinder reverse engineering.
     *
     * @param classNode class from which metadata will be removed.
     */
    private static void removeMetaData(ClassNode classNode) {
        classNode.sourceFile = null;
        classNode.signature = null;             // ?
        classNode.visibleAnnotations = null;    // ?
        classNode.invisibleAnnotations = null; //?
        classNode.attrs = null;                 // ?

        for (MethodNode method : classNode.methods) {
            method.signature = null;        //?
            method.visibleAnnotations = null; //?
            method.invisibleAnnotations = null; // ?
            method.attrs = null; // ?
        }
    }

    /**
     * Uses regex to obfuscate the binary name within synthetic methods.
     *
     * @param syntheticMethodName method to be filtered.
     * @return an obfuscated method.
     */
    private static String filterSynthetic(String syntheticMethodName) {
        if (List.of("<init>", "<clinit>", "main").contains(syntheticMethodName)) return syntheticMethodName;

        if (!syntheticMethodName.contains("$")) return obfuscateName(syntheticMethodName);

        Matcher m = (Pattern.compile("\\$(.*?)\\$")).matcher(syntheticMethodName); // non-greedy regex

        while (m.find()) {
            String methodName = m.group(1);
            if (methodName.contains(".") || Objects.equals(methodName,"")) // account for anonymous and local classes
                continue;
            syntheticMethodName = syntheticMethodName.replace(methodName, obfuscateName(methodName));
        }

        return syntheticMethodName;
    }

    /**
     * @return the current mapping of the obfuscator.
     */
    public static Map<String, String> getNameMap() {
        return nameMap;
    }
}
