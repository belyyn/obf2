package obfuscator;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.UncheckedIOException;

/**
 * Class containing the main thread. This class is responsible for managing the exceptions.
 */
public class Manager {
    /**
     * Logger for this class.
     */
    private static final Logger logger = LogManager.getLogger();

    /**
     * Main thread of the program. Contains exception handling for each part of the code for user communication.
     *
     * @param args arguments of the execution.
     */
    public static void main(String[] args) {
        CommandLineHandler argumentHandler = new CommandLineHandler();

        if (!argumentHandler.process(args)) return;

        try {
            if (!NameFactory.initialize(argumentHandler.getOutputPath(), argumentHandler.getMaxObfuscatedNames()))
                return;
        } catch (NumberFormatException formatException) {
            logger.debug(formatException + ": enter a valid number.");
            return;
        }

        try {
            argumentHandler.getInputClasses().forEach(NameFactory::obfuscateClass);
        } catch (UncheckedIOException ue) {
            return;
        } catch (IndexOutOfBoundsException ie) {
            logger.debug(ie);
            return;
        }

        logger.info("Obfuscation completed successfully!\n" + NameFactory.getNameMap());
    }
}

