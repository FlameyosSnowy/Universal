package io.github.flameyossnowy.universal.postgresql.jmh;

import io.github.flameyossnowy.universal.api.annotations.AutoIncrement;
import io.github.flameyossnowy.universal.api.annotations.Id;
import io.github.flameyossnowy.universal.api.annotations.OneToMany;
import io.github.flameyossnowy.universal.api.annotations.Repository;
import jakarta.persistence.*;
import java.util.List;

@Repository(name = "benchmark_parents")
@Entity
@Table(name = "benchmark_parents")
public class BenchmarkParent {

    @Id @AutoIncrement
    @jakarta.persistence.Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "label", nullable = false)
    private String label;

    @OneToMany(mappedBy = BenchmarkChild.class, lazy = true)
    @jakarta.persistence.OneToMany(mappedBy = "parent", fetch = FetchType.LAZY)
    private List<BenchmarkChild> children;

    public Long                 getId()                          { return id; }
    public void                 setId(Long id)                   { this.id = id; }
    public String               getLabel()                       { return label; }
    public void                 setLabel(String label)           { this.label = label; }
    public List<BenchmarkChild> getChildren()                    { return children; }
    public void                 setChildren(List<BenchmarkChild> c) { this.children = c; }
}
