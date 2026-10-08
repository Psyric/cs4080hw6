package lox;

import java.util.List;
import java.util.Map;

class LoxClass implements LoxCallable {
    final String name;
    private final Map<String, LoxFunction> methods;
    private final Map<String, LoxFunction> staticMethods;
    private final Map<String, LoxFunction> getters;

    LoxClass(String name, Map<String, LoxFunction> methods,
             Map<String, LoxFunction> staticMethods, Map<String, LoxFunction> getters) {
        this.name = name;
        this.methods = methods;
        this.staticMethods = staticMethods;
        this.getters = getters;
    }

    LoxFunction findMethod(String name) {
        return methods.get(name);
    }

    Object getStatic(Token name) {
        LoxFunction method = staticMethods.get(name.lexeme);
        if (method != null) return method.bind(this);
        throw new RuntimeError(name, "Undefined property '" + name.lexeme + "'.");
    }

    LoxFunction findGetter(String name) {
        return getters.get(name);
    }

    @Override
    public int arity() {
        LoxFunction initializer = findMethod("init");
        return initializer == null ? 0 : initializer.arity();
    }

    @Override
    public Object call(Interpreter interpreter, List<Object> arguments) {
        LoxInstance instance = new LoxInstance(this);
        LoxFunction initializer = findMethod("init");
        if (initializer != null) initializer.bind(instance).call(interpreter, arguments);
        return instance;
    }

    @Override
    public String toString() {
        return name;
    }
}