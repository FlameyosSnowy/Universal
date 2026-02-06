package io.github.flameyossnowy.universal.checker.processor;

import javax.lang.model.element.TypeElement;
import javax.lang.model.type.*;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class TypeMirrorUtils {
    private TypeMirrorUtils() {}

    public static TypeMirror typeOf(Class<?> clazz, Elements elements) {
        TypeElement el = elements.getTypeElement(clazz.getCanonicalName());
        return el == null ? null : el.asType();
    }

    public static String qualifiedName(TypeMirror mirror) {
        if (mirror == null) return null;

        if (mirror.getKind() == TypeKind.ARRAY) {
            ArrayType at = (ArrayType) mirror;
            return qualifiedName(at.getComponentType()) + "[]";
        }

        if (mirror instanceof DeclaredType dt) {
            TypeElement te = (TypeElement) dt.asElement();
            String raw = te.getQualifiedName().toString();
            if (dt.getTypeArguments().isEmpty()) return raw;

            List<String> args = new ArrayList<>();
            for (TypeMirror typeMirror : dt.getTypeArguments()) {
                String s = qualifiedName(typeMirror);
                args.add(s);
            }
            return raw + "<" + String.join(", ", args) + ">";
        }

        return mirror.toString();
    }

    public static boolean isCollectionValueMap(Types types, Elements elements, TypeMirror mirror) {
        if (!isMap(types, elements, mirror)) return false;

        // crude string-based check: Map<?, Collection<?>>
        if (mirror instanceof DeclaredType dt && !dt.getTypeArguments().isEmpty()) {
            TypeMirror valueType = dt.getTypeArguments().get(1);
            return isCollection(types, elements, valueType);
        }

        return false;
    }

    public static boolean isArray(TypeMirror mirror) {
        return mirror != null && mirror.getKind() == TypeKind.ARRAY;
    }

    public static boolean isList(Types types, Elements elements, TypeMirror mirror) {
        if (mirror == null) return false;
        TypeMirror unwrapped = unwrap(mirror, types);
        return isAssignableFrom(types, elements, unwrapped, List.class);
    }

    public static boolean isSet(Types types, Elements elements, TypeMirror mirror) {
        if (mirror == null) return false;
        TypeMirror unwrapped = unwrap(mirror, types);
        return isAssignableFrom(types, elements, unwrapped, Set.class);
    }

    public static boolean isCollection(Types types, Elements elements, TypeMirror mirror) {
        if (mirror == null) return false;
        TypeMirror unwrapped = unwrap(mirror, types);
        return isAssignableFrom(types, elements, unwrapped, Collection.class);
    }

    public static boolean isMap(Types types, Elements elements, TypeMirror mirror) {
        if (mirror == null) return false;
        return isAssignableFrom(types, elements, mirror, Map.class);
    }

    public static TypeMirror unwrap(TypeMirror mirror, Types types) {
        if (mirror == null) return null;
        return switch (mirror.getKind()) {
            case TYPEVAR -> unwrap(((TypeVariable) mirror).getUpperBound(), types);
            case WILDCARD -> {
                WildcardType wt = (WildcardType) mirror;
                TypeMirror extendsBound = wt.getExtendsBound();
                yield extendsBound != null ? unwrap(extendsBound, types) : mirror;
            }
            default -> mirror;
        };
    }

    public static boolean isAssignableFrom(
        Types types,
        Elements elements,
        TypeMirror mirror,
        Class<?> clazz
    ) {
        if (mirror == null) return false;
        TypeElement target = elements.getTypeElement(clazz.getCanonicalName());
        if (target == null) {
            return false;
        }

        // Erase both types to handle generics properly
        TypeMirror erasedMirror = types.erasure(mirror);
        TypeMirror erasedTarget = types.erasure(target.asType());

        return types.isAssignable(erasedMirror, erasedTarget);
    }
}
