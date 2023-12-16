package pt.up.fe.comp2023;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import pt.up.fe.comp.TestUtils;
import pt.up.fe.comp.jmm.analysis.JmmAnalysis;
import pt.up.fe.comp.jmm.analysis.JmmSemanticsResult;
import pt.up.fe.comp.jmm.jasmin.JasminResult;
import pt.up.fe.comp.jmm.ollir.JmmOptimization;
import pt.up.fe.comp.jmm.ollir.OllirResult;
import pt.up.fe.comp.jmm.parser.JmmParserResult;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp2023.jasmin.JasminGenerator;
import pt.up.fe.comp2023.ollir.JmmOptimizer;
import pt.up.fe.comp2023.semantic.AnalysisClass;
import pt.up.fe.specs.util.SpecsIo;
import pt.up.fe.specs.util.SpecsLogs;
import pt.up.fe.specs.util.SpecsSystem;

public class Launcher {

    public static void main(String[] args) throws IOException {
        // Setups console logging and other things
        SpecsSystem.programStandardInit();

        // Parse arguments as a map with predefined options
        var config = parseArgs(args);

        // Get input file
        File inputFile = new File(config.get("inputFile"));

        // Check if file exists
        if (!inputFile.isFile()) {
            throw new RuntimeException("Expected a path to an existing input file, got '" + inputFile + "'.");
        }

        // Read contents of input file
        String code = SpecsIo.read(inputFile);

        // Instantiate JmmParser
        SimpleParser parser = new SimpleParser();
        // Parse stage
        JmmParserResult parserResult = parser.parse(code, config);
        // Check if there are parsing errors
        // TestUtils.noErrors(parserResult.getReports());
        for (Report r : parserResult.getReports()) {
            System.out.println(r.toString());
        }

        // Print the AST
        System.out.println(parserResult.getRootNode().toTree());

        // Initiate JmmAnalysis
        AnalysisClass analysis = new AnalysisClass();
        // Analysis Stage
        JmmSemanticsResult semanticsResult = analysis.semanticAnalysis(parserResult);
        for (Report r : semanticsResult.getReports()) {
            System.out.println(r.toString());
        }

        // Print symbol table
        System.out.println(semanticsResult.getSymbolTable().print());

        if (config.get("optimize").equals("true")) {
            semanticsResult = new JmmOptimizer().optimize(semanticsResult);
        }

        // Ollir Stage
        JmmOptimization optimizer = new JmmOptimizer();
        OllirResult optimizationResult = optimizer.toOllir(semanticsResult);
        for (Report r : optimizationResult.getReports()) {
            System.out.println(r.toString());
        }

        // Jasmin Stage
        JasminGenerator jasminGenerator = new JasminGenerator();
        JasminResult jasminResult = jasminGenerator.toJasmin(optimizationResult);
        for (Report r : jasminResult.getReports()) {
            System.out.println(r.toString());
        }

        if (config.get("debug").equals("false")) {
            jasminCode(args[args.length - 1], jasminResult);
        }
    }

    private static Map<String, String> parseArgs(String[] args) {
        SpecsLogs.info("Executing with args: " + Arrays.toString(args));

        // Create config
        Map<String, String> config = new HashMap<>();
        config.put("inputFile", args[0]);
        config.put("optimize", "false");
        config.put("registerAllocation", "-1");
        config.put("debug", "false");

        for(int i = 0; i < args.length; i++) {
            if (args[i].equals("-o")) {
                config.put("optimize", "true");
            }
        }

        return config;
    }

    private static void jasminCode(String inputFilePath, JasminResult jasminResult) throws IOException {
        String fileName = Paths.get(inputFilePath).getFileName().toString();
        String fileNameWOExtension = fileName.split("\\.")[0];
        Files.writeString(Paths.get(fileNameWOExtension + ".j"), jasminResult.getJasminCode());
    }

}
