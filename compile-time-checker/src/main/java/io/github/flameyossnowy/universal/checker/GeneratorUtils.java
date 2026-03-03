package io.github.flameyossnowy.universal.checker;

import com.squareup.javapoet.*;

import javax.annotation.processing.Filer;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import java.io.IOException;
import java.util.List;

/**
 * Stateless utility methods shared across all sub-generators.
 * Nothing here has side-effects beyond writing a file.
 */
public final class GeneratorUtils {

    private GeneratorUtils() {}

    // ------------------------------------------------------------------ I/O

    public static void write(String pkg, TypeSpec spec, Filer filer) {
        try {
            JavaFile.builder(pkg, spec).build().writeTo(filer);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    // --------------------------------------------------------- Code helpers

    public static void addFields(TypeSpec.Builder type, FieldSpec.Builder... fields) {
        for (FieldSpec.Builder f : fields) {
            type.addField(f.build());
        }
    }

    public static MethodSpec constantMethod(String name, Class<?> returnType, Object value) {
        return MethodSpec.methodBuilder(name)
            .addModifiers(javax.lang.model.element.Modifier.PUBLIC)
            .returns(returnType)
            .addStatement("return $L", literal(value))
            .build();
    }

    public static String literal(Object value) {
        if (value instanceof String)    return "\"" + value + "\"";
        if (value instanceof Character) return "'" + value + "'";
        if (value instanceof Boolean || value instanceof Number) return value.toString();
        throw new IllegalArgumentException("Unsupported constant type: " + value.getClass());
    }

    // --------------------------------------------------- Type introspection

    public static boolean isListType(TypeMirror type) {
        String n = type.toString();
        return n.startsWith("java.util.List") || n.startsWith("java.util.ArrayList");
    }

    public static boolean isSetType(TypeMirror type) {
        String n = type.toString();
        return n.startsWith("java.util.Set") || n.startsWith("java.util.HashSet");
    }

    public static boolean isMapType(TypeMirror type) {
        String n = type.toString();
        return n.startsWith("java.util.Map") || n.startsWith("java.util.HashMap");
    }

    public static boolean isCollectionOrMapType(Types types, Elements elements, TypeMirror type) {
        return io.github.flameyossnowy.universal.checker.processor.TypeMirrorUtils
                   .isCollection(types, elements, type)
               || io.github.flameyossnowy.universal.checker.processor.TypeMirrorUtils
                   .isMap(types, elements, type);
    }

    /** Returns the Nth type argument of a declared type, or {@code null}. */
    public static TypeMirror genericArg(TypeMirror type, int index) {
        if (type instanceof DeclaredType dt) {
            List<? extends TypeMirror> args = dt.getTypeArguments();
            if (args.size() > index) return args.get(index);
        }
        return null;
    }

    public static boolean isMultiMap(TypeMirror valueType) {
        if (valueType == null) return false;
        String n = valueType.toString();
        return n.startsWith("java.util.List")
            || n.startsWith("java.util.Set")
            || n.startsWith("java.util.Collection");
    }

    // -------------------------------------------------- Qualified name helpers

    public static String qualifiedName(String pkg, String simpleName) {
        return (pkg != null && !pkg.isEmpty()) ? pkg + "." + simpleName : simpleName;
    }
}
