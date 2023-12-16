package pt.up.fe.comp2023.ollir;

import pt.up.fe.comp.jmm.analysis.table.Symbol;
import java.util.List;

public class OllirUtils {
    public static String getOllirType(String type) {

        if (type.equals("int")) {
            return "i32";
        }
        if (type.equals("boolean")) {
            return "bool";
        }
        if (type.equals("void")) {
            return "V";
        }
        return type;
    }
    public static String getOllirType(Symbol symbol) {
        return getOllirType(symbol.getType().getName());
    }

    public static String invoke(String invokeFunction, String className, String methodName, List<String> arguments, String ollirMethodType) {
        StringBuilder stringBuilder = new StringBuilder(invokeFunction + "(" + className + ", " + "\"" + methodName + "\"");
        if (!arguments.isEmpty()) {
            stringBuilder.append(", ");
            for (var arg : arguments) {
                stringBuilder.append(arg).append(", ");
            }
            stringBuilder.delete(stringBuilder.lastIndexOf(","), stringBuilder.length());
        }
        stringBuilder.append(").").append(ollirMethodType);
        return stringBuilder.toString();
    }

    public static String getField(String className, String field, String ollirType) {
        return "getfield(" + className + ", " + field + "." + ollirType + ")." + ollirType;
    }

    public static String putField(String className, String variableName, String value) {
        return "putfield(" + className + ", " + variableName + ", " + value + ").V";
    }

    public static String arrayLength(String variableName, String elementOllirType) {
        return "arraylength(" + variableName + ".array." + elementOllirType + ").i32";
    }

    public static String getSafeVariableName(String variableName) {
        final String[] reserved = {"ret", "putfield", "getstatic", "invokespecial", "invokestatic", "invokevirtual"};
        for (var r : reserved) {
            if (variableName.equals(r)) {
                return variableName + "_";
            }
        }
        return variableName;
    }
}
