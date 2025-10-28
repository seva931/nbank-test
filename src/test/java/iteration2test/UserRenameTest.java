package iteration2test;

import io.restassured.RestAssured;
import io.restassured.filter.log.RequestLoggingFilter;
import io.restassured.filter.log.ResponseLoggingFilter;
import io.restassured.http.ContentType;
import org.apache.http.HttpStatus;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static io.restassured.RestAssured.given;

public class UserRenameTest {
    @BeforeAll
    public static void setupRestAssured() {
        RestAssured.filters(
                List.of(new RequestLoggingFilter(),
                        new ResponseLoggingFilter())
        );
    }

    @Test
    public void userCanSetValidFullName() {

        UserCredentials creds = TestDataFactory.generateUser();
        String username = creds.getUsername();
        String password = creds.getPassword();

        // 1 создаём юзера под админом
        given()
                .contentType(ContentType.JSON)
                .accept(ContentType.JSON)
                .header("Authorization", "Basic YWRtaW46YWRtaW4=") // admin:admin
                .body("""
                        {
                          "username": "%s",
                          "password": "%s",
                          "role": "USER"
                        }
                        """.formatted(username, password))
                .when()
                .post("http://localhost:4111/api/v1/admin/users")
                .then()
                .assertThat()
                .statusCode(HttpStatus.SC_CREATED);

        // 2 логинимся юзером, забираем токен
        String userAuthHeader =
                given()
                        .contentType(ContentType.JSON)
                        .accept(ContentType.JSON)
                        .body("""
                                {
                                  "username": "%s",
                                  "password": "%s"
                                }
                                """.formatted(username, password))
                        .when()
                        .post("http://localhost:4111/api/v1/auth/login")
                        .then()
                        .assertThat()
                        .statusCode(HttpStatus.SC_OK)
                        .extract()
                        .header("Authorization");


        // 3 меняем имя юзера
        given()
                .header("Authorization", userAuthHeader)
                .contentType(ContentType.JSON)
                .accept(ContentType.JSON)
                .body("""
                        {
                          "name": "Johnswd Smith"
                        }
                        """)
                .when()
                .put("http://localhost:4111/api/v1/customer/profile")
                .then()
                .assertThat()
                .statusCode(HttpStatus.SC_OK);

    }

    @Test
    public void userCannotSetOneLongWord() {

        UserCredentials creds = TestDataFactory.generateUser();
        String username = creds.getUsername();
        String password = creds.getPassword();

        // 1 создаём юзера под админом
        given()
                .contentType(ContentType.JSON)
                .accept(ContentType.JSON)
                .header("Authorization", "Basic YWRtaW46YWRtaW4=") // admin:admin
                .body("""
                        {
                          "username": "%s",
                          "password": "%s",
                          "role": "USER"
                        }
                        """.formatted(username, password))
                .when()
                .post("http://localhost:4111/api/v1/admin/users")
                .then()
                .assertThat()
                .statusCode(HttpStatus.SC_CREATED);

        // 2 логинимся юзером, забираем токен
        String userAuthHeader =
                given()
                        .contentType(ContentType.JSON)
                        .accept(ContentType.JSON)
                        .body("""
                                {
                                  "username": "%s",
                                  "password": "%s"
                                }
                                """.formatted(username, password))
                        .when()
                        .post("http://localhost:4111/api/v1/auth/login")
                        .then()
                        .assertThat()
                        .statusCode(HttpStatus.SC_OK)
                        .extract()
                        .header("Authorization");


        // 3 меняем имя юзера
        given()
                .header("Authorization", userAuthHeader)
                .contentType(ContentType.JSON)
                .accept(ContentType.JSON)
                .body("""
                        {
                          "name": "Johnswdsmiiith"
                        }
                        """)
                .when()
                .put("http://localhost:4111/api/v1/customer/profile")
                .then()
                .assertThat()
                .statusCode(HttpStatus.SC_BAD_REQUEST);

    }

    @Test
    public void userCanSetShortNames() {

        UserCredentials creds = TestDataFactory.generateUser();
        String username = creds.getUsername();
        String password = creds.getPassword();

        // 1 создаём юзера под админом
        given()
                .contentType(ContentType.JSON)
                .accept(ContentType.JSON)
                .header("Authorization", "Basic YWRtaW46YWRtaW4=") // admin:admin
                .body("""
                        {
                          "username": "%s",
                          "password": "%s",
                          "role": "USER"
                        }
                        """.formatted(username, password))
                .when()
                .post("http://localhost:4111/api/v1/admin/users")
                .then()
                .assertThat()
                .statusCode(HttpStatus.SC_CREATED);

        // 2 логинимся юзером, забираем токен
        String userAuthHeader =
                given()
                        .contentType(ContentType.JSON)
                        .accept(ContentType.JSON)
                        .body("""
                                {
                                  "username": "%s",
                                  "password": "%s"
                                }
                                """.formatted(username, password))
                        .when()
                        .post("http://localhost:4111/api/v1/auth/login")
                        .then()
                        .assertThat()
                        .statusCode(HttpStatus.SC_OK)
                        .extract()
                        .header("Authorization");


        // 3 меняем имя юзера
        given()
                .header("Authorization", userAuthHeader)
                .contentType(ContentType.JSON)
                .accept(ContentType.JSON)
                .body("""
                        {
                          "name": "J W"
                        }
                        """)
                .when()
                .put("http://localhost:4111/api/v1/customer/profile")
                .then()
                .assertThat()
                .statusCode(HttpStatus.SC_OK);

    }

    @Test
    public void userCannotSetOnlySpaces() {

        UserCredentials creds = TestDataFactory.generateUser();
        String username = creds.getUsername();
        String password = creds.getPassword();

        // 1 создаём юзера под админом
        given()
                .contentType(ContentType.JSON)
                .accept(ContentType.JSON)
                .header("Authorization", "Basic YWRtaW46YWRtaW4=") // admin:admin
                .body("""
                        {
                          "username": "%s",
                          "password": "%s",
                          "role": "USER"
                        }
                        """.formatted(username, password))
                .when()
                .post("http://localhost:4111/api/v1/admin/users")
                .then()
                .assertThat()
                .statusCode(HttpStatus.SC_CREATED);

        // 2 логинимся юзером, забираем токен
        String userAuthHeader =
                given()
                        .contentType(ContentType.JSON)
                        .accept(ContentType.JSON)
                        .body("""
                                {
                                  "username": "%s",
                                  "password": "%s"
                                }
                                """.formatted(username, password))
                        .when()
                        .post("http://localhost:4111/api/v1/auth/login")
                        .then()
                        .assertThat()
                        .statusCode(HttpStatus.SC_OK)
                        .extract()
                        .header("Authorization");


        // 3 меняем имя юзера
        given()
                .header("Authorization", userAuthHeader)
                .contentType(ContentType.JSON)
                .accept(ContentType.JSON)
                .body("""
                        {
                          "name": "     "
                        }
                        """)
                .when()
                .put("http://localhost:4111/api/v1/customer/profile")
                .then()
                .assertThat()
                .statusCode(HttpStatus.SC_BAD_REQUEST);

    }

    @Test
    public void userCanUseWordNull() {

        UserCredentials creds = TestDataFactory.generateUser();
        String username = creds.getUsername();
        String password = creds.getPassword();

        // 1 создаём юзера под админом
        given()
                .contentType(ContentType.JSON)
                .accept(ContentType.JSON)
                .header("Authorization", "Basic YWRtaW46YWRtaW4=") // admin:admin
                .body("""
                        {
                          "username": "%s",
                          "password": "%s",
                          "role": "USER"
                        }
                        """.formatted(username, password))
                .when()
                .post("http://localhost:4111/api/v1/admin/users")
                .then()
                .assertThat()
                .statusCode(HttpStatus.SC_CREATED);

        // 2 логинимся юзером, забираем токен
        String userAuthHeader =
                given()
                        .contentType(ContentType.JSON)
                        .accept(ContentType.JSON)
                        .body("""
                                {
                                  "username": "%s",
                                  "password": "%s"
                                }
                                """.formatted(username, password))
                        .when()
                        .post("http://localhost:4111/api/v1/auth/login")
                        .then()
                        .assertThat()
                        .statusCode(HttpStatus.SC_OK)
                        .extract()
                        .header("Authorization");


        // 3 меняем имя юзера
        given()
                .header("Authorization", userAuthHeader)
                .contentType(ContentType.JSON)
                .accept(ContentType.JSON)
                .body("""
                        {
                          "name": "Null Null"
                        }
                        """)
                .when()
                .put("http://localhost:4111/api/v1/customer/profile")
                .then()
                .assertThat()
                .statusCode(HttpStatus.SC_OK);

    }

    @Test
    public void userCanUseLowercaseWords() {

        UserCredentials creds = TestDataFactory.generateUser();
        String username = creds.getUsername();
        String password = creds.getPassword();

        // 1 создаём юзера под админом
        given()
                .contentType(ContentType.JSON)
                .accept(ContentType.JSON)
                .header("Authorization", "Basic YWRtaW46YWRtaW4=") // admin:admin
                .body("""
                        {
                          "username": "%s",
                          "password": "%s",
                          "role": "USER"
                        }
                        """.formatted(username, password))
                .when()
                .post("http://localhost:4111/api/v1/admin/users")
                .then()
                .assertThat()
                .statusCode(HttpStatus.SC_CREATED);

        // 2 логинимся юзером, забираем токен
        String userAuthHeader =
                given()
                        .contentType(ContentType.JSON)
                        .accept(ContentType.JSON)
                        .body("""
                                {
                                  "username": "%s",
                                  "password": "%s"
                                }
                                """.formatted(username, password))
                        .when()
                        .post("http://localhost:4111/api/v1/auth/login")
                        .then()
                        .assertThat()
                        .statusCode(HttpStatus.SC_OK)
                        .extract()
                        .header("Authorization");


        // 3 меняем имя юзера
        given()
                .header("Authorization", userAuthHeader)
                .contentType(ContentType.JSON)
                .accept(ContentType.JSON)
                .body("""
                        {
                          "name": "kohnswd imiiith"
                        }
                        """)
                .when()
                .put("http://localhost:4111/api/v1/customer/profile")
                .then()
                .assertThat()
                .statusCode(HttpStatus.SC_OK);

    }

    @Test
    public void userCannotUseDigitsOnly() {

        UserCredentials creds = TestDataFactory.generateUser();
        String username = creds.getUsername();
        String password = creds.getPassword();

        // 1 создаём юзера под админом
        given()
                .contentType(ContentType.JSON)
                .accept(ContentType.JSON)
                .header("Authorization", "Basic YWRtaW46YWRtaW4=") // admin:admin
                .body("""
                        {
                          "username": "%s",
                          "password": "%s",
                          "role": "USER"
                        }
                        """.formatted(username, password))
                .when()
                .post("http://localhost:4111/api/v1/admin/users")
                .then()
                .assertThat()
                .statusCode(HttpStatus.SC_CREATED);

        // 2 логинимся юзером, забираем токен
        String userAuthHeader =
                given()
                        .contentType(ContentType.JSON)
                        .accept(ContentType.JSON)
                        .body("""
                                {
                                  "username": "%s",
                                  "password": "%s"
                                }
                                """.formatted(username, password))
                        .when()
                        .post("http://localhost:4111/api/v1/auth/login")
                        .then()
                        .assertThat()
                        .statusCode(HttpStatus.SC_OK)
                        .extract()
                        .header("Authorization");


        // 3 меняем имя юзера
        given()
                .header("Authorization", userAuthHeader)
                .contentType(ContentType.JSON)
                .accept(ContentType.JSON)
                .body("""
                        {
                          "name": "123 5343"
                        }
                        """)
                .when()
                .put("http://localhost:4111/api/v1/customer/profile")
                .then()
                .assertThat()
                .statusCode(HttpStatus.SC_BAD_REQUEST);

    }

    @Test
    public void userCannotUseLettersAndDigitsMixed() {

        UserCredentials creds = TestDataFactory.generateUser();
        String username = creds.getUsername();
        String password = creds.getPassword();

        // 1 создаём юзера под админом
        given()
                .contentType(ContentType.JSON)
                .accept(ContentType.JSON)
                .header("Authorization", "Basic YWRtaW46YWRtaW4=") // admin:admin
                .body("""
                        {
                          "username": "%s",
                          "password": "%s",
                          "role": "USER"
                        }
                        """.formatted(username, password))
                .when()
                .post("http://localhost:4111/api/v1/admin/users")
                .then()
                .assertThat()
                .statusCode(HttpStatus.SC_CREATED);

        // 2 логинимся юзером, забираем токен
        String userAuthHeader =
                given()
                        .contentType(ContentType.JSON)
                        .accept(ContentType.JSON)
                        .body("""
                                {
                                  "username": "%s",
                                  "password": "%s"
                                }
                                """.formatted(username, password))
                        .when()
                        .post("http://localhost:4111/api/v1/auth/login")
                        .then()
                        .assertThat()
                        .statusCode(HttpStatus.SC_OK)
                        .extract()
                        .header("Authorization");


        // 3 меняем имя юзера
        given()
                .header("Authorization", userAuthHeader)
                .contentType(ContentType.JSON)
                .accept(ContentType.JSON)
                .body("""
                        {
                          "name": "Johnswd1 Smiiith2"
                        }
                        """)
                .when()
                .put("http://localhost:4111/api/v1/customer/profile")
                .then()
                .assertThat()
                .statusCode(HttpStatus.SC_BAD_REQUEST);

    }

    @Test
    public void userCannotSetThreeWordName() {

        UserCredentials creds = TestDataFactory.generateUser();
        String username = creds.getUsername();
        String password = creds.getPassword();

        // 1 создаём юзера под админом
        given()
                .contentType(ContentType.JSON)
                .accept(ContentType.JSON)
                .header("Authorization", "Basic YWRtaW46YWRtaW4=") // admin:admin
                .body("""
                        {
                          "username": "%s",
                          "password": "%s",
                          "role": "USER"
                        }
                        """.formatted(username, password))
                .when()
                .post("http://localhost:4111/api/v1/admin/users")
                .then()
                .assertThat()
                .statusCode(HttpStatus.SC_CREATED);

        // 2 логинимся юзером, забираем токен
        String userAuthHeader =
                given()
                        .contentType(ContentType.JSON)
                        .accept(ContentType.JSON)
                        .body("""
                                {
                                  "username": "%s",
                                  "password": "%s"
                                }
                                """.formatted(username, password))
                        .when()
                        .post("http://localhost:4111/api/v1/auth/login")
                        .then()
                        .assertThat()
                        .statusCode(HttpStatus.SC_OK)
                        .extract()
                        .header("Authorization");


        // 3 меняем имя юзера
        given()
                .header("Authorization", userAuthHeader)
                .contentType(ContentType.JSON)
                .accept(ContentType.JSON)
                .body("""
                        {
                          "name": "John Van Smith"
                        }
                        """)
                .when()
                .put("http://localhost:4111/api/v1/customer/profile")
                .then()
                .assertThat()
                .statusCode(HttpStatus.SC_BAD_REQUEST);

    }

    @Test
    public void userCannotSetEmptyName() {

        UserCredentials creds = TestDataFactory.generateUser();
        String username = creds.getUsername();
        String password = creds.getPassword();

        // 1 создаём юзера под админом
        given()
                .contentType(ContentType.JSON)
                .accept(ContentType.JSON)
                .header("Authorization", "Basic YWRtaW46YWRtaW4=") // admin:admin
                .body("""
                        {
                          "username": "%s",
                          "password": "%s",
                          "role": "USER"
                        }
                        """.formatted(username, password))
                .when()
                .post("http://localhost:4111/api/v1/admin/users")
                .then()
                .assertThat()
                .statusCode(HttpStatus.SC_CREATED);

        // 2 логинимся юзером, забираем токен
        String userAuthHeader =
                given()
                        .contentType(ContentType.JSON)
                        .accept(ContentType.JSON)
                        .body("""
                                {
                                  "username": "%s",
                                  "password": "%s"
                                }
                                """.formatted(username, password))
                        .when()
                        .post("http://localhost:4111/api/v1/auth/login")
                        .then()
                        .assertThat()
                        .statusCode(HttpStatus.SC_OK)
                        .extract()
                        .header("Authorization");


        // 3 меняем имя юзера
        given()
                .header("Authorization", userAuthHeader)
                .contentType(ContentType.JSON)
                .accept(ContentType.JSON)
                .body("""
                        {
                          "name": ""
                        }
                        """)
                .when()
                .put("http://localhost:4111/api/v1/customer/profile")
                .then()
                .assertThat()
                .statusCode(HttpStatus.SC_BAD_REQUEST);

    }
}
