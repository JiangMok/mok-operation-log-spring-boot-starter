package top.jiangmok.operationlog.desensitize.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.jiangmok.operationlog.desensitize.ParamDesensitizer;

import java.util.*;

/**
 * 默认参数脱敏器
 * <p>
 * 递归遍历 JSON 节点，将包含敏感关键词的字段值替换为 "***"。
 * 默认敏感关键词：password, token, secret, key, pwd, pass, credential
 * </p>
 *
 * @author mok
 */
public class DefaultParamDesensitizer implements ParamDesensitizer {

    private static final Logger log = LoggerFactory.getLogger(DefaultParamDesensitizer.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static final List<String> DEFAULT_SENSITIVE_KEYS = Arrays.asList(
            "password", "token", "secret", "key", "pwd", "pass", "credential"
    );

    /** 可被子类覆盖以自定义敏感词列表 */
    protected List<String> getSensitiveKeys() {
        return DEFAULT_SENSITIVE_KEYS;
    }

    @Override
    public String desensitize(String jsonParam) {
        if (jsonParam == null || jsonParam.isBlank()) {
            return jsonParam;
        }

        try {
            JsonNode jsonNode = OBJECT_MAPPER.readTree(jsonParam);
            JsonNode processedNode = desensitizeNode(jsonNode, getSensitiveKeys());
            return OBJECT_MAPPER.writeValueAsString(processedNode);
        } catch (Exception e) {
            log.debug("JSON 脱敏失败，返回原始字符串: {}", e.getMessage());
            return jsonParam;
        }
    }

    private JsonNode desensitizeNode(JsonNode node, List<String> sensitiveKeys) {
        if (node == null || node.isNull()) {
            return node;
        }

        if (node.isObject()) {
            ObjectNode objectNode = (ObjectNode) node;
            ObjectNode result = objectNode.deepCopy();
            List<String> fieldsToRemove = new ArrayList<>();

            Iterator<String> fieldNames = result.fieldNames();
            while (fieldNames.hasNext()) {
                String fieldName = fieldNames.next();
                if (isSensitiveField(fieldName, sensitiveKeys)) {
                    fieldsToRemove.add(fieldName);
                } else {
                    result.set(fieldName, desensitizeNode(result.get(fieldName), sensitiveKeys));
                }
            }
            fieldsToRemove.forEach(result::remove);
            return result;

        } else if (node.isArray()) {
            ArrayNode arrayNode = (ArrayNode) node;
            ArrayNode result = OBJECT_MAPPER.createArrayNode();
            for (JsonNode element : arrayNode) {
                result.add(desensitizeNode(element, sensitiveKeys));
            }
            return result;
        }

        return node;
    }

    private boolean isSensitiveField(String fieldName, List<String> sensitiveKeys) {
        if (fieldName == null || fieldName.isBlank() || sensitiveKeys.isEmpty()) {
            return false;
        }
        String lower = fieldName.toLowerCase();
        return sensitiveKeys.stream()
                .filter(k -> k != null && !k.isBlank())
                .anyMatch(lower::contains);
    }
}
