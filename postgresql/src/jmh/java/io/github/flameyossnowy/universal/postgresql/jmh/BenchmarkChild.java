package io.github.flameyossnowy.universal.postgresql.jmh;

import io.github.flameyossnowy.universal.api.annotations.AutoIncrement;
import io.github.flameyossnowy.universal.api.annotations.Id;
import io.github.flameyossnowy.universal.api.annotations.ManyToOne;
import io.github.flameyossnowy.universal.api.annotations.Repository;
import jakarta.persistence.*;

@Repository(name = "benchmark_child")
@Entity
@Table(name = "benchmark_child")
public class BenchmarkChild {

    @Id @AutoIncrement
    @jakarta.persistence.Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "label", nullable = false)
    private String label;

    @ManyToOne
    @jakarta.persistence.ManyToOne(fetch = FetchType.LAZY)
    private BenchmarkParent parent;

    public Long                 getId()                          { return id; }
    public void                 setId(Long id)                   { this.id = id; }
    public String               getLabel()                       { return label; }
    public void                 setLabel(String label)           { this.label = label; }
    public BenchmarkParent getParent()                            { return parent; }
    public void                 setParent(BenchmarkParent c)     { this.parent = c; }
}