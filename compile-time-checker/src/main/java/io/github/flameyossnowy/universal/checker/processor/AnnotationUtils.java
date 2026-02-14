package io.github.flameyossnowy.universal.checker.processor;

import io.github.flameyossnowy.universal.api.annotations.enums.Consistency;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;
import java.util.ArrayList;
import java.util.List;

public final class AnnotationUtils {
    private AnnotationUtils() {}

    public static boolean hasAnnotation(Element e, String fqcn) {
        for (AnnotationMirror am : e.getAnnotationMirrors()) {
            if (((TypeElement) am.getAnnotationType().asElement())
                .getQualifiedName().contentEquals(fqcn)) {
                return true;
            }
        }
        return false;
    }

    public static List<AnnotationMirror> getAnnotations(Element e, String fqcn) {
        List<AnnotationMirror> out = new ArrayList<>(8);
        for (AnnotationMirror am : e.getAnnotationMirrors()) {
            if (((TypeElement) am.getAnnotationType().asElement())
                .getQualifiedName().contentEquals(fqcn)) {
                out.add(am);
            }
        }
        return out;
    }

    public static AnnotationMirror getAnnotationMirror(Element element, String fqcn) {
        for (AnnotationMirror am : element.getAnnotationMirrors()) {
            TypeElement ann = (TypeElement) am.getAnnotationType().asElement();
            if (ann.getQualifiedName().contentEquals(fqcn)) {
                return am;
            }
        }
        return null;
    }

    public static TypeMirror getClassValue(AnnotationMirror am, String name) {
        if (am == null) {
            return null;
        }
        for (var e : am.getElementValues().entrySet()) {
            if (e.getKey().getSimpleName().contentEquals(name)) {
                return (TypeMirror) e.getValue().getValue();
            }
        }
        return null;
    }

    public static boolean getBooleanValue(AnnotationMirror am, String name) {
        if (am == null) {
            return false;
        }
        for (var e : am.getElementValues().entrySet()) {
            if (e.getKey().getSimpleName().contentEquals(name)) {
                return (boolean) e.getValue().getValue();
            }
        }
        return false;
    }

    public static String getStringValue(AnnotationMirror am, String name) {
        if (am == null) {
            return null;
        }
        for (var e : am.getElementValues().entrySet()) {
            if (e.getKey().getSimpleName().contentEquals(name)) {
                Object v = e.getValue().getValue();
                return v == null ? null : v.toString();
            }
        }
        return null;
    }

    public static String getEnumValueName(AnnotationMirror am, String name) {
        if (am == null) {
            return null;
        }
        for (var e : am.getElementValues().entrySet()) {
            if (e.getKey().getSimpleName().contentEquals(name)) {
                Object v = e.getValue().getValue();
                if (v == null) {
                    return null;
                }
                String s = v.toString();
                int lastDot = s.lastIndexOf('.');
                return lastDot == -1 ? s : s.substring(lastDot + 1);
            }
        }
        return null;
    }

    public static Consistency getConsistencyValue(AnnotationMirror am, String name) {
        if (am == null) {
            return Consistency.NONE;
        }
        for (var e : am.getElementValues().entrySet()) {
            if (e.getKey().getSimpleName().contentEquals(name)) {
                return (Consistency) e.getValue().getValue();
            }
        }
        return Consistency.NONE;
    }

    public static List<String> getStringArrayValue(AnnotationMirror am) {
        List<String> out = new ArrayList<>(8);
        for (var e : am.getElementValues().entrySet()) {
            if (e.getKey().getSimpleName().contentEquals("fields")) {
                @SuppressWarnings("unchecked")
                List<? extends AnnotationValue> vals =
                    (List<? extends AnnotationValue>) e.getValue().getValue();
                for (AnnotationValue v : vals) {
                    out.add(v.getValue().toString());
                }
            }
        }
        return out;
    }
}
