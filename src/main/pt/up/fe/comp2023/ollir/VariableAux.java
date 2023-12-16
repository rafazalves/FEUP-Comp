package pt.up.fe.comp2023.ollir;

import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp2023.semantic.ASymbolTable;

import static pt.up.fe.comp2023.ollir.OllirUtils.getOllirType;

public class VariableAux {

    private Type typeVariable;
    private String nameVariable;
    private String valueVariable;
    private Type assignType = new Type("void", false);

    public VariableAux(String nameVariable) {
        this.nameVariable = nameVariable;
    }

    public Type getTypeVariable() {
        return typeVariable;
    }

    public void setTypeVariable(Type typeVariable) {
        this.typeVariable = typeVariable;
    }

    public String getNameVariable() {
        return nameVariable;
    }

    public void setNameVariable(String nameVariable) {
        this.nameVariable = nameVariable;
    }

    public String getValueVariable() {
        return valueVariable != null ? valueVariable : nameVariable;
    }

    public void setValueVariable(String valueVariable) {
        this.valueVariable = valueVariable;
    }

    public String getVarAuxWithType() {
        String typeOllir = typeVariable != null ? getOllirType(typeVariable.getName()) : "i32";
        return getVarAux() + "." + typeOllir;
    }

    public String getVarAux() {
        if(getValueVariable() != null){
            return getValueVariable();
        }else{
            return getNameVariable();
        }
    }

    public String getInvokeString(JmmNode node, ASymbolTable symbolTable) {
        String invokeClass = getVarAux();
        if(symbolTable.isLocalVariable(node, invokeClass)){
            return invokeClass + "." + getOllirType(getTypeVariable().getName());
        }else{
            return invokeClass;
        }
    }

    public void setAssignType(Type assignType) {
        this.assignType = assignType;
    }

    public Type getAssignType() {
        return assignType;
    }
}
