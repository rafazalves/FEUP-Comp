package pt.up.fe.comp2023.jasmin;

//JasminUtils e JasminInstruction criadas para dividir e melhorar as funções criadas anteriormente em OllirToJasmin
import org.specs.comp.ollir.*;
import pt.up.fe.specs.util.exceptions.NotImplementedException;
import java.util.ArrayList;
import java.util.HashMap;

public class JasminUtils {
    ClassUnit classUnit;
    private static int currentStack;
    private static int stackLimit;

    JasminUtils(ClassUnit classUnit) {
        this.classUnit = classUnit;
    }

    //Antes usada similarmente no OllirToJasmin
    public String getCodeFullName(String className) {

        if (className == null) return "java/lang/Object";

        for (var importString : this.classUnit.getImports()) {
            var splitImportString = importString.split("\\.");
            String name;

            if (splitImportString.length == 0) {
                name = importString;
            } else {
                name = splitImportString[splitImportString.length - 1];
            }
            if (name.equals(className)) {
                return importString.replace('.', '/');
            }
        }

        if (this.classUnit.getClassName().equals(className)) return className;

        else throw new RuntimeException("Could not find import for class " + className);
    }

    //Funções para os Iload, Store e Const
    public String getIload(int paramId) {
        var code = new StringBuilder();

        code.append("iload");

        if (paramId < 4) {
            code.append("_" + paramId);
        }else {
            code.append(" " + paramId);
        }

        return code.toString();
    }

    public String getIStore(int paramId) {
        var code = new StringBuilder();

        code.append("istore");

        if (paramId < 4) {
            code.append("_" + paramId);
        }else {
            code.append(" " + paramId);
        }

        return code.toString();
    }

    public String getIConst(String _const) {

        var code = new StringBuilder();
        int value = Integer.parseInt(_const);

        if (value < 6 && value >= 0) {
            code.append("iconst_" + _const);
        }else if (value < 128 && value >= 0) {
            code.append("bipush " + _const);
        }else if (value < 32768 && value >= 0) {
            code.append("sipush " + _const);
        }else {
            code.append("ldc " + _const);
        }

        return code.toString();
    }

    public String loadArrayRefAndIndex(ArrayOperand arrayOperand, HashMap<String, Descriptor> varTable) {
        StringBuilder code = new StringBuilder();
        if (arrayOperand.isParameter()) {
            code.append("\taload ");
            code.append(arrayOperand.getParamId());
            code.append("\n\t");
        } else {
            var virtualReg = -1;

            if (varTable.get(arrayOperand.getName()) != null) {
                virtualReg = varTable.get(arrayOperand.getName()).getVirtualReg();
            }
            code.append("\taload ");
            code.append(virtualReg);
            code.append("\n\t");
        }
        code.append(this.loadArrayIndexes(arrayOperand, varTable));
        code.append("\n");

        this.addCurrentStack();

        return code.toString();
    }

    //Code for store e load elements (anteriormente dividido por várias funções dependendo das instrução)
    public String storeElement(Element element, HashMap<String, Descriptor> varTable) {
        String instrStr = "";
        if (element.isLiteral()) {
            LiteralElement literalElement = (LiteralElement) element;
            switch (literalElement.getType().getTypeOfElement()) {
                case INT32:
                    instrStr += "\t";
                    instrStr += getIStore(Integer.parseInt(literalElement.getLiteral()));
                    break;
                default:
                    instrStr += "\t";
                    instrStr += "store " + literalElement.getLiteral();
                    break;
            }

        } else {
            Operand operand = (Operand) element;
            boolean isArrayOperand = false;

            int id;
            if (operand.isParameter()) {
                id = operand.getParamId();
            } else {
                id = varTable.get(operand.getName()).getVirtualReg();
            }

            if (operand instanceof ArrayOperand) {
                isArrayOperand = true;
            }
            ElementType elementType = operand.getType().getTypeOfElement();

            if (id != -1) {
                switch (elementType) {
                    case INT32:
                        instrStr += "\t";
                        if (isArrayOperand) {
                            instrStr += "iastore";
                        } else {
                            instrStr += getIStore(id);
                        }
                        break;
                    case BOOLEAN:
                        instrStr += "\t";
                        instrStr += getIStore(id);
                        break;
                    case ARRAYREF:
                    case OBJECTREF:
                    case CLASS:
                    case STRING:
                        instrStr += "\t";
                        instrStr += "astore" + (id <= 3 ? '_' : ' ') + id;
                        break;
                    case THIS:
                        instrStr += "\t";
                        instrStr += "astore_0";
                        break;
                    case VOID:
                        break;
                }
            } else {
                instrStr += "\tputfield " + getJasminType(elementType) + "/" + operand.getName() + " " + getJasminType(elementType);
            }

        }
        instrStr += "\n";
        this.updateStackLimit();
        this.subCurrentStack();

        return instrStr;
    }

    public String loadElement(Element element, HashMap<String, Descriptor> varTable) {
        String instrStr = "";

        int loads = 1;

        if (element.isLiteral()) {
            LiteralElement literalElement = (LiteralElement) element;

            switch (literalElement.getType().getTypeOfElement()) {
                case INT32:
                case BOOLEAN:
                    instrStr = getIConst(literalElement.getLiteral());
                    break;
                default:
                    instrStr = "\tldc " + literalElement.getLiteral();
                    break;
            }

        } else {
            Operand operand = (Operand) element;
            boolean isArrayOperand = operand instanceof ArrayOperand;
            int id;

            if (operand.isParameter()) {
                id = operand.getParamId();
            } else {
                id = varTable.get(operand.getName()).getVirtualReg();
            }

            if (id != -1) {
                ElementType elementType = operand.getType().getTypeOfElement();
                switch (elementType) {
                    case INT32:
                        if (isArrayOperand) {
                            instrStr += "aload" + (id <= 3 ? '_' : ' ') + id + "\n\t";
                            instrStr += loadArrayIndexes((ArrayOperand) operand, varTable);
                            instrStr += "iaload";
                            loads += 2;
                        } else {
                            instrStr = getIload(id);
                        }
                        break;
                    case BOOLEAN:
                        instrStr = getIload(id);
                        break;
                    case ARRAYREF:
                    case OBJECTREF:
                    case CLASS:
                    case STRING:
                        instrStr = "aload" + (id <= 3 ? '_' : ' ') + id;
                        break;
                    case THIS:
                        instrStr = "aload_0";
                        break;
                    case VOID:
                        break;
                }
            } else {
                instrStr += "aload_0\n";
                instrStr += "\tgetfield " + this.classUnit.getClassName() + "/" + operand.getName();
                instrStr += (isArrayOperand ? " [" : " ") + getJasminType(element.getType()) ;

                if (isArrayOperand) {
                    instrStr += "\n\t" + loadArrayIndexes((ArrayOperand) operand, varTable);
                    instrStr += "iaload";
                    loads += 2;
                }
            }
        }

        instrStr += "\n";
        this.currentStack += loads;

        return instrStr;
    }

    public String getFieldSpecs(Operand firstArg, String secondArg) {
        var code = new StringBuilder();

        switch (firstArg.getType().getTypeOfElement()) {
            case THIS:
                code.append(this.classUnit.getClassName() + "/");
                code.append(secondArg);
                break;
            default:
                code.append(getCodeFullName(firstArg.getName()) + "/" + secondArg);
                break;
        }

        return code.toString();
    }

    private String loadArrayIndexes(ArrayOperand arrayOperand, HashMap<String, Descriptor> varTable) {
        StringBuilder code = new StringBuilder();

        ArrayList<Element> list = arrayOperand.getIndexOperands();
        if (list.isEmpty()) {
            return "";
        }

        code.append(this.loadElement(list.get(0), varTable) + "\t");
        return code.toString();
    }

    //Check if the number can be divided by 2 repeatedly until it becomes 1 (check numShifts)
    public int checkIfIsPower2(int number) {
        int acc = 0;
        while (number % 2 == 0) {
            number /= 2;
            acc++;
        }

        if (number == 1){
            return acc;
        } else{
            return -1;
        }
    }

    //Code for stack that didn't have in previous checkpoint
    public int getStackLimit() {
        return this.stackLimit;
    }

    public int getCurrentStack() {
        return this.currentStack;
    }

    public void updateStackLimit() {
        this.stackLimit = Math.max(this.stackLimit, this.currentStack);
    }

    public void addCurrentStack() {
        this.currentStack++;
    }

    public void subCurrentStack() {
        this.currentStack--;
    }

    public void resetStack(){
        this.currentStack = 0;
        this.stackLimit = 0;
    }

    public String getJasminReturnType(ElementType elementType) throws NotImplementedException {
        switch (elementType) {
            case STRING:
                return "Ljava/lang/String;";
            case VOID:
                return "";
            case INT32:
            case BOOLEAN:
                return "i";
            case ARRAYREF:
            case OBJECTREF:
                return "a";
            default:
                throw new NotImplementedException(elementType);
        }
    }

    public String getJasminType(Type type) {
        return getJasminType(type, false);
    }

    public String getJasminType(Type type, boolean isMethodSignature) {
        if (type instanceof ArrayType) {
            return "[" + getJasminType(((ArrayType) type).getArrayType());
        } else if (type instanceof ClassType) {
            if (isMethodSignature)
                return "L" + ((ClassType) type).getName() + ";";
            else
                return ((ClassType) type).getName();
        } else if (type instanceof Type)
            return getJasminType(type.getTypeOfElement());
        else {
            throw new NotImplementedException(type);
        }
    }

    public String getJasminType(ElementType elementType) throws NotImplementedException {
        switch (elementType) {
            case STRING:
                return "Ljava/lang/String;";
            case VOID:
                return "V";
            case INT32:
                return "I";
            case BOOLEAN:
                return "Z";
            case THIS:
            case OBJECTREF:
                return "this";
            default:
                throw new NotImplementedException(elementType);
        }
    }
}

