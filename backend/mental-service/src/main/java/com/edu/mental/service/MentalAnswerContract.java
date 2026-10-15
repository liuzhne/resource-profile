package com.edu.mental.service;

import com.edu.common.exception.BusinessException;
import com.edu.mental.dto.SubmitAnswerRequest;
import com.edu.mental.dto.LevelRule;
import com.edu.mental.dto.QuestionnaireFullDto;
import com.edu.mental.entity.Question;
import com.edu.mental.entity.Questionnaire;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.core.StreamReadFeature;
import java.util.*;

/** Shared validation for authoring, Excel preview and the authoritative submission calculator. */
public final class MentalAnswerContract {
    private static final ObjectMapper JSON = JsonMapper.builder().disable(com.fasterxml.jackson.databind.DeserializationFeature.ACCEPT_FLOAT_AS_INT).enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).findAndAddModules().build();
    private MentalAnswerContract() {}
    private static void require(boolean condition, String message) {
        if (!condition) throw new BusinessException(400, message);
    }
    public static List<Map<String, Object>> options(Question q) {
        if (q.getOptions() == null || q.getOptions().isBlank()) return List.of();
        try { List<Map<String,Object>> result = JSON.readValue(q.getOptions(), new TypeReference<>() {}); require(result != null, "选项数组不能为null"); return result; }
        catch (Exception e) { throw new BusinessException(400, "题目选项不是有效数组"); }
    }
    public static Map<String, Object> rules(Question q) {
        if (q.getScoringRules() == null || q.getScoringRules().isBlank()) return Map.of();
        try { Map<String,Object> result = JSON.readValue(q.getScoringRules(), new TypeReference<>() {}); require(result != null, "计分对象不能为null"); return result; }
        catch (Exception e) { throw new BusinessException(400, "题目计分规则不是有效对象"); }
    }
    private static int integer(Object value, String message) {
        require(value instanceof Number, message);
        Number n = (Number) value;
        require(Double.isFinite(n.doubleValue()) && n.doubleValue() == n.intValue() && Math.abs(n.longValue()) <= 100000, message);
        return n.intValue();
    }
    private static int add(int total,int value) {
        try { return Math.addExact(total,value); }
        catch(ArithmeticException e) { throw new BusinessException(400,"问卷计分范围过大"); }
    }
    private static int choiceScore(Map<String, Object> option, int index, Map<String, Object> rules) {
        Object fromRule = rules.get(String.valueOf(option.getOrDefault("value", String.valueOf(index))));
        Object own = option.get("score");
        require(own != null || fromRule != null, "选项缺少计分值，不能按零分发布");
        int score = integer(own != null ? own : fromRule, "选项分值必须是有界整数");
        if (own != null && fromRule != null) require(score == integer(fromRule, "计分值必须为整数"), "选项与计分规则分值冲突");
        return score;
    }
    public static void validateQuestion(Question q) {
        require(q != null && q.getContent() != null && !q.getContent().isBlank() && q.getContent().length() <= 1000, "题干不能为空且不超过1000字");
        require(q.getQuestionType()!=null && Set.of("single_choice", "multiple_choice", "text", "scale").contains(q.getQuestionType()), "不支持的题型");
        require(q.getRequired() == null || q.getRequired() == 0 || q.getRequired() == 1, "必答标记只能为0或1");
        require(q.getSortOrder() == null || q.getSortOrder() > 0, "排序必须为正整数");
        if ("text".equals(q.getQuestionType())) return;
        Map<String, Object> mapping = rules(q);
        if ("scale".equals(q.getQuestionType())) {
            require(q.getScaleMin() != null && q.getScaleMax() != null && q.getScaleMin() <= q.getScaleMax()
                    && q.getScaleMin() >= -1000 && q.getScaleMax() <= 1000, "量表范围必须是-1000到1000以内的整数闭区间");
            if (!mapping.isEmpty()) for (int v = q.getScaleMin(); v <= q.getScaleMax(); v++)
                integer(mapping.get(String.valueOf(v)), "量表计分映射必须覆盖范围内每个数值");
            if (q.getScaleLabels() != null) {
                try {
                    Map<String, Object> labels = JSON.readValue(q.getScaleLabels(), new TypeReference<>() {});
                    for (Object v : labels.values()) require(v instanceof String && ((String)v).length() <= 200, "量表标签必须是短文本");
                } catch (BusinessException e) { throw e; }
                catch (Exception e) { throw new BusinessException(400, "量表标签不是有效对象"); }
            }
            return;
        }
        List<Map<String, Object>> opts = options(q);
        require(opts.size() >= 2 && opts.size() <= 100, "选择题需要2到100个选项");
        Set<String> values = new HashSet<>();
        for (int i=0; i<opts.size(); i++) {
            Map<String, Object> opt = opts.get(i);
            require(opt != null && opt.get("label") instanceof String && !((String)opt.get("label")).isBlank()
                    && ((String)opt.get("label")).length() <= 500, "选项文字不能为空且不超过500字");
            require(values.add(String.valueOf(opt.getOrDefault("value", String.valueOf(i)))), "选项值不能重复");
            choiceScore(opt, i, mapping);
        }
    }
    public static int score(List<Question> questions, List<SubmitAnswerRequest.AnswerItem> answers) {
        require(questions != null && !questions.isEmpty(), "问卷尚无题目，不能提交");
        require(questions.size() <= 500 && answers != null && answers.size() <= questions.size(), "答卷题目数量不正确");
        Map<Long, SubmitAnswerRequest.AnswerItem> byId = new HashMap<>();
        Set<Long> expected = new HashSet<>();
        for (Question q : questions) { validateQuestion(q); expected.add(q.getId()); }
        for (var a : answers) {
            require(a != null && a.getQuestionId() != null && expected.contains(a.getQuestionId()), "答案包含不属于本问卷的题目");
            require(byId.putIfAbsent(a.getQuestionId(), a) == null, "同一道题不能重复提交");
        }
        int total = 0;
        for (Question q : questions) {
            var a = byId.get(q.getId());
            boolean required = !Integer.valueOf(0).equals(q.getRequired());
            if (a == null) { require(!required, "必答题未作答"); continue; }
            List<Integer> picks = a.getOptionIndices() == null ? List.of() : a.getOptionIndices();
            String text = a.getText() == null ? "" : a.getText();
            require(text.length() <= 5000, "简答不超过5000字");
            String type = q.getQuestionType();
            if ("text".equals(type)) {
                require(picks.isEmpty() && a.getScaleValue() == null, "简答题不能提交选项或量表值");
                require(!required || !text.isBlank(), "必答简答题未作答");
            } else if ("scale".equals(type)) {
                require(picks.isEmpty() && text.isBlank(), "量表题只接受整数数值");
                Integer value = a.getScaleValue();
                require(!required || value != null, "必答量表题未作答");
                if (value != null) {
                    require(value >= q.getScaleMin() && value <= q.getScaleMax(), "量表值超出范围");
                    Map<String, Object> mapping = rules(q);
                    total = add(total, mapping.isEmpty() ? value : integer(mapping.get(String.valueOf(value)), "量表计分缺失"));
                }
            } else {
                require(text.isBlank() && a.getScaleValue() == null, "选择题不能提交文本或量表值");
                require(!required || !picks.isEmpty(), "必答选择题未作答");
                require(!"single_choice".equals(type) || picks.size() <= 1, "单选题只能选择一个选项");
                require(new HashSet<>(picks).size() == picks.size(), "选项不能重复");
                var opts = options(q); var mapping = rules(q);
                for (Integer idx : picks) {
                    require(idx != null && idx >= 0 && idx < opts.size(), "选项索引超出范围");
                    total = add(total, choiceScore(opts.get(idx), idx, mapping));
                }
            }
        }
        return total;
    }
    public static List<LevelRule> levels(String raw) {
        try {
            List<LevelRule> levels = JSON.readValue(raw == null ? "[]" : raw, new TypeReference<>() {});
            require(!levels.isEmpty(), "发布问卷前必须配置等级规则");
            Set<Integer> mins = new HashSet<>(); Set<String> names = new HashSet<>();
            for (var rule : levels) require(rule != null && rule.getMinScore() != null && rule.getLevel() != null
                    && Set.of("正常","轻度","中度","重度","高危").contains(rule.getLevel())
                    && mins.add(rule.getMinScore()) && names.add(rule.getLevel()), "等级名/阈值重复或无效");
            return levels;
        } catch (BusinessException e) { throw e; }
        catch (Exception e) { throw new BusinessException(400, "等级规则不是有效JSON数组"); }
    }
    public static void validatePublished(Questionnaire questionnaire,List<Question> questions) {
        require(questions!=null && !questions.isEmpty() && questions.size()<=500,"发布前需要1到500道题目");
        int minimum=0;
        for(Question q:questions) {
            validateQuestion(q); int low=0;
            if("scale".equals(q.getQuestionType())) {
                var mapping=rules(q);
                low=mapping.isEmpty()?q.getScaleMin():mapping.values().stream().mapToInt(v -> integer(v,"量表计分无效")).min().orElse(0);
            } else if(!"text".equals(q.getQuestionType())) {
                var opts=options(q); var mapping=rules(q); List<Integer> scores=new ArrayList<>();
                for(int i=0;i<opts.size();i++) scores.add(choiceScore(opts.get(i),i,mapping));
                low=scores.stream().mapToInt(Integer::intValue).min().orElse(0);
                if("multiple_choice".equals(q.getQuestionType()) && low<0) low=scores.stream().filter(v -> v<0).mapToInt(Integer::intValue).sum();
            }
            if(Integer.valueOf(0).equals(q.getRequired())) low=Math.min(0,low);
            minimum=add(minimum,low);
        }
        int threshold=levels(questionnaire.getLevelRules()).stream().mapToInt(LevelRule::getMinScore).min().orElseThrow();
        require(threshold<=minimum,"等级规则必须覆盖问卷的最低可能得分");
    }
    /** Copy before stripping; do not mutate database objects or the stored scoring contract. */
    public static QuestionnaireFullDto respondentSnapshot(Questionnaire questionnaire, List<Question> questions) {
        QuestionnaireFullDto dto = new QuestionnaireFullDto();
        Questionnaire q = JSON.convertValue(questionnaire, Questionnaire.class);
        q.setLevelRules(null); q.setCreatorId(null); dto.setQuestionnaire(q); dto.setLevelRules(List.of());
        List<Question> safe = new ArrayList<>();
        for (Question question : questions) {
            Question copy = JSON.convertValue(question, Question.class); copy.setScoringRules(null);
            var opts = options(copy);
            List<Map<String,Object>> labels = new ArrayList<>();
            for (var o : opts) {
                require(o != null && o.get("label") instanceof String && !((String)o.get("label")).isBlank(), "题目选项缺少有效文字");
                labels.add(Map.of("label", o.get("label")));
            }
            try { copy.setOptions(labels.isEmpty() ? null : JSON.writeValueAsString(labels)); }
            catch (Exception e) { throw new IllegalStateException(e); }
            safe.add(copy);
        }
        dto.setQuestions(safe); return dto;
    }
}
