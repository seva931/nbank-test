package requests.steps;

import io.restassured.specification.RequestSpecification;
import io.restassured.specification.ResponseSpecification;
import models.UpdateProfileRequest;
import models.UpdateProfileResponse;
import requests.skelethon.Endpoint;
import requests.skelethon.requester.ValidatedCrudRequester;

public class ProfileSteps {

    public static UpdateProfileResponse updateProfile(RequestSpecification reqSpec,
                                                      ResponseSpecification respSpec,
                                                      UpdateProfileRequest payload) {
        return new ValidatedCrudRequester<UpdateProfileResponse>(
                reqSpec, Endpoint.PROFILE_UPDATE, respSpec
        ).update(0L, payload);
    }
}
