package pt.up.fe.comp2023.semantic;

import pt.up.fe.comp.jmm.analysis.table.Symbol;
import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.AJmmVisitor;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.jmm.report.ReportType;
import pt.up.fe.comp.jmm.report.Stage;
import java.util.*;

public class SemanticVisitor extends AJmmVisitor<String, Void>{

    ASymbolTable symbolTable;
    List<Report> reports;

    SemanticVisitor(ASymbolTable symbolTable){
        this.symbolTable = symbolTable;
        this.reports = new ArrayList<>();
    }

    @Override
    protected void buildVisitor() {
        addVisit("MainMethod", this::visitMethod);
        addVisit("InstanceMethod", this::visitMethod);
        addVisit("MethodCall", this::visitMethodCall);
        addVisit("Conditional", this::visitCondition);
        addVisit("WhileLoop", this::visitCondition);
        addVisit("Assignment", this::visitAssignment);
        addVisit("ArrayAssignment", this::visitArrayAssignment);
        addVisit("ArrayExpr", this::visitArrayExpr);
        addVisit("ArrayInit", this::visitArrayInit);
        addVisit("BinaryOp", this::visitBinaryOp);
        addVisit("UnaryOp", this::visitUnaryOp);
        addVisit("PrioExpr", this::visitPrioExpr);
        addVisit("Constructor", this::visitConstructor);
        addVisit("Length", this::visitLength);
        addVisit("Integer", this::visitLiteral);
        addVisit("BoolExpr", this::visitLiteral);
        addVisit("Reference", this::visitLiteral);
        addVisit("Identifier", this::visitLiteral);
        this.setDefaultVisit(this::visitDefault);
    }

    //Functions for all visitors
    private Void visitMethod(JmmNode jmmNode, String reach) {
        visitAllChildren(jmmNode, jmmNode.get("methodName"));
        if(!jmmNode.get("methodName").equals("main")) {
            JmmNode returnExpr = jmmNode.getJmmChild(jmmNode.getNumChildren() - 1);

            if (!(symbolTable.getReturnType(jmmNode.get("methodName")).getName().equals(returnExpr.get("type")) || returnExpr.get("type").equals("inferred"))) {
                String msg = "Incompatible return type. Expected '" + symbolTable.getReturnType(jmmNode.get("methodName")).getName() + "', instead got '" + returnExpr.get("type") + "'.";
                reports.add(new Report(ReportType.ERROR, Stage.SEMANTIC, -1, msg));
            }
        }
        return null;
    }

    private Void visitMethodCall(JmmNode jmmNode, String reach) {
        JmmNode obj = jmmNode.getJmmChild(0);
        visit(obj, reach);

        JmmNode method = jmmNode.getJmmChild(1);
        visit(method, reach);

        if(obj.getKind().equals("Reference") && method.get("methodName").equals("main")) {
            String msg = "Static method '" + method.get("methodName") + "' cannot be invoked by an instance.";
            reports.add(new Report(ReportType.ERROR, Stage.SEMANTIC, -1, msg));
            jmmNode.put("type", "null");
            return null;
        }

        if(!symbolTable.getMethods().contains(method.get("methodName"))) {
            List<String> importedClasses = getImportedClassNames();
            if (!importedClasses.contains(obj.get("type")) && symbolTable.getSuper() == null) {
                String msg = "Method '" + method.get("methodName") + "' does not exist.";
                reports.add(new Report(ReportType.ERROR, Stage.SEMANTIC, -1, msg));
                jmmNode.put("type", "null");
                return null;
            }

            jmmNode.put("type", "inferred");
            return null;
        }

        if (method.getNumChildren() != symbolTable.getParameters(method.get("methodName")).size()) {
            String msg = "Wrong number of arguments passed to method '" + method.get("methodName") +
                    "'. Expected " + symbolTable.getParameters(method.get("methodName")).size() +
                    ", instead got " + method.getNumChildren() + ".";
            reports.add(new Report(ReportType.ERROR, Stage.SEMANTIC, -1, msg));
            jmmNode.put("type", "null");
            return null;
        }

        String superClass = symbolTable.getSuper() != null ? symbolTable.getSuper() : "Object";
        for(int i = 0; i < method.getNumChildren(); i++) {
            JmmNode arg = method.getJmmChild(i);
            Type paramType = symbolTable.getParameters(method.get("methodName")).get(i).getType();

            visit(arg, reach);

            if(arg.get("type").equals("inferred"))
                continue;

            if(arg.get("type").equals(symbolTable.getClassName()) && paramType.getName().equals(superClass))
                continue;

            if(arg.get("type").equals(paramType.getName())) {
                if(!paramType.isArray() && (!arg.hasAttribute("isArray") || !Boolean.parseBoolean(arg.get("isArray"))))
                    continue;
                else if (paramType.isArray() && arg.hasAttribute("isArray") && Boolean.parseBoolean(arg.get("isArray")))
                    continue;
            }

            String msg = "Incompatible argument " + i+1 + " for method '" + method.get("methodName") + "'. Expected '" + paramType + "', instead got '" + arg.get("type") + "'.";
            reports.add(new Report(ReportType.ERROR, Stage.SEMANTIC, -1, msg));
            break;
        }

        jmmNode.put("type", symbolTable.getReturnType(method.get("methodName")).getName());
        return null;
    }

    private Void visitCondition(JmmNode jmmNode, String reach) {
        JmmNode condition = jmmNode.getJmmChild(0);
        visit(condition, reach);

        if(!(condition.get("type").equals("boolean") || condition.get("type").equals("inferred"))) {
            String msg = (jmmNode.getKind().equals("WhileLoop") ? "'while'" : "'if'") + " condition must be of 'boolean' type, instead got '" + condition.get("type") + "'.";
            reports.add(new Report(ReportType.ERROR, Stage.SEMANTIC, -1, msg));
            return null;
        }

        for(int i = 1; i < jmmNode.getNumChildren(); i++)
            visit(jmmNode.getJmmChild(i), reach);
        return null;
    }

    private Void visitAssignment(JmmNode jmmNode, String reach) {
        Symbol symbol = symbolSearch(jmmNode.get("var"), reach);
        if (symbol == null) {
            String msg = "Invalid assignment, identifier '" + jmmNode.get("var") + "' doesn't exist or is out of reach.";
            reports.add(new Report(ReportType.ERROR, Stage.SEMANTIC, -1, msg));
            return null;
        }

        JmmNode expr = jmmNode.getJmmChild(0);
        visit(expr, reach);

        if (doesClassExist(symbol.getType().getName()) || doesClassExist(expr.get("type"))) {
            String superClass = symbolTable.getSuper() != null ? symbolTable.getSuper() : "Object";
            if (!expr.get("type").equals(symbol.getType().getName())) {
                if (!(symbol.getType().getName().equals(superClass) && expr.get("type").equals(symbolTable.getClassName()))) {
                    List<String> importedClasses = getImportedClassNames();
                    if (!(importedClasses.contains(symbol.getType().getName()) && importedClasses.contains(expr.get("type")))) {
                        String msg = "Invalid assignment, class '" + symbol.getType().getName() + "' isn't compatible with class '" + expr.get("type") + "'.";
                        reports.add(new Report(ReportType.ERROR, Stage.SEMANTIC, -1, msg));
                    }
                }
            }
        } else {
            if (!(expr.get("type").equals(symbol.getType().getName()) || expr.get("type").equals("inferred"))) {
                String msg = "Invalid assignment, expression type doesn't match variable '" + symbol.getName() + "' type. Expected '" + symbol.getType().getName() + "', instead got '" + expr.get("type") + "'.";
                reports.add(new Report(ReportType.ERROR, Stage.SEMANTIC, -1, msg));
            }

            if (expr.getKind().equals("Identifier") && !String.valueOf(symbol.getType().isArray()).equals(expr.get("isArray"))) {
                String msg = "Invalid assignment, assigning array to non-array variable";
                reports.add(new Report(ReportType.ERROR, Stage.SEMANTIC, -1, msg));
            }
        }

        if (!areTypesCompatible(symbol.getType(), expr.get("type"))) {
            String msg = "Invalid assignment, incompatible types. Cannot assign '" + expr.get("type") + "' to '" + symbol.getType().getName() + "'.";
            reports.add(new Report(ReportType.ERROR, Stage.SEMANTIC, -1, msg));
        }

        jmmNode.put("type", symbol.getType().getName());
        return null;
    }

    private boolean areTypesCompatible(Type type1, String type2) {
        if (type1.getName().equals(type2)) {
            return true;
        }

        String superClass = symbolTable.getSuper() != null ? symbolTable.getSuper() : "Object";
        if (type1.getName().equals(superClass) && type2.equals(symbolTable.getClassName())) {
            return true;
        }

        List<String> importedClasses = getImportedClassNames();
        if (importedClasses.contains(type1.getName()) && importedClasses.contains(type2)) {
            return true;
        }

        return false;
    }


    private Void visitArrayAssignment(JmmNode jmmNode, String reach) {
        Symbol symbol = symbolSearch(jmmNode.get("var"), reach);
        if(symbol == null) {
            String msg = "Invalid assignment, identifier '" + jmmNode.get("var") + "' doesn't exist or is out of reach.";
            reports.add(new Report(ReportType.ERROR, Stage.SEMANTIC, -1, msg));
            jmmNode.put("type", "null");
            return null;
        }

        if(!symbol.getType().isArray()) {
            String msg = "Invalid assignment, variable '" + jmmNode.get("var") +"' is not an array, it cannot be indexed.";
            reports.add(new Report(ReportType.ERROR, Stage.SEMANTIC, -1, msg));
        }

        JmmNode index = jmmNode.getJmmChild(0);
        visit(index, reach);
        if(!index.get("type").equals("int")) {
            String msg = "Invalid assignment. Index must be an 'int', instead got '" + index.get("type") + "'.";
            reports.add(new Report(ReportType.ERROR, Stage.SEMANTIC, -1, msg));
        }

        JmmNode expr = jmmNode.getJmmChild(1);
        visit(expr, reach);
        if(!expr.get("type").equals(symbol.getType().getName())) {
            String msg = "Invalid assignment, expression type doesn't match variable '" + symbol.getName() + "' type. Expected '" + symbol.getType().getName() + "', instead got '" + expr.get("type") + "'.";
            reports.add(new Report(ReportType.ERROR, Stage.SEMANTIC, -1, msg));
        }

        if(expr.getKind().equals("Identifier") && !String.valueOf(symbol.getType().isArray()).equals(expr.get("isArray"))) {
            String msg = "Invalid assignment, assigning array to non-array variable";
            reports.add(new Report(ReportType.ERROR, Stage.SEMANTIC, -1, msg));
        }

        jmmNode.put("type", symbol.getType().getName());
        return null;
    }

    private Void visitArrayExpr(JmmNode jmmNode, String reach) {
        JmmNode id = jmmNode.getJmmChild(0);
        JmmNode index = jmmNode.getJmmChild(1);

        visit(id, reach);
        if(id.getKind().equals("Identifier") && !id.get("isArray").equals("true")) {
            String msg = "Invalid array access, variable '" + id.get("value") + "' is not an array.";
            reports.add(new Report(ReportType.ERROR, Stage.SEMANTIC, -1, msg));
            jmmNode.put("type", "null");
            return null;
        }

        visit(index, reach);
        if(!(index.get("type").equals("int") || index.get("type").equals("inferred"))) {
            String msg = "Invalid index. Index must be an 'int', instead got '" + index.get("type") + "'.";
            reports.add(new Report(ReportType.ERROR, Stage.SEMANTIC, -1, msg));
            jmmNode.put("type", "null");
            return null;
        }

        jmmNode.put("type", id.get("type"));
        return null;
    }

    private Void visitArrayInit(JmmNode jmmNode, String reach) {
        JmmNode size = jmmNode.getJmmChild(0);
        visit(size, reach);

        if(!(size.get("type").equals("int") || size.get("type").equals("inferred"))) {
            String msg = "Invalid array initialization. Array size must be of type 'int', instead got '" + size.get("type") + "'.";
            reports.add(new Report(ReportType.ERROR, Stage.SEMANTIC, -1, msg));
            jmmNode.put("type", "null");
            jmmNode.put("isArray", "null");
            return null;
        }

        jmmNode.put("type", "int");
        jmmNode.put("isArray", "true");
        return null;
    }

    private Void visitBinaryOp(JmmNode jmmNode, String reach) {
        switch (jmmNode.get("op")) {
            case "+", "-", "*", "/" -> {
                for (JmmNode child : jmmNode.getChildren()) {
                    visit(child, reach);
                    if (!(child.get("type").equals("int") || child.get("type").equals("inferred"))) {
                        String msg = "Invalid operand type in '" + jmmNode.get("op") + "' operation. Expected 'int', instead got '" + child.get("type") + "'.";
                        reports.add(new Report(ReportType.ERROR, Stage.SEMANTIC, -1, msg));
                        jmmNode.put("type", "null");
                        return null;
                    }
                    if (child.getKind().equals("Identifier") && child.get("isArray").equals("true")) {
                        String msg = "'" + jmmNode.get("op") + "' operation doesn't support arrays.";
                        reports.add(new Report(ReportType.ERROR, Stage.SEMANTIC, -1, msg));
                        jmmNode.put("type", "null");
                        return null;
                    }
                }
                jmmNode.put("type", "int");
            }
            case ">", "<" -> {
                for (JmmNode child : jmmNode.getChildren()) {
                    visit(child, reach);
                    if (!(child.get("type").equals("int") || child.get("type").equals("inferred"))) {
                        String msg = "Invalid operand type in '" + jmmNode.get("op") + "' operation. Expected 'int', instead got '" + child.get("type") + "'.";
                        reports.add(new Report(ReportType.ERROR, Stage.SEMANTIC, -1, msg));
                        jmmNode.put("type", "null");
                        return null;
                    }
                    if (child.getKind().equals("Identifier") && child.get("isArray").equals("true")) {
                        String msg = "'" + jmmNode.get("op") + "' operation doesn't support arrays.";
                        reports.add(new Report(ReportType.ERROR, Stage.SEMANTIC, -1, msg));
                        jmmNode.put("type", "null");
                        return null;
                    }
                }
                jmmNode.put("type", "boolean");
            }
            case "&&", "||" -> {
                for (JmmNode child : jmmNode.getChildren()) {
                    visit(child, reach);
                    if (!(child.get("type").equals("boolean") || child.get("type").equals("inferred"))) {
                        String msg = "Invalid operand type in '" + jmmNode.get("op") + "' operation. Expected 'boolean', instead got '" + child.get("type") + "'.";
                        reports.add(new Report(ReportType.ERROR, Stage.SEMANTIC, -1, msg));
                        jmmNode.put("type", "null");
                        return null;
                    }
                    if (child.getKind().equals("Identifier") && child.get("isArray").equals("true")) {
                        String msg = "'" + jmmNode.get("op") + "' operation doesn't support arrays.";
                        reports.add(new Report(ReportType.ERROR, Stage.SEMANTIC, -1, msg));
                        jmmNode.put("type", "null");
                        return null;
                    }
                }
                jmmNode.put("type", "boolean");
            }
        }
        return null;
    }

    private Void visitUnaryOp(JmmNode jmmNode, String reach) {
        JmmNode operand = jmmNode.getJmmChild(0);
        visit(operand, reach);
        if (!(operand.get("type").equals("boolean") || operand.get("type").equals("inferred"))) {
            String msg = "Invalid operand type in '" + jmmNode.get("op") + "' operation. Expected 'boolean', instead got '" + operand.get("type") + "'.";
            reports.add(new Report(ReportType.ERROR, Stage.SEMANTIC, -1, msg));
            jmmNode.put("type", "null");
            return null;
        }

        jmmNode.put("type", "boolean");
        return null;
    }

    private Void visitPrioExpr(JmmNode jmmNode, String reach) {
        visitAllChildren(jmmNode, reach);
        jmmNode.put("type", jmmNode.getJmmChild(0).get("type"));
        return null;
    }

    private Void visitConstructor(JmmNode jmmNode, String reach) {
        String className = jmmNode.get("className");

        if(!doesClassExist(className)) {
            String msg = "Cannot resolve class '" + className + "'.";
            reports.add(new Report(ReportType.ERROR, Stage.SEMANTIC, -1, msg));
        }

        jmmNode.put("type", className);
        return null;
    }

    private Void visitLength(JmmNode jmmNode, String reach) {
        JmmNode expr = jmmNode.getJmmChild(0);
        visit(expr, reach);

        if(!(expr.hasAttribute("isArray") && expr.get("isArray").equals("true")))
            reports.add(new Report(ReportType.ERROR, Stage.SEMANTIC, -1,"Cannot get length of an non-array expression."));

        jmmNode.put("type", "int");
        return null;
    }

    private Void visitLiteral(JmmNode jmmNode, String reach) {
        switch (jmmNode.getKind()) {
            case "Integer" -> {
                jmmNode.put("type", "int");
            }
            case "BoolExpr" -> {
                jmmNode.put("type", "boolean");
            }
            case "Reference" -> {
                if(reach.equals("main")) {
                    reports.add(new Report(ReportType.ERROR, Stage.SEMANTIC, -1, "Cannot use 'this' in a static method."));
                    jmmNode.put("type", "null");
                    return null;
                }
                jmmNode.put("type", symbolTable.getClassName());
            }
            case "Identifier" -> {
                Symbol symbol = symbolSearch(jmmNode.get("value"), reach);
                String className = doesClassExist(jmmNode.get("value")) ? jmmNode.get("value") : null;
                if (symbol == null && className == null) {
                    String msg = "Identifier '" + jmmNode.get("value") + "' does not correspond to any symbol or class.";
                    reports.add(new Report(ReportType.ERROR, Stage.SEMANTIC, -1, msg));
                    jmmNode.put("type", "null");
                    jmmNode.put("isArray", "null");
                    return null;
                }
                jmmNode.put("type", (symbol != null) ? symbol.getType().getName() : className);
                jmmNode.put("isArray", (symbol != null) ? String.valueOf(symbol.getType().isArray()) : "false");
            }
        }
        return null;
    }

    private Void visitDefault(JmmNode jmmNode, String reach) {
        visitAllChildren(jmmNode, reach);
        return null;
    }

    //Aux Functions for Visitors
    private Symbol symbolSearch(String var_name, String reach) {
        if (!reach.equals("")) {
            for (Symbol symbol : symbolTable.getLocalVariables(reach)) {
                if (symbol.getName().equals(var_name))
                    return symbol;
            }

            for (Symbol symbol : symbolTable.getParameters(reach)) {
                if (symbol.getName().equals(var_name))
                    return symbol;
            }
        }

        if (reach.equals("main"))
            return null;

        for (Symbol symbol : symbolTable.getFields()) {
            if (symbol.getName().equals(var_name))
                return symbol;
        }

        return null;
    }

    private boolean doesClassExist(String className) {
        if(className.equals(symbolTable.getClassName()))
            return true;

        if(symbolTable.getSuper() != null) {
            String[] splits = symbolTable.getSuper().split("[.]");
            if (splits[splits.length - 1].equals(className))
                return true;
        }

        for(String importedClass : symbolTable.getImports()) {
            String[] splits = importedClass.split("[.]");
            if (splits[splits.length-1].equals(className))
                return true;
        }
        return false;
    }

    private List<String> getImportedClassNames() {
        List<String> importedClasses = new ArrayList<>();
        for (String importedClass : symbolTable.getImports()) {
            String[] splits = importedClass.split("[.]");
            importedClasses.add(splits[splits.length - 1]);
        }
        return importedClasses;
    }
}
