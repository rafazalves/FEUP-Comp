package pt.up.fe.comp2023.ollir;

import pt.up.fe.comp.jmm.ast.AJmmVisitor;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.analysis.table.Type;

import static pt.up.fe.comp2023.ollir.OllirUtils.*;
import pt.up.fe.comp2023.semantic.ASymbolTable;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class OllirGenerator extends AJmmVisitor<VariableAux, Boolean> {

    public final StringBuilder ollirCode;
    public final ASymbolTable symbolTable;
    public int temporaryVariableCounter = -1;
    public int indentationLevel = 0;
    public final int numberOfSpaces;
    String currentMethodName;
    public final OllirVisitor visitCode;

    public OllirGenerator(StringBuilder ollirCode, ASymbolTable symbolTable, int numberOfSpaces) {
        this.currentMethodName = "";
        this.ollirCode = ollirCode;
        this.symbolTable = symbolTable;
        this.numberOfSpaces = numberOfSpaces;
        this.visitCode = new OllirVisitor(this);

        addVisit("Program", this::visitStart);
        addVisit("ClassDeclaration", this::visitClassDeclaration);
        addVisit("VarDeclaration", this::visitFieldDeclaration);
        addVisit("InstanceMethod", this::visitMethod);
        addVisit("MainMethod", this::visitMethod);
        addVisit("MethodCall", this::visitMethodCall);
        addVisit("Assignment", this::visitAssignment);
        addVisit("BinaryOp", this::visitBinOp);
        addVisit("UnaryOp", this::visitUnaryOp);
        addVisit("ExprStmt", this::visitExpressionStmt);
        addVisit("StmtBlock", this::visitStmtBlock);
        addVisit("Identifier", this::visitIdentifier);
        addVisit("Integer", this::visitIdentifier);
        addVisit("Conditional", this::visitConditional);
        addVisit("WhileLoop", this::visitWhile);
        addVisit("ArrayAssignment", this::visitArrayAssignment);
        setDefaultVisit((node, v) -> null);
    }
    public Boolean visitStart(JmmNode node, VariableAux v) {
        fillImports();
        visitAllChildren(node, v);

        return true;
    }

    public Boolean visitClassDeclaration(JmmNode node, VariableAux v) {
        String className = node.get("className");

        ollirCode.append(className).append(" ");
        if (symbolTable.getSuper() != null) {
            ollirCode.append("extends ").append(symbolTable.getSuper()).append(" ");
        }

        ollirCode.append("{");
        indentationLevel++;

        for (var child : node.getChildren().stream().filter((n) -> n.getKind().equals("varDeclaration")).collect(Collectors.toList())) {
            newLineStart();
            ollirCode.append(".field public ");
            visit(child);
        }

        addVisit("VarDeclaration", (n, d) -> null);
        newLineStart();

        var fields = symbolTable.getFields();
        if (!fields.isEmpty()) {
            newLineStart();
            for (var field : symbolTable.getFields()) {
                ollirCode.append(".field public ");
                if (field.getType().isArray()) {
                    ollirCode.append(field.getName() + ".array." + getOllirType(field.getType().getName()) + ";");
                } else {
                    String fieldNameType = field.getName() + "." + getOllirType(field.getType().getName());
                    ollirCode.append(fieldNameType + ";");
                }

                newLineStart();
            }
        }

        ollirCode.append(".construct ").append(className).append("().V {");
        indentationLevel++;
        newLineStart();
        ollirCode.append(invoke("invokespecial", "this", "<init>", new ArrayList<>(), "V")).append(";");
        indentationLevel--;
        newLineStart();
        ollirCode.append("}");

        for (var child : node.getChildren().stream().filter((n) -> n.getKind().equals("InstanceMethod")).collect(Collectors.toList())) {
            newLineStart();
            ollirCode.append(".method public ");
            visit(child);
        }
        for (var child : node.getChildren().stream().filter((n) -> n.getKind().equals("MainMethod")).collect(Collectors.toList())) {
            newLineStart();
            ollirCode.append(".method public static ");
            visit(child);
        }

        indentationLevel--;
        newLineStart();
        ollirCode.append("}");
        return true;
    }

    public Boolean visitFieldDeclaration(JmmNode node, VariableAux v) {
        String variableName = node.getChildren().get(1).get("name");
        Type type = new Type(node.getChildren().get(0).get("name"), Boolean.parseBoolean(node.getChildren().get(0).get("isArray")));

        ollirCode.append(variableName).append(".").append(getOllirType(type.getName()));
        ollirCode.append(";");

        return true;
    }

    public Boolean visitMethod(JmmNode node, VariableAux v) {
        String methodName = node.get("methodName");

        this.currentMethodName = methodName;
        var returnType = symbolTable.getReturnType(methodName);
        ollirCode.append(methodName).append("(");

        var parameters = symbolTable.getParameters(methodName);
        if (!parameters.isEmpty()) {
            for (var parameter : symbolTable.getParameters(methodName)) {
                ollirCode.append(parameter.getName()).append(".");
                if (parameter.getType().isArray()) {
                    ollirCode.append("array.");
                }
                ollirCode.append(getOllirType(parameter)).append(", ");
            }
            ollirCode.delete(ollirCode.lastIndexOf(","), ollirCode.length());
        }
        ollirCode.append(").");

        if (returnType.isArray()) {
            ollirCode.append("array.");
        }
        ollirCode.append(getOllirType(returnType.getName()));
        ollirCode.append(" {");

        indentationLevel++;
        newLineStart();
        boolean returned = false;
        int currentTemporaryVariableCounter = temporaryVariableCounter;
        int numberChildren = node.getChildren().size();

        for (int i = 0; i < numberChildren; i++) {
            if (!Objects.equals(methodName, "main")) {
                Type returns = symbolTable.getReturnType(methodName);
                String returnName = returns.getName();

                String returnAbv;
                switch (returnName) {
                    case "int":
                        returnAbv = "i32";
                        break;
                    case "boolean":
                        returnAbv = "bool";
                        break;
                    default:
                        returnAbv = returnName;
                }

                if (i < numberChildren - 1) {
                    visit(node.getJmmChild(i));
                }

                int[] theNumbers = getTheNumbers();
                if (i == numberChildren - 1) {
                    var rhsCode = visitCode.visit(node.getJmmChild(i), theNumbers);
                    var code = new StringBuilder();
                    code.append(rhsCode.prefixCode());
                    code.append("ret.");
                    if (returnType.isArray()) {
                        code.append("array.");
                    }
                    code.append(getOllirType(node.getJmmChild(0).getJmmChild(0).get("value")) + " " + rhsCode.value() + ".");
                    if (returnType.isArray()) {
                        code.append("array.");
                    }
                    code.append(getOllirType(node.getJmmChild(0).getJmmChild(0).get("value")) + ";");
                    ollirCode.append(code);
                    newLineStart();
                }
            } else {
                visit(node.getJmmChild(i));
                if (i == numberChildren - 1) {
                    if (node.getJmmChild(i).getKind().equals("Conditional") || node.getJmmChild(i).getKind().equals("WhileLoop")) {
                        ollirCode.append("ret.V;");
                    }
                }
            }
        }
        temporaryVariableCounter = currentTemporaryVariableCounter;

        indentationLevel--;
        newLineStart();
        ollirCode.append("}");

        return true;
    }

    public Boolean visitMethodCall(JmmNode node, VariableAux v) {
        List<JmmNode> children = node.getChildren();

        if (children.size() != 2) {
            return false;
        }
        JmmNode identifierNode = children.get(0);
        JmmNode callNode = children.get(1);

        if (!callNode.getKind().equals("Call")) {
            return false;
        }

        List<JmmNode> callChildren = callNode.getChildren();
        List<String> arguments = new ArrayList<>();
        if (callChildren.size() > 0) {
            for (JmmNode argumentNode : callChildren) {
                OllirCodeResult code = visitCode.visit(argumentNode, getTheNumbers());
                if (code.prefixCode().length() > 0) {
                    ollirCode.append(code.prefixCode());
                    newLineStart();
                }
                String type = visitCode.convertType(argumentNode.get("type"));
                String varName = code.value();
                String varCall = varName + type;
                arguments.add(varCall);
            }
        }
        String type = visitCode.convertType(node.get("type"));

        if (type.equals(".inferred")) {
            type = ".V";
        }
        if (!v.getAssignType().getName().equals("void")) {
            String varName = v.getValueVariable();
            ollirCode.append(varName + type + " :=" + type + " ");
        }
        visit(identifierNode, v);
        visitCall(callNode, arguments);

        ollirCode.append(")" + type + ";");
        newLineStart();

        return true;
    }

    public Boolean visitCall(JmmNode node, List<String> v) {

        ollirCode.append('"' + node.get("methodName") + '"');

        for (String vv : v) {
            ollirCode.append(", " + vv);
        }

        return true;
    }

    public Boolean visitAssignment(JmmNode node, VariableAux v) {

        var lhsCode = node.get("var");
        int[] theNumbers = getTheNumbers();
        var rhsCode = visitCode.visit(node.getJmmChild(0), theNumbers);

        var code = new StringBuilder();
        code.append(rhsCode.prefixCode());

        var returnType = "";
        if (node.getJmmChild(0).getKind().equals("ArrayInit")) {
            returnType = "array.";
        }
        returnType = returnType.concat(getOllirType(node.get("type")));

        code.append(lhsCode + "." + returnType + " :=" + "." + returnType + " " + rhsCode.value() + "." + returnType + ";");
        newLineStart();

        ollirCode.append(code);
        newLineStart();

        return true;
    }

    public Boolean visitBinOp(JmmNode node, VariableAux v) {

        JmmNode child = node.getJmmChild(0);

        if (child.getKind().equals("BinaryOp")) {
            visit(child);
            if (node.getJmmParent().getKind() != "Conditional") {
                ollirCode.append("temp := ");
                ollirCode.append(node.get("op")).append(".bool ");
            }
            visit(node.getJmmChild(1));
        } else {
            visit(child);
            ollirCode.append(" ");
            ollirCode.append(node.get("op")).append(".bool ");
            child = node.getJmmChild(1);
            visit(child);
        }

        return true;
    }

    public Boolean visitUnaryOp(JmmNode node, VariableAux v) {
        if (node.get("op").equals("not")) {
            VariableAux holder = createVariableTemporary(node);
            visit(node.getJmmChild(0), holder);

            v.setTypeVariable(holder.getTypeVariable());
            newLineStart();

            ollirCode.append(createAssignTemporary(v, "!.bool " + holder.getVarAuxWithType()));

            v.setTypeVariable(holder.getTypeVariable());
        }
        return true;
    }

    public Boolean visitExpressionStmt(JmmNode node, VariableAux v) {
        if (v == null) {
            v = createVariableTemporary(node);
        }

        for (var child : node.getChildren()) {
            visit(child, v);
        }

        return true;
    }

    public Boolean visitStmtBlock(JmmNode node, VariableAux v) {
        for (var child : node.getChildren()) {
            visit(child, v);
        }
        return true;
    }

    public Boolean visitIdentifier(JmmNode node, VariableAux v) {

        String nameVariable = node.get("value");
        String nameReturn = node.get("type");
        String abbreviationReturn = "";

        switch (nameReturn) {
            case "int":
                abbreviationReturn = "i32";
                break;
            case "boolean":
                abbreviationReturn = "bool";
                break;
            default:
                abbreviationReturn = nameReturn;
        }

        JmmNode parent = node.getJmmParent();
        if (parent.getKind().equals("MethodCall")) {
            if (symbolTable.getImports().contains(nameReturn)) {
                ollirCode.append("invokestatic(" + nameVariable + ",");
            } else {
                ollirCode.append("invokevirtual(" + nameVariable + visitCode.convertType(node.get("type")) + ",");
            }
            return true;
        }

        ollirCode.append(nameVariable + ".");
        ollirCode.append(abbreviationReturn);

        return true;
    }

    public Boolean visitConditional(JmmNode node, VariableAux v) {

        //Initial IF
        OllirCodeResult result = visitCode.visit(node.getJmmChild(0), getTheNumbers());
        ollirCode.append(result.prefixCode());

        String type = visitCode.convertType(node.getJmmChild(0).get("type"));
        String name = result.value() + type;
        ollirCode.append("if (" + name);

        //ELSE
        ollirCode.append(") goto Then;");
        indentationLevel++;
        newLineStart();

        visit(node.getJmmChild(2), v);
        ollirCode.append("goto End;");
        indentationLevel--;
        newLineStart();

        //IF
        ollirCode.append("Then:");
        indentationLevel++;
        newLineStart();

        visit(node.getJmmChild(1), v);
        ollirCode.append("goto End;");
        indentationLevel--;
        newLineStart();

        ollirCode.append("End:");
        newLineStart();

        return true;
    }

    public Boolean visitWhile(JmmNode node, VariableAux v) {

        //While
        OllirCodeResult result = visitCode.visit(node.getJmmChild(0), getTheNumbers());
        ollirCode.append(result.prefixCode());

        String type = visitCode.convertType(node.getJmmChild(0).get("type"));
        String name = result.value() + type;

        ollirCode.append("WhileCondition:");
        newLineStart();

        ollirCode.append("if (" + name);

        //Else
        ollirCode.append(") goto WhileBody;");
        indentationLevel++;
        newLineStart();

        ollirCode.append("goto WhileEnd;");
        indentationLevel--;
        newLineStart();

        //If
        ollirCode.append("WhileBody:");
        indentationLevel++;
        visit(node.getJmmChild(1), v);

        ollirCode.append("goto WhileCondition;");
        indentationLevel--;
        newLineStart();

        ollirCode.append("WhileEnd:");
        newLineStart();

        return true;
    }

    public Boolean visitArrayAssignment(JmmNode node, VariableAux v) {
        var indexResult = visitCode.visit(node.getJmmChild(0), getTheNumbers());
        var valueResult = visitCode.visit(node.getJmmChild(1), getTheNumbers());
        ollirCode.append(indexResult.prefixCode());
        ollirCode.append(valueResult.prefixCode());

        String indexType = visitCode.convertType(node.getJmmChild(0).get("type"));
        String indexName = indexResult.value() + indexType;

        String valueType = visitCode.convertType(node.getJmmChild(1).get("type"));
        String valueName = valueResult.value() + valueType;

        String arrayIdentifier = node.get("var");
        String arrayType = visitCode.convertType(node.get("type"));

        ollirCode.append(arrayIdentifier + "[" + indexName + "]" + arrayType + " :=" + valueType + " " + valueName + ";");
        newLineStart();

        return true;
    }

    //Aux functions for visitFunctions
    public VariableAux createVariableTemporary(JmmNode closestNode) {
        temporaryVariableCounter += 1;
        String name = "temp_" + temporaryVariableCounter;

        return new VariableAux(name);
    }

    public void newLineStart() {
        ollirCode.append("\n").append(" ".repeat(numberOfSpaces * indentationLevel));
    }

    public String createAssignTemporary(VariableAux variableAux, String value) {
        String ollirType = getOllirType(variableAux.getTypeVariable().getName());
        if (ollirType.equals("V")) {
            return value + ";";
        }
        return variableAux.getNameVariable() + "." + ollirType + " :=." + ollirType + " " + value + ";";
    }

    public void fillImports() {
        for (var imp : symbolTable.getImports()) {
            ollirCode.append("import ").append(imp).append(";");
            newLineStart();
        }
    }

    public int[] getTheNumbers() {
        int[] theNumbers = new int[2];
        theNumbers[0] = numberOfSpaces;
        theNumbers[1] = indentationLevel;
        return theNumbers;
    }

    //Intellij "needs" this function to execute
    @Override
    protected void buildVisitor() {

    }
}
