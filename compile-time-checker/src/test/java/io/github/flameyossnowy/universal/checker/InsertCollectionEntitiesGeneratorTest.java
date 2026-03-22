package io.github.flameyossnowy.universal.checker;

import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.TypeName;
import io.github.flameyossnowy.universal.api.annotations.Condition;
import io.github.flameyossnowy.universal.api.annotations.OnDelete;
import io.github.flameyossnowy.universal.api.annotations.OnUpdate;
import io.github.flameyossnowy.universal.api.annotations.enums.Consistency;
import io.github.flameyossnowy.universal.api.meta.JsonIndexModel;
import io.github.flameyossnowy.universal.api.meta.JsonStorageKind;
import io.github.flameyossnowy.universal.api.meta.RelationshipKind;
import io.github.flameyossnowy.universal.checker.generator.InsertCollectionEntitiesGenerator;
import org.junit.jupiter.api.Test;

import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;

import java.util.List;
import java.lang.reflect.Proxy;

import static org.junit.jupiter.api.Assertions.*;

class InsertCollectionEntitiesGeneratorTest {

    private static TypeMirror typeMirror(String name) {
        return (TypeMirror) Proxy.newProxyInstance(
            TypeMirror.class.getClassLoader(),
            new Class<?>[] { TypeMirror.class },
            (proxy, method, args) -> {
                if (method.getName().equals("toString")) {
                    return name;
                }
                if (method.getName().equals("getKind")) {
                    return TypeKind.DECLARED;
                }
                throw new UnsupportedOperationException(method.toString());
            }
        );
    }

    private static DeclaredType declaredType(String name, List<? extends TypeMirror> args) {
        return (DeclaredType) Proxy.newProxyInstance(
            DeclaredType.class.getClassLoader(),
            new Class<?>[] { DeclaredType.class },
            (proxy, method, methodArgs) -> {
                if (method.getName().equals("toString")) {
                    return name;
                }
                if (method.getName().equals("getTypeArguments")) {
                    return args;
                }
                if (method.getName().equals("getKind")) {
                    return TypeKind.DECLARED;
                }
                throw new UnsupportedOperationException(method.toString());
            }
        );
    }

    private static ArrayType arrayType(String name, TypeMirror component) {
        return (ArrayType) Proxy.newProxyInstance(
            ArrayType.class.getClassLoader(),
            new Class<?>[] { ArrayType.class },
            (proxy, method, methodArgs) -> {
                if (method.getName().equals("toString")) {
                    return name;
                }
                if (method.getName().equals("getKind")) {
                    return TypeKind.ARRAY;
                }
                if (method.getName().equals("getComponentType")) {
                    return component;
                }
                throw new UnsupportedOperationException(method.toString());
            }
        );
    }

    private static FieldModel field(String name, TypeMirror type, boolean relationship) {
        return new FieldModel(
            name,
            name,
            type,
            type != null ? type.toString() : null,
            false,
            false,
            true,
            relationship,
            (RelationshipKind) null,
            (Consistency) null,
            "get" + Character.toUpperCase(name.charAt(0)) + name.substring(1),
            "set" + Character.toUpperCase(name.charAt(0)) + name.substring(1),
            false,
            true,
            true,
            false,
            false,
            false,
            null,
            null,
            false,
            null,
            (Condition) null,
            (OnDelete) null,
            (OnUpdate) null,
            null,
            null,
            null,
            null,
            false,
            null,
            false,
            (JsonStorageKind) null,
            null,
            null,
            false,
            false,
            false,
            (List<JsonIndexModel>) null
        );
    }

    private static RepositoryModel repo(List<FieldModel> fields) {
        return new RepositoryModel(
            "io.example",
            "Entity",
            "io.example.Entity",
            "entity",
            false,
            null,
            null,
            fields,
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            0,
            false,
            null,
            false,
            null,
            null,
            null,
            null
        );
    }

    @Test
    void generatesInsertCollectionForListField() {
        TypeMirror elementType = typeMirror("java.lang.String");
        DeclaredType listType = declaredType(
            "java.util.List<java.lang.String>",
            List.of(elementType)
        );

        MethodSpec m = new InsertCollectionEntitiesGenerator().generate(
            repo(List.of(field("tags", listType, false))),
            ClassName.get("io.example", "Entity"),
            TypeName.INT
        );

        String src = m.toString();
        assertTrue(src.contains("handler.insertCollection"));
        assertTrue(src.contains("\"tags\""));
        assertTrue(src.contains("java.lang.String.class"));
    }

    @Test
    void generatesInsertMapAndInsertMultiMap() {
        // Map<String,Integer>
        TypeMirror keyType = typeMirror("java.lang.String");
        TypeMirror valueType = typeMirror("java.lang.Integer");
        DeclaredType mapType = declaredType(
            "java.util.Map<java.lang.String,java.lang.Integer>",
            List.of(keyType, valueType)
        );

        // Map<String,List<Long>> -> multi map
        TypeMirror mmKeyType = typeMirror("java.lang.String");
        TypeMirror mmElementType = typeMirror("java.lang.Long");
        DeclaredType mmValueType = declaredType(
            "java.util.List<java.lang.Long>",
            List.of(mmElementType)
        );
        DeclaredType multiMapType = declaredType(
            "java.util.Map<java.lang.String,java.util.List<java.lang.Long>>",
            List.of(mmKeyType, mmValueType)
        );

        MethodSpec m = new InsertCollectionEntitiesGenerator().generate(
            repo(List.of(
                field("counts", mapType, false),
                field("multi", multiMapType, false)
            )),
            ClassName.get("io.example", "Entity"),
            TypeName.INT
        );

        String src = m.toString();
        assertTrue(src.contains("handler.insertMap"));
        assertTrue(src.contains("\"counts\""));
        assertTrue(src.contains("java.lang.String.class"));
        assertTrue(src.contains("java.lang.Integer.class"));

        assertTrue(src.contains("handler.insertMultiMap"));
        assertTrue(src.contains("\"multi\""));
        assertTrue(src.contains("java.lang.Long.class"));
    }

    @Test
    void generatesInsertArrayInsideSupportsArraysNativelyGuard() {
        TypeMirror component = typeMirror("java.lang.String");
        ArrayType arrayType = arrayType("java.lang.String[]", component);

        MethodSpec m = new InsertCollectionEntitiesGenerator().generate(
            repo(List.of(field("names", arrayType, false))),
            ClassName.get("io.example", "Entity"),
            TypeName.INT
        );

        String src = m.toString();
        assertTrue(src.contains("if (!params.supportsArraysNatively())"));
        assertTrue(src.contains("handler.insertArray"));
        assertTrue(src.contains("\"names\""));
        assertTrue(src.contains("java.lang.String.class"));
    }

    @Test
    void ignoresRelationshipFields() {
        DeclaredType listType = declaredType(
            "java.util.List<java.lang.String>",
            List.of(typeMirror("java.lang.String"))
        );

        MethodSpec m = new InsertCollectionEntitiesGenerator().generate(
            repo(List.of(field("tags", listType, true))),
            ClassName.get("io.example", "Entity"),
            TypeName.INT
        );

        String src = m.toString();
        assertFalse(src.contains("handler.insertCollection"));
        assertFalse(src.contains("handler.insertMap"));
        assertFalse(src.contains("handler.insertArray"));
    }
}
