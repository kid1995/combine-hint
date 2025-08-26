package de.signaliduna.elpa.hint.http.client;

import de.signaliduna.elpa.hint.model.HintDto;
import de.signaliduna.elpa.hint.model.HintSearchRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.Optional;

/**
 * FeignClient interface defining the operations of the <i>Elpa hint-service</i>.
 */
public interface HintClient {

	@GetMapping("/api/hints/{id}")
	ResponseEntity<HintDto> getHintById(@PathVariable String id);

	@GetMapping("/api/hints")
	ResponseEntity<List<HintDto>> getHints(
		@RequestParam("hintSource") Optional<String> hintSource,
		@RequestParam("hintTextOriginal") Optional<String> hintTextOriginal,
		@RequestParam("hintCategory") Optional<String> hintCategory,
		@RequestParam("showToUser") Optional<Boolean> showToUser,
		@RequestParam("processId") Optional<String> processId,
		@RequestParam("processVersion") Optional<String> processVersion,
		@RequestParam("resourceId") Optional<String> resourceId
	);

	default ResponseEntity<List<HintDto>> searchHints(HintSearchRequest request) {
		return getHints(
			Optional.ofNullable(request.hintSource()),
			Optional.ofNullable(request.hintTextOriginal()),
			Optional.ofNullable(request.hintCategory()),
			Optional.ofNullable(request.showToUser()),
			Optional.ofNullable(request.processId()),
			Optional.ofNullable(request.processVersion()),
			Optional.ofNullable(request.resourceId())
		);
	}

	@PostMapping("/api/hints")
	ResponseEntity<Void> saveHints(List<HintDto> hints);
}
