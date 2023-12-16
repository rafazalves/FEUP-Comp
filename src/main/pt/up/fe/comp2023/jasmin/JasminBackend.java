package pt.up.fe.comp2023.jasmin;

import pt.up.fe.comp.jmm.jasmin.JasminResult;
import pt.up.fe.comp.jmm.ollir.OllirResult;

public interface JasminBackend {
    JasminResult toJasmin(OllirResult ollirResult);
}
