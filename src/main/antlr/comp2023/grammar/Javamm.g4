grammar Javamm;

@header {
    package pt.up.fe.comp2023;
}

INTEGER : [0-9]+ ;
ID : [a-zA-Z_$][a-zA-Z_$0-9]* ;

WS : [ \t\n\r\f]+ -> skip ;
COMMENT : '/*' .*? '*/' -> skip ;
LINE_COMMENT : '//' ~[\r\n]* -> skip ;

program
    : importDeclaration* classDeclaration EOF
    ;

importDeclaration
    : WS* 'import' path ('.' path)* ';'
    ;

classDeclaration
    : 'class' className=ID ('extends' parent=ID)? '{' varDeclaration* methodDeclaration* '}'
    ;

varDeclaration
    : type var=ID ';'
    ;

methodDeclaration
    : ('public')? returnType methodName=ID '(' (param (',' param)* )? ')' '{' varDeclaration* statement* 'return' expression ';' '}'  #InstanceMethod
    | ('public')? 'static' 'void' methodName='main' '(' 'String' '[' ']' paramName=ID ')' '{' varDeclaration* statement* '}' (';')?   #MainMethod
    ;

returnType
    : type
    ;

path
    : value=ID
    ;

param
    : type name=ID
    ;

type
    : value='int' '['']'                #Array
    | value='boolean'                   #Single
    | value='int'                       #Single
    | value='String'                    #Single
    | value=ID                          #Single
    ;

statement
    : '{' statement* '}'                                        #StmtBlock
    | 'if' '(' expression ')' statement 'else' statement        #Conditional
    | 'while' '(' expression ')' statement                      #WhileLoop
    | expression ';'                                            #ExprStmt
    | var=ID '=' expression ';'                                 #Assignment
    | var=ID '[' expression ']' '=' expression ';'              #ArrayAssignment
    ;

expression
    : '(' expression ')'                     #PrioExpr
    | expression '[' expression ']'          #ArrayExpr
    | expression '.' call                    #MethodCall
    | expression '.' 'length'                #Length
    | op='!' expression                      #UnaryOp
    | expression op=('*' | '/') expression   #BinaryOp
    | expression op=('+' | '-') expression   #BinaryOp
    | expression op=('>' | '<') expression   #BinaryOp
    | expression op=('||' | '&&') expression #BinaryOp
    | 'new' value='int' '[' expression ']'   #ArrayInit
    | 'new' className=ID '(' ')'             #Constructor
    | value=INTEGER                          #Integer
    | value='true'                           #BoolExpr
    | value='false'                          #BoolExpr
    | value='this'                           #Reference
    | value=ID                               #Identifier
    ;

call
    : methodName=ID '(' (expression (',' expression)*)? ')'
    ;
