package com.edu.mental.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.edu.common.exception.BusinessException;
import com.edu.mental.dto.QuestionnaireUpdateRequest;
import com.edu.mental.service.MentalAnswerContract;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.edu.mental.entity.Question;
import com.edu.mental.entity.Questionnaire;
import com.edu.mental.mapper.QuestionnaireMapper;
import com.edu.mental.service.QuestionService;
import com.edu.mental.service.QuestionnaireService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class QuestionnaireServiceImpl implements QuestionnaireService {

    private final QuestionnaireMapper questionnaireMapper;
    private final QuestionService questionService;
    private final ObjectMapper objectMapper;

    @Override
    public Page<Questionnaire> list(Integer page, Integer size) {
        if(page == null || page < 1 || size == null || size < 1 || size > 100) throw new BusinessException(400,"分页参数无效");
        Page<Questionnaire> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<Questionnaire> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByDesc(Questionnaire::getCreateTime);
        Page<Questionnaire> result=questionnaireMapper.selectPage(pageParam, wrapper);
        result.getRecords().forEach(q -> q.setQuestions(questionService.listByQuestionnaire(q.getId()).size()));
        return result;
    }

    @Override
    public Questionnaire getById(Long id) {
        Questionnaire q=questionnaireMapper.selectById(id);
        if(q==null) throw new BusinessException(404,"问卷不存在");
        q.setQuestions(questionService.listByQuestionnaire(id).size()); return q;
    }

    @Override
    public void save(Questionnaire questionnaire) {
        validateMetadata(questionnaire);
        if(!Integer.valueOf(0).equals(questionnaire.getStatus())) throw new BusinessException(400,"新问卷请先保存草稿，设计题目和等级规则后发布");
        questionnaire.setId(null); questionnaire.setQuestions(0);
        questionnaireMapper.insert(questionnaire);
    }

    @Override
    public void update(Questionnaire questionnaire) {
        questionnaireMapper.updateById(questionnaire);
    }

    @Override @Transactional
    public void updateMetadata(QuestionnaireUpdateRequest request) {
        Questionnaire current=questionnaireMapper.selectById(request.getId());
        if(current==null) throw new BusinessException(404,"问卷不存在");
        if(request.getTitle()!=null) current.setTitle(request.getTitle());
        if(request.getType()!=null) current.setType(request.getType());
        if(request.getDescription()!=null) current.setDescription(request.getDescription());
        if(request.getStatus()!=null) current.setStatus(request.getStatus());
        if(request.getLevelRules()!=null) current.setLevelRules(request.getLevelRules());
        if(request.isStartTimePresent()) current.setStartTime(request.getStartTime());
        if(request.isEndTimePresent()) current.setEndTime(request.getEndTime());
        validateMetadata(current);
        List<Question> questions=questionService.listByQuestionnaire(current.getId());
        if(Integer.valueOf(1).equals(current.getStatus())) {
            if(questions.isEmpty()) throw new BusinessException(400,"发布前请先设计题目");
            MentalAnswerContract.validatePublished(current,questions);
        }
        current.setQuestions(questions.size());
        var update=new LambdaUpdateWrapper<Questionnaire>().eq(Questionnaire::getId,current.getId())
                .set(Questionnaire::getStartTime,current.getStartTime()).set(Questionnaire::getEndTime,current.getEndTime());
        // Dates are written by the wrapper so explicit null clears them; avoid duplicate SET columns.
        Questionnaire patch = new Questionnaire();
        org.springframework.beans.BeanUtils.copyProperties(current, patch);
        patch.setStartTime(null); patch.setEndTime(null);
        questionnaireMapper.update(patch,update);
    }

    private void validateMetadata(Questionnaire q) {
        if(q.getTitle()==null || q.getTitle().isBlank() || q.getTitle().length()>200) throw new BusinessException(400,"标题不能为空且不超过200字");
        if(q.getType()!=null && q.getType().length()>50) throw new BusinessException(400,"类型不超过50字");
        if(q.getDescription()!=null && q.getDescription().length()>500) throw new BusinessException(400,"描述不超过500字");
        if(q.getStatus()==null || q.getStatus()<0 || q.getStatus()>2) throw new BusinessException(400,"状态只能为草稿/开放/结束");
        if(q.getStartTime()!=null && q.getEndTime()!=null && q.getStartTime().isAfter(q.getEndTime())) throw new BusinessException(400,"开始日期不能晚于结束日期");
        if(q.getLevelRules()!=null && !q.getLevelRules().isBlank()) MentalAnswerContract.levels(q.getLevelRules());
    }

    @Override public List<Question> previewImport(Long questionnaireId,MultipartFile file) {
        if(questionnaireMapper.selectById(questionnaireId)==null) throw new BusinessException(404,"问卷不存在");
        if(file==null || file.isEmpty() || file.getSize()>10*1024*1024) throw new BusinessException(400,"请选择不超过10MB的Excel文件");
        List<Question> questions=parseExcel(file);
        if(questions.isEmpty() || questions.size()>500) throw new BusinessException(400,"导入题目数必须为1到500");
        questions.forEach(MentalAnswerContract::validateQuestion);
        Questionnaire current = questionnaireMapper.selectById(questionnaireId);
        if(Integer.valueOf(1).equals(current.getStatus())) MentalAnswerContract.validatePublished(current, questions);
        return questions;
    }

    @Override
    public void delete(Long id) {
        questionnaireMapper.deleteById(id);
    }

    @Override
    @Transactional
    public void importQuestionsFromExcel(Long questionnaireId, MultipartFile file) {
        Questionnaire questionnaire = questionnaireMapper.selectById(questionnaireId);
        if (questionnaire == null) {
            throw new RuntimeException("问卷不存在");
        }

        List<Question> questions = previewImport(questionnaireId,file);
        if (questions.isEmpty()) {
            throw new RuntimeException("Excel文件中没有有效题目");
        }

        // 先删除该问卷已有题目
        questionService.deleteByQuestionnaireId(questionnaireId);

        // 批量保存新题目
        questionService.saveBatch(questionnaireId, questions);

        // 更新问卷题目数
        questionnaire.setQuestions(questions.size());
        questionnaireMapper.updateById(questionnaire);
    }

    private List<Question> parseExcel(MultipartFile file) {
        try (Workbook workbook = WorkbookFactory.create(file.getInputStream())) {
            Sheet sheet = workbook.getSheetAt(0);
            if(sheet.getLastRowNum()>500) throw new BusinessException(400,"最多导入500行题目");
            List<Question> questions = new ArrayList<>();

            // 跳过表头行，从第二行开始读取
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null || getCellStringValue(row.getCell(0)).isEmpty()) {
                    continue;
                }

                Question question = new Question();

                // 题号
                String sortOrderStr = getCellStringValue(row.getCell(0));
                try {
                    question.setSortOrder(Integer.parseInt(sortOrderStr));
                } catch (NumberFormatException e) {
                    question.setSortOrder(i);
                }

                // 题目内容
                question.setContent(getCellStringValue(row.getCell(1)));
                if (question.getContent().isEmpty()) {
                    continue;
                }

                // 题目类型
                String questionType = getCellStringValue(row.getCell(2));
                question.setQuestionType(normalizeQuestionType(questionType));

                // 选项和分值 (choice types)
                if ("single_choice".equals(question.getQuestionType()) || "multiple_choice".equals(question.getQuestionType())) {
                    List<Map<String, Object>> options = new ArrayList<>();
                    Map<String, Object> scoringRules = new LinkedHashMap<>();

                    String[] optionValues = {"A", "B", "C", "D", "E", "F"};
                    for (int j = 0; j < 6; j++) {
                        String optionText = getCellStringValue(row.getCell(3 + j));
                        if (!optionText.isEmpty()) {
                            Map<String, Object> opt = new LinkedHashMap<>();
                            opt.put("label", optionText);
                            opt.put("value", optionValues[j]);

                            // 对应分值
                            String scoreStr = getCellStringValue(row.getCell(9 + j));
                            if (!scoreStr.isEmpty()) {
                                try {
                                    int score=Integer.parseInt(scoreStr);
                                    scoringRules.put(optionValues[j],score); opt.put("score",score);
                                } catch (NumberFormatException e) {
                                    throw new BusinessException(400,"第"+(i+1)+"行选项分值必须为整数");
                                }
                            } else throw new BusinessException(400,"第"+(i+1)+"行选项缺少分值");
                            options.add(opt);
                        }
                    }

                    if (!options.isEmpty()) {
                        question.setOptions(toJson(options));
                    }
                    if (!scoringRules.isEmpty()) {
                        question.setScoringRules(toJson(scoringRules));
                    }
                }

                // 量表类型: 选项A 为范围 "1~10", 选项B=min标签, 选项C=max标签
                if ("scale".equals(question.getQuestionType())) {
                    String range = getCellStringValue(row.getCell(3));
                    if (!range.isEmpty() && range.contains("~")) {
                        String[] parts = range.split("~", -1);
                        if(parts.length != 2) throw new BusinessException(400, "第"+(i+1)+"行量表范围必须为最小值~最大值");
                        try {
                            question.setScaleMin(Integer.parseInt(parts[0].trim()));
                            question.setScaleMax(Integer.parseInt(parts[1].trim()));
                        } catch (NumberFormatException e) {
                            throw new BusinessException(400,"第"+(i+1)+"行量表范围无效");
                        }
                    }

                    String minLabel = getCellStringValue(row.getCell(4));
                    String maxLabel = getCellStringValue(row.getCell(5));
                    if (!minLabel.isEmpty() || !maxLabel.isEmpty()) {
                        Map<String, String> labels = new LinkedHashMap<>();
                        labels.put("min", minLabel);
                        labels.put("max", maxLabel);
                        question.setScaleLabels(toJson(labels));
                    }
                }

                // 是否必答
                String requiredStr = getCellStringValue(row.getCell(15));
                question.setRequired("否".equals(requiredStr) || "N".equalsIgnoreCase(requiredStr) || "0".equals(requiredStr) ? 0 : 1);

                questions.add(question);
            }

            return questions;
        } catch (BusinessException e) { throw e;
        } catch (Exception e) {
            throw new BusinessException(400,"Excel文件无法解析，请检查格式与列顺序");
        }
    }

    private String getCellStringValue(Cell cell) {
        if (cell == null) {
            return "";
        }
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue().trim();
            case NUMERIC -> {
                double val = cell.getNumericCellValue();
                if (val == (long) val) {
                    yield String.valueOf((long) val);
                }
                yield String.valueOf(val);
            }
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            default -> "";
        };
    }

    private String normalizeQuestionType(String type) {
        if (type == null || type.isEmpty()) {
            return "single_choice";
        }
        return switch (type) {
            case "单选", "single_choice" -> "single_choice";
            case "多选", "multiple_choice" -> "multiple_choice";
            case "文本", "填空", "text" -> "text";
            case "量表", "scale" -> "scale";
            default -> throw new BusinessException(400,"不支持的导入题型："+type);
        };
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            return "";
        }
    }
}
