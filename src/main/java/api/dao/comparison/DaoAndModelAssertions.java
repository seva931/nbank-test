package api.dao.comparison;

import api.models.BaseModel;
import org.assertj.core.api.AbstractAssert;

public class DaoAndModelAssertions {

    private static final DaoComparator daoComparator = new DaoComparator();

    public static DaoModelAssert assertThat(BaseModel apiModel, Object daoModel) {
        return new DaoModelAssert(apiModel, daoModel);
    }

    public static class DaoModelAssert extends AbstractAssert<DaoModelAssert, Object> {

        private final BaseModel apiModel;
        private final Object daoModel;

        public DaoModelAssert(BaseModel apiModel, Object daoModel) {
            super(apiModel, DaoModelAssert.class);
            this.apiModel = apiModel;
            this.daoModel = daoModel;
        }

        public DaoModelAssert match() {
            if (apiModel == null) {
                failWithMessage("API model should not be null");
            }
            if (daoModel == null) {
                failWithMessage("DAO model should not be null");
            }

            try {
                daoComparator.compare(apiModel, daoModel);
            } catch (AssertionError e) {
                failWithMessage(e.getMessage());
            }

            return this;
        }
    }
}