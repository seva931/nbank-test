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

public class DepositTest {
    @BeforeAll
    public static void setupRestAssured() {
        RestAssured.filters(
                List.of(new RequestLoggingFilter(),
                        new ResponseLoggingFilter())
        );
    }


    @Test
    public void depositMinus1ShouldFail() {

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

        // 3 создаём счёт и достаём его id
        Integer accountId =
                given()
                        .header("Authorization", userAuthHeader)
                        .contentType(ContentType.JSON)
                        .accept(ContentType.JSON)
                        .when()
                        .post("http://localhost:4111/api/v1/accounts")
                        .then()
                        .assertThat()
                        .statusCode(HttpStatus.SC_CREATED)
                        .extract()
                        .jsonPath()
                        .getInt("id");

        // 4 пробуем положить -1 (ожидаем 400)
        given()
                .header("Authorization", userAuthHeader)
                .contentType(ContentType.JSON)
                .accept(ContentType.JSON)
                .body("""
                        {
                          "id": %d,
                          "balance": -1
                        }
                        """.formatted(accountId))
                .when()
                .post("http://localhost:4111/api/v1/accounts/deposit")
                .then()
                .assertThat()
                .statusCode(HttpStatus.SC_BAD_REQUEST);
    }

    @Test
    public void deposit0ShouldFail() {

        UserCredentials creds = TestDataFactory.generateUser();
        String username = creds.getUsername();
        String password = creds.getPassword();

        // 1 создаём юзера под админом
        given()
                .contentType(ContentType.JSON)
                .accept(ContentType.JSON)
                .header("Authorization", "Basic YWRtaW46YWRtaW4=")
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

        // 2 логин
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

        // 3 создаём счёт
        Integer accountId =
                given()
                        .header("Authorization", userAuthHeader)
                        .contentType(ContentType.JSON)
                        .accept(ContentType.JSON)
                        .when()
                        .post("http://localhost:4111/api/v1/accounts")
                        .then()
                        .assertThat()
                        .statusCode(HttpStatus.SC_CREATED)
                        .extract()
                        .jsonPath()
                        .getInt("id");

        // 4 депозит 0
        given()
                .header("Authorization", userAuthHeader)
                .contentType(ContentType.JSON)
                .accept(ContentType.JSON)
                .body("""
                        {
                          "id": %d,
                          "balance": 0
                        }
                        """.formatted(accountId))
                .when()
                .post("http://localhost:4111/api/v1/accounts/deposit")
                .then()
                .assertThat()
                .statusCode(HttpStatus.SC_BAD_REQUEST);
    }

    @Test
    public void deposit1ShouldPass() {

        UserCredentials creds = TestDataFactory.generateUser();
        String username = creds.getUsername();
        String password = creds.getPassword();

        // 1 создаём юзера
        given()
                .contentType(ContentType.JSON)
                .accept(ContentType.JSON)
                .header("Authorization", "Basic YWRtaW46YWRtaW4=")
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

        // 2 логин
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

        // 3 создаём счёт
        Integer accountId =
                given()
                        .header("Authorization", userAuthHeader)
                        .contentType(ContentType.JSON)
                        .accept(ContentType.JSON)
                        .when()
                        .post("http://localhost:4111/api/v1/accounts")
                        .then()
                        .assertThat()
                        .statusCode(HttpStatus.SC_CREATED)
                        .extract()
                        .jsonPath()
                        .getInt("id");

        // 4 депозит 1
        given()
                .header("Authorization", userAuthHeader)
                .contentType(ContentType.JSON)
                .accept(ContentType.JSON)
                .body("""
                        {
                          "id": %d,
                          "balance": 1
                        }
                        """.formatted(accountId))
                .when()
                .post("http://localhost:4111/api/v1/accounts/deposit")
                .then()
                .assertThat()
                .statusCode(HttpStatus.SC_OK);
    }

    @Test
    public void deposit4999ShouldPass() {

        UserCredentials creds = TestDataFactory.generateUser();
        String username = creds.getUsername();
        String password = creds.getPassword();

        // 1 юзер
        given()
                .contentType(ContentType.JSON)
                .accept(ContentType.JSON)
                .header("Authorization", "Basic YWRtaW46YWRtaW4=")
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

        // 2 логин
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

        // 3 счёт
        Integer accountId =
                given()
                        .header("Authorization", userAuthHeader)
                        .contentType(ContentType.JSON)
                        .accept(ContentType.JSON)
                        .when()
                        .post("http://localhost:4111/api/v1/accounts")
                        .then()
                        .assertThat()
                        .statusCode(HttpStatus.SC_CREATED)
                        .extract()
                        .jsonPath()
                        .getInt("id");

        // 4 депозит 4999
        given()
                .header("Authorization", userAuthHeader)
                .contentType(ContentType.JSON)
                .accept(ContentType.JSON)
                .body("""
                        {
                          "id": %d,
                          "balance": 4999
                        }
                        """.formatted(accountId))
                .when()
                .post("http://localhost:4111/api/v1/accounts/deposit")
                .then()
                .assertThat()
                .statusCode(HttpStatus.SC_OK);
    }

    @Test
    public void deposit5000ShouldPass() {

        UserCredentials creds = TestDataFactory.generateUser();
        String username = creds.getUsername();
        String password = creds.getPassword();

        // 1 юзер
        given()
                .contentType(ContentType.JSON)
                .accept(ContentType.JSON)
                .header("Authorization", "Basic YWRtaW46YWRtaW4=")
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

        // 2 логин
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

        // 3 счёт
        Integer accountId =
                given()
                        .header("Authorization", userAuthHeader)
                        .contentType(ContentType.JSON)
                        .accept(ContentType.JSON)
                        .when()
                        .post("http://localhost:4111/api/v1/accounts")
                        .then()
                        .assertThat()
                        .statusCode(HttpStatus.SC_CREATED)
                        .extract()
                        .jsonPath()
                        .getInt("id");

        // 4 депозит 5000
        given()
                .header("Authorization", userAuthHeader)
                .contentType(ContentType.JSON)
                .accept(ContentType.JSON)
                .body("""
                        {
                          "id": %d,
                          "balance": 5000
                        }
                        """.formatted(accountId))
                .when()
                .post("http://localhost:4111/api/v1/accounts/deposit")
                .then()
                .assertThat()
                .statusCode(HttpStatus.SC_OK);
    }

    @Test
    public void deposit5001ShouldFail() {

        UserCredentials creds = TestDataFactory.generateUser();
        String username = creds.getUsername();
        String password = creds.getPassword();

        // 1 юзер
        given()
                .contentType(ContentType.JSON)
                .accept(ContentType.JSON)
                .header("Authorization", "Basic YWRtaW46YWRtaW4=")
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

        // 2 логин
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

        // 3 счёт
        Integer accountId =
                given()
                        .header("Authorization", userAuthHeader)
                        .contentType(ContentType.JSON)
                        .accept(ContentType.JSON)
                        .when()
                        .post("http://localhost:4111/api/v1/accounts")
                        .then()
                        .assertThat()
                        .statusCode(HttpStatus.SC_CREATED)
                        .extract()
                        .jsonPath()
                        .getInt("id");

        // 4 депозит 5001
        given()
                .header("Authorization", userAuthHeader)
                .contentType(ContentType.JSON)
                .accept(ContentType.JSON)
                .body("""
                        {
                          "id": %d,
                          "balance": 5001
                        }
                        """.formatted(accountId))
                .when()
                .post("http://localhost:4111/api/v1/accounts/deposit")
                .then()
                .assertThat()
                .statusCode(HttpStatus.SC_BAD_REQUEST);
    }

    @Test
    public void userCannotDepositToForeignAccount() {

        // создаём владельца счёта
        UserCredentials ownerCreds = TestDataFactory.generateUser();
        String ownerUsername = ownerCreds.getUsername();
        String ownerPassword = ownerCreds.getPassword();

        given()
                .contentType(ContentType.JSON)
                .accept(ContentType.JSON)
                .header("Authorization", "Basic YWRtaW46YWRtaW4=")
                .body("""
                        {
                          "username": "%s",
                          "password": "%s",
                          "role": "USER"
                        }
                        """.formatted(ownerUsername, ownerPassword))
                .when()
                .post("http://localhost:4111/api/v1/admin/users")
                .then()
                .assertThat()
                .statusCode(HttpStatus.SC_CREATED);

        String ownerToken =
                given()
                        .contentType(ContentType.JSON)
                        .accept(ContentType.JSON)
                        .body("""
                                {
                                  "username": "%s",
                                  "password": "%s"
                                }
                                """.formatted(ownerUsername, ownerPassword))
                        .when()
                        .post("http://localhost:4111/api/v1/auth/login")
                        .then()
                        .assertThat()
                        .statusCode(HttpStatus.SC_OK)
                        .extract()
                        .header("Authorization");

        Integer ownerAccountId =
                given()
                        .header("Authorization", ownerToken)
                        .contentType(ContentType.JSON)
                        .accept(ContentType.JSON)
                        .when()
                        .post("http://localhost:4111/api/v1/accounts")
                        .then()
                        .assertThat()
                        .statusCode(HttpStatus.SC_CREATED)
                        .extract()
                        .jsonPath()
                        .getInt("id");

        // создаём второго юзера, который будет пытаться класть деньги не в свой счёт
        UserCredentials attackerCreds = TestDataFactory.generateUser();
        String attackerUsername = attackerCreds.getUsername();
        String attackerPassword = attackerCreds.getPassword();

        given()
                .contentType(ContentType.JSON)
                .accept(ContentType.JSON)
                .header("Authorization", "Basic YWRtaW46YWRtaW4=")
                .body("""
                        {
                          "username": "%s",
                          "password": "%s",
                          "role": "USER"
                        }
                        """.formatted(attackerUsername, attackerPassword))
                .when()
                .post("http://localhost:4111/api/v1/admin/users")
                .then()
                .assertThat()
                .statusCode(HttpStatus.SC_CREATED);

        String otherToken =
                given()
                        .contentType(ContentType.JSON)
                        .accept(ContentType.JSON)
                        .body("""
                                {
                                  "username": "%s",
                                  "password": "%s"
                                }
                                """.formatted(attackerUsername, attackerPassword))
                        .when()
                        .post("http://localhost:4111/api/v1/auth/login")
                        .then()
                        .assertThat()
                        .statusCode(HttpStatus.SC_OK)
                        .extract()
                        .header("Authorization");

        // второй юзер пытается пополнить чужой счёт
        given()
                .header("Authorization", otherToken)
                .contentType(ContentType.JSON)
                .accept(ContentType.JSON)
                .body("""
                        {
                          "id": %d,
                          "balance": 100
                        }
                        """.formatted(ownerAccountId))
                .when()
                .post("http://localhost:4111/api/v1/accounts/deposit")
                .then()
                .assertThat()
                .statusCode(HttpStatus.SC_FORBIDDEN);
    }

    @Test
    public void depositToNotExistingAccountShouldFail() {

        UserCredentials creds = TestDataFactory.generateUser();
        String username = creds.getUsername();
        String password = creds.getPassword();

        // 1 юзер
        given()
                .contentType(ContentType.JSON)
                .accept(ContentType.JSON)
                .header("Authorization", "Basic YWRtaW46YWRtaW4=")
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

        // 2 логин
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

        // 3 создаём хоть один счёт, чтобы токен точно рабочий
        given()
                .header("Authorization", userAuthHeader)
                .contentType(ContentType.JSON)
                .accept(ContentType.JSON)
                .when()
                .post("http://localhost:4111/api/v1/accounts")
                .then()
                .assertThat()
                .statusCode(HttpStatus.SC_CREATED);

        // 4 пытаемся пополнить НЕсуществующий accountId
        int fakeAccountId = 999999;

        given()
                .header("Authorization", userAuthHeader)
                .contentType(ContentType.JSON)
                .accept(ContentType.JSON)
                .body("""
                        {
                          "id": %d,
                          "balance": 100
                        }
                        """.formatted(fakeAccountId))
                .when()
                .post("http://localhost:4111/api/v1/accounts/deposit")
                .then()
                .assertThat()
                .statusCode(HttpStatus.SC_FORBIDDEN);
    }


    @Test
    public void depositWithEmptyStringBalanceShouldFail() {

        UserCredentials creds = TestDataFactory.generateUser();
        String username = creds.getUsername();
        String password = creds.getPassword();

        // 1 создаём юзера через админа
        given()
                .contentType(ContentType.JSON)
                .accept(ContentType.JSON)
                .header("Authorization", "Basic YWRtaW46YWRtaW4=")
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

        // 2 логинимся, забираем токен
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

        // 3 создаём счёт и берём его id
        Integer accountId =
                given()
                        .header("Authorization", userAuthHeader)
                        .contentType(ContentType.JSON)
                        .accept(ContentType.JSON)
                        .when()
                        .post("http://localhost:4111/api/v1/accounts")
                        .then()
                        .assertThat()
                        .statusCode(HttpStatus.SC_CREATED)
                        .extract()
                        .jsonPath()
                        .getInt("id");

        // 4 депозит с balance = "" (пустая строка)
        given()
                .header("Authorization", userAuthHeader)
                .contentType(ContentType.JSON)
                .accept(ContentType.JSON)
                .body("""
                        {
                          "id": %d,
                          "balance": ""
                        }
                        """.formatted(accountId))
                .when()
                .post("http://localhost:4111/api/v1/accounts/deposit")
                .then()
                .assertThat()
                .statusCode(HttpStatus.SC_INTERNAL_SERVER_ERROR);
    }

    @Test
    public void depositWithStringZeroBalanceShouldFail() {

        UserCredentials creds = TestDataFactory.generateUser();
        String username = creds.getUsername();
        String password = creds.getPassword();

        // 1 создаём юзера через админа
        given()
                .contentType(ContentType.JSON)
                .accept(ContentType.JSON)
                .header("Authorization", "Basic YWRtaW46YWRtaW4=")
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

        // 2 логинимся, берём токен
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

        // 3 создаём счёт и достаём id
        Integer accountId =
                given()
                        .header("Authorization", userAuthHeader)
                        .contentType(ContentType.JSON)
                        .accept(ContentType.JSON)
                        .when()
                        .post("http://localhost:4111/api/v1/accounts")
                        .then()
                        .assertThat()
                        .statusCode(HttpStatus.SC_CREATED)
                        .extract()
                        .jsonPath()
                        .getInt("id");

        // 4 депозит с balance = 0
        given()
                .header("Authorization", userAuthHeader)
                .contentType(ContentType.JSON)
                .accept(ContentType.JSON)
                .body("""
                        {
                          "id": %d,
                          "balance": 0
                        }
                        """.formatted(accountId))
                .when()
                .post("http://localhost:4111/api/v1/accounts/deposit")
                .then()
                .assertThat()
                .statusCode(HttpStatus.SC_BAD_REQUEST);
    }

    @Test
    public void depositWithTextBalanceShouldFail() {

        UserCredentials creds = TestDataFactory.generateUser();
        String username = creds.getUsername();
        String password = creds.getPassword();

        // 1 создаём юзера через админа
        given()
                .contentType(ContentType.JSON)
                .accept(ContentType.JSON)
                .header("Authorization", "Basic YWRtaW46YWRtaW4=")
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

        // 2 логинимся, забираем токен
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

        // 3 создаём счёт, берём id
        Integer accountId =
                given()
                        .header("Authorization", userAuthHeader)
                        .contentType(ContentType.JSON)
                        .accept(ContentType.JSON)
                        .when()
                        .post("http://localhost:4111/api/v1/accounts")
                        .then()
                        .assertThat()
                        .statusCode(HttpStatus.SC_CREATED)
                        .extract()
                        .jsonPath()
                        .getInt("id");

        // 4 депозит с balance = "hello" (текст вместо суммы)
        given()
                .header("Authorization", userAuthHeader)
                .contentType(ContentType.JSON)
                .accept(ContentType.JSON)
                .body("""
                        {
                          "id": %d,
                          "balance": "hello"
                        }
                        """.formatted(accountId))
                .when()
                .post("http://localhost:4111/api/v1/accounts/deposit")
                .then()
                .assertThat()
                .statusCode(HttpStatus.SC_INTERNAL_SERVER_ERROR);
    }
}


