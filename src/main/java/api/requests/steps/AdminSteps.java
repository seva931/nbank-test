package api.requests.steps;

import api.generators.RandomModelGenerator;
import api.models.CreateUserRequest;
import api.models.CreateUserResponse;
import api.requests.skelethon.Endpoint;
import api.requests.skelethon.requester.ValidatedCrudRequester;
import api.specs.RequestSpecs;
import api.specs.ResponseSpecs;
import common.storage.TestDataRegistry;

import static io.restassured.RestAssured.given;

public class AdminSteps {

    private AdminSteps() {
    }

    // старый удобный метод: возвращает только CreateUserRequest
    public static CreateUserRequest createUser() {
        CreateUserRequest userRequest = RandomModelGenerator.generate(CreateUserRequest.class);
        createUser(userRequest);          // регистрируем через перегрузку
        return userRequest;
    }

    // перегрузка с явным request и полным ответом
    public static CreateUserResponse createUser(CreateUserRequest userRequest) {
        CreateUserResponse response = new ValidatedCrudRequester<CreateUserResponse>(
                RequestSpecs.adminSpec(),
                Endpoint.ADMIN_USER,
                ResponseSpecs.entityWasCreated()
        ).post(userRequest);

        TestDataRegistry.registerUser(response);
        return response;
    }

    public static void deleteUser(long userId) {
        deleteAdminResource(Endpoint.ADMIN_USER.getUrl() + "/" + userId);
    }

    // --- private ---

    private static void deleteAdminResource(String url) {
        given()
                .spec(RequestSpecs.adminSpec())
                .when()
                .delete(url)
                .then()
                .spec(ResponseSpecs.requestReturnsOK());
    }
}
