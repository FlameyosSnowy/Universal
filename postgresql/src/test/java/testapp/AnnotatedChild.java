package testapp;

import io.github.flameyossnowy.universal.api.annotations.AutoIncrement;
import io.github.flameyossnowy.universal.api.annotations.Id;
import io.github.flameyossnowy.universal.api.annotations.ManyToOne;
import io.github.flameyossnowy.universal.api.annotations.OnDelete;
import io.github.flameyossnowy.universal.api.annotations.OnUpdate;
import io.github.flameyossnowy.universal.api.annotations.Repository;
import io.github.flameyossnowy.universal.api.annotations.enums.OnModify;

@Repository(name = "annotated_child")
public class AnnotatedChild {
    @Id
    @AutoIncrement
    private Long id;

    @ManyToOne
    @OnDelete(OnModify.RESTRICT)
    @OnUpdate(OnModify.NO_ACTION)
    private AnnotatedParent parent;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public AnnotatedParent getParent() {
        return parent;
    }

    public void setParent(AnnotatedParent parent) {
        this.parent = parent;
    }
}
