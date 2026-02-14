package io.github.flameyossnowy.universal.checker.generator;

import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;
import com.squareup.javapoet.WildcardTypeName;
import io.github.flameyossnowy.universal.checker.RepositoryModel;

import javax.lang.model.element.Modifier;
import java.util.Objects;

public final class RepositoryServicesGenerator {
    private RepositoryServicesGenerator() {}

    public static void generateAuditLogger(TypeSpec.Builder type, RepositoryModel repo) {
        ClassName auditInterface = ClassName.get(
            "io.github.flameyossnowy.universal.api.listener",
            "AuditLogger"
        );

        TypeName entityType = ClassName.bestGuess(repo.entityQualifiedName());
        TypeName auditType = ParameterizedTypeName.get(auditInterface, entityType);

        FieldSpec field;
        if (repo.hasAuditLogger()) {
            field = FieldSpec.builder(auditType, "AUDIT_LOGGER",
                    Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL)
                .initializer("new $T()",
                    ClassName.bestGuess(Objects.requireNonNull(repo.auditLoggerQualifiedName())))
                .build();
        } else {
            field = FieldSpec.builder(auditType, "AUDIT_LOGGER",
                    Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL)
                .initializer("null")
                .build();
        }

        type.addField(field);
    }

    public static void generateExceptionHandler(TypeSpec.Builder type, RepositoryModel repo) {
        ClassName handlerInterface = ClassName.get(
            "io.github.flameyossnowy.universal.api.exceptions.handler",
            "ExceptionHandler"
        );

        TypeName entityType = ClassName.bestGuess(repo.entityQualifiedName());
        TypeName idType = repo.primaryKeys().isEmpty()
            ? ClassName.get(Object.class)
            : TypeName.get(repo.primaryKeys().getFirst().type());

        TypeName handlerType = ParameterizedTypeName.get(
            handlerInterface,
            entityType,
            idType.box(),
            WildcardTypeName.subtypeOf(Object.class)
        );

        FieldSpec field;
        if (repo.hasExceptionHandler()) {
            field = FieldSpec.builder(handlerType, "EXCEPTION_HANDLER",
                    Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL)
                .initializer("new $T()",
                    ClassName.bestGuess(Objects.requireNonNull(repo.exceptionHandlerQualifiedName())))
                .build();
        } else {
            field = FieldSpec.builder(handlerType, "EXCEPTION_HANDLER",
                    Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL)
                .initializer("null")
                .build();
        }

        type.addField(field);
    }

    public static void generateLifecycleListener(TypeSpec.Builder type, RepositoryModel repo) {
        ClassName listenerInterface = ClassName.get(
            "io.github.flameyossnowy.universal.api.listener",
            "EntityLifecycleListener"
        );

        TypeName entityType = ClassName.bestGuess(repo.entityQualifiedName());
        TypeName listenerType = ParameterizedTypeName.get(listenerInterface, entityType);

        FieldSpec field;
        if (repo.hasLifecycleListener()) {
            field = FieldSpec.builder(listenerType, "LIFECYCLE",
                    Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL)
                .initializer("new $T()",
                    ClassName.bestGuess(Objects.requireNonNull(repo.lifecycleListenerQualifiedName())))
                .build();
        } else {
            field = FieldSpec.builder(listenerType, "LIFECYCLE",
                    Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL)
                .initializer("new $T() {}", listenerInterface)
                .build();
        }

        type.addField(field);
    }
}
