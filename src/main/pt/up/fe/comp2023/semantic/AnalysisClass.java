package pt.up.fe.comp2023.semantic;

import pt.up.fe.comp.jmm.analysis.JmmAnalysis;
import pt.up.fe.comp.jmm.analysis.JmmSemanticsResult;
import pt.up.fe.comp.jmm.parser.JmmParserResult;

import java.util.Collections;

public class AnalysisClass implements JmmAnalysis {

    @Override
    public JmmSemanticsResult semanticAnalysis(JmmParserResult parserResult) {
        ASymbolTable symbolTable = new ASymbolTable();

        Visitor visitorsymbolTable = new Visitor(symbolTable);

        visitorsymbolTable.visit(parserResult.getRootNode(),null);

        visitorsymbolTable.print();

        SemanticVisitor semanticVisitor = new SemanticVisitor(symbolTable);

        semanticVisitor.visit(parserResult.getRootNode(), "");
        if (semanticVisitor.reports.size() > 0)
            return new JmmSemanticsResult(parserResult, symbolTable, semanticVisitor.reports);
        else
            return new JmmSemanticsResult(parserResult, symbolTable, Collections.emptyList());
    }
}


