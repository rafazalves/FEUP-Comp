package pt.up.fe.comp2023.ollir;

import pt.up.fe.comp.jmm.ollir.JmmOptimization;
import pt.up.fe.comp.jmm.ollir.OllirResult;
import pt.up.fe.comp.jmm.analysis.JmmSemanticsResult;
import pt.up.fe.comp2023.semantic.ASymbolTable;

public class JmmOptimizer implements JmmOptimization{
    @Override
    public OllirResult toOllir(JmmSemanticsResult jmmSemanticsResult) {
        final StringBuilder ollirCode = new StringBuilder();
        OllirGenerator ollirGenerator = new OllirGenerator(ollirCode, (ASymbolTable) jmmSemanticsResult.getSymbolTable(), 4);
        ollirGenerator.visit(jmmSemanticsResult.getRootNode());
        return new OllirResult(jmmSemanticsResult, ollirGenerator.ollirCode.toString(), jmmSemanticsResult.getReports());
    }
}
