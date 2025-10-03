package obfuscator;

import org.apache.commons.cli.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * Class responsible for handling arguments of the program execution.
 */
public class CommandLineHandler {
    /**
     * The parser of this execution's arguments.
     */
    private CommandLine cmd;
    /**
     * Logger for this class.
     */
    private static final Logger logger = LogManager.getLogger();

    /**
     * Refer to {@link CommandLineHandler#process(String[])} for initialization of the command-line.
     */
    public CommandLineHandler() {
    }

    /**
     * Processes this execution's arguments with a command-line parser.
     *
     * @param args arguments of this execution. As passed in main of Manager.
     * @return false if an error has occurred. True otherwise.
     */
    public boolean process(String[] args) {
        Options options = getOptions();

        try {
            this.cmd = (new DefaultParser()).parse(options, args);
        } catch (ParseException e) {
            logger.error(e.getMessage());
            (new HelpFormatter()).printHelp("input classes in order of least dependency on classes within the package " +
                    "and most inheritance from classes outside the package", options);
            return false;
        }

        return true;
    }

    /**
     * @return the collection of options with set parameters used for the parser.
     */
    private Options getOptions() {
        Options options = new Options();

        setOption(new Option("i", "input", true,
                        "input file paths"),
                options, true, -2); // UNLIMITED_VALUES = -2

        setOption(new Option("o", "output", true,
                        "output path"),
                options, false, 1);

        setOption(new Option("max", "maxObfuscatedNames", true,
                        "number of names that can be obfuscated in one program call\n bigger value -> longer names"),
                options, false, 1);


        return options;
    }

    /**
     * Adds an option with set parameters.
     *
     * @param option      an option to be added.
     * @param options     the collection to which this option will be added.
     * @param setRequired whether this option is mandatory.
     * @param setArgs     the number of argument values this option can take. The value -2 represents unlimited values.
     */
    private void setOption(Option option, Options options, boolean setRequired, int setArgs) {
        option.setRequired(setRequired);
        option.setArgs(setArgs);
        options.addOption(option);
    }

    // Getters

    /**
     * Filters out same inputs.
     *
     * @return an ordered set of classes passed in the -input argument.
     */
    public HashSet<String> getInputClasses() {
        List<String> temp = new ArrayList<>(List.of(cmd.getOptionValues("input")));
        // UNIX / , Windows \
        // temp.replaceAll(s -> s.replace(System.getProperty("user.dir") + System.getProperty("file.separator"), "")); // ClassReader uses windows separator(?) not sure about compatibility with unix systems
        return new LinkedHashSet<>(temp); // Saving order of insertion
    }

    /**
     * @return the output path for obfuscated files passed in the -output argument. If left unspecified, uses the working directory.
     */
    public String getOutputPath() {
        return cmd.getOptionValue("output") != null
                ? cmd.getOptionValue("output")
                : ""; // default value (working directory)
    }

    /**
     * @return the maximum number of names that can be mapped per one execution passed in the -maxObfuscatedNames argument. If left unspecified, uses 12 character long names.
     */
    public Integer getMaxObfuscatedNames() {
            return cmd.getOptionValue("maxObfuscatedNames") != null
                    ? Integer.parseInt(cmd.getOptionValue("maxObfuscatedNames"))
                    : 4096; // default value (12 character long strings)
    }

}
