package com.edu.agent.service;

import com.edu.agent.dto.FeedbackRequest;
import com.edu.agent.entity.InterventionFeedback;

import java.util.List;

/**
 * I-5：干预反馈闭环服务。
 */
public interface InterventionFeedbackService {

    /** 提交反馈，返回新记录 id。参数非法抛 IllegalArgumentException。 */
    Long submit(FeedbackRequest req, Long counselorId);

    /** 某月（yyyy-MM）反馈明细，按创建时间升序。 */
    List<InterventionFeedback> monthlyReport(String month);

    /** 某月反馈导出为 CSV 文本（UTF-8）。 */
    String exportCsv(String month);
}
