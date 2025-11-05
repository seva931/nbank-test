package iteration2test;

import io.restassured.builder.ResponseSpecBuilder;
import models.CreateUserRequest;
import models.UpdateProfileRequest;
import models.UpdateProfileResponse;
import org.apache.http.HttpStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import requests.skelethon.Endpoint;
import requests.skelethon.requester.CrudRequester;
import requests.steps.AdminSteps;
import requests.steps.ProfileSteps;
import specs.RequestSpecs;
import specs.ResponseSpecs;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static models.comparison.ModelAssertions.assertThatModels;

// Кейсы для изменения имени в профиле: PUT /api/v1/customer/profile
public class UserRenameTest extends BaseTest {

    private CreateUserRequest user;

    @BeforeEach
    @DisplayName("Предусловие: создан пользователь с ролью USER")
    void initUser() {
        user = AdminSteps.createUser();
    }

    // Позитивные кейсы:
    static Stream<String> validNames() {
        return Stream.of(
                "john smith", "john Smith", "John smith", "John Smith", "JOHN smith", "john SMITH", "JOHN SMITH",
                "maria Ivanova", "ALpha Beta", "alpha BETA", "ALPHA beta", "bruce Wayne", "Peter parker",
                "PeTeR PaRkEr", "longname Longname"
        );
    }

    @ParameterizedTest
    @MethodSource("validNames")
    @DisplayName("Позитив: валидные значения name")
    void userCanSetValidFullName(String newName) {
        var userSpec = RequestSpecs.authAsUser(user.getUsername(), user.getPassword());

        UpdateProfileRequest payload = new UpdateProfileRequest(newName);
        UpdateProfileResponse resp = ProfileSteps.updateProfile(
                userSpec, ResponseSpecs.requestReturnsOK(), payload
        );

        // Сопоставление по конфигу (name -> customer.name, константы для message и role)
        assertThatModels(payload, resp).match();

        // Проверка связки с предусловием (username приходит из созданного пользователя)
        softly.assertThat(resp.getCustomer().getUsername()).isEqualTo(user.getUsername());
    }

    // Негативные кейсы: невалидные строки name
    static Stream<String> invalidNames() {
        return Stream.of(
                "", " ", "   ", "John", "John  Smith", " John Smith", "John Smith ", "John  ", "  Smith",
                "John  Michael Smith", "John-Smith", "John_Smith", "John Smith Jr", "J0hn Smith", "John Sm1th",
                "John Sm!th", "John Smi th", "Jo hn Smith", "John  Smith  ", "123 456", "JohnSmith", "John  ", "  John   Smith  "
        );
    }

    @ParameterizedTest
    @MethodSource("invalidNames")
    @DisplayName("Негатив: невалидные строки name")
    void userCannotSetInvalidFullName(String invalidName) {
        var userSpec = RequestSpecs.authAsUser(user.getUsername(), user.getPassword());

        new CrudRequester(
                userSpec,
                Endpoint.PROFILE_UPDATE,
                new ResponseSpecBuilder().expectStatusCode(HttpStatus.SC_BAD_REQUEST).build()
        ).update(0L, new UpdateProfileRequest(invalidName));
    }

    // Негативные кейсы: невалидный payload
    static Stream<Object> invalidBodies() {
        Map<String, Object> nameNull = new HashMap<>();
        nameNull.put("name", null);
        Map<String, Object> nameNumber = new HashMap<>();
        nameNumber.put("name", 12345);
        Map<String, Object> nameBool = new HashMap<>();
        nameBool.put("name", true);
        Map<String, Object> nameArray = new HashMap<>();
        nameArray.put("name", List.of("John", "Smith"));
        Map<String, Object> nameObject = new HashMap<>();
        nameObject.put("name", Map.of("first", "John", "last", "Smith"));
        String rawJsonNull = "null";
        return Stream.of(nameNull, nameNumber, nameBool, nameArray, nameObject, rawJsonNull);
    }

    @ParameterizedTest
    @MethodSource("invalidBodies")
    @DisplayName("Негатив: невалидный payload (тип/структура)")
    void userCannotSetInvalidFullNameByPayload(Object body) {
        var userSpec = RequestSpecs.authAsUser(user.getUsername(), user.getPassword());

        var bad = new ResponseSpecBuilder()
                .expectStatusCode(HttpStatus.SC_BAD_REQUEST)
                .build();

        new CrudRequester(userSpec, Endpoint.PROFILE_UPDATE, bad)
                .update(0L, body);
    }
}