package iteration2test;

import generators.RandomData;
import models.CreateUserRequest;
import models.UpdateProfileRequest;
import models.UpdateProfileResponse;
import models.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import requests.AdminCreateUserRequester;
import specs.RequestSpecs;
import specs.ResponseSpecs;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static io.restassured.RestAssured.given;

// Кейсы для изменения имени в профиле: PUT /api/v1/customer/profile
public class UserRenameTest extends BaseTest {

    private String username;
    private String password;

    @BeforeEach
    @DisplayName("Предусловие: создан пользователь с ролью USER")
    void initUser() {
        username = RandomData.getUsername();
        password = RandomData.getPassword();

        new AdminCreateUserRequester(
                RequestSpecs.adminSpec(),
                ResponseSpecs.entityWasCreated()
        ).post(CreateUserRequest.builder()
                .username(username)
                .password(password)
                .role(UserRole.USER)
                .build());
    }

    // Позитивные кейсы:
    static Stream<String> validNames() {
        return Stream.of(
                "john smith",
                "john Smith",
                "John smith",
                "John Smith",
                "JOHN smith",
                "john SMITH",
                "JOHN SMITH",
                "maria Ivanova",
                "ALpha Beta",
                "alpha BETA",
                "ALPHA beta",
                "bruce Wayne",
                "Peter parker",
                "PeTeR PaRkEr",
                "longname Longname"
        );
    }

    @ParameterizedTest
    @MethodSource("validNames")
    @DisplayName("Позитив: валидные значения name")
    void userCanSetValidFullName(String newName) {
        var userSpec = RequestSpecs.authAsUser(username, password);

        UpdateProfileResponse resp =
                given().spec(userSpec)
                        .body(new UpdateProfileRequest(newName))
                        .when()
                        .put("/api/v1/customer/profile")
                        .then()
                        .spec(ResponseSpecs.requestReturnsOK())
                        .extract()
                        .as(UpdateProfileResponse.class);

        softly.assertThat(resp.getMessage()).isEqualTo("Profile updated successfully");
        softly.assertThat(resp.getCustomer().getUsername()).isEqualTo(username);
        softly.assertThat(resp.getCustomer().getName()).isEqualTo(newName);
        softly.assertThat(resp.getCustomer().getRole()).isEqualTo("USER");
    }

    // Негативные кейсы:
    static Stream<String> invalidNames() {
        return Stream.of(
                "",
                " ",
                "   ",
                "John",
                "John  Smith",
                " John Smith",
                "John Smith ",
                "John  ",
                "  Smith",
                "John  Michael Smith",
                "John-Smith",
                "John_Smith",
                "John Smith Jr",
                "J0hn Smith",
                "John Sm1th",
                "John Sm!th",
                "John Smi th",
                "Jo hn Smith",
                "John  Smith  ",
                "123 456",
                "JohnSmith",
                "John  ",
                "  John   Smith  "
        );
    }

    @ParameterizedTest
    @MethodSource("invalidNames")
    @DisplayName("Негатив: невалидные строки name")
    void userCannotSetInvalidFullName(String invalidName) {
        var userSpec = RequestSpecs.authAsUser(username, password);

        given().spec(userSpec)
                .body(new UpdateProfileRequest(invalidName))
                .when()
                .put("/api/v1/customer/profile")
                .then()
                .statusCode(400);
    }

    // Негативные кейсы: валидация поля name
    static Stream<Object> invalidBodies() {
        // name = null
        Map<String, Object> nameNull = new HashMap<>();
        nameNull.put("name", null);

        // name = число
        Map<String, Object> nameNumber = new HashMap<>();
        nameNumber.put("name", 12345);

        // name = булево
        Map<String, Object> nameBool = new HashMap<>();
        nameBool.put("name", true);

        // name = массив
        Map<String, Object> nameArray = new HashMap<>();
        nameArray.put("name", List.of("John", "Smith"));

        // name = объект
        Map<String, Object> nameObject = new HashMap<>();
        nameObject.put("name", Map.of("first", "John", "last", "Smith"));


        // сырой "null" (JSON literal), если сервер обрабатывает тело как null
        String rawJsonNull = "null";

        return Stream.of(
                nameNull,
                nameNumber,
                nameBool,
                nameArray,
                nameObject,
                rawJsonNull
        );
    }

    @ParameterizedTest
    @MethodSource("invalidBodies")
    @DisplayName("Негатив: невалидный payload (тип/структура)")
    void userCannotSetInvalidFullNameByPayload(Object body) {
        var userSpec = RequestSpecs.authAsUser(username, password);

        given().spec(userSpec)
                .body(body)
                .when()
                .put("/api/v1/customer/profile")
                .then()
                .statusCode(400);
    }
}
