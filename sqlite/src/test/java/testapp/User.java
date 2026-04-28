package testapp;

import io.github.flameyossnowy.universal.api.annotations.Id;
import io.github.flameyossnowy.universal.api.annotations.JsonField;
import io.github.flameyossnowy.universal.api.annotations.JsonVersioned;
import io.github.flameyossnowy.universal.api.annotations.Repository;
import io.github.flameyossnowy.universal.api.annotations.ResolveWith;

import java.util.List;
import java.util.UUID;

@SuppressWarnings("unused")
@Repository(name = "users")
public class User {
    @Id
    private UUID id;

    private String username;
    private int age;

    @ResolveWith(PasswordConverter.class)
    private Password password;

    @JsonField
    @JsonVersioned
    private List<String> hobbies;

    public User(UUID id, String username, int age, Password password, List<String> hobbies) {
        this.id = id;
        this.username = username;
        this.age = age;
        this.password = password;
        this.hobbies = hobbies;
    }

    public User() {
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

    public Password getPassword() {
        return password;
    }

    public void setPassword(Password password) {
        this.password = password;
    }

    public String toString() {
        return "testapp.User{" +
            "id=" + id +
            ", username='" + username + '\'' +
            ", age=" + age +
            ", password=" + password +
            ", hobbies=" + hobbies +
            '}';
    }
}
