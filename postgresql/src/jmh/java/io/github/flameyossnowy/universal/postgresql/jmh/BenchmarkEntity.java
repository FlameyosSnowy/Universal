package io.github.flameyossnowy.universal.postgresql.jmh;

import io.github.flameyossnowy.universal.api.annotations.AutoIncrement;
import io.github.flameyossnowy.universal.api.annotations.Id;
import io.github.flameyossnowy.universal.api.annotations.Repository;
import jakarta.persistence.*;

/**
 * Single entity mapped to {@code benchmark_entities}.
 * Universal annotations drive schema creation; JPA annotations are read by Hibernate.
 */
@Repository(name = "benchmark_entities")
@Entity
@Table(name = "benchmark_entities")
public class BenchmarkEntity {

    @Id @AutoIncrement
    @jakarta.persistence.Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "score")
    private int score;

    @Column(name = "active")
    private boolean active;

    public Long    getId()               { return id; }
    public void    setId(Long id)        { this.id = id; }
    public String  getName()             { return name; }
    public void    setName(String name)  { this.name = name; }
    public int     getScore()            { return score; }
    public void    setScore(int score)   { this.score = score; }
    public boolean getActive()            { return active; }
    public void    setActive(boolean a)  { this.active = a; }
}
