package de.signaliduna.elpa.hint.adapter.http.api;

import de.signaliduna.elpa.hint.model.HintDto;
import de.signaliduna.elpa.hint.core.HintService;
import de.signaliduna.elpa.hint.model.HintParams;
import de.signaliduna.elpa.hint.model.HintQueryRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("hints")
@PreAuthorize("isAuthenticated() and isAuthorizedUser()")
@Validated
public class HintApi {
	private static final Logger log = LoggerFactory.getLogger(HintApi.class);

	private final HintService hintService;

	public HintApi(HintService hintService) {
		this.hintService = hintService;
	}

	@GetMapping
	@Operation(summary = "Returns all hints.")
	@ApiResponses(value = {
		@ApiResponse(responseCode = "200",
			description = "OK",
			content = @Content(schema = @Schema(implementation = HintDto.class))),
		@ApiResponse(responseCode = "500",
			description = "Internal server error")
	})
	public List<HintDto> getHints(@ModelAttribute HintQueryRequest request) {
		Map<HintParams, Object> queryParams = request.toQueryParams();
		log.info("Fetching hints with query params: {}", queryParams);
		return hintService.getHints(queryParams);
	}

	@GetMapping("/{id}")
	@Operation(summary = "Returns hint by id.")
	@ApiResponses(value = {
		@ApiResponse(responseCode = "200",
			description = "OK",
			content = @Content(schema = @Schema(implementation = HintDto.class))),
		@ApiResponse(responseCode = "404",
			description = "No hint for id"),
		@ApiResponse(responseCode = "500",
			description = "Internal server error")
	})
	public ResponseEntity<HintDto> getHintById(@PathVariable("id") String id) {
		log.info("Request for hint with id: '{}'", id);
		return this.hintService.getHintById(id).map(ResponseEntity::ok)
			.orElse(ResponseEntity.notFound().build());
	}

	@PostMapping
	@Operation(summary = "Saves given hints.")
	@ApiResponses(value = {
		@ApiResponse(responseCode = "201",
			description = "CREATED"),
		@ApiResponse(responseCode = "500",
			description = "Internal server error")
	})
	public ResponseEntity<List<HintDto>> saveHints(@NotNull @RequestBody @Size(max = 100, message = "List size must be between 0 and 100") List<HintDto> hintDtos) {
		log.info("Going to save received hints with process ids: '{}', .", hintDtos.stream().map(HintDto::processId).toList());
		this.hintService.saveHints(hintDtos);
		return ResponseEntity.status(HttpStatusCode.valueOf(201)).body(hintDtos);
	}
}
