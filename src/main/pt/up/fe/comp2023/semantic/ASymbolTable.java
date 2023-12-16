package pt.up.fe.comp2023.semantic;

import pt.up.fe.comp.jmm.analysis.table.Symbol;
import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.JmmNode;

import java.util.*;

public class ASymbolTable implements SymbolTable {
    public List<String> imports;
    public String className;
    public String superClass;
    public static List<Symbol> fields;
    public List<String> methods;
    protected Map<String, Type> typeret;
    protected Map<String, List<Symbol>> methparams;
    protected static Map<String, List<Symbol>> methvars;

    public ASymbolTable() {
        this.imports = new ArrayList<>();
        this.fields = new ArrayList<>();
        this.methods = new ArrayList<>();
        this.typeret = new HashMap<>();
        this.methparams = new HashMap<>();
        this.methvars = new HashMap<>();
    }

    @Override
    public List<String> getImports() {
        return imports;
    }

    @Override
    public String getClassName() {
        return className;
    }
    @Override
    public String getSuper() {
        return superClass;
    }

    @Override
    public List<Symbol> getFields() {
        return fields;
    }

    @Override
    public List<String> getMethods() {
        return methods;
    }

    @Override
    public Type getReturnType(String methodSignature) {
        return typeret.get(methodSignature);
    }

    @Override
    public List<Symbol> getParameters(String methodSignature) {
        return methparams.get(methodSignature);
    }

    @Override
    public List<Symbol> getLocalVariables(String methodSignature) {
        return methvars.get(methodSignature);
    }

    public void printAST(){
        String tree = "";

        // Add imports
        for (String imp: imports){
            tree += imp + "\n";
        }

        //Add class and super
        if (superClass!=null) {
            tree += " " + this.className + ", SUPER: " + this.superClass + "\n";
        }
        else {
            tree += " CLASS: " + this.className + "\n";
        }

        for (Symbol field: this.fields){
            tree += "   FIELD: " + field.toString() + "\n";
        }

        for (String methods: this.methods){
            tree +="   METHOD:" + methods;
            List<Symbol> params= getParameters(methods);
            if (params!=null) {
                tree += " , params=" + params;
            }
            List<Symbol> vars = getLocalVariables(methods);
            if (vars!=null){
                tree+= "\n   VARS:" + vars;
            }
            tree += "\n    RETURN:" + getReturnType(methods) + "\n";
        }

        System.out.println(tree);
    }

    //Aux functions to Ollir using SymbolTable
    public static Optional<Symbol> getClosestSymbol(JmmNode node, String name) {
        var method = getClosestMethod(node);
        if (method.isPresent()) {
            String methodName;
            if(method.get().getKind().equals("RegularMethod")){
                methodName = method.get().getChildren().get(1).get("name");
            }else{
                methodName = "main";
            }
            for (Symbol symbol : methvars.get(methodName)) {
                if (symbol.getName().equals(name)) {
                    return Optional.of(symbol);
                }
            }
        }
        for (Symbol symbol : fields) {
            if (symbol.getName().equals(name)) {
                return Optional.of(symbol);
            }
        }
        return Optional.empty();
    }

    public static Optional<JmmNode> getClosestMethod(JmmNode node) {
        var method = node.getAncestor("RegularMethod");
        if (method.isPresent()) {
            return method;
        }
        method = node.getAncestor("MainMethod");
        return method;
    }

    public boolean isLocalVariable(JmmNode node, String name) {
        var closestSymbol = getClosestSymbol(node, name);
        if (closestSymbol.isEmpty()) {
            return (!this.getImports().contains(name) && !name.equals("this"));
        }

        var closestMethod = getClosestMethod(node);
        if (closestMethod.isEmpty()){
            return false;
        }

        return this.getLocalVariables(getMethodName(closestMethod.get())).stream().anyMatch(s -> s.getName().equals(name));
    }

    public static String getMethodName(JmmNode method) {
        if(method.getKind().equals("RegularMethod")){
            return method.getChildren().get(1).get("name");
        }else{
            return "main";
        }
    }
}
