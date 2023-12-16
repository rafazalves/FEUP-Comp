package pt.up.fe.comp2023.ollir;

import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.ast.PreorderJmmVisitor;

public class OllirVisitor extends PreorderJmmVisitor<int[], OllirCodeResult> {

    private int varTempCounter;
    private OllirGenerator ollirGenerator;

    public OllirVisitor(OllirGenerator ollirGenerator) {
        this.varTempCounter = 0;
        this.ollirGenerator = ollirGenerator;
    }

    @Override
    protected void buildVisitor() {
        addVisit("BinaryOp", this::visitBinaryOp);
        addVisit("UnaryOp", this::visitUnaryOp);
        addVisit("Identifier", this::visitLiteral);
        addVisit("Integer", this::visitLiteral);
        addVisit("BoolExpr", this::visitLiteral);
        addVisit("ArrayInit", this::visitArrayInit);
        addVisit("ArrayExpr", this::visitArrayExpression);
        addVisit("Length", this::visitLength);
        addVisit("Constructor", this::visitConstructor);
        addVisit("MethodCall", this::visitMethodCall);
        setDefaultVisit(this::defaultVisit);
    }

    private String newVarTemp() {
        var newVarTemp = "temp_" + varTempCounter;
        varTempCounter++;
        return newVarTemp;
    }

    public OllirCodeResult defaultVisit(JmmNode node, int[] numbers) {
        ollirGenerator.visit(node);
        return new OllirCodeResult("", "");
    }

    public OllirCodeResult visitBinaryOp(JmmNode node, int[] numbers) {

        var lhsCode = visit(node.getJmmChild(0), numbers);
        var rhsCode = visit(node.getJmmChild(1), numbers);

        var code = new StringBuilder();
        code.append(lhsCode.prefixCode());
        code.append(rhsCode.prefixCode());

        var value = newVarTemp();

        String type = convertType(node.get("type"));
        code.append(value + type + " :=" + type);
        code.append(" " + lhsCode.value() + convertType(node.getJmmChild(0).get("type")));
        code.append(" " + node.get("op") + convertType(node.get("type")));
        code.append(" " + rhsCode.value() + convertType(node.getJmmChild(1).get("type")) + ";");
        newLineStart(numbers, code);

        return new OllirCodeResult(code.toString(), value);
    }

    public OllirCodeResult visitUnaryOp(JmmNode node, int[] numbers) {

        var code = new StringBuilder();
        var rhsCode = visit(node.getJmmChild(0), numbers);
        code.append(rhsCode.prefixCode());

        var value = newVarTemp();

        String nameReturn = node.get("type");
        String abvReturn = "";

        switch (nameReturn) {
            case "int":
                abvReturn = ".i32";
                break;
            case "boolean":
                abvReturn = ".bool";
                break;
            default:
                abvReturn = nameReturn;
        }

        code.append(value + abvReturn + " :=");
        code.append(abvReturn + " " + node.get("op") + abvReturn + " " + rhsCode.value() + abvReturn + ";");
        newLineStart(numbers, code);

        return new OllirCodeResult(code.toString(), value);
    }

    public OllirCodeResult visitLiteral(JmmNode node, int[] numbers) {

        var code = new StringBuilder();
        if (node.getJmmParent().getKind().equals("ArrayInit")) {
            code.append(node.get("value"));
            return new OllirCodeResult(code.toString(), node.get("value"));
        }
        if (node.getKind().equals("BoolExpr")) {
            return new OllirCodeResult("", node.get("value").equals("true") ? "1" : "0");
        }

        return new OllirCodeResult("", node.get("value"));
    }

    public OllirCodeResult visitArrayInit(JmmNode node, int[] numbers) {
        var code = new StringBuilder();

        var value = newVarTemp();
        code.append(value + ".i32 :=.i32 ");

        var lhsCode = visit(node.getJmmChild(0), numbers);
        code.append(lhsCode.prefixCode() + ".i32;");
        newLineStart(numbers, code);

        var value2 = newVarTemp();
        code.append(value2 + ".array.i32 :=.array.i32 new(array, " + value + ".i32).array.i32;");
        newLineStart(numbers, code);

        return new OllirCodeResult(code.toString(), value2);
    }

    public OllirCodeResult visitArrayExpression(JmmNode node, int[] numbers) {

        var code = new StringBuilder();
        var value = newVarTemp();
        var arrayType = convertType(node.get("type"));
        var arrayIdentifier = node.getChildren().get(0).get("value");
        var indexResult = visit(node.getJmmChild(1), numbers);

        code.append(indexResult.prefixCode());
        code.append(value + arrayType + " :=" + arrayType + " " + arrayIdentifier + ".array" + arrayType + "[");
        code.append(indexResult.value() + arrayType);
        code.append("]" + arrayType + ";");
        newLineStart(numbers, code);

        return new OllirCodeResult(code.toString(), value);

    }

    public OllirCodeResult visitLength(JmmNode node, int[] numbers) {
        var code = new StringBuilder();

        var length = newVarTemp();
        code.append(length + ".i32 :=.i32 arraylength(");
        var lhsCode = visit(node.getJmmChild(0), numbers);
        code.append(lhsCode.value() + ".array.i32).i32;");

        return new OllirCodeResult(code.toString(), length);
    }

    public OllirCodeResult visitConstructor(JmmNode node, int[] numbers) {
        var code = new StringBuilder();

        String temp = newVarTemp();
        String className = node.get("type");

        code.append(temp + "." + className + " :=." + className + " new(" + className + ")." + className + ";");
        newLineStart(numbers, code);

        code.append("invokespecial(" + temp + "." + className + ", \"\").V;");
        newLineStart(numbers, code);

        return new OllirCodeResult(code.toString(), temp);
    }

    public OllirCodeResult visitMethodCall(JmmNode node, int[] numbers) {
        var code = new StringBuilder();
        String temp = newVarTemp();
        VariableAux tempAux = new VariableAux(temp);
        ollirGenerator.visitMethodCall(node, tempAux);

        return new OllirCodeResult("", tempAux.getValueVariable());
    }

    private static void newLineStart(int[] numbers, StringBuilder code) {
        code.append("\n").append(" ".repeat(numbers[0] * numbers[1]));
    }

    public String convertType(String type) {
        switch (type) {
            case "int":
                return ".i32";
            case "boolean":
                return ".bool";
            default:
                return "."+type;
        }
    }
}

