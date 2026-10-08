package lox;

import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.Collections;

class LoxClass implements LoxCallable {
    final String name;
    final LoxClass superclass;
    private final Map<String, LoxFunction> methods;
    private final Map<String, LoxFunction> staticMethods;
    private final Map<String, LoxFunction> getters;

    LoxClass(String name, LoxClass superclass, Map<String, LoxFunction> methods,
             Map<String, LoxFunction> staticMethods, Map<String, LoxFunction> getters) {
        this.name = name;
        this.superclass = superclass;
        this.methods = methods;
        this.staticMethods = staticMethods;
        this.getters = getters;
    }

    LoxFunction findMethod(String name) {
        LoxFunction method = methods.get(name);
        if (method != null) return method;
        if (superclass != null) return superclass.findMethod(name);
        return null;
    }
    LoxFunction findMethodFromRoot(String name) {
        if (superclass != null) {
            LoxFunction inherited = superclass.findMethodFromRoot(name);
            if (inherited != null) return inherited;
        }
        return methods.get(name);
    }

    LoxFunction findNextMethod(String name, LoxClass owner) {
        List<LoxClass> chain = new ArrayList<>();
        for (LoxClass current = this; current != null; current = current.superclass) {
            chain.add(current);
        }
        Collections.reverse(chain);
        boolean pastOwner = false;
        for (LoxClass current : chain) {
            if (pastOwner) {
                LoxFunction method = current.methods.get(name);
                if (method != null) return method;
            }
            if (current == owner) pastOwner = true;
        }
        return null;
    }

    LoxFunction findNextStaticMethod(String name, LoxClass owner) {
        List<LoxClass> chain = new ArrayList<>();
        for (LoxClass current = this; current != null; current = current.superclass) {
            chain.add(current);
        }
        Collections.reverse(chain);
        boolean pastOwner = false;
        for (LoxClass current : chain) {
            if (pastOwner) {
                LoxFunction method = current.staticMethods.get(name);
                if (method != null) return method;
            }
            if (current == owner) pastOwner = true;
        }
        return null;
    }

    Object getStatic(Token name) {
        LoxFunction method = findStaticMethodFromRoot(name.lexeme);
        if (method != null) return method.bind(this);
        throw new RuntimeError(name, "Undefined property '" + name.lexeme + "'.");
    }

    private LoxFunction findStaticMethod(String name) {
        LoxFunction method = staticMethods.get(name);
        if (method != null) return method;
        return superclass == null ? null : superclass.findStaticMethod(name);
    }

    private LoxFunction findStaticMethodFromRoot(String name) {
        if (superclass != null) {
            LoxFunction inherited = superclass.findStaticMethodFromRoot(name);
            if (inherited != null) return inherited;
        }
        return staticMethods.get(name);
    }

    LoxFunction findGetter(String name) {
        if (superclass != null) {
            LoxFunction inherited = superclass.findGetter(name);
            if (inherited != null) return inherited;
        }
        return getters.get(name);
    }

    @Override
    public int arity() {
        LoxFunction initializer = findMethodFromRoot("init");
        return initializer == null ? 0 : initializer.arity();
    }

    @Override
    public Object call(Interpreter interpreter, List<Object> arguments) {
        LoxInstance instance = new LoxInstance(this);
        LoxFunction initializer = findMethodFromRoot("init");
        if (initializer != null) initializer.bind(instance).call(interpreter, arguments);
        return instance;
    }

    @Override
    public String toString() {
        return name;
    }
}