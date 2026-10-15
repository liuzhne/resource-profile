package com.edu.mental.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.edu.mental.entity.Questionnaire;
import com.edu.mental.entity.Question;
import com.edu.mental.dto.QuestionnaireUpdateRequest;
import java.util.List;
import org.springframework.web.multipart.MultipartFile;

public interface QuestionnaireService {

    Page<Questionnaire> list(Integer page, Integer size);

    Questionnaire getById(Long id);

    void save(Questionnaire questionnaire);

    void update(Questionnaire questionnaire);
    void updateMetadata(QuestionnaireUpdateRequest request);

    void delete(Long id);

    void importQuestionsFromExcel(Long questionnaireId, MultipartFile file);
    List<Question> previewImport(Long questionnaireId, MultipartFile file);
}
