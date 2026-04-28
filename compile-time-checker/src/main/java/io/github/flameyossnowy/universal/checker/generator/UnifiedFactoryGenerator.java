package io.github.flameyossnowy.universal.checker.generator;

import io.github.flameyossnowy.universal.checker.RepositoryModel;

import javax.annotation.processing.Filer;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import java.util.ArrayList;
import java.util.List;

/**
 * Orchestrator – the single public entry point for code generation.
 *
 * <p>This class owns <em>no</em> generation logic itself; it delegates each
 * concern to a dedicated sub-generator following the Single Responsibility
 * Principle:
 *
 * <ul>
 *   <li>{@link RepositoryModelGenerator}      – {@code *_RepositoryModel_Impl}</li>
 *   <li>{@link ObjectModelGenerator}          – {@code *_ObjectModel} (+ lazy proxies)</li>
 *   <li>{@link RelationshipLoaderGenerator}   – {@code *_RelationshipLoader}</li>
 *   <li>{@link ValueReaderGenerator}          – {@code *_ValueReader}</li>
 * </ul>
 *
 * <p>Internal method-generation is further split into:
 * <ul>
 *   <li>{@link InsertEntityGenerator}             – {@code insertEntity} method</li>
 *   <li>{@link InsertCollectionEntitiesGenerator} – {@code insertCollectionEntities} method</li>
 *   <li>{@link RelationshipMethodGenerator}       – oneToOne / oneToMany / manyToOne methods</li>
 *   <li>{@link CollectionLoaderMethodGenerator}   – loadList / loadSet / loadMap / loadArray methods</li>
 *   <li>{@link LazyProxyGenerator}                – lazy-loading proxy classes</li>
 * </ul>
 */
public final class UnifiedFactoryGenerator {

    private final Filer   filer;
    private final Types   types;
    private final Elements elements;

    private final RepositoryModelGenerator    repositoryModelGen;
    private final ObjectModelGenerator        objectModelGen;
    private final RelationshipLoaderGenerator relationshipLoaderGen;
    private final ValueReaderGenerator        valueReaderGen;

    private final List<String> qualifiedNames = new ArrayList<>(8);

    public UnifiedFactoryGenerator(ProcessingEnvironment env, Filer filer) {
        this.filer    = filer;
        this.types    = env.getTypeUtils();
        this.elements = env.getElementUtils();

        this.repositoryModelGen    = new RepositoryModelGenerator(filer, elements, types, env.getMessager());
        this.objectModelGen        = new ObjectModelGenerator(filer, types, elements, env.getMessager());
        this.relationshipLoaderGen = new RelationshipLoaderGenerator(filer);
        this.valueReaderGen        = new ValueReaderGenerator(types, elements, filer);
    }

    // ------------------------------------------------------------------

    /** Generate all artifacts for one repository and record their qualified names. */
    public void generate(RepositoryModel repo) {
        repositoryModelGen.generate(repo, qualifiedNames);
        objectModelGen.generate(repo, qualifiedNames);
        relationshipLoaderGen.generate(repo, qualifiedNames);
        valueReaderGen.generateValueReader(repo, qualifiedNames);
    }

    /** Returns the fully-qualified names of every class generated so far. */
    public List<String> getQualifiedNames() {
        return qualifiedNames;
    }
}
