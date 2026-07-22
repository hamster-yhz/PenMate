package com.penmate.backend.application.storybible;

import java.util.List;
import java.util.Map;

public interface StoryBiblePatchValidator {

    ValidatedPatch validate(String patchJson, String fieldSchemaJson);

    Map<String, Object> apply(Map<String, Object> state, ValidatedPatch patch);

    record ValidatedPatch(String patchJson, List<String> paths) {
        public ValidatedPatch {
            paths = List.copyOf(paths == null ? List.of() : paths);
        }
    }
}
