package pt.up.fe.comp2023.jasmin;

//JasminUtils e JasminInstruction criadas para dividir e melhorar as funções criadas anteriormente em OllirToJasmin
import org.specs.comp.ollir.*;
import pt.up.fe.specs.util.classmap.FunctionClassMap;
import pt.up.fe.specs.util.exceptions.NotImplementedException;
import java.util.*;
import java.util.stream.Collectors;
import static java.util.Collections.sort;

public class JasminInstruction {

    private Method method;
    private ClassUnit classUnit;
    private JasminUtils jasminUtils;
    private HashMap<String, Descriptor> varTable;
    private static int conditionalId = 0;

    JasminInstruction(ClassUnit classUnit, Method method) {
        this.classUnit = classUnit;
        this.method = method;
        this.varTable = method.getVarTable();
        this.jasminUtils = new JasminUtils(this.classUnit);
    }

    @Deprecated
    public int getLastReg() {
        var varT = this.varTable.entrySet().iterator();
        ArrayList<Integer> allRegs = new ArrayList<>();

        while(varT.hasNext()) {
            Map.Entry<String, Descriptor> entry = (Map.Entry)varT.next();
            String key = (String)entry.getKey();
            Descriptor d1 = (Descriptor)entry.getValue();
            allRegs.add(d1.getVirtualReg());
        }
        sort(allRegs);

        if(allRegs.size() == 0){
            return 0;
        }else{
            return allRegs.get(allRegs.size()-1);
        }
    }

    public String getCode(Instruction instruction){
        return this.getCode(instruction, false);
    }

    public String getCode(Instruction instruction, boolean isAssign){
        var code = new StringBuilder();

        FunctionClassMap<Instruction, String> instructionMap = new FunctionClassMap<>();

        //Instruction that were in the older OllirToJasmin with improvements
        instructionMap.put(CallInstruction.class, this::getCode);
        instructionMap.put(GetFieldInstruction.class, this::getCode);
        instructionMap.put(PutFieldInstruction.class, this::getCode);
        instructionMap.put(AssignInstruction.class, this::getCode);
        instructionMap.put(ReturnInstruction.class, this::getCode);
        instructionMap.put(UnaryOpInstruction.class, this::getCode);
        instructionMap.put(BinaryOpInstruction.class, this::getCode);

        //Instruction that weren't in the older OllirToJasmin
        instructionMap.put(SingleOpInstruction.class, this::getCode);
        instructionMap.put(GotoInstruction.class, this::getCode);
        instructionMap.put(SingleOpCondInstruction.class, this::getCode);
        instructionMap.put(OpCondInstruction.class, this::getCode);
        instructionMap.put(CondBranchInstruction.class, this::getCode);

        var labels = method.getLabels(instruction);

        if(labels.size() != 0) {
            for (String label : labels) {
                code.append(label + ":\n");
            }
        }
        if(instruction instanceof CallInstruction){
            code.append(getCode((CallInstruction) instruction, isAssign));
        } else {
            code.append(instructionMap.apply(instruction));
        }

        return code.toString();
    }

    public String getCode(CallInstruction callInstruction, boolean isAssign) {

        switch(callInstruction.getInvocationType()){
            case invokestatic:
                return getCodeInvokeStatic(callInstruction, isAssign);
            case invokespecial:
                return getCodeInvokeSpecial(callInstruction, isAssign);
            case invokevirtual:
                return getCodeInvokeVirtual(callInstruction, isAssign);
            case NEW:
                return getCodeNewInstr(callInstruction);
            case ldc:
                return getCodeLdc(callInstruction);
            case arraylength:
                return getArrayLength(callInstruction);
        }
        throw new NotImplementedException(callInstruction.getInvocationType());
    }

    // Call of Instructions Invoke
    private String getCodeInvokeStatic(CallInstruction callInstruction, boolean isAssign) {
        var code = new StringBuilder();
        var methodClass = ((Operand) callInstruction.getFirstArg()).getName();
        var methodName = ((LiteralElement) callInstruction.getSecondArg()).getLiteral().replace("\"", "");

        for(Element element : callInstruction.getListOfOperands()){
            code.append("\t" +this.jasminUtils.loadElement(element, this.varTable) );
        }

        code.append("\tinvokestatic " +this.jasminUtils.getCodeFullName(methodClass));
        code.append("/" + methodName + "(");

        var operandsTypes = callInstruction.getListOfOperands().stream().map(element ->this.jasminUtils.getJasminType(element.getType())).collect(Collectors.joining());
        code.append(operandsTypes).append(")").append(this.jasminUtils.getJasminType(callInstruction.getReturnType()) + "\n");

        this.jasminUtils.updateStackLimit();
        for(int i = 0; i < callInstruction.getListOfOperands().size(); i++){
            this.jasminUtils.subCurrentStack();
        }

        if(!callInstruction.getReturnType().getTypeOfElement().equals(ElementType.VOID) && !isAssign){
            code.append("\tpop\n");
            this.jasminUtils.updateStackLimit();
            this.jasminUtils.subCurrentStack();
        }
        this.jasminUtils.addCurrentStack();

        return code.toString();
    }

    private String getCodeInvokeSpecial(CallInstruction callInstruction, boolean isAssign) {
        var code = new StringBuilder();

        var firstArg = ((Operand) callInstruction.getFirstArg());
        var methodName = ((LiteralElement) callInstruction.getSecondArg()).getLiteral().replace("\"", "");

        code.append("\t" + this.jasminUtils.loadElement(callInstruction.getFirstArg(), this.varTable));

        for(Element element : callInstruction.getListOfOperands()){
            code.append("\t" +this.jasminUtils.loadElement(element, this.varTable) );
        }

        code.append("\tinvokespecial " +this.jasminUtils.getJasminType(firstArg.getType()));
        code.append("/" + methodName + "(");
        code.append(createListOperands(callInstruction));
        code.append(")").append(this.jasminUtils.getJasminType(callInstruction.getReturnType()) + '\n');

        this.jasminUtils.updateStackLimit();
        this.jasminUtils.subCurrentStack();
        for(int i = 0; i < callInstruction.getListOfOperands().size(); i++){
            this.jasminUtils.subCurrentStack();
        }

        if(!callInstruction.getReturnType().getTypeOfElement().equals(ElementType.VOID)
                && !isAssign){
            code.append("\tpop\n");
            return code.toString();
        }
        this.jasminUtils.addCurrentStack();

        return code.toString();
    }

    private String createListOperands(CallInstruction callInstruction) {
        return callInstruction.getListOfOperands().stream().map(element ->this.jasminUtils.getJasminType(element.getType(), true)).collect(Collectors.joining());
    }

    private String getCodeInvokeVirtual(CallInstruction callInstruction, boolean isAssign) {
        var code = new StringBuilder();

        var firstArg = ((Operand) callInstruction.getFirstArg());
        var secondArg = ((LiteralElement) callInstruction.getSecondArg()).getLiteral().replace("\"", "");

        code.append("\t"+this.jasminUtils.loadElement(firstArg, this.varTable));

        for(Element element : callInstruction.getListOfOperands()){
            code.append("\t" +this.jasminUtils.loadElement(element, this.varTable) );
        }

        code.append("\tinvokevirtual " +this.jasminUtils.getJasminType(firstArg.getType()) +  "/" + secondArg + "(");
        code.append(createListOperands(callInstruction));
        code.append(")").append(this.jasminUtils.getJasminType(callInstruction.getReturnType()) + '\n');

        this.jasminUtils.updateStackLimit();
        this.jasminUtils.subCurrentStack();
        for(int i = 0; i < callInstruction.getListOfOperands().size(); i++){
            this.jasminUtils.subCurrentStack();
        }

        if(!method.getReturnType().getTypeOfElement().equals(ElementType.VOID)
                && !isAssign){
            code.append("\tpop\n");
            this.jasminUtils.updateStackLimit();
            this.jasminUtils.subCurrentStack();
        }
        this.jasminUtils.addCurrentStack();

        return code.toString();
    }

    private String getCodeNewInstr(CallInstruction callInstruction) {
        var code = new StringBuilder();
        var firstArg = ((Operand) callInstruction.getFirstArg());

        for(Element element : callInstruction.getListOfOperands()){
            code.append("\t" +this.jasminUtils.loadElement(element, this.varTable) );
        }

        code.append("\tnew");
        switch (firstArg.getType().getTypeOfElement()){
            case INT32:
                break;
            case OBJECTREF:
                code.append("\t" + firstArg.getName() + "\n");
                break;
            case ARRAYREF:
                code.append("array int");
                break;
            case CLASS:
                code.append("\t" + firstArg.getName() + "\n");
                code.append("\tdup\n");
                break;
            default:
                throw new NotImplementedException(firstArg.getType().getTypeOfElement());
        }
        code.append("\n");

        this.jasminUtils.updateStackLimit();
        for(int i = 0; i < callInstruction.getListOfOperands().size(); i++){
            this.jasminUtils.subCurrentStack();
        }
        this.jasminUtils.addCurrentStack();

        return code.toString();
    }

    // Constants
    private String getCodeLdc(CallInstruction callInstruction) {
        return "\t" + this.jasminUtils.loadElement(callInstruction.getFirstArg(), this.varTable );
    }

    // Array Length
    private String getArrayLength(CallInstruction callInstruction){
        var code = new StringBuilder();

        code.append("\t" + this.jasminUtils.loadElement(callInstruction.getFirstArg(), this.varTable));
        code.append("\t" + "arraylength\n");

        this.jasminUtils.updateStackLimit();
        this.jasminUtils.subCurrentStack();
        this.jasminUtils.addCurrentStack();

        return code.toString();
    }

    // Get Code for putFieldInstruction
    public String getCode(PutFieldInstruction putFieldInstruction) {

        var code = new StringBuilder();
        var firstArg = ((Operand) putFieldInstruction.getFirstOperand());
        var secondArg = putFieldInstruction.getSecondOperand();
        var thirdArg = putFieldInstruction.getThirdOperand();

        String secondArgStr = "";

        code.append("\t" + this.jasminUtils.loadElement(firstArg, this.varTable));
        code.append("\t" + this.jasminUtils.loadElement(thirdArg, this.varTable));

        code.append("\tputfield ");

        if (secondArg.isLiteral()) {
            secondArgStr = ((LiteralElement)secondArg).getLiteral();
        } else {
            var o1 = (Operand)secondArg;
            secondArgStr = o1.getName();
        }

        code.append(this.jasminUtils.getFieldSpecs(firstArg, secondArgStr) + " ");
        code.append(this.jasminUtils.getJasminType(thirdArg.getType()) + "\n");

        this.jasminUtils.updateStackLimit();
        this.jasminUtils.subCurrentStack();
        this.jasminUtils.subCurrentStack();

        return code.toString();
    }

    // Get Code for getFieldInstruction
    public String getCode(GetFieldInstruction getFieldInstruction) {

        var code = new StringBuilder();

        var firstArg = ((Operand) getFieldInstruction.getFirstOperand());
        var secondArg = getFieldInstruction.getSecondOperand();
        String secondArgStr = "";

        code.append("\t" + this.jasminUtils.loadElement(firstArg, this.varTable));

        code.append("\tgetfield ");

        if (secondArg.isLiteral()) {
            secondArgStr = ((LiteralElement)secondArg).getLiteral();
        } else {
            var o1 = (Operand)secondArg;
            secondArgStr = o1.getName();
        }

        code.append(this.jasminUtils.getFieldSpecs(firstArg, secondArgStr) + " ");
        code.append(this.jasminUtils.getJasminType(getFieldInstruction.getFieldType()) + "\n");

        this.jasminUtils.updateStackLimit();
        this.jasminUtils.subCurrentStack();
        this.jasminUtils.addCurrentStack();

        return code.toString();
    }

    // Get Code for returnInstruction
    public String getCode(ReturnInstruction returnInstruction) {
        var code = new StringBuilder();

        if(!returnInstruction.hasReturnValue()) {
            return "";
        }

        code.append("\t" + this.jasminUtils.loadElement(returnInstruction.getOperand(), this.varTable));
        code.append("\t" + this.jasminUtils.getJasminReturnType(returnInstruction.getOperand().getType().getTypeOfElement()));
        code.append("return\n");

        this.jasminUtils.updateStackLimit();
        this.jasminUtils.subCurrentStack();

        return code.toString();
    }

    private String checkIfOpt(Operand dest, Instruction instruction) {
        if(instruction instanceof BinaryOpInstruction) {
            BinaryOpInstruction binaryOpInstruction = (BinaryOpInstruction) instruction;
            if (binaryOpInstruction.getOperation().getOpType().equals(OperationType.ADD)) {
                LiteralElement literalElement;
                Operand operand;

                if (binaryOpInstruction.getLeftOperand().isLiteral() && !binaryOpInstruction.getRightOperand().isLiteral()) {
                    literalElement = (LiteralElement) binaryOpInstruction.getLeftOperand();
                    operand = (Operand) binaryOpInstruction.getRightOperand();
                } else if (!binaryOpInstruction.getLeftOperand().isLiteral() && binaryOpInstruction.getRightOperand().isLiteral()) {
                    literalElement = (LiteralElement) binaryOpInstruction.getRightOperand();
                    operand = (Operand) binaryOpInstruction.getLeftOperand();
                } else {
                    return "";
                }

                if (this.varTable.get(dest.getName()).getVirtualReg() == this.varTable.get(operand.getName()).getVirtualReg()) {
                    var code = new StringBuilder();
                    code.append("\tiinc " + this.varTable.get(operand.getName()).getVirtualReg() + " " + literalElement.getLiteral() + "\n");
                    return code.toString();
                } else if (instruction.getPredecessors() != null) {
                    Node successor = instruction.getSuccessors().get(0);
                    if (successor instanceof AssignInstruction) {
                        Instruction instruction2 = ((AssignInstruction) successor).getRhs();
                        if (instruction2 instanceof SingleOpInstruction) {
                            Operand destOperand = (Operand) ((AssignInstruction) successor).getDest();
                            Operand assignOperand = (Operand) ((SingleOpInstruction) instruction2).getSingleOperand();
                            if (this.varTable.get(destOperand.getName()).getVirtualReg() == this.varTable.get(operand.getName()).getVirtualReg()) {
                                var code = new StringBuilder();
                                code.append("\tiinc " + this.varTable.get(destOperand.getName()).getVirtualReg() + " " + literalElement.getLiteral() + "\n");
                                code.append("\t" + this.jasminUtils.loadElement(destOperand, this.varTable));
                                code.append("\t" + this.jasminUtils.storeElement(assignOperand, this.varTable));

                                return code.toString();
                            }
                        }
                    }

                }
                return "";
            }
            return "";
        }
        return "";
    }


    // Get Code for assignInstruction
    public String getCode(AssignInstruction assignInstruction) {
        var code = new StringBuilder();
        Operand operand = (Operand) assignInstruction.getDest();
        if(operand instanceof ArrayOperand) {
            code.append(jasminUtils.loadArrayRefAndIndex((ArrayOperand) operand, varTable));
        }

        for (Node node: assignInstruction.getSuccessors()) {
            assignInstruction.getRhs().addSucc(node);
        }

        String result = checkIfOpt(operand, assignInstruction.getRhs());
        if(result == "") {
            code.append(getCode(assignInstruction.getRhs(), true));
            code.append(this.jasminUtils.storeElement(operand, this.varTable));
        } else {
            code.append(result);
        }

        return code.toString();
    }

    // Get Code for singleOpInstruction
    public String getCode(SingleOpInstruction singleOpInstruction) {
        return "\t" + this.jasminUtils.loadElement(singleOpInstruction.getSingleOperand(), this.varTable);
    }

    public String getCode(UnaryOpInstruction unaryOpInstruction) {
        var code = new StringBuilder();
        code.append("\t"+this.jasminUtils.loadElement(unaryOpInstruction.getOperand(), varTable));

        switch(unaryOpInstruction.getOperation().getOpType()){
            case NOT:
            case NOTB:
                code.append("\tifne TRUE" + conditionalId + "\n");
                this.jasminUtils.updateStackLimit();
                this.jasminUtils.subCurrentStack();
                code.append("\ticonst_1\n");
                code.append("\tgoto FALSE" + conditionalId + "\n");
                code.append("TRUE" + conditionalId + ":\n");
                code.append("\ticonst_0\n");
                code.append("FALSE"+conditionalId+":\n");
                this.jasminUtils.addCurrentStack();
                conditionalId++;
                break;
            default:
                throw new NotImplementedException(unaryOpInstruction.getOperation().getOpType());
        }
        return code.toString();
    }

    private String createArithmeticCode(String operation, Element leftOperand, Element rightOperand) {
        var code = new StringBuilder();
        List<String> availableOps = new ArrayList<>(Arrays.asList("iadd", "imul", "idiv", "isub"));

        if(leftOperand.isLiteral() && rightOperand.isLiteral() && availableOps.contains(operation)) {
            int result = 0;
            LiteralElement left = (LiteralElement) leftOperand;
            LiteralElement right = (LiteralElement) rightOperand;

            switch (operation) {
                case "iadd":
                    result = Integer.parseInt(left.getLiteral()) + Integer.parseInt(right.getLiteral());
                    break;
                case "isub":
                    result = Integer.parseInt(left.getLiteral()) - Integer.parseInt(right.getLiteral());
                    break;
                case "imul":
                    result = Integer.parseInt(left.getLiteral()) * Integer.parseInt(right.getLiteral());
                    break;
                case "idiv":
                    result = Integer.parseInt(left.getLiteral()) / Integer.parseInt(right.getLiteral());
                    break;
            }

            LiteralElement literal = new LiteralElement(result + "", new Type(ElementType.INT32));
            code.append("\t").append(jasminUtils.loadElement(literal, varTable));

            this.jasminUtils.updateStackLimit();
        } else {
            code.append("\t").append(this.jasminUtils.loadElement(leftOperand, this.varTable));
            code.append("\t").append(this.jasminUtils.loadElement(rightOperand, this.varTable));
            code.append("\t").append(operation).append("\n");

            this.jasminUtils.updateStackLimit();
            this.jasminUtils.subCurrentStack();
            this.jasminUtils.subCurrentStack();
            this.jasminUtils.addCurrentStack();
        }

        return code.toString();
    }

    private String createBranchCode(String operation, Element leftOperand, Element rightOperand) {
        var code = new StringBuilder();
        LiteralElement literalElement = null;
        Operand operand = null;
        String prefix = "_icmp";

        List<String> listBitwiseOperations = new ArrayList<>(Arrays.asList("ne", "eq", "and", "or"));
        List<String> listCmpOperations = new ArrayList<>(Arrays.asList("lt", "le", "gt", "ge"));

        if(leftOperand.isLiteral() && rightOperand.isLiteral()) {
            boolean result = false;

            if(listBitwiseOperations.contains(operation))
                result = doBitwiseOptimization(operation, ((LiteralElement) leftOperand).getLiteral(),((LiteralElement) rightOperand).getLiteral());
            else if(listCmpOperations.contains(operation)) {
                result = doCmpOptimization(operation, ((LiteralElement) leftOperand).getLiteral(),((LiteralElement) rightOperand).getLiteral());
            }

            this.jasminUtils.addCurrentStack();

            if (result){
                return "\ticonst_1\n";
            } else {
                return "\ticonst_0\n";
            }
        } else if(leftOperand.isLiteral() && !rightOperand.isLiteral()) {
            literalElement = (LiteralElement) leftOperand;
            operand = (Operand) rightOperand;
        } else if(!leftOperand.isLiteral() && rightOperand.isLiteral()) {
            literalElement = (LiteralElement) rightOperand;
            operand = (Operand) leftOperand;
        }

        if(literalElement != null && literalElement.getLiteral().equals("0")) {
            prefix = "";
        }

        if(prefix.equals("")) {
            code.append("\t" + this.jasminUtils.loadElement(operand, this.varTable));

            this.jasminUtils.updateStackLimit();
            this.jasminUtils.subCurrentStack();
        } else {
            code.append("\t" + this.jasminUtils.loadElement(leftOperand, this.varTable));
            code.append("\t" + this.jasminUtils.loadElement(rightOperand, this.varTable));

            this.jasminUtils.updateStackLimit();
            this.jasminUtils.subCurrentStack();
            this.jasminUtils.subCurrentStack();
        }

        code.append("\t" + "if" + prefix + operation + " FALSE" + conditionalId + "\n" );
        code.append("\ticonst_0\n");
        code.append("\tgoto TRUE" + conditionalId +"\n");
        code.append("FALSE" + conditionalId + ":\n");
        code.append("\ticonst_1\n");
        code.append("TRUE" + conditionalId + ":\n");

        this.jasminUtils.addCurrentStack();
        conditionalId++;

        return code.toString();
    }

    // Code for optimization part
    private boolean doCmpOptimization(String operation, String leftLiteral, String rightLiteral) {
        int left = Integer.parseInt(leftLiteral);
        int right = Integer.parseInt(rightLiteral);

        switch(operation) {
            case "lt":
                return left < right;
            case "le":
                return  left <= right;
            case "gt":
                return left > right;
            case "ge":
                return left >= right;
            default:
                return false;
        }
    }

    private boolean doBitwiseOptimization(String operation, String leftLiteral, String rightLiteral) {
        boolean left = false;
        boolean right = false;

        if(leftLiteral.equals("1")){
            left = true;
        }
        if(rightLiteral.equals("1")){
            right = true;
        }

        switch(operation) {
            case "and":
                return left && right;
            case "or":
                return  left || right;
            case "eq":
                return left == right;
            case "ne":
                return left != right;
            default:
                return false;
        }
    }

    private String doLogicalOptimization(String operation ,LiteralElement literalElement) {
        if(operation.equals("and") && literalElement.getLiteral().equals("0")){
            return "\ticonst_0\n";
        } else if(operation.equals("or") && literalElement.getLiteral().equals("1")) {
            return "\ticonst_1\n";
        } else {
            return "";
        }
    }

    private String doOrAndCode(String operation, Element leftOperand, Element rightOperand) {
        var code = new StringBuilder();
        LiteralElement literal = null;
        String result = "";

        if(leftOperand.isLiteral() && rightOperand.isLiteral()) {
            this.jasminUtils.addCurrentStack();
            boolean res = doBitwiseOptimization(operation, ((LiteralElement) leftOperand).getLiteral(), ((LiteralElement) rightOperand).getLiteral());

            if (res) {
                return "\ticonst_1\n";
            } else {
                return "\ticonst_0\n";
            }
        } else if(leftOperand.isLiteral() && !rightOperand.isLiteral()) {
            literal = (LiteralElement) leftOperand;
        } else if(!leftOperand.isLiteral() && rightOperand.isLiteral()) {
            literal = (LiteralElement) rightOperand;
        }

        if(literal != null) {
            result = doLogicalOptimization(operation, literal);
        }

        if(result != ""){
            return result;
        }

        code.append("\t" + this.jasminUtils.loadElement(leftOperand, this.varTable));
        code.append("\tifeq" + " FALSE" + conditionalId + "\n");

        this.jasminUtils.updateStackLimit();
        this.jasminUtils.subCurrentStack();

        code.append("\t" + this.jasminUtils.loadElement(rightOperand, this.varTable));
        code.append("\tifeq" + " FALSE" + conditionalId + "\n");

        this.jasminUtils.updateStackLimit();
        this.jasminUtils.subCurrentStack();

        code.append("\ticonst_1\n");
        code.append("\tgoto TRUE" + conditionalId + "\n");
        code.append("FALSE" + conditionalId + ":\n");
        code.append("\ticonst_0\n");
        code.append("TRUE" + conditionalId + ":\n");

        this.jasminUtils.addCurrentStack();
        conditionalId++;

        return code.toString();
    }

    private String doDivOptimization(Element leftOperand, Element rightOperand) {
        LiteralElement literalElement = null;
        Element operand = null;

        if(rightOperand.isLiteral() && !leftOperand.isLiteral()) {
            literalElement = (LiteralElement) rightOperand;
            operand = leftOperand;
        } else if(rightOperand.isLiteral() && leftOperand.isLiteral()) {
            literalElement = (LiteralElement) rightOperand;
            operand = leftOperand;
        }

        if(literalElement != null) {
            int numShifts = this.jasminUtils.checkIfIsPower2(Integer.parseInt(literalElement.getLiteral()));

            if(numShifts != -1) {
                Type type = new Type(ElementType.INT32);
                LiteralElement newLiteral = new LiteralElement(Integer.toString(numShifts), type);
                return createArithmeticCode("ishr", operand,  newLiteral);
            }
        }

        return "";
    }

    private String doMulOptimization(Element leftOperand, Element rightOperand) {
        LiteralElement literalElement = null;
        Element operand = null;

        if(rightOperand.isLiteral() && !leftOperand.isLiteral()) {
            literalElement = (LiteralElement) rightOperand;
            operand = leftOperand;
        } else if(!rightOperand.isLiteral() && leftOperand.isLiteral()) {
            literalElement = (LiteralElement) leftOperand;
            operand = rightOperand;
        } else if(rightOperand.isLiteral() && leftOperand.isLiteral()) {
            literalElement = (LiteralElement) leftOperand;
            operand = rightOperand;
        }

        if(literalElement != null) {
            int numShifts = this.jasminUtils.checkIfIsPower2(Integer.parseInt(literalElement.getLiteral()));

            if(operand instanceof LiteralElement && numShifts == -1) {
                numShifts = this.jasminUtils.checkIfIsPower2(Integer.parseInt(((LiteralElement)operand).getLiteral()));
                operand = literalElement;
            }

            if(numShifts != -1) {
                Type type = new Type(ElementType.INT32);
                LiteralElement newLiteral = new LiteralElement(Integer.toString(numShifts), type);
                return createArithmeticCode("ishl" , operand,  newLiteral);
            }
        }

        return "";
    }

    // Get Code for Binary Operations Instruction
    public String getCode(BinaryOpInstruction binaryOpInstruction) {
        var code = new StringBuilder();
        Operation op = binaryOpInstruction.getOperation();
        String opCode;

        Element leftOperand = binaryOpInstruction.getLeftOperand();
        Element rightOperand= binaryOpInstruction.getRightOperand();

        switch(op.getOpType()){
            case ADD:
                code.append(createArithmeticCode("iadd", leftOperand, rightOperand));
                break;
            case SUB:
                code.append(createArithmeticCode("isub", leftOperand, rightOperand));
                break;
            case MUL:
                opCode = doMulOptimization(leftOperand, rightOperand);
                if(opCode == "") {
                    code.append(createArithmeticCode("imul", leftOperand, rightOperand));
                } else {
                    code.append(opCode);
                }
                this.jasminUtils.updateStackLimit();
                break;
            case DIV:
                opCode = doDivOptimization(leftOperand, rightOperand);
                if(opCode == "") {
                    code.append(createArithmeticCode("idiv", leftOperand, rightOperand));
                } else {
                    code.append(opCode);
                }
                break;
            case EQ:
                code.append(createBranchCode("eq", leftOperand,rightOperand));
                break;
            case NEQ:
                code.append(createBranchCode("ne", leftOperand,rightOperand));
                break;
            case GTH:
                code.append(createBranchCode("gt", leftOperand, rightOperand));
                break;
            case LTH:
                code.append(createBranchCode("lt", leftOperand, rightOperand));
                break;
            case AND:
                code.append(this.jasminUtils.loadElement(leftOperand, this.varTable));
                code.append(this.jasminUtils.loadElement(rightOperand, this.varTable));
                this.jasminUtils.updateStackLimit();
                this.jasminUtils.subCurrentStack();
                this.jasminUtils.subCurrentStack();
                this.jasminUtils.addCurrentStack();
                code.append("\tiand\n");
                break;
            case ANDB:
                code.append(doOrAndCode("and", leftOperand, rightOperand));
                break;
            case OR:
                code.append(this.jasminUtils.loadElement(leftOperand, this.varTable));
                code.append(this.jasminUtils.loadElement(rightOperand, this.varTable));
                code.append("ior\n");
                this.jasminUtils.updateStackLimit();
                this.jasminUtils.subCurrentStack();
                this.jasminUtils.subCurrentStack();
                this.jasminUtils.addCurrentStack();
                break;
            case ORB:
                code.append(doOrAndCode("or", leftOperand, rightOperand));
                break;
            case LTE:
                code.append(createBranchCode("le", leftOperand, rightOperand));
                break;
            case GTE:
                code.append(createBranchCode("ge", leftOperand, rightOperand));
                break;
            case XOR:
                code.append(this.jasminUtils.loadElement(leftOperand, this.varTable));
                code.append(this.jasminUtils.loadElement(rightOperand, this.varTable));
                code.append("ixor\n");
                this.jasminUtils.updateStackLimit();
                this.jasminUtils.subCurrentStack();
                this.jasminUtils.subCurrentStack();
                this.jasminUtils.addCurrentStack();
                break;
            case NOTB:
                code.append("\tif_ne TRUE" + conditionalId + "\n");
                code.append("\ticonst_1\n");
                code.append("\tgoto FALSE" + conditionalId + "\n");
                code.append("TRUE" + conditionalId + ":\n");
                code.append("\ticonst_0");
                code.append("FALSE"+conditionalId+":\n");
                conditionalId++;
                break;
            default:
                throw new NotImplementedException(op.getOpType());
        }

        this.jasminUtils.updateStackLimit();

        return code.toString();
    }

    // Get Code for goToInstruction
    public String getCode(GotoInstruction goToInstruction) {
        return "\tgoto " + goToInstruction.getLabel() + "\n";
    }

    // Get Code for singleOpCondInstruction
    public String getCode(SingleOpCondInstruction singleOpCondInstruction) {
        var code = new StringBuilder();

        code.append(getCode(singleOpCondInstruction.getCondition()));
        code.append("\tifne " + singleOpCondInstruction.getLabel() + " \n");

        this.jasminUtils.updateStackLimit();
        this.jasminUtils.subCurrentStack();

        return code.toString();
    }

    // Get Code for opCondInstruction
    public String getCode(OpCondInstruction opCondInstruction) {
        var code = new StringBuilder();

        code.append(getCode(opCondInstruction.getCondition()));
        code.append("\tifne " + opCondInstruction.getLabel() + "\n");

        this.jasminUtils.updateStackLimit();
        this.jasminUtils.subCurrentStack();

        return code.toString();
    }

    // Get Code for condBranchInstruction
    public String getCode(CondBranchInstruction condBranchInstruction) {
        var code = new StringBuilder();

        code.append(getCode(condBranchInstruction.getCondition()));
        code.append(condBranchInstruction.getLabel() + "\n");

        return code.toString();
    }

    public static void reset() {
        conditionalId = 0;
    }

    public int getStackLimit() {
        return this.jasminUtils.getStackLimit();
    }
}
