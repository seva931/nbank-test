package requests.steps;

import io.restassured.specification.RequestSpecification;
import io.restassured.specification.ResponseSpecification;
import models.AccountResponse;
import models.DepositRequest;
import models.TransferRequest;
import models.TransferResponse;
import requests.skelethon.Endpoint;
import requests.skelethon.requester.ValidatedCrudRequester;

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

    public static AccountResponse createAccount(RequestSpecification reqSpec,
                                                ResponseSpecification respSpec) {
        return new ValidatedCrudRequester<AccountResponse>(
                reqSpec, Endpoint.ACCOUNTS, respSpec
        ).post(null);
    }
}