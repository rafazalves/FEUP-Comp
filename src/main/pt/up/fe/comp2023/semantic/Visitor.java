package pt.up.fe.comp2023.semantic;

import pt.up.fe.comp.jmm.analysis.table.Symbol;
import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.AJmmVisitor;
import pt.up.fe.comp.jmm.ast.JmmNode;
import java.util.ArrayList;
import java.util.stream.Collectors;

public class Visitor extends AJmmVisitor<Void, Void> {
    private ASymbolTable symbolTable;
    public Visitor(ASymbolTable symbolTable) {
        this.symbolTable = symbolTable;
    }
    @Override
    protected void buildVisitor() {
        addVisit("Program", this::visitProgram);
        addVisit("ImportDeclaration", this::visitImport);
        addVisit("ClassDeclaration", this::visitClassDec);
        addVisit("MainMethod", this::visitMainMethod);
        addVisit("InstanceMethod", this::visitInstanceMethod);
        addVisit("VarDeclaration", this::visitVarDec);
        this.setDefaultVisit(this::visitDefault);
    }

    private Void visitProgram(JmmNode jmmNode, Void unused) {
        visitAllChildren(jmmNode, unused);
        return null;
    }

    private Void visitImport(JmmNode jmmNode, Void unused) {
        var children = jmmNode.getChildren();
        var path = children.stream().map(child -> child.get("value")).collect(Collectors.joining("."));
        symbolTable.getImports().add(path);

        return null;
    }
    private Void visitClassDec(JmmNode jmmNode, Void unused) {
        symbolTable.className = jmmNode.get("className");
        if(jmmNode.hasAttribute("parent")){
            symbolTable.superClass = jmmNode.get("parent");
        }
        else {
            symbolTable.superClass = null;
        }
        visitAllChildren(jmmNode, unused);
        return null;
    }
    private Void visitVarDec(JmmNode jmmNode, Void unused) {
        var namesymb = jmmNode.get("var");
        var typesymb = jmmNode.getJmmChild(0).get("value");
        var isArray = jmmNode.getJmmChild(0).getKind().equals("Array");

        switch (jmmNode.getJmmParent().getKind()) {
            case "ClassDeclaration" ->
                    symbolTable.getFields().add(new Symbol(new Type(typesymb, isArray), namesymb));
            case "InstanceMethod", "MainMethod" -> {
                var methodName = jmmNode.getJmmParent().get("methodName");
                symbolTable.getLocalVariables(methodName).add(new Symbol(new Type(typesymb, isArray), namesymb));
            }
        }
        return null;
    }

    private Void visitMainMethod(JmmNode jmmNode, Void unused) {
        symbolTable.getMethods().add("main");
        symbolTable.typeret.put("main", new Type("void", false));
        symbolTable.methparams.put("main", new ArrayList<>());
        symbolTable.methparams.get("main").add(new Symbol(new Type("String", true), "args"));
        symbolTable.methvars.putIfAbsent("main", new ArrayList<>());

        visitAllChildren(jmmNode, unused);
        return null;
    }

    private Void visitInstanceMethod(JmmNode jmmNode, Void unused) {
        var methodName = jmmNode.get("methodName");
        symbolTable.getMethods().add(methodName);

        var returnType = jmmNode.getJmmChild(0).getJmmChild(0).get("value");
        var returnTypeIsArray = jmmNode.getJmmChild(0).getJmmChild(0).getKind().equals("Array");
        symbolTable.typeret.put(methodName, new Type(returnType, returnTypeIsArray));

        symbolTable.methparams.put(methodName, new ArrayList<>());
        for(int i = 1; i < jmmNode.getNumChildren(); i++) {
            if (!jmmNode.getJmmChild(i).getKind().equals("Param"))
                continue;
            var methodParam = jmmNode.getJmmChild(i);
            var paramName = methodParam.get("name");
            var paramType = methodParam.getJmmChild(0).get("value");
            var paramTypeIsArray = methodParam.getJmmChild(0).getKind().equals("Array");
            symbolTable.methparams.get(methodName).add(new Symbol(new Type(paramType, paramTypeIsArray), paramName));
        }
        symbolTable.methvars.putIfAbsent(methodName, new ArrayList<>());

        visitAllChildren(jmmNode, unused);
        return null;
    }

    private Void visitDefault(JmmNode jmmNode, Void unused) { return null; }

    public void print() {
        this.symbolTable.printAST();
    }
}
