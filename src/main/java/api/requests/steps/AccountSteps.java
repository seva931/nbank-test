package api.requests.steps;

import api.models.AccountResponse;
import api.models.DepositRequest;
import api.models.TransferRequest;
import api.models.TransferResponse;
import api.requests.skelethon.Endpoint;
import api.requests.skelethon.requester.ValidatedCrudRequester;
import api.specs.ResponseSpecs;
import common.storage.TestDataRegistry;
import io.restassured.specification.RequestSpecification;
import io.restassured.specification.ResponseSpecification;

import java.util.Arrays;

import static io.restassured.RestAssured.given;

public class AccountSteps {

    public static AccountResponse deposit(RequestSpecification reqSpec,
                                          ResponseSpecification respSpec,
                                          DepositRequest payload) {
        return new ValidatedCrudRequester<AccountResponse>(
                reqSpec,
                Endpoint.DEPOSIT,
                respSpec
        ).post(payload);
    }

    public static TransferResponse transfer(RequestSpecification reqSpec,
                                            ResponseSpecification respSpec,
                                            TransferRequest payload) {
        return new ValidatedCrudRequester<TransferResponse>(
                reqSpec, Endpoint.TRANSFER, respSpec
        ).post(payload);
    }
    public static TransferResponse transferWithFraudCheck(RequestSpecification reqSpec,
                                                          ResponseSpecification respSpec,
                                                          TransferRequest payload) {
        return new ValidatedCrudRequester<TransferResponse>(
                reqSpec,
                Endpoint.TRANSFER_WITH_FRAUD_CHECK,
                respSpec
        ).post(payload);
    }

    public static AccountResponse createAccount(RequestSpecification reqSpec,
                                                ResponseSpecification respSpec) {
        AccountResponse account = new ValidatedCrudRequester<AccountResponse>(
                reqSpec, Endpoint.ACCOUNTS, respSpec
        ).post(null);

        TestDataRegistry.registerAccount(reqSpec, account);
        return account;
    }

    public static AccountResponse getAccountByNumber(RequestSpecification reqSpec,
                                                     ResponseSpecification respSpec,
                                                     String accountNumber) {
        AccountResponse[] accounts = given()
                .spec(reqSpec)
                .when()
                .get("customer/accounts")
                .then()
                .spec(respSpec)
                .extract()
                .as(AccountResponse[].class);

        return Arrays.stream(accounts)
                .filter(a -> a.getAccountNumber().equals(accountNumber))
                .findFirst()
                .orElseThrow();
    }

    public static void deleteAccount(RequestSpecification reqSpec,
                                     long accountId) {
        deleteAccountResource(reqSpec, Endpoint.ACCOUNTS.getUrl() + "/{accountId}", accountId);
    }

    // --- private ---

    private static void deleteAccountResource(RequestSpecification reqSpec,
                                              String urlTemplate,
                                              long accountId) {
        given()
                .spec(reqSpec)
                .when()
                .delete(urlTemplate, accountId)
                .then()
                .spec(ResponseSpecs.requestReturnsOK());
    }
}
