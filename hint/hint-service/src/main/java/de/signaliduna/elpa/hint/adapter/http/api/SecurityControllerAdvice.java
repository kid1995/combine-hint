package de.signaliduna.elpa.hint.adapter.http.api;

import de.signaliduna.elpa.hint.adapter.app.exception.SiErrorMessage;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.WebRequest;

import java.security.Principal;

@ControllerAdvice
public class SecurityControllerAdvice {
	private static final Logger log = LoggerFactory.getLogger(SecurityControllerAdvice.class);

	@ExceptionHandler(AccessDeniedException.class)
	public ResponseEntity<SiErrorMessage> handleAccessDeniedException(AccessDeniedException ex, WebRequest request) {
		if (request != null) {
			Principal principal = request.getUserPrincipal();
			if (principal != null) {
				log.warn("Access denied for user: {}.", principal.getName(), ex);
			} else {
				// request is not null, but getUserPrincipal() returned null (e.g., unauthenticated access)
				log.warn("Access denied for unauthenticated access.", ex);
			}
		} else {
			// request object itself is null
			log.warn("Access denied. WebRequest object was null.", ex);
		}
		final SiErrorMessage siErrorMessage = new SiErrorMessage(ex.getMessage(), "Access denied for user.");
		return new ResponseEntity<>(siErrorMessage, HttpStatus.FORBIDDEN);
	}

	@ExceptionHandler(RuntimeException.class)
	public ResponseEntity<SiErrorMessage> handleRuntimeException(RuntimeException ex) {
		log.error("RuntimeException while processing hint.", ex);
		final SiErrorMessage siErrorMessage = new SiErrorMessage(ex.getMessage(), "General runtime exception.");
		return new ResponseEntity<>(siErrorMessage, HttpStatus.INTERNAL_SERVER_ERROR);
	}

	@ExceptionHandler(ConstraintViolationException.class)
	public ResponseEntity<SiErrorMessage> handleRuntimeException(ConstraintViolationException ex) {
		final SiErrorMessage siErrorMessage = new SiErrorMessage("Request invalid", ex.getMessage());
		return new ResponseEntity<>(siErrorMessage, HttpStatus.BAD_REQUEST);
	}
}
