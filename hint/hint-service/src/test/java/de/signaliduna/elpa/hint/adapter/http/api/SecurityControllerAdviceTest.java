package de.signaliduna.elpa.hint.adapter.http.api;

import de.signaliduna.elpa.hint.adapter.app.exception.SiErrorMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.context.request.WebRequest;

import java.security.Principal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SecurityControllerAdviceTest {
	private static final String RUNTIME_EXCEPTION_MESSAGE = "Runtime exception message";
	private static final String ACCESS_DENIED_EXCEPTION_MESSAGE = "Access denied exception message";
	@InjectMocks
    SecurityControllerAdvice classUnderTest;

	@Mock
	RuntimeException runtimeExceptionMock;
	@Mock
	AccessDeniedException accessDeniedExceptionMock;
	@Mock
	WebRequest webRequestMock;
	@Mock
	Principal principalMock;

	@Test
	void handleAccessDeniedException() {
		when(accessDeniedExceptionMock.getMessage()).thenReturn(ACCESS_DENIED_EXCEPTION_MESSAGE);
		when(webRequestMock.getUserPrincipal()).thenReturn(principalMock);
		when(principalMock.getName()).thenReturn("S12345");

		ResponseEntity<SiErrorMessage> siErrorMessageResponseEntity = classUnderTest.handleAccessDeniedException(accessDeniedExceptionMock, webRequestMock);

		assertThat(siErrorMessageResponseEntity.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
		assertThat(siErrorMessageResponseEntity.getBody()).isEqualTo(new SiErrorMessage(ACCESS_DENIED_EXCEPTION_MESSAGE, "Access denied for user."));
	}

	@Test
	void handleAccessDeniedExceptionMissingPrincipal() {
		when(accessDeniedExceptionMock.getMessage()).thenReturn(ACCESS_DENIED_EXCEPTION_MESSAGE);

		ResponseEntity<SiErrorMessage> siErrorMessageResponseEntity = classUnderTest.handleAccessDeniedException(accessDeniedExceptionMock, webRequestMock);

		assertThat(siErrorMessageResponseEntity.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
		assertThat(siErrorMessageResponseEntity.getBody()).isEqualTo(new SiErrorMessage(ACCESS_DENIED_EXCEPTION_MESSAGE, "Access denied for user."));
	}

	@Test
	void handleAccessDeniedExceptionMissingRequest() {
		when(accessDeniedExceptionMock.getMessage()).thenReturn(ACCESS_DENIED_EXCEPTION_MESSAGE);

		ResponseEntity<SiErrorMessage> siErrorMessageResponseEntity = classUnderTest.handleAccessDeniedException(accessDeniedExceptionMock, null);

		assertThat(siErrorMessageResponseEntity.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
		assertThat(siErrorMessageResponseEntity.getBody()).isEqualTo(new SiErrorMessage(ACCESS_DENIED_EXCEPTION_MESSAGE, "Access denied for user."));
	}

	@Test
	void handleRuntimeException() {
		when(runtimeExceptionMock.getMessage()).thenReturn(RUNTIME_EXCEPTION_MESSAGE);

		ResponseEntity<SiErrorMessage> siErrorMessageResponseEntity = classUnderTest.handleRuntimeException(runtimeExceptionMock);

		assertThat(siErrorMessageResponseEntity.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
		assertThat(siErrorMessageResponseEntity.getBody()).isEqualTo(new SiErrorMessage(RUNTIME_EXCEPTION_MESSAGE, "General runtime exception."));
	}
}
