package com.penmate.backend.application.agent.tool.runtime;

import java.util.Map;

/** Optional tool-specific approval preview contribution. */
public interface ToolApprovalPreviewProvider {

    String toolCode();

    Map<String, String> preview(Map<String, Object> toolArguments);
}
