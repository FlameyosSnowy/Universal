package testapp;

import io.github.flameyossnowy.universal.api.annotations.Id;
import io.github.flameyossnowy.universal.api.annotations.Repository;
import io.github.flameyossnowy.universal.api.annotations.ResolveWith;

import java.util.*;

@SuppressWarnings("unused")
@Repository(name = "db_users")
public class DbUser {
    @Id
    private UUID id;

    private String username;
    private int age;

    @ResolveWith(PasswordConverter.class)
    private Password password;

    private List<String> hobbies;
    private Set<String> tags;
    private Map<String, String> metadata;

    public DbUser(UUID id, String username, int age, Password password, List<String> hobbies) {
        this.id = id;
        this.username = username;
        this.age = age;
        this.password = password;
        this.hobbies = hobbies;
    }

    public DbUser(UUID id, String username, int age, Password password,
                  List<String> hobbies, Set<String> tags, Map<String, String> metadata) {
        this.id = id;
        this.username = username;
        this.age = age;
        this.password = password;
        this.hobbies = hobbies;
        this.tags = tags;
        this.metadata = metadata;
    }

    public DbUser() {
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public int getAge() {
        return age;
    }

    public void setAge(int age) {
        this.age = age;
    }

    public List<String> getHobbies() {
        return hobbies;
    }

    public void setHobbies(List<String> hobbies) {
        this.hobbies = hobbies;
    }

    public Set<String> getTags() {
        return tags;
    }

    public void setTags(Set<String> tags) {
        this.tags = tags;
    }

    public Map<String, String> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, String> metadata) {
        this.metadata = metadata;
    }

    public Password getPassword() {
        return password;
    }

    public void setPassword(Password password) {
        this.password = password;
    }

    public String toString() {
        return "DbUser{" +
            "id=" + id +
            ", username='" + username + '\'' +
            ", age=" + age +
            ", password=" + password +
            ", hobbies=" + hobbies +
            ", tags=" + tags +
            ", metadata=" + metadata +
            '}';
    }
}