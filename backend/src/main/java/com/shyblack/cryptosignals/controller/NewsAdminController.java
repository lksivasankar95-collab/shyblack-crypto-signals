package com.shyblack.cryptosignals.controller;

import com.shyblack.cryptosignals.dto.news.NewsSyncResponse;
import com.shyblack.cryptosignals.service.news.NewsIngestionService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/news")
@RequiredArgsConstructor
public class NewsAdminController {

	private final NewsIngestionService ingestionService;

	@PostMapping("/sync")
	@PreAuthorize("hasRole('ADMIN')")
	public List<NewsSyncResponse> sync() {
		return ingestionService.syncAll();
	}
}