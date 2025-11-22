package api.models;

import api.generators.GeneratingRule;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateProfileRequest extends BaseModel {
    @GeneratingRule(regex = "[A-Za-z]{3,10} [A-Za-z]{3,10}")
    private String name;
}
