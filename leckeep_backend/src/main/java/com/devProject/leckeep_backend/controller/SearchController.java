package com.devProject.leckeep_backend.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.devProject.leckeep_backend.utils.StandardResponseDto;
import com.devProject.leckeep_backend.service.search.SearchService;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/search")
@RequiredArgsConstructor
public class SearchController {
    private final SearchService searchService;

    @GetMapping
    public StandardResponseDto search(
            @RequestParam(name = "q") String query,
            @RequestParam(required = false, defaultValue = "all") String scope
    ) {
        return new StandardResponseDto(200, "Search completed", searchService.search(query, scope));
    }
}
