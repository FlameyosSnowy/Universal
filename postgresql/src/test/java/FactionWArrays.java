import io.github.flameyossnowy.universal.api.annotations.*;

import java.util.Arrays;

@Repository(name = "FactionsWArrays")
public class FactionWArrays {
    @Id
    @AutoIncrement
    private Long id;

    private String name;

    private String[] members;

    public void setName(String name) {
        this.name = name;
    }

    public void setMembers(String[] members) {
        this.members = members;
    }

    public String[] getMembers() {
        return members;
    }

    public String getName() {
        return name;
    }

    public Long getId() {
        return id;
    }

    public int hashCode() {
        return id.hashCode();
    }

    public boolean equals(Object o) {
        if (!(o instanceof FactionWArrays)) return false;
        System.out.println(this);
        return id.equals(((FactionWArrays) o).id);
    }

    public void setId(long id) {
        this.id = id;
    }

    public String toString() {
        return "FactionWArrays{" +
                "id=" + id +
                ", name='" + name + '\'' +
                ", members=" + Arrays.toString(members) +
                '}';
    }
}
