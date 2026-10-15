package com.edu.data.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import java.util.List;

@Data
@AllArgsConstructor
public class TodoPageDto {
    private Long total;
    private List<TodoItemDto> items;
}
