package api.requests.steps;

import api.models.CustomerDto;
import api.models.UpdateProfileRequest;
import api.models.UpdateProfileResponse;
import api.requests.skelethon.Endpoint;
import api.requests.skelethon.requester.ValidatedCrudRequester;
import io.restassured.specification.RequestSpecification;
import io.restassured.specification.ResponseSpecification;

import static io.restassured.RestAssured.given;

public class ProfileSteps {

    public static UpdateProfileResponse updateProfile(RequestSpecification reqSpec,
                                                      ResponseSpecification respSpec,
                                                      UpdateProfileRequest payload) {
        return new ValidatedCrudRequester<UpdateProfileResponse>(
                reqSpec, Endpoint.PROFILE_UPDATE, respSpec
        ).update(0L, payload);
    }

    public static CustomerDto getProfile(RequestSpecification reqSpec,
                                         ResponseSpecification respSpec) {
        return given()
                .spec(reqSpec)
                .when()
                .get(Endpoint.PROFILE_UPDATE.getUrl())
                .then()
                .spec(respSpec)
                .extract()
                .as(CustomerDto.class);
    }
}
