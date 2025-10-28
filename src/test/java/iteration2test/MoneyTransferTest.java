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

public class MoneyTransferTest {

    @BeforeAll
    public static void setupRestAssured() {
        RestAssured.filters(
                List.of(new RequestLoggingFilter(),
                        new ResponseLoggingFilter())
        );
    }

    @Test
    public void userCanTransferMoneyBetweenOwnAccounts() {

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

        // 4 кладем 4000 на счет
        given()
                .header("Authorization", userAuthHeader)
                .contentType(ContentType.JSON)
                .accept(ContentType.JSON)
                .body("""
                        {
                          "id": %d,
                          "balance": 4000
                        }
                        """.formatted(accountId))
                .when()
                .post("http://localhost:4111/api/v1/accounts/deposit")
                .then()
                .assertThat()
                .statusCode(HttpStatus.SC_OK);

        // 5 создаём второй счет и достаём его id
        Integer accountId2 =
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
        // 6 перевод денег с одного своего счета на другой свой счет
        given()
                .header("Authorization", userAuthHeader)
                .contentType(ContentType.JSON)
                .accept(ContentType.JSON)
                .body("""
                        {
                          "senderAccountId": %d,
                          "receiverAccountId": %d,
                          "amount": 2500.50
                        }
                        """.formatted(accountId, accountId2))
                .when()
                .post("http://localhost:4111/api/v1/accounts/transfer")
                .then()
                .assertThat()
                .statusCode(HttpStatus.SC_OK);
    }

    @Test
    public void userCanTransferMaxAllowedAmountBetweenOwnAccountsSuccess() {

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

        // 4 кладем 4000 на счет
        given()
                .header("Authorization", userAuthHeader)
                .contentType(ContentType.JSON)
                .accept(ContentType.JSON)
                .body("""
                        {
                          "id": %d,
                          "balance": 4000
                        }
                        """.formatted(accountId))
                .when()
                .post("http://localhost:4111/api/v1/accounts/deposit")
                .then()
                .assertThat()
                .statusCode(HttpStatus.SC_OK);
        // 5 кладем еще раз 4000
        given()
                .header("Authorization", userAuthHeader)
                .contentType(ContentType.JSON)
                .accept(ContentType.JSON)
                .body("""
                        {
                          "id": %d,
                          "balance": 4000
                        }
                        """.formatted(accountId))
                .when()
                .post("http://localhost:4111/api/v1/accounts/deposit")
                .then()
                .assertThat()
                .statusCode(HttpStatus.SC_OK);
        // 6 кладем еще раз 4000
        given()
                .header("Authorization", userAuthHeader)
                .contentType(ContentType.JSON)
                .accept(ContentType.JSON)
                .body("""
                        {
                          "id": %d,
                          "balance": 4000
                        }
                        """.formatted(accountId))
                .when()
                .post("http://localhost:4111/api/v1/accounts/deposit")
                .then()
                .assertThat()
                .statusCode(HttpStatus.SC_OK);

        // 7 создаём второй счет и достаём его id
        Integer accountId2 =
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

        // 8 перевод денег с одного своего счета на другой свой счет
        given()
                .header("Authorization", userAuthHeader)
                .contentType(ContentType.JSON)
                .accept(ContentType.JSON)
                .body("""
                        {
                          "senderAccountId": %d,
                          "receiverAccountId": %d,
                          "amount": 10000
                        }
                        """.formatted(accountId, accountId2))
                .when()
                .post("http://localhost:4111/api/v1/accounts/transfer")
                .then()
                .assertThat()
                .statusCode(HttpStatus.SC_OK);
    }

    @Test
    public void userCannotTransferMoreThanLimitBetweenOwnAccountsAmountTooLarge() {

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

        // 4 кладем 4000 на счет
        given()
                .header("Authorization", userAuthHeader)
                .contentType(ContentType.JSON)
                .accept(ContentType.JSON)
                .body("""
                        {
                          "id": %d,
                          "balance": 4000
                        }
                        """.formatted(accountId))
                .when()
                .post("http://localhost:4111/api/v1/accounts/deposit")
                .then()
                .assertThat()
                .statusCode(HttpStatus.SC_OK);
        // 5 кладем еще раз 4000
        given()
                .header("Authorization", userAuthHeader)
                .contentType(ContentType.JSON)
                .accept(ContentType.JSON)
                .body("""
                        {
                          "id": %d,
                          "balance": 4000
                        }
                        """.formatted(accountId))
                .when()
                .post("http://localhost:4111/api/v1/accounts/deposit")
                .then()
                .assertThat()
                .statusCode(HttpStatus.SC_OK);
        // 6 кладем еще раз 4000
        given()
                .header("Authorization", userAuthHeader)
                .contentType(ContentType.JSON)
                .accept(ContentType.JSON)
                .body("""
                        {
                          "id": %d,
                          "balance": 4000
                        }
                        """.formatted(accountId))
                .when()
                .post("http://localhost:4111/api/v1/accounts/deposit")
                .then()
                .assertThat()
                .statusCode(HttpStatus.SC_OK);

        // 7 создаём второй счет и достаём его id
        Integer accountId2 =
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
        // 8 перевод денег с одного счета своего на другой
        given()
                .header("Authorization", userAuthHeader)
                .contentType(ContentType.JSON)
                .accept(ContentType.JSON)
                .body("""
                        {
                          "senderAccountId": %d,
                          "receiverAccountId": %d,
                          "amount": 10001
                        }
                        """.formatted(accountId, accountId2))
                .when()
                .post("http://localhost:4111/api/v1/accounts/transfer")
                .then()
                .assertThat()
                .statusCode(HttpStatus.SC_BAD_REQUEST);
    }

    @Test
    public void userCannotTransferZeroAmountBetweenOwnAccountsAmountZero() {

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

        // 4 кладем 4000 на счет
        given()
                .header("Authorization", userAuthHeader)
                .contentType(ContentType.JSON)
                .accept(ContentType.JSON)
                .body("""
                        {
                          "id": %d,
                          "balance": 4000
                        }
                        """.formatted(accountId))
                .when()
                .post("http://localhost:4111/api/v1/accounts/deposit")
                .then()
                .assertThat()
                .statusCode(HttpStatus.SC_OK);
        // 5 создаём второй счет и достаём его id
        Integer accountId2 =
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
        // 6 переводим с одного своего счета на другой (сумма 0)
        given()
                .header("Authorization", userAuthHeader)
                .contentType(ContentType.JSON)
                .accept(ContentType.JSON)
                .body("""
                        {
                          "senderAccountId": %d,
                          "receiverAccountId": %d,
                          "amount": 0
                        }
                        """.formatted(accountId, accountId2))
                .when()
                .post("http://localhost:4111/api/v1/accounts/transfer")
                .then()
                .assertThat()
                .statusCode(HttpStatus.SC_BAD_REQUEST);
    }

    @Test
    public void userTransferWithEmptyAmountReturnsServerError() {

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

        // 4 кладем 4000 на счет
        given()
                .header("Authorization", userAuthHeader)
                .contentType(ContentType.JSON)
                .accept(ContentType.JSON)
                .body("""
                        {
                          "id": %d,
                          "balance": 4000
                        }
                        """.formatted(accountId))
                .when()
                .post("http://localhost:4111/api/v1/accounts/deposit")
                .then()
                .assertThat()
                .statusCode(HttpStatus.SC_OK);
        // 5 создаём второй счет и достаём его id
        Integer accountId2 =
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
        // 6 переводим деньги с одного счвоего счета на другой (пустая строка)
        given()
                .header("Authorization", userAuthHeader)
                .contentType(ContentType.JSON)
                .accept(ContentType.JSON)
                .body("""
                        {
                          "senderAccountId": %d,
                          "receiverAccountId": %d,
                          "amount": ""
                        }
                        """.formatted(accountId, accountId2))
                .when()
                .post("http://localhost:4111/api/v1/accounts/transfer")
                .then()
                .assertThat()
                .statusCode(HttpStatus.SC_INTERNAL_SERVER_ERROR);
    }

    @Test
    public void userCannotTransferNegativeAmountBetweenOwnAccountsAmountNegative() {

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

        // 4 кладем 4000 на счет
        given()
                .header("Authorization", userAuthHeader)
                .contentType(ContentType.JSON)
                .accept(ContentType.JSON)
                .body("""
                        {
                          "id": %d,
                          "balance": 4000
                        }
                        """.formatted(accountId))
                .when()
                .post("http://localhost:4111/api/v1/accounts/deposit")
                .then()
                .assertThat()
                .statusCode(HttpStatus.SC_OK);
        // 5 создаём второй счет и достаём его id
        Integer accountId2 =
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
        // 6 перевод денег с одного своего счета на другой (отрицательное число)
        given()
                .header("Authorization", userAuthHeader)
                .contentType(ContentType.JSON)
                .accept(ContentType.JSON)
                .body("""
                        {
                          "senderAccountId": %d,
                          "receiverAccountId": %d,
                          "amount": -1
                        }
                        """.formatted(accountId, accountId2))
                .when()
                .post("http://localhost:4111/api/v1/accounts/transfer")
                .then()
                .assertThat()
                .statusCode(HttpStatus.SC_BAD_REQUEST);
    }

    @Test
    public void userCannotTransferMoreThanBalanceBetweenOwnAccountsInsufficientFunds() {

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

        // 4 кладем 4000 на счет
        given()
                .header("Authorization", userAuthHeader)
                .contentType(ContentType.JSON)
                .accept(ContentType.JSON)
                .body("""
                        {
                          "id": %d,
                          "balance": 4000
                        }
                        """.formatted(accountId))
                .when()
                .post("http://localhost:4111/api/v1/accounts/deposit")
                .then()
                .assertThat()
                .statusCode(HttpStatus.SC_OK);
        // 5 создаём второй счет и достаём его id
        Integer accountId2 =
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
        // 6 переводим деньги с одного счета на другой (сумма больше, чем на счету)
        given()
                .header("Authorization", userAuthHeader)
                .contentType(ContentType.JSON)
                .accept(ContentType.JSON)
                .body("""
                        {
                          "senderAccountId": %d,
                          "receiverAccountId": %d,
                          "amount": 6000
                        }
                        """.formatted(accountId, accountId2))
                .when()
                .post("http://localhost:4111/api/v1/accounts/transfer")
                .then()
                .assertThat()
                .statusCode(HttpStatus.SC_BAD_REQUEST);
    }

    @Test
    public void userCanTransferMoneyToAnotherUsersAccountSuccess() {
        UserCredentials senderCreds = TestDataFactory.generateUser();
        String senderUsername = senderCreds.getUsername();
        String senderPassword = senderCreds.getPassword();

        //1 создаём отправителя под админом
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
                        """.formatted(senderUsername, senderPassword))
                .when()
                .post("http://localhost:4111/api/v1/admin/users")
                .then()
                .assertThat()
                .statusCode(HttpStatus.SC_CREATED);

        //2 логинимся отправителем, получаем его токен
        String senderToken =
                given()
                        .contentType(ContentType.JSON)
                        .accept(ContentType.JSON)
                        .body("""
                                {
                                  "username": "%s",
                                  "password": "%s"
                                }
                                """.formatted(senderUsername, senderPassword))
                        .when()
                        .post("http://localhost:4111/api/v1/auth/login")
                        .then()
                        .assertThat()
                        .statusCode(HttpStatus.SC_OK)
                        .extract()
                        .header("Authorization");

        //3 создаём счёт отправителя
        Integer senderAccountId =
                given()
                        .header("Authorization", senderToken)
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

        //4 пополняем счёт отправителя на 2100
        given()
                .header("Authorization", senderToken)
                .contentType(ContentType.JSON)
                .accept(ContentType.JSON)
                .body("""
                        {
                          "id": %d,
                          "balance": 2100
                        }
                        """.formatted(senderAccountId))
                .when()
                .post("http://localhost:4111/api/v1/accounts/deposit")
                .then()
                .assertThat()
                .statusCode(HttpStatus.SC_OK);

        //5 создаем получателя

        UserCredentials receiverCreds = TestDataFactory.generateUser();
        String receiverUsername = receiverCreds.getUsername();
        String receiverPassword = receiverCreds.getPassword();

        // создаём получателя под админом
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
                        """.formatted(receiverUsername, receiverPassword))
                .when()
                .post("http://localhost:4111/api/v1/admin/users")
                .then()
                .assertThat()
                .statusCode(HttpStatus.SC_CREATED);

        //6 логинимся получателем, получаем его токен (чтобы иметь возможность создать ему счёт)
        String receiverToken =
                given()
                        .contentType(ContentType.JSON)
                        .accept(ContentType.JSON)
                        .body("""
                                {
                                  "username": "%s",
                                  "password": "%s"
                                }
                                """.formatted(receiverUsername, receiverPassword))
                        .when()
                        .post("http://localhost:4111/api/v1/auth/login")
                        .then()
                        .assertThat()
                        .statusCode(HttpStatus.SC_OK)
                        .extract()
                        .header("Authorization");

        //7 создаём счёт получателя
        Integer receiverAccountId =
                given()
                        .header("Authorization", receiverToken)
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

        //8 перевод денег
        given()
                .header("Authorization", senderToken) //
                .contentType(ContentType.JSON)
                .accept(ContentType.JSON)
                .body("""
                        {
                          "senderAccountId": %d,
                          "receiverAccountId": %d,
                          "amount": 2100
                        }
                        """.formatted(senderAccountId, receiverAccountId))
                .when()
                .post("http://localhost:4111/api/v1/accounts/transfer")
                .then()
                .assertThat()
                .statusCode(HttpStatus.SC_OK);
    }

}
