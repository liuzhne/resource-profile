package com.edu.data.dto;

import lombok.Data;

/** Minimal staff directory projection; no contact, birth date, or academic/mental result. */
@Data
public class SearchResultDto {
    private String type;
    private Long id;
    private String name;
    private String code;
    private String deptName;
}
