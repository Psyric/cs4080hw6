package lox;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

class Interpreter implements Expr.Visitor<Object>, Stmt.Visitor<Object> {
    private static class LocalAddress {
        final int depth;
        final int slot;

        LocalAddress(int depth, int slot) {
            this.depth = depth;
            this.slot = slot;
        }
    }

    final Environment globals = new Environment();
    private Environment environment = globals;
    private final Map<Expr, LocalAddress> locals = new HashMap<>();
    private static class BreakException extends RuntimeException {}

    Interpreter() {
        globals.define("clock", new LoxCallable() {
            @Override
            public int arity() {
                return 0;
            }

            @Override
            public Object call(Interpreter interpreter, List<Object> arguments) {
                return (double) System.currentTimeMillis() / 1000.0;
            }

            @Override
            public String toString() {
                return "<native fn>";
            }
        });
    }

    void interpret(List<Stmt> statements) {
        try {
            for (Stmt statement : statements) {
                execute(statement);
            }
        } catch (RuntimeError error) {
            Lox.runtimeError(error);
        }
    }

    void execute(Stmt stmt) {
        stmt.accept(this);
    }

    void resolve(Expr expr, int depth, int slot) {
        locals.put(expr, new LocalAddress(depth, slot));
    }

    @Override
    public Object visitLiteralExpr(Expr.Literal expr) {
        return expr.value;
    }

    @Override
    public Object visitGroupingExpr(Expr.Grouping expr) {
        return evaluate(expr.expression);
    }

    @Override
    public Object visitUnaryExpr(Expr.Unary expr) {
        Object right = evaluate(expr.right);

        switch (expr.operator.type) {
            case BANG:
                return !isTruthy(right);
            case MINUS:
                checkNumberOperand(expr.operator, right);
                return -(double)right;
        }

        return null;
    }

    @Override
    public Object visitBinaryExpr(Expr.Binary expr) {
        Object left = evaluate(expr.left);
        Object right = evaluate(expr.right);

        switch (expr.operator.type) {
            case GREATER:
                checkNumberOperands(expr.operator, left, right);
                return (double)left > (double)right;
            case GREATER_EQUAL:
                checkNumberOperands(expr.operator, left, right);
                return (double)left >= (double)right;
            case LESS:
                checkNumberOperands(expr.operator, left, right);
                return (double)left < (double)right;
            case LESS_EQUAL:
                checkNumberOperands(expr.operator, left, right);
                return (double)left <= (double)right;
            case BANG_EQUAL:
                return !isEqual(left, right);
            case EQUAL_EQUAL:
                return isEqual(left, right);
            case MINUS:
                checkNumberOperands(expr.operator, left, right);
                return (double)left - (double)right;
            case PLUS:
                if (left instanceof String || right instanceof String) {
                    return stringify(left) + stringify(right);
                }
                if (left instanceof Double && right instanceof Double) {
                    return (double)left + (double)right;
                }
                throw new RuntimeError(expr.operator, "Operands must be two numbers or two strings.");
            case SLASH:
                checkNumberOperands(expr.operator, left, right);
                if ((double)right == 0.0) {
                    throw new RuntimeError(expr.operator, "Division by zero.");
                }
                return (double)left / (double)right;
            case STAR:
                checkNumberOperands(expr.operator, left, right);
                return (double)left * (double)right;
        }

        return null;
    }

    @Override
    public Object visitCallExpr(Expr.Call expr) {
        Object callee = evaluate(expr.callee);
        List<Object> arguments = new ArrayList<>();
        for (Expr argument : expr.arguments) {
            arguments.add(evaluate(argument));
        }

        if (!(callee instanceof LoxCallable)) {
            throw new RuntimeError(expr.paren, "Can only call functions.");
        }

        LoxCallable function = (LoxCallable) callee;
        if (arguments.size() != function.arity()) {
            throw new RuntimeError(expr.paren, "Expected " + function.arity() +
                    " arguments but got " + arguments.size() + ".");
        }

        return function.call(this, arguments);
    }

    @Override
    public Object visitGetExpr(Expr.Get expr) {
        Object object = evaluate(expr.object);
        if (object instanceof LoxInstance) {
            return ((LoxInstance) object).get(expr.name, this);
        }
        if (object instanceof LoxClass) {
            return ((LoxClass) object).getStatic(expr.name);
        }
        throw new RuntimeError(expr.name, "Only instances and classes have properties.");
    }

    @Override
    public Object visitSetExpr(Expr.Set expr) {
        Object object = evaluate(expr.object);
        if (!(object instanceof LoxInstance)) {
            throw new RuntimeError(expr.name, "Only instances have fields.");
        }
        Object value = evaluate(expr.value);
        ((LoxInstance) object).set(expr.name, value);
        return value;
    }

    @Override
    public Object visitThisExpr(Expr.This expr) {
        return lookUpVariable(expr.keyword, expr);
    }

    @Override
    public Object visitLogicalExpr(Expr.Logical expr) {
        Object left = evaluate(expr.left);

        if (expr.operator.type == TokenType.OR) {
            if (isTruthy(left)) return left;
        } else {
            if (!isTruthy(left)) return left;
        }

        return evaluate(expr.right);
    }

    @Override
    public Object visitCommaExpr(Expr.Comma expr) {
        evaluate(expr.left);
        return evaluate(expr.right);
    }

    @Override
    public Object visitTernaryExpr(Expr.Ternary expr) {
        Object condition = evaluate(expr.condition);

        if (isTruthy(condition)) {
            return evaluate(expr.thenBranch);
        } else {
            return evaluate(expr.elseBranch);
        }
    }

    @Override
    public Object visitVariableExpr(Expr.Variable expr) {
        return lookUpVariable(expr.name, expr);
    }

    @Override
    public Object visitAssignExpr(Expr.Assign expr) {
        Object value = evaluate(expr.value);
        LocalAddress address = locals.get(expr);
        if (address != null) {
            environment.assignAt(address.depth, address.slot, value);
        } else {
            globals.assign(expr.name, value);
        }
        return value;
    }

    @Override
    public Object visitFunctionExpr(Expr.Function expr) {
        return new LoxFunction(null, expr.params, expr.body, environment, expr.localCount);
    }

    @Override
    public Object visitBlockStmt(Stmt.Block stmt) {
        executeBlock(stmt.statements, new Environment(environment, stmt.localCount));
        return null;
    }

    void executeBlock(List<Stmt> statements, Environment blockEnvironment) {
        Environment previous = environment;
        try {
            environment = blockEnvironment;
            for (Stmt statement : statements) {
                execute(statement);
            }
        } finally {
            environment = previous;
        }
    }

    @Override
    public Object visitBreakStmt(Stmt.Break stmt) {
        throw new BreakException();
    }

    @Override
    public Object visitClassStmt(Stmt.Class stmt) {
        if (stmt.slot >= 0) {
            environment.defineAt(stmt.slot, null);
        } else {
            globals.define(stmt.name.lexeme, null);
        }

        Map<String, LoxFunction> methods = new HashMap<>();
        Map<String, LoxFunction> staticMethods = new HashMap<>();
        Map<String, LoxFunction> getters = new HashMap<>();
        for (Stmt.Function method : stmt.methods) {
            boolean initializer = !method.isStatic && method.name.lexeme.equals("init");
            LoxFunction function = new LoxFunction(method.name.lexeme, method.params,
                    method.body, environment, method.localCount, initializer);
            if (method.isStatic) {
                staticMethods.put(method.name.lexeme, function);
            } else if (method.isGetter) {
                getters.put(method.name.lexeme, function);
            } else {
                methods.put(method.name.lexeme, function);
            }
        }

        LoxClass klass = new LoxClass(stmt.name.lexeme, methods, staticMethods, getters);
        if (stmt.slot >= 0) {
            environment.assignAt(0, stmt.slot, klass);
        } else {
            globals.assign(stmt.name, klass);
        }
        return null;
    }

    @Override
    public Object visitExpressionStmt(Stmt.Expression stmt) {
        evaluate(stmt.expression);
        return null;
    }

    @Override
    public Object visitFunctionStmt(Stmt.Function stmt) {
        LoxFunction function = new LoxFunction(stmt.name.lexeme, stmt.params, stmt.body,
                environment, stmt.localCount);
        if (stmt.slot >= 0) {
            environment.defineAt(stmt.slot, function);
        } else {
            globals.define(stmt.name.lexeme, function);
        }
        return null;
    }

    @Override
    public Object visitIfStmt(Stmt.If stmt) {
        if (isTruthy(evaluate(stmt.condition))) {
            execute(stmt.thenBranch);
        } else if (stmt.elseBranch != null) {
            execute(stmt.elseBranch);
        }
        return null;
    }

    @Override
    public Object visitPrintStmt(Stmt.Print stmt) {
        Object value = evaluate(stmt.expression);
        System.out.println(stringify(value));
        return null;
    }

    @Override
    public Object visitReturnStmt(Stmt.Return stmt) {
        Object value = null;
        if (stmt.value != null) value = evaluate(stmt.value);
        throw new ReturnException(value);
    }

    @Override
    public Object visitVarStmt(Stmt.Var stmt) {
        Object value = null;
        if (stmt.initializer != null) {
            value = evaluate(stmt.initializer);
        }
        if (stmt.slot >= 0) {
            environment.defineAt(stmt.slot, value);
        } else {
            globals.define(stmt.name.lexeme, value);
        }
        return null;
    }

    @Override
    public Object visitWhileStmt(Stmt.While stmt) {
        while (isTruthy(evaluate(stmt.condition))) {
            try {
                execute(stmt.body);
            } catch (BreakException error) {
                break;
            }
        }
        return null;
    }

    private Object evaluate(Expr expr) {
        return expr.accept(this);
    }

    private boolean isTruthy(Object object) {
        if (object == null) return false;
        if (object instanceof Boolean) return (boolean)object;
        return true;
    }

    private Object lookUpVariable(Token name, Expr expr) {
        LocalAddress address = locals.get(expr);
        if (address != null) {
            return environment.getAt(address.depth, address.slot, name);
        }
        return globals.get(name);
    }

    private boolean isEqual(Object a, Object b) {
        if (a == null && b == null) return true;
        if (a == null) return false;
        return a.equals(b);
    }

    private void checkNumberOperand(Token operator, Object operand) {
        if (operand instanceof Double) return;
        throw new RuntimeError(operator, "Operand must be a number.");
    }

    private void checkNumberOperands(Token operator, Object left, Object right) {
        if (left instanceof Double && right instanceof Double) return;
        throw new RuntimeError(operator, "Operands must be numbers.");
    }

    private String stringify(Object object) {
        if (object == null) return "nil";

        if (object instanceof Double) {
            String text = object.toString();
            if (text.endsWith(".0")) {
                text = text.substring(0, text.length() - 2);
            }
            return text;
        }

        return object.toString();
    }

    public void interpretExpression(Expr expression) {
        try {
            Object value = evaluate(expression);
            System.out.println(stringify(value));
        } catch (RuntimeError error) {
            Lox.runtimeError(error);
        }
    }
}