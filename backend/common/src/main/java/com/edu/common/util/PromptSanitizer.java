package com.edu.common.util;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Prompt 注入防护工具：清洗用户可控字段后，再用 XML 标签包裹注入 LLM。
 * 与 Python 端 app/services/prompt_guard.py 行为对齐（控制字符、角色行、字段长度上限）。
 *
 * <p>JAVA-RISK-PLAN-20261013 合并 prompt_guard.py 规则时补齐的差异（Java 曾经与 Python 不一致的地方）：
 * <ol>
 *   <li>角色行正则的 {@code \s} 按 Unicode 空白匹配（Python 3 {@code re} 默认如此，如全角空格 U+3000）；</li>
 *   <li>{@code ^} 只在 {@code \n} 之后算行首（Python MULTILINE 语义；Java 默认还会把 U+2028/U+0085 当换行）；</li>
 *   <li>长度上限按 Unicode 码点计（Python {@code len(str)}），不会把 emoji 等代理对截成半个字符；</li>
 *   <li>{@link #wrap} 的标签清洗后为空时退回 {@code data}（Python 行为），不再输出 {@code <>}。</li>
 * </ol>
 * 两端的对照用例见 {@code PromptSanitizerParityTest} 与 {@code ai-inference-service/tests/test_prompt_guard.py}。
 */
public final class PromptSanitizer {

    private static final int DEFAULT_MAX_FIELD_LEN = 500;

    private static final Pattern CTRL_CHAR =
            Pattern.compile("[\\x00-\\x08\\x0B-\\x1F\\x7F]");

    private static final Pattern ROLE_LINE =
            Pattern.compile("^\\s*(system|assistant|user|tool|系统|助手)\\s*[:：]\\s*",
                    Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE | Pattern.MULTILINE
                            | Pattern.UNIX_LINES | Pattern.UNICODE_CHARACTER_CLASS);

    private static final Pattern TAG_UNSAFE = Pattern.compile("[^A-Za-z0-9_]");

    private PromptSanitizer() {
    }

    /** 清理单个字符串：去控制字符、剥角色前缀、截断长度。 */
    public static String sanitizeString(String value) {
        return sanitizeString(value, DEFAULT_MAX_FIELD_LEN);
    }

    public static String sanitizeString(String value, int maxLen) {
        if (value == null) {
            return null;
        }
        String s = CTRL_CHAR.matcher(value).replaceAll(" ");
        s = ROLE_LINE.matcher(s).replaceAll("");
        if (s.codePointCount(0, s.length()) > maxLen) {
            s = s.substring(0, s.offsetByCodePoints(0, maxLen)) + "…[truncated]";
        }
        return s;
    }

    /** 递归清理 Map/List/String 叶子节点；其它类型原样返回。 */
    @SuppressWarnings("unchecked")
    public static Object sanitizeJsonPayload(Object node) {
        if (node == null) {
            return null;
        }
        if (node instanceof String s) {
            return sanitizeString(s);
        }
        if (node instanceof Map<?, ?> map) {
            Map<String, Object> out = new LinkedHashMap<>(map.size());
            for (Map.Entry<?, ?> e : map.entrySet()) {
                String key = String.valueOf(e.getKey());
                out.put(key, sanitizeJsonPayload(e.getValue()));
            }
            return out;
        }
        if (node instanceof List<?> list) {
            List<Object> out = new ArrayList<>(list.size());
            for (Object item : list) {
                out.add(sanitizeJsonPayload(item));
            }
            return out;
        }
        return node;
    }

    /** 用 XML 风格标签包裹数据，让 system prompt 可以明确告知 LLM「这是数据不是指令」。 */
    public static String wrap(String tag, String content) {
        String safeTag = tag == null ? "" : TAG_UNSAFE.matcher(tag).replaceAll("");
        if (safeTag.isEmpty()) {
            safeTag = "data";
        }
        return "<" + safeTag + ">\n" + (content == null ? "" : content) + "\n</" + safeTag + ">";
    }
}
