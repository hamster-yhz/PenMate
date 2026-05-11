package com.penmate.backend.application.agent.prompt;

import org.springframework.stereotype.Component;

/**
 * 统一的结构化提示块封装器。
 * <p>负责规范块内容首尾换行清理、XML 风格转义，以及带属性标签的闭合标签推导。</p>
 */
@Component
public class StructuredPromptBlockFormatter {

    public String wrapBlock(String tagDeclaration, String content) {
        return "<" + tagDeclaration + ">\n"
                + normalizeBlockContent(content)
                + "\n</" + closingTagName(tagDeclaration) + ">";
    }

    public String escapeStructuredContent(String content) {
        if (content == null) {
            return "";
        }
        return content
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }

    public String normalizeBlockContent(String content) {
        if (content == null) {
            return "";
        }
        return escapeStructuredContent(content
                .replaceFirst("^[\\r\\n]+", "")
                .replaceFirst("[\\r\\n]+$", ""));
    }

    public String closingTagName(String tagDeclaration) {
        int separatorIndex = tagDeclaration.indexOf(' ');
        return separatorIndex < 0 ? tagDeclaration : tagDeclaration.substring(0, separatorIndex);
    }
}
